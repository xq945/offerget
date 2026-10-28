# OfferGet AI 接入设计

> 版本 v0.3 · 2026-10-28 · 状态：第 2、3 节均已实现并验证
> 范围：校招信息自动采集、AI 简历定制，以及两者共用的基础设施

---

## 0. 总览

### 0.1 现状

| 能力 | 当前实现 | 说明 |
|---|---|---|
| AI 匹配 | 本地规则引擎 `AiMatchEngine` | 70 词技能词典 + 加权命中率，不上传简历 |
| 简历优化 | 本地规则引擎 `ResumeOptimizeEngine` | 仅生成文字建议，不改简历；`scoreAfter` 为公式估算 |
| 校招信息库 | 手工维护 12 条 `company_calendar` | 公共只读，无自动更新 |
| DeepSeek | 配置与 `RestClient` Bean 就绪 | 尚未被业务调用 |

### 0.2 本设计要解决的两件事

1. **校招信息采集**：按关注公司定向搜索官网，抽取岗位与项目时间线，审核后入库。
2. **AI 简历定制**：按目标岗位 JD 逐段改写简历，用户审阅确认后生成新版本。

两者共用：LLM 调用封装、JSON 结构化输出、确定性校验层。

### 0.3 已完成的验证

2026-10-27 实测 DeepSeek Search / Fetch / chat 三段链路（详见 memory `deepseek-search-fetch-verify`）：

- Search `POST /v1/tools/search`、Fetch `POST /v1/tools/fetch` 可用，每 1000 次成功请求 5 元
- `site:` 运算符精确有效；自然搜索无法可靠识别官网
- `after:` 会误杀无发布日期的页面，**不使用**
- Fetch 能穿透 SPA；但首页/导航页是空壳
- LLM 抽取 JSON 稳定，但会把页面 URL 冒充 `apply_url`
- 单公司一次采集成本 < 0.05 元，限流 5 QPS

---

## 1. 公共基础设施

### 1.1 AiClient

复用现有 [`DeepSeekConfig`](../offerget-server/src/main/java/com/offerget/config/DeepSeekConfig.java) 的 `RestClient` Bean，新增一个薄封装：

```java
public interface AiClient {
    String chat(List<ChatMessage> messages, boolean jsonMode);          // POST /chat/completions
    List<SearchResult> search(String query, int count, boolean needContent); // POST /v1/tools/search
    FetchResult fetch(String url);                                       // POST /v1/tools/fetch
}
```

要点：

- **配置守卫**：调用前检查 `DeepSeekProperties.configured()`，未配置时抛 `BusinessException`，提示「未配置 AI 密钥」
- **超时**：chat 120s，search 30s，fetch 90s（现有 Bean 的 60s 读超时对 fetch 偏紧，需单独配置或改用独立 RestClient）
- **限流**：采集任务串行执行，天然低于 5 QPS；如需并发，用 `Semaphore(3)` 控制
- **重试**：仅对网络错误和 5xx 重试 1 次，4xx 不重试
- **日志**：记录 requestId、耗时、token 用量、调用类型；**不记录简历正文和密钥**。不做成本记账表和上限熔断——单次成本低于 0.05 元，量级不构成风险，日志足以在异常时定位
- **降级**：AI 不可用时，采集与定制功能返回明确错误，不影响现有匹配/优化/投递主流程

### 1.2 结构化输出约定

所有抽取类调用统一：

- 请求带 `response_format: {"type": "json_object"}`
- Prompt 中给出完整 JSON 示例（含 `null` 字段的写法）
- 每个结论字段必须附 `evidence` 原文片段
- 解析失败重试 1 次；仍失败则该条不落库，记录原始响应（截断）便于排查
- 用 Jackson 严格解析，字段缺失不报错（视为 null），类型错误直接拒绝

统一返回外壳：

```json
{
  "items": [ { "…": "…", "evidence": "原文片段" } ],
  "page_type": "POSITION | PROJECT | EVENT | NAV",
  "notes": "模型对不确定项的说明"
}
```

### 1.3 确定性校验层

