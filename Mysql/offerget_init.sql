-- ============================================================
-- OfferGet 初始化脚本
-- 演示账号：demo / demo123
-- 注意：脚本只在 Docker 数据卷首次初始化时执行
-- ============================================================
CREATE DATABASE IF NOT EXISTS offerget DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE offerget;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS mock_interview_question;
DROP TABLE IF EXISTS mock_interview_session;
DROP TABLE IF EXISTS job_event;
DROP TABLE IF EXISTS ai_match_record;
DROP TABLE IF EXISTS resume_customization;
DROP TABLE IF EXISTS resume_optimization;
DROP TABLE IF EXISTS crawl_draft;
DROP TABLE IF EXISTS crawl_watch_company;
DROP TABLE IF EXISTS company_calendar;
DROP TABLE IF EXISTS resume;
DROP TABLE IF EXISTS job;
DROP TABLE IF EXISTS app_user;

-- ---------------- 用户 ----------------
CREATE TABLE app_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)     NOT NULL COMMENT '登录名',
    password_hash   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希',
    nickname        VARCHAR(50)     NOT NULL COMMENT '昵称',
    email           VARCHAR(120)             DEFAULT NULL COMMENT '邮箱',
    avatar          VARCHAR(255)             DEFAULT NULL COMMENT '头像地址',
    graduation_year SMALLINT UNSIGNED        DEFAULT NULL COMMENT '毕业年份，如 2027',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

