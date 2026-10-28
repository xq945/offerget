-- ============================================================
-- 二期迁移：简历制作器 / 简历优化记录 / 校招信息库
-- 对已初始化的 offerget 库执行一次
-- 执行：docker exec -i offerget-mysql mysql -uroot -pofferget123 offerget < Mysql/migration/20261027_phase2.sql
-- ============================================================
USE offerget;
SET NAMES utf8mb4;

-- 1. 简历制作器：保存结构化内容，便于二次编辑
ALTER TABLE resume
    ADD COLUMN content_json TEXT NULL COMMENT '结构化简历 JSON（制作器）' AFTER content_text;

-- 2. 简历优化记录
CREATE TABLE IF NOT EXISTS resume_optimization (
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

-- 3. 校招信息库（公共只读数据）
CREATE TABLE IF NOT EXISTS company_calendar (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    company     VARCHAR(100)    NOT NULL COMMENT '企业名称',
    industry    VARCHAR(50)     NOT NULL COMMENT '行业',
    city        VARCHAR(50)     NOT NULL COMMENT '城市',
    job_title   VARCHAR(120)    NOT NULL COMMENT '岗位名称',
    batch       VARCHAR(30)     NOT NULL COMMENT '提前批 / 正式批 / 补录',
    open_date   DATE                     DEFAULT NULL COMMENT '开启日期',
    deadline    DATE                     DEFAULT NULL COMMENT '截止日期',
    apply_url   VARCHAR(300)             DEFAULT NULL COMMENT '校招官网',
    tags        VARCHAR(200)             DEFAULT NULL COMMENT '标签，逗号分隔',
    description VARCHAR(500)             DEFAULT NULL COMMENT '岗位说明',
    tone        VARCHAR(30)              DEFAULT NULL COMMENT '前端配色标识',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_industry (industry),
    KEY idx_company_deadline (deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校招信息库';

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