模型输出一律视为不可信输入，落库前必须过校验：

| 校验器 | 职责 | 适用 |
|---|---|---|
| `DateFieldValidator` | 日期格式、范围（今-30 天 ~ 今+400 天）、`open_date <= deadline`、日期必须能规范化后在 evidence 中找到 | 采集 |
| `DomainValidator` | `apply_url` 的 host 必须在该公司官网域内；等于 `source_url` 时置 null | 采集 |
| `GraduationValidator` | 批次/届别与目标年级一致；页面出现其他届别时丢弃该条 | 采集 |
| `DedupeValidator` | `(company, job_title, batch)` 唯一；命中已发布条目时走「更新期限」而非新增 | 采集 |
| `FactValidator` | 改写稿中的技能词、数字、日期、公司名必须能在原简历中找到出处 | 简历定制 |

---

## 2. 校招信息采集

### 2.1 范围与决策

| 维度 | 决定 |
|---|---|
| 范围 | 用户维护的「关注公司」清单，官网定向采集 |
| 触发 | 手动，单公司一次 |
| 入库 | 草稿确认制，人工审核后写入 `company_calendar` |
| 采集单元 | L1 岗位详情 + L2 招聘项目时间线 |
| 宣讲会 | **不采集**，识别到当日志丢弃 |
| 模型链路 | Search → Fetch → LLM 抽取（显式三步，不用托管 web_search） |

### 2.2 数据模型

#### 新表 `crawl_watch_company`

```sql
CREATE TABLE crawl_watch_company (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id                BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    company                VARCHAR(100)    NOT NULL COMMENT '公司名',
    official_domain        VARCHAR(200)             DEFAULT NULL COMMENT '官网域名，如 campus.jd.com',
    industry               VARCHAR(50)              DEFAULT NULL,
    city                   VARCHAR(50)              DEFAULT NULL,
    target_graduation_year SMALLINT UNSIGNED       DEFAULT NULL COMMENT '目标届别，默认取用户毕业年份',
    enabled                TINYINT(1)      NOT NULL DEFAULT 1,
    last_crawled_at        DATETIME                 DEFAULT NULL,
    created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_watch_user_domain (user_id, official_domain)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校招关注公司';
```

#### 新表 `crawl_draft`

```sql
CREATE TABLE crawl_draft (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    watch_company_id BIGINT UNSIGNED NOT NULL,
    info_type        VARCHAR(20)     NOT NULL COMMENT 'POSITION / PROJECT',
    company          VARCHAR(100)    NOT NULL,
    industry         VARCHAR(50)              DEFAULT NULL,
    city             VARCHAR(50)              DEFAULT NULL,
    job_title        VARCHAR(120)    NOT NULL COMMENT '岗位名或项目名',
    batch            VARCHAR(30)              DEFAULT NULL COMMENT '提前批 / 正式批 / 补录',
    open_date        DATE                     DEFAULT NULL,
    deadline         DATE                     DEFAULT NULL,
    apply_url        VARCHAR(300)             DEFAULT NULL,
    source_url       VARCHAR(300)    NOT NULL COMMENT '抽取来源页',
    tags             VARCHAR(200)             DEFAULT NULL COMMENT '逗号分隔',
    description      VARCHAR(500)             DEFAULT NULL,
    evidence_json    TEXT                     DEFAULT NULL COMMENT '逐字段原文证据',
    confidence       DECIMAL(3,2)             DEFAULT NULL COMMENT '0.00-1.00',
    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / APPROVED / REJECTED / STALE',
    crawled_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at      DATETIME                 DEFAULT NULL,
    published_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT '确认后写入的 company_calendar.id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_draft_unique (watch_company_id, job_title, batch),
    KEY idx_draft_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校招采集候选';
```

#### 改造 `company_calendar`

