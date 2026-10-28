package com.offerget.service;

import java.util.List;

import com.offerget.dto.crawl.ApproveBatchRequest;
import com.offerget.dto.crawl.BatchRunVO;
import com.offerget.dto.crawl.CrawlDraftVO;
import com.offerget.dto.crawl.CrawlRunVO;
import com.offerget.dto.crawl.DomainCandidateVO;
import com.offerget.dto.crawl.DraftUpdateRequest;
import com.offerget.dto.crawl.WatchCompanyRequest;
import com.offerget.dto.crawl.WatchCompanyVO;

public interface CrawlService {

    List<WatchCompanyVO> listWatch();

    WatchCompanyVO createWatch(WatchCompanyRequest request);

    WatchCompanyVO updateWatch(Long id, WatchCompanyRequest request);

    void deleteWatch(Long id);

    List<DomainCandidateVO> discoverDomain(Long id);

    CrawlRunVO run(Long id);

    /** 采集当前用户所有启用的关注公司 */
    List<BatchRunVO> runAll();

    /** 采集所有用户启用的关注公司，供定时任务使用 */
    List<BatchRunVO> runAllUsers();

    List<CrawlDraftVO> listDrafts(String status);

    CrawlDraftVO updateDraft(Long id, DraftUpdateRequest request);

    CrawlDraftVO approve(Long id);

    CrawlDraftVO reject(Long id);

    List<CrawlDraftVO> approveBatch(ApproveBatchRequest request);

}
