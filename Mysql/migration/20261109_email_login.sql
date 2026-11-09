-- ============================================================
-- 邮箱验证码登录：邮箱规范化 + 唯一索引
-- 执行：docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261109_email_login.sql
-- 注意：若第 1 步预检查输出重复邮箱，请先人工合并，否则唯一索引创建会失败。
-- ============================================================
USE offerget;
SET NAMES utf8mb4;

-- 1) 预检查：规范化后重复的邮箱（有结果时先人工处理）
SELECT LOWER(TRIM(email)) AS normalized_email,
       COUNT(*)           AS cnt,
       GROUP_CONCAT(id ORDER BY id) AS user_ids
FROM app_user
WHERE email IS NOT NULL AND TRIM(email) <> ''
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;

-- 2) 统一去空格并转小写；空白串置 NULL（唯一索引允许多个 NULL）
UPDATE app_user
SET email = NULLIF(LOWER(TRIM(email)), '')
WHERE email IS NOT NULL;

-- 3) 幂等添加唯一索引（全新库已由 init.sql 创建）
SET @idx := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'app_user'
      AND index_name = 'uk_user_email'
);
SET @ddl := IF(@idx = 0,
    'ALTER TABLE app_user ADD UNIQUE KEY uk_user_email (email)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