```sql
ALTER TABLE company_calendar
    ADD COLUMN info_type       VARCHAR(20)  NOT NULL DEFAULT 'POSITION' COMMENT 'POSITION / PROJECT' AFTER batch,
    ADD COLUMN source_url      VARCHAR(300)          DEFAULT NULL COMMENT '信息来源页',
    ADD COLUMN source_type     VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / AI',
    ADD COLUMN last_checked_at DATETIME              DEFAULT NULL COMMENT '最近一次核查';

ALTER TABLE company_calendar
    ADD UNIQUE KEY uk_calendar_unique (company, job_title, batch);
```

说明：

- `batch` 改为可空（大量官网只写「招满即止」）
- `PROJECT` 条目：`job_title` 存项目名（如「2027校园招聘」），`open_date`/`deadline` 存项目起止；前端禁用「一键投递」，改为「查看官网」
- 一个项目的时间只存一份，不重复写进几十条岗位里
- **不引入 `status` 字段**：过期在查询时按 `deadline < today` 动态判断（与 `CompanyVO.expired` 现有实现一致），避免状态同步、定时调度和时钟边界问题

### 2.3 采集流程

```
① 添加关注公司（公司名）
        │ 官方域名为空 → 域名发现
        ▼
② 域名发现：search("{公司} 校园招聘 官网")
   → 过滤 authority ≥ 2 的结果
   → LLM 判断哪个是官方招聘站（输出域名 + 理由）
   → 用户确认后落库 official_domain
        │
        ▼
③ 定向搜索：search("{公司} {届别} 校园招聘 site:{domain}", count=10)
        │
        ▼
④ 页面筛选（规则，不消耗 token）
   保留：URL 含 position/detail/job/campus/talent/recruit
   排除：/news/、首页、活动、宣讲会、行程
   最多取 3 个页面
        │
        ▼
⑤ Fetch → Markdown（超 12000 字符截断，优先保留含岗位/时间关键词的段落）
        │
        ▼
⑥ LLM 一次调用完成「页面分类 + 抽取」
   页面类型：POSITION / PROJECT / EVENT / NAV
   EVENT、NAV → 返回空数组，日志记录并丢弃
        │
        ▼
⑦ 确定性校验（1.3 节）
        │
        ▼
⑧ 写入 crawl_draft（DRAFT）
```

### 2.4 抽取 Prompt 要点

System 提示词必须包含：

- 角色：校招信息抽取器，只从给定页面提取，不推测
- 页面分类规则：岗位页（有岗位名 + JD）、项目页（有招聘流程/时间安排）、宣讲会页（有宣讲/行程）、导航页
- **只抽取岗位与项目时间线，宣讲会/活动一律返回空数组**
- 每个字段必须有 `evidence`；找不到返回 `null`，禁止补齐
- `apply_url` 只允许填写页面中明确出现的投递链接，**不得使用页面自身 URL**
- 页面出现非目标届别（如目标 2027 却只写 2026）时不提取
- `batch` 枚举：提前批 / 正式批 / 补录 / 其他

### 2.5 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/api/crawl/watch-companies` | 关注公司列表 / 新增 |
| DELETE | `/api/crawl/watch-companies/{id}` | 移除关注 |
| POST | `/api/crawl/watch-companies/{id}/discover-domain` | 域名发现，返回候选项供确认 |
| PUT | `/api/crawl/watch-companies/{id}` | 修改域名 / 城市 / 届别 |
| POST | `/api/crawl/watch-companies/{id}/run` | 触发采集，同步返回采集摘要（约 15-30s） |
| GET | `/api/crawl/drafts` | 候选列表（按 status 过滤） |
| PUT | `/api/crawl/drafts/{id}` | 审核时编辑字段 |
| PUT | `/api/crawl/drafts/{id}/approve` | 确认入库 |
| PUT | `/api/crawl/drafts/{id}/reject` | 驳回 |
| POST | `/api/crawl/drafts/approve-batch` | 批量确认 |

同步还是异步：单公司一次采集约 15-30 秒，手动单次先同步 + 前端 loading；批量采集再引入异步任务。

### 2.6 前端

`CompaniesView.vue` 增加两个 tab：

