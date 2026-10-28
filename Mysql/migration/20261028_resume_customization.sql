-- ============================================================
-- AI 简历定制：改写记录表
-- 对已初始化的 offerget 库执行一次
-- 执行：docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261028_resume_customization.sql
-- ============================================================
USE offerget;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS resume_customization (
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