-- ---------------- 岗位 ----------------
CREATE TABLE job (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    company       VARCHAR(100)    NOT NULL COMMENT '公司',
    title         VARCHAR(120)    NOT NULL COMMENT '岗位名称',
    city          VARCHAR(50)              DEFAULT NULL COMMENT '工作城市',
    salary_range  VARCHAR(50)              DEFAULT NULL COMMENT '薪资范围',
    job_type      VARCHAR(30)              DEFAULT NULL COMMENT '校招 / 实习 / 社招',
    source        VARCHAR(30)              DEFAULT NULL COMMENT '信息来源：官网 / BOSS / 牛客 / 内推',
    jd_text       MEDIUMTEXT               DEFAULT NULL COMMENT '岗位 JD 原文',
    deadline      DATE                     DEFAULT NULL COMMENT '投递截止日期',
    status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPLIED/WRITTEN_TEST/INTERVIEW/OFFER/REJECTED',
    match_score   TINYINT UNSIGNED         DEFAULT NULL COMMENT '最近一次 AI 匹配得分',
    resume_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT '绑定简历版本',
    note          VARCHAR(500)             DEFAULT NULL COMMENT '备注',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_user_status (user_id, status),
    KEY idx_job_user_deadline (user_id, deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '投递岗位';

-- ---------------- 简历 ----------------
CREATE TABLE resume (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    name          VARCHAR(100)    NOT NULL COMMENT '简历名称',
    version_label VARCHAR(30)              DEFAULT NULL COMMENT '版本标签，如 产品版 / 后端版',
    file_name     VARCHAR(200)             DEFAULT NULL COMMENT '原始文件名',
    file_path     VARCHAR(300)             DEFAULT NULL COMMENT '存储相对路径',
    file_size     BIGINT UNSIGNED          DEFAULT NULL COMMENT '文件字节数',
    content_text  MEDIUMTEXT               DEFAULT NULL COMMENT '解析后的纯文本',
    content_json  TEXT                     DEFAULT NULL COMMENT '结构化简历 JSON（制作器）',
    is_default    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认版本',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_resume_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '简历版本';

-- ---------------- AI 匹配记录 ----------------
CREATE TABLE ai_match_record (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    job_id           BIGINT UNSIGNED NOT NULL COMMENT '岗位',
    resume_id        BIGINT UNSIGNED NOT NULL COMMENT '简历版本',
    score            TINYINT UNSIGNED NOT NULL COMMENT '综合得分 0-100',
    matched_keywords TEXT                     DEFAULT NULL COMMENT '命中关键词 JSON 数组',
    missing_keywords TEXT                     DEFAULT NULL COMMENT '缺失关键词 JSON 数组',
    suggestions      TEXT                     DEFAULT NULL COMMENT '优化建议 JSON 数组',
    summary          VARCHAR(500)             DEFAULT NULL COMMENT '一句话结论',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_match_user_job (user_id, job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 匹配记录';

-- ---------------- 求职事件（台历 / 待办） ----------------
CREATE TABLE job_event (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    job_id     BIGINT UNSIGNED NOT NULL COMMENT '关联岗位',
    event_type VARCHAR(20)     NOT NULL COMMENT 'DEADLINE/APPLY/WRITTEN_TEST/INTERVIEW/RESULT',
    title      VARCHAR(120)    NOT NULL COMMENT '事件标题',
    event_time DATETIME        NOT NULL COMMENT '发生时间',
    note       VARCHAR(255)             DEFAULT NULL COMMENT '备注',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_user_time (user_id, event_time),
    KEY idx_event_job (job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '求职事件';

-- ---------------- 简历优化记录 ----------------
CREATE TABLE resume_optimization (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    job_id         BIGINT UNSIGNED NOT NULL COMMENT '岗位',
    resume_id      BIGINT UNSIGNED NOT NULL COMMENT '简历版本',
    score_before   TINYINT UNSIGNED NOT NULL COMMENT '优化前匹配分',
    score_after    TINYINT UNSIGNED NOT NULL COMMENT '预估优化后匹配分',
    added_keywords TEXT                     DEFAULT NULL COMMENT '建议补充的关键词 JSON 数组',
    sections       TEXT                     DEFAULT NULL COMMENT '分段优化建议 JSON',
    summary        VARCHAR(500)             DEFAULT NULL COMMENT '一句话结论',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_opt_user_job (user_id, job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '简历优化记录';

-- ---------------- AI 简历定制记录 ----------------
CREATE TABLE resume_customization (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    job_id           BIGINT UNSIGNED NOT NULL COMMENT '目标岗位',
    source_resume_id BIGINT UNSIGNED NOT NULL COMMENT '原简历',
    target_resume_id BIGINT UNSIGNED          DEFAULT NULL COMMENT '确认后生成的新版本',
    resume_type      VARCHAR(20)     NOT NULL DEFAULT 'TEXT' COMMENT 'BUILDER / TEXT',
    model            VARCHAR(50)              DEFAULT NULL COMMENT '模型名',
    sections_json    MEDIUMTEXT               DEFAULT NULL COMMENT '逐段改写结果 JSON',
    warnings_json    TEXT                     DEFAULT NULL COMMENT '事实校验告警 JSON',
    score_before     TINYINT UNSIGNED         DEFAULT NULL COMMENT '改写前匹配分',
    score_after      TINYINT UNSIGNED         DEFAULT NULL COMMENT '改写后匹配分',
    summary          VARCHAR(500)             DEFAULT NULL COMMENT '整体调整思路',
    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / APPLIED / DISCARDED',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at       DATETIME                 DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_custom_user_job (user_id, job_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 简历定制记录';

-- ---------------- AI 模拟面试会话 ----------------
CREATE TABLE mock_interview_session (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    job_id         BIGINT UNSIGNED NOT NULL COMMENT '目标岗位',
    resume_id      BIGINT UNSIGNED NOT NULL COMMENT '使用简历',
    interview_type VARCHAR(20)     NOT NULL DEFAULT 'COMPREHENSIVE' COMMENT 'COMPREHENSIVE / TECHNICAL / BEHAVIORAL',
    difficulty     VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM' COMMENT 'EASY / MEDIUM / HARD',
    status         VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS / COMPLETED / ABANDONED',
    opening        VARCHAR(500)             DEFAULT NULL COMMENT '开场白',
    total_score    TINYINT UNSIGNED         DEFAULT NULL COMMENT '报告总分',
    report_json    MEDIUMTEXT               DEFAULT NULL COMMENT '聚合报告 JSON',
    started_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at    DATETIME                 DEFAULT NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session_user_job (user_id, job_id),
    KEY idx_session_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 模拟面试会话';

-- ---------------- AI 模拟面试题目与作答 ----------------
CREATE TABLE mock_interview_question (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id      BIGINT UNSIGNED NOT NULL COMMENT '会话',
    seq             SMALLINT UNSIGNED NOT NULL COMMENT '题号',
    question_type   VARCHAR(20)     NOT NULL COMMENT 'INTRO / PROJECT / TECHNICAL / BEHAVIORAL / PRESSURE / REVERSE',
    question        VARCHAR(1000)   NOT NULL COMMENT '主问题',
    basis_source    VARCHAR(20)              DEFAULT NULL COMMENT 'JD / RESUME',
    basis_quote     VARCHAR(500)             DEFAULT NULL COMMENT '题目依据原文',
    focus           VARCHAR(200)             DEFAULT NULL COMMENT '考察点',
    difficulty      VARCHAR(20)              DEFAULT NULL COMMENT 'EASY / MEDIUM / HARD',
    followups_json  TEXT                     DEFAULT NULL COMMENT '追问链 JSON：question / answer / at',
    answer          TEXT                     DEFAULT NULL COMMENT '用户对主问题的回答，反问时存用户提问',
    answer_feedback TEXT                     DEFAULT NULL COMMENT '反问时面试官的回答',
    followup_count  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已追问层数',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ASKED / ANSWERED / SKIPPED',
    score_json      MEDIUMTEXT               DEFAULT NULL COMMENT '逐题评分 JSON',
    risk_flags      VARCHAR(200)             DEFAULT NULL COMMENT '风险标记，逗号分隔',
    answered_at     DATETIME                 DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_question_session (session_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 模拟面试题目与作答';

-- ---------------- 提醒已读状态 ----------------
CREATE TABLE notification_read (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    notification_key VARCHAR(120)    NOT NULL COMMENT '提醒唯一键（含日期，按天重置）',
    read_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_user_key (user_id, notification_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提醒已读状态';

-- ---------------- 校招关注公司 ----------------
CREATE TABLE crawl_watch_company (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    company                VARCHAR(100)    NOT NULL COMMENT '公司名',
    official_domain        VARCHAR(200)             DEFAULT NULL COMMENT '招聘官网域名，如 campus.jd.com',
    industry               VARCHAR(50)              DEFAULT NULL COMMENT '行业',
    city                   VARCHAR(50)              DEFAULT NULL COMMENT '城市',
    target_graduation_year SMALLINT UNSIGNED       DEFAULT NULL COMMENT '目标届别，空则取用户毕业年份',
    enabled                TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_crawled_at        DATETIME                 DEFAULT NULL COMMENT '上次采集时间',
    created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_watch_user_company (user_id, company),
    KEY idx_watch_domain (official_domain)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校招关注公司';

-- ---------------- 校招采集候选 ----------------
CREATE TABLE crawl_draft (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    watch_company_id BIGINT UNSIGNED NOT NULL COMMENT '关注公司',
    info_type        VARCHAR(20)     NOT NULL DEFAULT 'POSITION' COMMENT 'POSITION / PROJECT',
    company          VARCHAR(100)    NOT NULL COMMENT '公司名',
    industry         VARCHAR(50)              DEFAULT NULL COMMENT '行业',
    city             VARCHAR(50)              DEFAULT NULL COMMENT '城市',
    job_title        VARCHAR(120)    NOT NULL COMMENT '岗位名或项目名',
    batch            VARCHAR(30)     NOT NULL DEFAULT '其他' COMMENT '提前批 / 正式批 / 补录 / 其他',
    open_date        DATE                     DEFAULT NULL COMMENT '开启日期',
    deadline         DATE                     DEFAULT NULL COMMENT '截止日期',
    apply_url        VARCHAR(300)             DEFAULT NULL COMMENT '投递入口',
    source_url       VARCHAR(300)    NOT NULL COMMENT '抽取来源页',
    tags             VARCHAR(200)             DEFAULT NULL COMMENT '标签，逗号分隔',
    description      VARCHAR(500)             DEFAULT NULL COMMENT '岗位说明',
    evidence_json    TEXT                     DEFAULT NULL COMMENT '逐字段原文证据',
    confidence       DECIMAL(3,2)             DEFAULT NULL COMMENT '置信度 0-1',
    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / APPROVED / REJECTED',
    crawled_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at      DATETIME                 DEFAULT NULL,
    published_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT '确认后写入 company_calendar 的 id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_draft_unique (watch_company_id, job_title, batch),
    KEY idx_draft_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校招采集候选';

-- ---------------- 校招信息库（公共数据） ----------------
CREATE TABLE company_calendar (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    company         VARCHAR(100)    NOT NULL COMMENT '企业名称',
    industry        VARCHAR(50)     NOT NULL COMMENT '行业',
    city            VARCHAR(50)     NOT NULL COMMENT '城市',
    job_title       VARCHAR(120)    NOT NULL COMMENT '岗位名称',
    batch           VARCHAR(30)     NOT NULL DEFAULT '其他' COMMENT '提前批 / 正式批 / 补录 / 其他',
    info_type       VARCHAR(20)     NOT NULL DEFAULT 'POSITION' COMMENT 'POSITION / PROJECT',
    open_date       DATE                     DEFAULT NULL COMMENT '开启日期',
    deadline        DATE                     DEFAULT NULL COMMENT '截止日期',
    apply_url       VARCHAR(300)             DEFAULT NULL COMMENT '校招官网',
    source_url      VARCHAR(300)             DEFAULT NULL COMMENT '信息来源页',
    source_type     VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / AI',
    last_checked_at DATETIME                 DEFAULT NULL COMMENT '最近一次核查',
    tags            VARCHAR(200)             DEFAULT NULL COMMENT '标签，逗号分隔',
    description     VARCHAR(500)             DEFAULT NULL COMMENT '岗位说明',
    tone            VARCHAR(30)              DEFAULT NULL COMMENT '前端配色标识',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_calendar_unique (company, job_title, batch),
    KEY idx_company_industry (industry),
    KEY idx_company_deadline (deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校招信息库';

-- ============================================================
-- 演示数据
-- ============================================================

INSERT INTO app_user (id, username, password_hash, nickname, email, graduation_year)
VALUES (1, 'demo', '$2b$10$POOwlL5lK82QjP0lHKRfMe27bamgANrolJQNdPHZ9GGjQrZrNyhxy',
        '张同学', 'demo@offerget.local', 2027);

INSERT INTO resume (id, user_id, name, version_label, file_name, file_path, file_size, content_text, is_default, created_at)
VALUES
(1, 1, '产品经理版-校招', '产品版', '产品经理版-校招.pdf', NULL, 245760,
 '张同学 | 2027 届本科生 | 求职方向：产品经理 / 产品运营\n教育背景：某大学 信息管理与信息系统 本科 2023.09-2027.06\n实习经历：字节跳动 产品实习生 2026.06-2026.09\n- 负责校园场景用户调研与需求分析，输出 3 份 PRD，推动功能上线，日活提升 12%\n- 使用 SQL 与 Excel 完成数据分析，搭建用户分层看板\n项目经历：校园二手交易平台 产品负责人\n- 完成竞品分析、需求优先级排序与原型设计（Axure），组织 5 人团队迭代上线\n技能：Axure、Figma、SQL、Python、Excel、数据分析、用户调研、竞品分析、PRD',
 1, '2026-10-20 10:00:00'),
(2, 1, 'Java 后端版-校招', '后端版', 'Java后端版-校招.pdf', NULL, 198656,
 '李同学 | 2027 届本科生 | 求职方向：Java 后端开发\n教育背景：某大学 计算机科学与技术 本科 2023.09-2027.06\n实习经历：某科技公司 Java 开发实习生 2026.06-2026.09\n- 基于 Spring Boot + MyBatis-Plus + MySQL 开发订单模块，接口 QPS 提升 30%\n- 使用 Redis 实现缓存与分布式锁，优化热点数据查询\n项目经历：商城系统\n- Spring Boot 3 + Vue 3 前后端分离，JWT 鉴权，Docker 部署\n技能：Java、Spring Boot、MyBatis-Plus、MySQL、Redis、Linux、Git、Docker',
 0, '2026-10-21 15:30:00');

INSERT INTO job (id, user_id, company, title, city, salary_range, job_type, source, jd_text, deadline, status, match_score, resume_id, note, created_at)
VALUES
(1, 1, '字节跳动', '产品经理校招生', '北京', '20-35K', '校招', '官网',
 '岗位职责：\n1. 负责校园场景产品的需求分析与方案设计，输出 PRD 并推动落地；\n2. 通过用户调研、数据分析与竞品分析发现问题并验证假设；\n3. 与研发、设计、运营跨团队协作，完成 AB 实验与上线复盘。\n任职要求：\n1. 2027 届本科及以上学历，专业不限；\n2. 熟悉 Axure、Figma 等原型工具，具备较强的数据分析能力；\n3. 有用户增长、埋点、AB 实验相关实习经历优先；\n4. 逻辑清晰，沟通表达能力强。',
 '2026-10-29', 'INTERVIEW', 82, 1, '三面安排在周二下午，重点准备校园场景增长案例', '2026-10-21 09:20:00'),
(2, 1, '腾讯', '数据分析师', '深圳', '18-28K', '校招', '内推',
 '岗位职责：\n1. 负责业务数据的采集、清洗与分析，搭建指标体系与数据看板；\n2. 输出专题分析报告，支撑产品迭代与运营决策。\n任职要求：\n1. 熟悉 SQL、Python，掌握 Excel 与可视化工具；\n2. 具备统计学基础，了解 AB 实验设计；\n3. 有互联网数据分析实习经历优先。',
 '2026-10-30', 'WRITTEN_TEST', 71, 1, '笔试周四 19:00，提前测试考试环境', '2026-10-21 14:05:00'),
(3, 1, '阿里巴巴', '解决方案架构师', '杭州', '22-38K', '校招', '官网',
 '岗位职责：\n1. 理解客户业务场景，输出技术解决方案；\n2. 参与项目售前支持与方案宣讲。\n任职要求：\n1. 熟悉 Java、Spring Boot、MySQL、Redis 等技术栈；\n2. 具备良好的沟通表达与文档能力；\n3. 有云原生或分布式系统实践经验优先。',
 '2026-11-02', 'APPLIED', 66, 2, '已投递，等待简历筛选结果', '2026-10-26 10:10:00'),
(4, 1, '美团', '产品运营', '上海', '15-25K', '校招', 'BOSS',
 '岗位职责：\n1. 负责用户增长活动的策划与执行，跟踪活动数据并复盘；\n2. 搭建用户分层运营体系，提升留存与转化。\n任职要求：\n1. 具备用户调研与数据分析能力；\n2. 熟悉活动运营、内容运营与社群运营；\n3. 有校园活动或互联网运营实习经历优先。',
 '2026-11-15', 'OFFER', 88, 1, '已收到意向书，等待正式 Offer 流程', '2026-10-26 16:40:00'),
(5, 1, '科大讯飞', '算法工程师', '合肥', '20-32K', '校招', '官网',
 '岗位职责：\n1. 负责自然语言处理算法的研发与优化；\n2. 参与大模型应用落地与效果评测。\n任职要求：\n1. 熟悉 Python、PyTorch，掌握机器学习与深度学习基础；\n2. 有 NLP 或大模型相关项目经验；\n3. 硕士及以上学历优先。',
 '2026-10-20', 'REJECTED', 58, 2, '笔试未通过，复盘算法基础', '2026-10-27 09:00:00'),
(6, 1, '小红书', '用户增长运营', '上海', '16-24K', '校招', '内推',
 '岗位职责：\n1. 负责拉新与留存策略的设计与落地；\n2. 通过用户调研、数据分析定位增长机会点；\n3. 策划社区活动，提升用户活跃与内容供给。\n任职要求：\n1. 熟悉用户增长方法论，具备数据分析能力；\n2. 对社区产品有热情，有内容运营或活动运营经验；\n3. 2027 届本科及以上学历。',
 '2026-11-05', 'PENDING', NULL, 1, '内推码已拿到，本周内完成投递', '2026-10-27 11:20:00');

INSERT INTO ai_match_record (user_id, job_id, resume_id, score, matched_keywords, missing_keywords, suggestions, summary, created_at)
VALUES (1, 1, 1, 82,
        '["用户调研","需求分析","PRD","数据分析","竞品分析","Axure","Figma"]',
        '["用户增长","AB 实验","埋点"]',
        '["补充一段数据驱动的迭代案例，量化上线后的转化提升","在项目经历中体现跨部门协作与优先级取舍，呼应岗位职责第 3 条","补充 AB 实验设计与埋点方案相关实践，提升与岗位要求的匹配度"]',
        '核心经历与岗位要求高度匹配，补充增长与实验相关案例后竞争力更强。',
        '2026-10-25 20:15:00');

INSERT INTO job_event (user_id, job_id, event_type, title, event_time, note)
VALUES
(1, 1, 'INTERVIEW', '字节跳动 产品经理 三面', '2026-10-27 14:00:00', '线上视频面试，提前 15 分钟进入会议室'),
(1, 2, 'WRITTEN_TEST', '腾讯 数据分析师 在线笔试', '2026-10-27 19:00:00', '时长 90 分钟，准备 SQL 与统计学复习'),
(1, 1, 'DEADLINE', '字节跳动投递截止', '2026-10-29 23:59:00', '已在流程中，无需再次投递'),
(1, 2, 'DEADLINE', '腾讯投递截止', '2026-10-30 23:59:00', NULL),
(1, 2, 'INTERVIEW', '腾讯 数据分析师 一面', '2026-10-30 11:00:00', '待笔试通过后确认'),
(1, 3, 'DEADLINE', '阿里巴巴投递截止', '2026-11-02 23:59:00', NULL),
(1, 6, 'DEADLINE', '小红书投递截止', '2026-11-05 23:59:00', '使用内推码投递'),
(1, 4, 'RESULT', '美团 产品运营 意向书', '2026-10-25 18:00:00', '等待正式 Offer 流程');

-- ---------------- 校招信息库（公共只读数据） ----------------
INSERT INTO company_calendar (company, industry, city, job_title, batch, open_date, deadline, apply_url, tags, description, tone)
VALUES
('字节跳动', '互联网', '北京', '产品经理校招生', '提前批', '2026-06-20', '2026-08-20', 'https://jobs.bytedance.com/', '互联网,产品,北京', '负责校园场景产品需求分析与方案设计，输出 PRD 并推动上线。', 'blue'),
('腾讯', '互联网', '深圳', '后端开发工程师', '正式批', '2026-09-01', '2026-11-30', 'https://join.qq.com/', '互联网,Java,深圳', '参与后台服务设计与开发，要求熟悉 Java、MySQL、Redis。', 'cyan'),
('阿里巴巴', '互联网', '杭州', '解决方案架构师', '正式批', '2026-09-05', '2026-11-20', 'https://talent.alibaba.com/', '互联网,架构,杭州', '面向客户业务场景输出技术解决方案，参与售前支持。', 'violet'),
('美团', '互联网', '上海', '产品运营', '正式批', '2026-09-01', '2026-12-01', 'https://zhaopin.meituan.com/', '互联网,运营,上海', '负责用户增长活动策划与执行，搭建用户分层运营体系。', 'amber'),
('小红书', '互联网', '上海', '用户增长运营', '正式批', '2026-09-10', '2026-12-10', 'https://job.xiaohongshu.com/', '互联网,增长,上海', '设计拉新与留存策略，通过数据分析定位增长机会点。', 'rose'),
('华为', '硬件', '深圳', '软件开发工程师', '提前批', '2026-06-15', '2026-08-31', 'https://career.huawei.com/', '硬件,软件,深圳', '参与嵌入式与云服务软件开发，要求扎实的编程基础。', 'indigo'),
('小米', '硬件', '北京', '前端开发工程师', '正式批', '2026-09-01', '2026-11-15', 'https://hr.xiaomi.com/', '硬件,前端,北京', '负责 Web 与小程序前端开发，熟悉 Vue / React 技术栈。', 'orange'),
('中国移动', '国企', '北京', '数据分析师', '正式批', '2026-09-01', '2026-11-25', 'https://job.10086.cn/', '国企,数据,北京', '负责业务数据采集与分析，搭建指标体系与数据看板。', 'emerald'),
('国家电网', '国企', '南京', '信息化管理岗', '正式批', '2026-09-15', '2026-12-05', 'https://zhaopin.sgcc.com.cn/', '国企,信息化,南京', '负责信息系统运维与数字化项目管理。', 'green'),
('招商银行', '金融', '深圳', '金融科技岗', '正式批', '2026-09-01', '2026-11-10', 'https://career.cmbchina.com/', '金融,科技,深圳', '参与银行业务系统研发与数据分析，要求编程与金融基础。', 'red'),
('科大讯飞', '人工智能', '合肥', '算法工程师', '正式批', '2026-09-01', '2026-11-30', 'https://campus.iflytek.com/', 'AI,NLP,合肥', '负责自然语言处理算法研发与大模型应用落地。', 'purple'),
('京东', '互联网', '北京', '供应链运营', '补录', '2026-10-15', '2026-12-20', 'https://campus.jd.com/', '互联网,供应链,北京', '负责供应链数据分析与流程优化，协调仓储与配送资源。', 'slate');
