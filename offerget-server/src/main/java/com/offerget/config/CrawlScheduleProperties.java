package com.offerget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 校招采集定时任务配置。
 */
@Data
@ConfigurationProperties(prefix = "app.crawl.schedule")
public class CrawlScheduleProperties {

    /** 是否启用定时采集 */
    private boolean enabled = true;

    /** cron 表达式，默认每天 07:30 */
    private String cron = "0 30 7 * * *";

    /** 时区 */
    private String zone = "Asia/Shanghai";

    /** 相邻两家公司之间的间隔，避免触发搜索接口限流 */
    private long companyDelayMillis = 2000;

}
