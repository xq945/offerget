# OfferGet

> 大学生校招投递管理平台。Vue 3 + Spring Boot 前后端分离实现，交互与信息架构参考 [offerbiu.com](https://offerbiu.com/)，品牌与文案为独立实现。

## 功能

### 一期（已实现）

- **营销首页**：Hero 产品演示、核心能力、价值主张、行动入口
- **账号体系**：注册 / 登录，BCrypt 加密，无状态 JWT，前端路由守卫
- **校招总览**：四项统计、本周投递趋势、3 天内截止、今日待办、AI 建议、投递台历
- **我的投递**：岗位增删改查、状态流转、状态筛选、关键词搜索、分页
- **简历管理**：PDF / DOC / DOCX / TXT 上传，Tika 解析文本，多版本管理，默认版本，内容预览
- **AI 匹配**：本地规则引擎（技能词典 + 加权命中率），输出匹配分、命中/缺失关键词与优化建议，保留匹配历史

### 二期（已实现）

- **个人中心**：资料修改、修改密码、使用统计（岗位 / 简历 / 匹配 / 优化）
- **投递复盘**：投递漏斗、环节转化率、近 6 个月投递趋势、高频缺失关键词、自动复盘结论
- **简历优化**：按 JD 缺失词生成分段改写建议，给出预估优化后匹配分，保留优化历史
- **校招信息库**：12 条公共校招岗位，支持行业 / 城市 / 关键词筛选，一键加入我的投递
- **简历制作器**：结构化表单 + A4 实时预览 + 浏览器打印导出 PDF，保存后可直接参与 AI 匹配
- **自动填充**：从在线简历或上传简历中提取网申字段，逐项复制，减少重复填写
- **智能提醒**：右上角铃铛汇总今日 / 明后天面试与笔试、3 天内截止、逾期未投递，支持单条已读、全部已读；已读状态按天重置

### 三期（已实现）

- **AI 简历定制**：按岗位 JD 逐段改写实习 / 项目 / 技能 / 自我评价，AI 只做重组与措辞调整；改写稿经过事实校验（新增技能、数字、年份、强动词告警）并逐段 diff 审阅，确认后才生成新版本，原版本保留
- **校招信息采集**：维护关注公司，按招聘官网定向搜索与抓取，AI 抽取岗位与招聘项目时间线；结果先进入待审核，确认后才写入信息库，重复采集只更新截止日期等关键字段
- **定时采集**：每天 07:30 自动遍历启用的关注公司批量采集，单家失败不影响其他；也可在页面手动「全部采集」

已按私人自用需求精简：移除会员中心、免费福利、用户协议 / 隐私政策，以及登录页的微信扫码入口。小程序、支付、扫码登录均不接入。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3.5、Vite 8、Vue Router 5、Pinia 4、Element Plus 2.14、Tailwind CSS 4、axios、lucide-vue-next |
| 后端 | Java 21、Spring Boot 4.1.1、Spring Security、MyBatis-Plus 3.5.17、jjwt 0.12.7、Apache Tika 3.3.2、Lombok |
| 数据库 | MySQL 8.0（Docker，独立实例） |
| 构建 | Maven / npm |

## 目录结构

```
offerget/
├── docker-compose.yml            # MySQL 8.0（3307）
├── Mysql/
│   ├── offerget_init.sql          # 建表 + 演示数据（新环境一次建好）
│   └── migration/20261027_phase2.sql  # 一期库升级到二期
├── offerget-server/              # Spring Boot 后端（8082）
│   └── src/main/java/com/offerget/
│       ├── common/               # 统一响应、分页、异常
│       ├── config/               # Security / MyBatis-Plus / 静态资源
│       ├── security/             # JWT 生成、过滤器、当前用户
│       ├── entity/ enums/ mapper/
│       ├── dto/                  # auth / job / resume / ai / dashboard / profile / review / company
│       ├── service/              # 接口
│       ├── service/impl/         # 实现（AI 匹配、简历优化、文本提取、自动填充）
│       └── controller/
└── offerget-web/                 # Vue 前端（5175）
    └── src/
        ├── api/                  # axios 封装与接口模块
        ├── layouts/              # 营销页布局 / 工作台布局
        ├── components/           # 统计卡、趋势图、台历、分数环、抽屉等
        ├── views/                # 首页、登录、总览、投递、简历、AI 匹配、
        │                         # 个人中心、复盘、优化、信息库、制作器、自动填充
        ├── stores/user.js
        ├── router/index.js
        └── styles/               # Tailwind 与 Element Plus 主题
```

## 快速开始

### 环境要求

- JDK 21、Maven 3.9+
- Node.js 20+、npm
- Docker Desktop

### 1. 启动数据库

```bash
docker compose up -d
```

首次启动会自动执行 `Mysql/offerget_init.sql`，创建 `offerget` 库、7 张表与演示数据。容器健康检查通过后即可使用。

如果数据库是从一期升级而来（表已存在），执行一次二期迁移：

```bash
docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261027_phase2.sql
```

### 2. 启动后端

```bash
cd offerget-server
mvn spring-boot:run
```

服务地址：<http://localhost:8082>

### 3. 启动前端

```bash
cd offerget-web
npm install
npm run dev
```

访问：<http://localhost:5175>

### 演示账号

| 用户名 | 密码 |
|---|---|
| `demo` | `demo123` |

演示数据包含 6 个覆盖不同状态的岗位、2 份简历、1 条匹配记录与若干面试/截止事件，登录后总览页即有内容。

## 配置

默认配置在 `offerget-server/src/main/resources/application.yml`，均支持环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `offerget123` | 数据库密码 |
| `APP_JWT_SECRET` | 开发用默认值 | JWT 签名密钥（生产必须替换，≥32 字节） |
| `APP_JWT_EXPIRATION_SECONDS` | `7200` | Token 有效期 |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5175` | 允许的前端来源 |
| `APP_FILE_STORAGE_ROOT` | `./uploads` | 简历文件存储目录 |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek 密钥，仅服务端使用，不可下发前端 |
| `DEEPSEEK_MODEL` | `deepseek-chat` | DeepSeek 模型名 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | OpenAI 兼容接口地址 |
| `CRAWL_SCHEDULE_ENABLED` | `true` | 是否启用定时采集 |
| `CRAWL_SCHEDULE_CRON` | `0 30 7 * * *` | 定时采集 cron（默认每天 07:30） |
| `CRAWL_SCHEDULE_ZONE` | `Asia/Shanghai` | 定时任务时区 |
| `CRAWL_COMPANY_DELAY_MS` | `2000` | 相邻两家公司之间的采集间隔 |

需要本地覆盖时，复制 `application-local.yml.example` 为 `application-local.yml`（已被 gitignore）。

前端接口默认走 Vite 代理 `/api` 与 `/uploads` → `http://localhost:8082`，无需额外配置。生产部署可将 `VITE_API_BASE_URL` 指向同源网关。

## API 一览

统一响应：`{ code, message, data, timestamp }`，成功 `code = 0`，鉴权使用 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/jobs` | 岗位分页（status / keyword） |
| GET / POST | `/api/jobs`、`/api/jobs/{id}` | 详情 / 新增 |
| PUT / DELETE | `/api/jobs/{id}` | 修改 / 删除 |
| PATCH | `/api/jobs/{id}/status` | 状态流转 |
| GET | `/api/dashboard/overview` | 总览数据 |
| GET | `/api/dashboard/calendar` | 月历事件（year / month） |
| GET / POST | `/api/resumes` | 简历列表 / 上传 |
| GET / PUT / DELETE | `/api/resumes/{id}` | 详情 / 改名 / 删除 |
| PUT | `/api/resumes/{id}/default` | 设为默认版本 |
| POST | `/api/ai/match` | 执行匹配 |
| GET | `/api/ai/matches` | 匹配历史 |
| GET | `/api/ai/matches/latest` | 最近一次匹配 |
| POST | `/api/ai/optimize` | 生成简历分段优化建议 |
| GET | `/api/ai/optimizations` | 优化历史（可按 jobId 过滤） |
| POST | `/api/ai/customize` | 按 JD 逐段改写简历，返回改写稿与事实告警 |
| GET | `/api/ai/customizations` | 定制历史（可按 jobId 过滤） |
| PUT | `/api/ai/customizations/{id}/apply` | 采用选定段落，生成新简历版本 |
| GET / POST | `/api/crawl/watch-companies` | 关注公司列表 / 新增 |
| PUT / DELETE | `/api/crawl/watch-companies/{id}` | 修改 / 删除关注 |
| POST | `/api/crawl/watch-companies/{id}/discover-domain` | 搜索并推荐招聘官网域名 |
| POST | `/api/crawl/watch-companies/{id}/run` | 执行一次采集 |
| GET | `/api/crawl/drafts` | 采集候选（可按 status 过滤） |
| PUT | `/api/crawl/drafts/{id}` | 编辑候选 |
| PUT | `/api/crawl/drafts/{id}/approve` | 确认写入信息库 |
| PUT | `/api/crawl/drafts/{id}/reject` | 驳回候选 |
| POST | `/api/crawl/drafts/approve-batch` | 批量确认 |
| GET | `/api/profile` | 个人资料与使用统计 |
| PUT | `/api/profile` | 修改昵称 / 邮箱 / 毕业年份 |
| PUT | `/api/profile/password` | 校验旧密码后修改密码 |
| GET | `/api/review/overview` | 投递复盘（漏斗、转化率、趋势、结论） |
| GET | `/api/companies` | 校招信息库分页（keyword / industry / city） |
| POST | `/api/companies/{id}/import` | 一键加入我的投递 |
| POST | `/api/resumes/builder` | 创建结构化在线简历 |
| PUT | `/api/resumes/{id}/builder` | 更新结构化在线简历 |
| GET | `/api/resumes/{id}/autofill` | 提取网申字段 |
| GET | `/api/notifications` | 提醒列表与未读数 |
| PUT | `/api/notifications/read` | 单条提醒标记已读 |
| PUT | `/api/notifications/read-all` | 全部标记已读 |

## 数据库

| 表 | 说明 |
|---|---|
| `app_user` | 用户 |
| `job` | 投递岗位 |
| `resume` | 简历版本 |
| `ai_match_record` | AI 匹配记录 |
| `job_event` | 求职事件（台历与待办） |
| `resume_optimization` | 简历优化记录（分段建议、预估分） |
| `resume_customization` | AI 简历定制记录（逐段改写、事实告警、前后匹配分） |
| `crawl_watch_company` | 校招关注公司（官网域名、目标届别、上次采集时间） |
| `crawl_draft` | 校招采集候选（人工确认后才写入信息库） |
| `company_calendar` | 校招信息库（公共只读数据） |
| `notification_read` | 提醒已读状态（key 含日期，按天重置） |

`resume` 表额外包含 `content_json`，用于保存简历制作器的结构化内容；上传简历该字段为空。

## 常见问题

**端口被占用？**
后端默认 8082、前端 5175、数据库 3307。若被占用，修改 `offerget-server/src/main/resources/application.yml` 的 `server.port`、`offerget-web/vite.config.js` 的 `server.port` 与代理目标。

**修改了 SQL 没有生效？**
初始化脚本只在数据卷首次创建时执行。需要重建：

```bash
docker compose down -v
docker compose up -d
```

**简历上传后没有解析出文本？**
扫描版 PDF 或纯图片简历无法提取文字，请上传文字版 PDF / DOCX。文件上限 10MB。

**AI 匹配是怎么实现的？**
本地规则引擎：从 JD 中提取技能词典命中词，按权重计算简历命中率，并结合实习、项目、竞赛经历加权，不依赖外部大模型，也不会上传简历内容。简历优化复用同一套词典，按简历段落生成补充建议和预估分。

**简历制作器怎么导出 PDF？**
点击「打印 / 导出 PDF」，在浏览器打印对话框中选择「另存为 PDF」。打印样式已隐藏侧栏、顶栏与表单，仅输出 A4 简历。

**自动填充能直接写进网申系统吗？**
不能。浏览器无法操作第三方招聘网站的表单，这里只做信息整理与逐项复制，复制后请自行核对再粘贴。

**定时采集什么时候跑？**
默认每天 07:30（Asia/Shanghai）遍历所有用户启用的关注公司，逐家采集，相邻两家间隔 2 秒以避开搜索接口限流。单家失败只记日志，不影响其他公司。改时间用 `CRAWL_SCHEDULE_CRON`，完全关闭用 `CRAWL_SCHEDULE_ENABLED=false`。定时任务与手动「全部采集」共用同一套逻辑，上一次未结束时本次会自动跳过。

**提醒是怎么生成的？**
后端按当前日期动态计算，不依赖定时任务：今天 / 明后天的面试与笔试事件、3 天内截止的岗位、已过截止但仍待投递的岗位。提醒 key 内包含日期，因此每天 0 点后会重新变为未读；点击条目会标记已读并跳到对应公司的投递列表。前端每 60 秒刷新一次未读数。

## AI 接入状态

DeepSeek 配置已就绪（`app.ai.deepseek`）：密钥只存放在 `application-local.yml`，后端提供带鉴权的 `RestClient` Bean 与 `GET /api/ai/config` 配置检查接口。

**AI 简历定制**已接入 DeepSeek（`deepseek-chat`）：调用时会发送岗位 JD 与简历文本至 DeepSeek，仅用于本次改写。模型只做重组与措辞调整，输出再经过事实校验（新增技能、数字、年份、强动词）与逐段人工确认，才会写入新版本；原版本始终保留。未配置密钥时该功能不可用，不影响其他模块。

**校招信息采集**同样基于 DeepSeek：Search 按 `site:` 定向搜索官网，Fetch 抓取页面 Markdown，chat 抽取岗位与项目时间线。发送的只有公开网页内容，不涉及个人简历；结果先进入候选表，人工确认后入库。宣讲会、行程类活动不采集。

AI 匹配与简历优化仍使用本地规则引擎，不消耗 API 额度；后续接入大模型时保持现有返回结构不变即可平滑替换。

## 说明

- 视觉、布局与信息架构参考 [offerbiu.com](https://offerbiu.com/)；品牌名 OfferGet、营销文案与实现代码均为独立完成，未复用对方 Logo 或受版权保护的素材。
- 图标来自 [lucide](https://lucide.dev/)（ISC），字体 Inter（OFL）。
- 本项目用于学习和个人使用。
