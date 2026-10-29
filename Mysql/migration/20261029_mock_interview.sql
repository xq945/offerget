-- ============================================================
-- AI 模拟面试：会话 / 题目与作答
-- 对已初始化的 offerget 库执行一次
-- 执行：docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261029_mock_interview.sql
-- ============================================================
USE offerget;
SET NAMES utf8mb4;

-- 1. 模拟面试会话
CREATE TABLE IF NOT EXISTS mock_interview_session (
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

-- 2. 题目与作答（追问链、逐题评分以 JSON 存储）
CREATE TABLE IF NOT EXISTS mock_interview_question (
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
