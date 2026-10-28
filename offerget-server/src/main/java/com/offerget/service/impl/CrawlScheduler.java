package com.offerget.service.impl;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.offerget.dto.crawl.BatchRunVO;
import com.offerget.service.CrawlService;

import lombok.RequiredArgsConstructor;

/**
 * 定时校招采集：遍历所有用户启用的关注公司，逐家采集。
 * 单实例部署，用原子标志避免上一次任务尚未结束时重复触发。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.crawl.schedule", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final CrawlService crawlService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${app.crawl.schedule.cron:0 30 7 * * *}",
            zone = "${app.crawl.schedule.zone:Asia/Shanghai}")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一次校招采集任务尚未结束，本次跳过");
            return;
        }
        long started = System.currentTimeMillis();
        try {
            List<BatchRunVO> results = crawlService.runAllUsers();
            long success = results.stream().filter(BatchRunVO::success).count();
            long created = results.stream().mapToLong(BatchRunVO::created).sum();
            long updated = results.stream().mapToLong(BatchRunVO::updated).sum();
            log.info("定时采集完成：{} 家公司，成功 {}，新增 {}，更新 {}，耗时 {} ms",
                    results.size(), success, created, updated,
                    System.currentTimeMillis() - started);
        }
        catch (Exception ex) {
            log.error("定时采集任务失败", ex);
        }
        finally {
            running.set(false);
        }
    }

}