1. **我的关注**：公司清单、域名状态（未确认/已确认）、上次采集时间、单条「采集」按钮、添加/移除
2. **待审核 (N)**：左侧候选列表，右侧详情
   - 来源链接、字段可编辑
   - 每个字段下方展示 evidence 原文
   - confidence 低或校验告警的条目标黄
   - 逐条确认 / 批量确认 / 驳回

信息库列表对 `PROJECT` 条目**单独分组展示**：列表顶部固定「招聘项目」区块（可折叠），下方是岗位列表并保持现有 `deadline` 升序。理由：项目条目语义是「招聘进程」而非「可投岗位」，混排会干扰投递决策；每家公司的项目条目只有 1~3 条，单独分组不会撑长列表。项目条目操作按钮为「查看官网」而非「加入投递」；城市筛选只作用于岗位，项目视为「全国」。

### 2.7 风险与边界

| 风险 | 对策 |
|---|---|
| 官网域名找错 | 域名发现必须人工确认；采集结果带来源页，可复核 |
| Fetch 抓到空壳页 | 页面长度 < 300 字符直接跳过；多页兜底 |
| 幻觉 deadline | 日期必须在 evidence 中出现；范围校验 |
| 旧批次混入 | 届别校验 + 页面日期 + 目标年级比对 |
| 重复采集 | 草稿唯一键；已发布条目走更新而非新增 |
| 官网反爬 | Fetch 失败即跳过并记录，不重试轰炸 |
| 合规 | 只抓公开官网、遵守 robots；不抓 Boss/牛客等反爬平台 |

---

## 3. AI 简历定制

### 3.1 定位与形态

**不是「一键生成整份新简历」，而是「逐段改写 + diff 审阅」。**

理由：

- 简历是个人诚信声明，用户必须对每个字负责，责任不能转移给模型
- 逐段确认成本低（每段几秒），能挡住绝大多数幻觉风险
- 用户在看 diff 的过程中会学到怎么写简历，这是附加价值

### 3.2 数据模型

#### 新表 `resume_customization`

```sql
CREATE TABLE resume_customization (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    job_id           BIGINT UNSIGNED NOT NULL COMMENT '目标岗位',
    source_resume_id BIGINT UNSIGNED NOT NULL COMMENT '原简历',
    target_resume_id BIGINT UNSIGNED          DEFAULT NULL COMMENT '确认后生成的新版本',
    model            VARCHAR(50)              DEFAULT NULL,
    sections_json    MEDIUMTEXT               DEFAULT NULL COMMENT '逐段改写结果与 warnings',
    warnings_json    TEXT                     DEFAULT NULL COMMENT '事实校验告警',
    score_before     TINYINT UNSIGNED         DEFAULT NULL,
    score_after      TINYINT UNSIGNED         DEFAULT NULL COMMENT '改写稿真实重算',
    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / APPLIED / DISCARDED',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at       DATETIME                 DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_custom_user_job (user_id, job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 简历定制记录';
```

`resume_optimization` 保持现状（规则建议），两者语义不同，不混用。

### 3.3 流程

```
① 选择：目标岗位（有 JD）+ 简历版本
   → 结构化简历（content_json 非空）优先；纯文本简历只走现有「建议」模式
        │
        ▼
② LLM 提取 JD 要求（技能、经验、软性要求），替代 70 词词典
        │
        ▼
③ 逐段改写（按 education/internship/project/skills/summary 分段）
   输出：[{section, original, rewritten, rationale}]
        │
        ▼
④ FactValidator 事实校验
   提取改写稿中的技能词、数字、日期、公司名
   逐个检查是否在原简历中出现
        │
        ▼
⑤ 同一 AiMatchEngine 对改写前后文本真实算分
        │
        ▼
⑥ 前端 diff 审阅：逐段接受 / 拒绝 / 手动编辑
        │
        ▼
⑦ 接受部分写回 content_json，生成新 resume 版本
   version_label = "{公司}-{岗位方向}-定制"
   source_resume_id 原版本保持不变，可回退
```

### 3.4 Prompt 约束

必须写明的红线：

