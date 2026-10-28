-- ============================================================
-- 通知已读状态：提醒按天动态生成，这里只记录哪些被点开过
-- 执行：docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261027_notification.sql
-- ============================================================
USE offerget;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS notification_read (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    notification_key VARCHAR(120)    NOT NULL COMMENT '提醒唯一键（含日期，按天重置）',
    read_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_user_key (user_id, notification_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提醒已读状态';
