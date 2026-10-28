-- ============================================================
-- 校招信息采集：关注公司 / 采集候选 / 信息库扩展字段
-- 对已初始化的 offerget 库执行一次
-- 执行：docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261028_crawl.sql
-- ============================================================
USE offerget;
SET NAMES utf8mb4;

-- 1. 关注公司
CREATE TABLE IF NOT EXISTS crawl_watch_company (
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

-- 2. 采集候选（人工确认后才写入信息库）
CREATE TABLE IF NOT EXISTS crawl_draft (
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

-- 3. 信息库扩展字段
ALTER TABLE company_calendar
    ADD COLUMN info_type       VARCHAR(20)  NOT NULL DEFAULT 'POSITION' COMMENT 'POSITION / PROJECT' AFTER batch,
    ADD COLUMN source_url      VARCHAR(300)          DEFAULT NULL COMMENT '信息来源页',
    ADD COLUMN source_type     VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / AI',
    ADD COLUMN last_checked_at DATETIME              DEFAULT NULL COMMENT '最近一次核查';

ALTER TABLE company_calendar
    MODIFY COLUMN batch VARCHAR(30) NOT NULL DEFAULT '其他' COMMENT '提前批 / 正式批 / 补录 / 其他';

ALTER TABLE company_calendar
    ADD UNIQUE KEY uk_calendar_unique (company, job_title, batch);