- 只能重组、压缩、调整顺序、更换措辞
- **禁止新增**原简历中不存在的技能、项目、公司、时间、数字
- 原有量化数据保留原值，不得放大或改写幅度
- 禁止堆砌 JD 关键词；自然融入，不相关的不写
- 保留个人表达特征，避免模板化句式
- 输出 JSON，每段附带 `rationale`（为什么这样改）和 `added_facts`（新增事实，必须为空）

### 3.5 FactValidator

| 检查 | 规则 |
|---|---|
| 技能词 | 用 `AiMatchEngine.allKeywords()` 提取改写稿中的技能词，必须出现在原简历中 |
| 数字 | 正则提取百分比、金额、数量、排名，逐个比对原简历 |
| 日期 | 提取年份/月份，不得新增 |
| 公司/学校名 | 不得新增 |

校验不通过：该段标红 + 在 warnings 中列出，用户必须手动确认才能接受。

### 3.6 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/customize` | 输入 jobId + resumeId，返回逐段改写与 warnings |
| GET | `/api/ai/customizations` | 历史记录 |
| PUT | `/api/ai/customizations/{id}/apply` | 提交用户接受的段落，生成新简历版本 |

不做批量定制：一次只处理一个岗位，用户切换岗位时复用同一页面重新生成。批量会引入并发控制、进度推送、部分成功、失败重试等复杂度，收益仅是少点几次按钮。

### 3.7 前端

改造 `OptimizeView.vue`：

- 顶部选择岗位与简历
- 逐段 diff：左原文、右改写、下方改写理由
- 每段「接受 / 拒绝 / 编辑」
- 事实告警标红置顶
- 底部展示真实的前后匹配分（替换现有假预估分）
- 全部接受后生成新版本，跳转「我的简历」

### 3.8 风险

| 风险 | 对策 |
|---|---|
| 幻觉编造 | FactValidator + 逐段确认 + 红线 prompt |
| 同质化 | 禁止模板句式；保留原文个人表达；diff 展示让用户判断 |
| 隐私 | README 与 UI 明确提示「简历内容将发送至 DeepSeek」；用户可关闭 |
| 纯文本简历结构丢失 | 只做建议，不做结构化改写 |
| 匹配分虚高 | 明确说明匹配分只衡量关键词覆盖，不代表简历质量 |

---

## 4. 实施顺序

| 阶段 | 内容 | 产出 |
|---|---|---|
| 0 | 技术验证 | ✅ 已完成（2026-10-27） |
| 1 | 公共基础设施 | `AiClient`、JSON 解析、校验器骨架、配置检查 |
| 2 | 采集最小闭环 | 关注公司 + 域名发现 + 单公司采集 + 审核入库 |
| 3 | 采集完善 | 批量、过期标记、更新已有条目 |
| 4 | 简历定制 | 逐段改写 + FactValidator + diff 审阅 + 新版本 |
| 5 | 匹配引擎升级（可选） | JD 关键词提取交给 LLM，保持 `MatchReport` 结构 |

优先级说明：简历定制（阶段 4）是高频核心链路，采集（阶段 2-3）是一次性内容供给。若时间有限，可先做阶段 1 + 4，采集延后。

---

## 5. 设计决策记录

| 问题 | 结论 | 理由 |
|---|---|---|
| 关注表是否保留 `user_id` | 保留 | 项目所有个人表均为多用户结构，`SecurityUtils.currentUserId()` 已是标准模式；去掉反而破坏一致性，成本为零 |
| `PROJECT` 条目的展示 | 单独分组，不混排 | 语义是「招聘进程」而非「可投岗位」，混排会干扰投递决策；每家 1~3 条，不撑列表；城市筛选只作用于岗位 |
| `EXPIRED` 计算位置 | 查询时动态判断 | 与现有 `CompanyVO.expired` 一致；引入定时任务会带来调度、幂等、时钟边界等无收益复杂度 |
| 多岗位批量定制 | 不做 | 单岗位一次生成已覆盖需求；批量引入并发、部分成功、失败重试等复杂度，收益仅是少点几次按钮 |
| AI 成本记账与上限 | 不做表，只记日志 | 单次低于 0.05 元，量级不构成风险；异常靠日志定位 |
