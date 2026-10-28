package com.offerget.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.ai.AiClient;
import com.offerget.common.BusinessException;
import com.offerget.config.CrawlScheduleProperties;
import com.offerget.dto.crawl.ApproveBatchRequest;
import com.offerget.dto.crawl.BatchRunVO;
import com.offerget.dto.crawl.CrawlDraftVO;
import com.offerget.dto.crawl.CrawlRunVO;
import com.offerget.dto.crawl.DomainCandidateVO;
import com.offerget.dto.crawl.DraftUpdateRequest;
import com.offerget.dto.crawl.WatchCompanyRequest;
import com.offerget.dto.crawl.WatchCompanyVO;
import com.offerget.entity.AppUser;
import com.offerget.entity.CompanyCalendar;
import com.offerget.entity.CrawlDraft;
import com.offerget.entity.CrawlWatchCompany;
import com.offerget.entity.Job;
import com.offerget.mapper.CompanyCalendarMapper;
import com.offerget.mapper.CrawlDraftMapper;
import com.offerget.mapper.CrawlWatchCompanyMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.UserMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.CrawlService;

import lombok.RequiredArgsConstructor;

/**
 * 校招信息采集：维护关注公司、按官网定向采集、审核候选后写入信息库。
 * 采集结果一律先落候选表，人工确认后才进 company_calendar。
 */
@Service
@RequiredArgsConstructor
public class CrawlServiceImpl implements CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlServiceImpl.class);

    private static final String STATUS_DRAFT = "DRAFT";

    private static final String STATUS_APPROVED = "APPROVED";

    private static final String STATUS_REJECTED = "REJECTED";

    private static final int MAX_DRAFT_LIST = 100;

    private static final int MAX_DOMAIN_CANDIDATES = 6;

    /** 从公告正文里识别域名，兼容被转义的写法（campus\.jd\.com） */
    private static final java.util.regex.Pattern DOMAIN_PATTERN = java.util.regex.Pattern.compile(
            "[a-zA-Z0-9][a-zA-Z0-9\\-]*(?:\\.[a-zA-Z0-9][a-zA-Z0-9\\-]*)*"
                    + "\\.(?:com|cn|net|org|io|tech|top|ai)(?:\\.[a-z]{2})?");

    private final CrawlWatchCompanyMapper watchMapper;

    private final CrawlDraftMapper draftMapper;

    private final CompanyCalendarMapper companyMapper;

    private final JobMapper jobMapper;

    private final UserMapper userMapper;

    private final AiClient aiClient;

    private final CrawlEngine crawlEngine;

    private final CrawlScheduleProperties scheduleProperties;

    // ---------------------------------------------------------------- 关注清单

    @Override
    public List<WatchCompanyVO> listWatch() {
        Long userId = SecurityUtils.currentUserId();
        List<CrawlWatchCompany> watches = watchMapper.selectList(
                new LambdaQueryWrapper<CrawlWatchCompany>()
                        .eq(CrawlWatchCompany::getUserId, userId)
                        .orderByDesc(CrawlWatchCompany::getCreatedAt));
        if (watches.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        List<CrawlDraft> drafts = draftMapper.selectList(new LambdaQueryWrapper<CrawlDraft>()
                .eq(CrawlDraft::getUserId, userId)
                .eq(CrawlDraft::getStatus, STATUS_DRAFT));
        for (CrawlDraft draft : drafts) {
            counts.merge(draft.getWatchCompanyId(), 1L, Long::sum);
        }

        return watches.stream()
                .map(watch -> toWatchVO(watch, counts.getOrDefault(watch.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public WatchCompanyVO createWatch(WatchCompanyRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String company = request.company().trim();
        Long exists = watchMapper.selectCount(new LambdaQueryWrapper<CrawlWatchCompany>()
                .eq(CrawlWatchCompany::getUserId, userId)
                .eq(CrawlWatchCompany::getCompany, company));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("已经关注过这家公司");
        }

        CrawlWatchCompany watch = new CrawlWatchCompany();
        watch.setUserId(userId);
        watch.setCompany(company);
        watch.setOfficialDomain(cleanDomain(request.officialDomain()));
        watch.setIndustry(trimOrNull(request.industry()));
        watch.setCity(trimOrNull(request.city()));
        watch.setTargetGraduationYear(request.targetGraduationYear() != null
                ? request.targetGraduationYear() : graduationYearOf(userId));
        watch.setEnabled(true);
        watchMapper.insert(watch);
        return toWatchVO(watch, 0);
    }

    @Override
    @Transactional
    public WatchCompanyVO updateWatch(Long id, WatchCompanyRequest request) {
        CrawlWatchCompany watch = requireWatch(id);
        watch.setCompany(request.company().trim());
        watch.setOfficialDomain(cleanDomain(request.officialDomain()));
        watch.setIndustry(trimOrNull(request.industry()));
        watch.setCity(trimOrNull(request.city()));
        if (request.targetGraduationYear() != null) {
            watch.setTargetGraduationYear(request.targetGraduationYear());
        }
        watchMapper.updateById(watch);
        return toWatchVO(watch, draftCountOf(watch.getId()));
    }

    @Override
    @Transactional
    public void deleteWatch(Long id) {
        CrawlWatchCompany watch = requireWatch(id);
        draftMapper.delete(new LambdaQueryWrapper<CrawlDraft>()
                .eq(CrawlDraft::getUserId, watch.getUserId())
                .eq(CrawlDraft::getWatchCompanyId, watch.getId()));
        watchMapper.deleteById(watch.getId());
    }

    /**
     * 域名发现：自然搜索经常被高校就业网占满，官网域名往往写在那些招聘简章正文里
     * （如「登录京东校园招聘官网 campus.jd.com」），所以先看结果域名，再抓公告正文提取。
     */
    @Override
    public List<DomainCandidateVO> discoverDomain(Long id) {
        CrawlWatchCompany watch = requireWatch(id);
        List<AiClient.SearchResult> results = aiClient.search(
                watch.getCompany() + " 校园招聘", 10);

        Map<String, DomainCandidateVO> candidates = new LinkedHashMap<>();
        List<String> cluePages = new ArrayList<>();
        for (AiClient.SearchResult result : results) {
            String url = result.url();
            if (!StringUtils.hasText(url) || !url.startsWith("http")) {
                continue;
            }
            String host = hostOf(url);
            if (host == null) {
                continue;
            }
            if (isUnlikelyOfficial(host)) {
                // 高校就业网 / 聚合站：抓正文找官网域名
                if (cluePages.size() < 2) {
                    cluePages.add(url);
                }
                continue;
            }
            candidates.putIfAbsent(host, new DomainCandidateVO(host, result.title(), url, "搜索结果"));
            if (candidates.size() >= MAX_DOMAIN_CANDIDATES) {
                break;
            }
        }

        for (String page : cluePages) {
            String markdown;
            try {
                markdown = aiClient.fetchMarkdown(page);
            }
            catch (BusinessException ex) {
                log.info("线索页抓取失败，跳过：{}", page);
                continue;
            }
            for (String domain : domainsIn(markdown)) {
                candidates.putIfAbsent(domain, new DomainCandidateVO(domain,
                        "从招聘简章提取", "https://" + domain, "出现在公告正文中"));
                if (candidates.size() >= MAX_DOMAIN_CANDIDATES) {
                    break;
                }
            }
            if (candidates.size() >= MAX_DOMAIN_CANDIDATES) {
                break;
            }
        }

        List<DomainCandidateVO> ordered = new ArrayList<>(candidates.values());
        ordered.sort((a, b) -> Integer.compare(domainScore(b.domain()), domainScore(a.domain())));
        return ordered;
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        }
        catch (Exception ex) {
            return null;
        }
    }

    private List<String> domainsIn(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String normalized = content.replace("\\.", ".");
        Set<String> found = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = DOMAIN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String domain = matcher.group().toLowerCase();
            if (domain.length() < 6 || domain.length() > 60 || isUnlikelyOfficial(domain)) {
                continue;
            }
            found.add(domain);
        }
        return new ArrayList<>(found);
    }

    private int domainScore(String domain) {
        String d = domain.toLowerCase();
        int score = 0;
        if (d.startsWith("campus.")) {
            score += 4;
        }
        if (d.contains("recruit") || d.contains("zhaopin") || d.contains("talent")
                || d.contains("career") || d.startsWith("job.")) {
            score += 2;
        }
        if (d.contains("hr")) {
            score += 1;
        }
        return score;
    }

    @Override
    public CrawlRunVO run(Long id) {
        return doRun(requireWatch(id));
    }

    @Override
    public List<BatchRunVO> runAll() {
        return runForUser(SecurityUtils.currentUserId());
    }

    @Override
    public List<BatchRunVO> runAllUsers() {
        List<Long> userIds = watchMapper.selectList(new LambdaQueryWrapper<CrawlWatchCompany>()
                        .select(CrawlWatchCompany::getUserId)
                        .eq(CrawlWatchCompany::getEnabled, true))
                .stream()
                .map(CrawlWatchCompany::getUserId)
                .distinct()
                .toList();
        List<BatchRunVO> results = new ArrayList<>();
        for (Long userId : userIds) {
            results.addAll(runForUser(userId));
        }
        return results;
    }

    /** 逐家采集，单家失败不影响其他公司 */
    private List<BatchRunVO> runForUser(Long userId) {
        List<CrawlWatchCompany> watches = watchMapper.selectList(new LambdaQueryWrapper<CrawlWatchCompany>()
                .eq(CrawlWatchCompany::getUserId, userId)
                .eq(CrawlWatchCompany::getEnabled, true)
                .orderByAsc(CrawlWatchCompany::getLastCrawledAt));
        List<BatchRunVO> results = new ArrayList<>();
        for (CrawlWatchCompany watch : watches) {
            if (!StringUtils.hasText(watch.getOfficialDomain())) {
                results.add(new BatchRunVO(watch.getCompany(), false, 0, 0, 0, "未设置官网域名"));
                continue;
            }
            try {
                CrawlRunVO run = doRun(watch);
                results.add(new BatchRunVO(watch.getCompany(), true,
                        run.created(), run.updated(), run.skipped(), run.message()));
            }
            catch (Exception ex) {
                log.warn("采集失败：{}（{}）", watch.getCompany(), ex.getMessage());
                results.add(new BatchRunVO(watch.getCompany(), false, 0, 0, 0,
                        ex.getMessage() == null ? "采集失败" : ex.getMessage()));
            }
            sleepQuietly();
        }
        return results;
    }

    private void sleepQuietly() {
        long delay = scheduleProperties.getCompanyDelayMillis();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 采集单家公司，不依赖登录上下文，手动触发与定时任务共用。
     * 不加事务：外部调用耗时长，且中途失败时已写入的候选应当保留。
     */
    private CrawlRunVO doRun(CrawlWatchCompany watch) {
        if (!StringUtils.hasText(watch.getOfficialDomain())) {
            throw BusinessException.badRequest("请先确认该公司的招聘官网域名");
        }
        int year = watch.getTargetGraduationYear() != null
                ? watch.getTargetGraduationYear() : graduationYearOf(watch.getUserId());

        CrawlEngine.Outcome outcome = crawlEngine.crawl(watch, year);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (CrawlEngine.ExtractedItem item : outcome.items()) {
            CrawlDraft existing = findExistingDraft(watch.getId(), item.jobTitle(), item.batch());
            if (existing == null) {
                draftMapper.insert(toDraft(watch, item));
                created++;
            }
            else {
                boolean changed = applyItem(existing, item);
                if (changed) {
                    // 已确认过的条目在关键字段变化时回到待审核，让用户复核
                    existing.setStatus(STATUS_DRAFT);
                    updated++;
                }
                else {
                    skipped++;
                }
                existing.setCrawledAt(LocalDateTime.now());
                draftMapper.updateById(existing);
            }
        }

        watch.setLastCrawledAt(LocalDateTime.now());
        watchMapper.updateById(watch);

        log.info("采集完成：{}，新增 {}，更新 {}，无变化 {}", watch.getCompany(), created, updated, skipped);
        return new CrawlRunVO(watch.getCompany(), outcome.searched(), outcome.fetched(),
                created, updated, skipped, outcome.message());
    }

    // ---------------------------------------------------------------- 候选审核

    @Override
    public List<CrawlDraftVO> listDrafts(String status) {
        Long userId = SecurityUtils.currentUserId();
        String target = StringUtils.hasText(status) ? status.trim().toUpperCase() : STATUS_DRAFT;
        List<CrawlDraft> drafts = draftMapper.selectList(new LambdaQueryWrapper<CrawlDraft>()
                .eq(CrawlDraft::getUserId, userId)
                .eq(CrawlDraft::getStatus, target)
                .orderByDesc(CrawlDraft::getCrawledAt)
                .last("limit " + MAX_DRAFT_LIST));
        if (drafts.isEmpty()) {
            return List.of();
        }
        Set<String> imported = importedKeysOf(userId);
        return drafts.stream().map(draft -> toDraftVO(draft, imported)).toList();
    }

    @Override
    @Transactional
    public CrawlDraftVO updateDraft(Long id, DraftUpdateRequest request) {
        CrawlDraft draft = requireDraft(id);
        if (StringUtils.hasText(request.infoType())) {
            draft.setInfoType("PROJECT".equalsIgnoreCase(request.infoType()) ? "PROJECT" : "POSITION");
        }
        if (request.industry() != null) {
            draft.setIndustry(trimOrNull(request.industry()));
        }
        if (request.city() != null) {
            draft.setCity(trimOrNull(request.city()));
        }
        if (StringUtils.hasText(request.jobTitle())) {
            draft.setJobTitle(request.jobTitle().trim());
        }
        if (StringUtils.hasText(request.batch())) {
            draft.setBatch(request.batch().trim());
        }
        if (request.openDate() != null) {
            draft.setOpenDate(request.openDate());
        }
        if (request.deadline() != null) {
            draft.setDeadline(request.deadline());
        }
        if (request.applyUrl() != null) {
            draft.setApplyUrl(trimOrNull(request.applyUrl()));
        }
        if (request.tags() != null) {
            draft.setTags(trimOrNull(request.tags()));
        }
        if (request.description() != null) {
            draft.setDescription(trimOrNull(request.description()));
        }
        draftMapper.updateById(draft);
        return toDraftVO(draft, importedKeysOf(draft.getUserId()));
    }

    @Override
    @Transactional
    public CrawlDraftVO approve(Long id) {
        CrawlDraft draft = requireDraft(id);
        CrawlWatchCompany watch = watchMapper.selectById(draft.getWatchCompanyId());

        CompanyCalendar calendar = new CompanyCalendar();
        calendar.setCompany(draft.getCompany());
        calendar.setIndustry(StringUtils.hasText(draft.getIndustry()) ? draft.getIndustry()
                : industryFallback(watch));
        calendar.setCity(StringUtils.hasText(draft.getCity()) ? draft.getCity()
                : cityFallback(watch));
        calendar.setJobTitle(draft.getJobTitle());
        calendar.setBatch(StringUtils.hasText(draft.getBatch()) ? draft.getBatch() : "其他");
        calendar.setInfoType(StringUtils.hasText(draft.getInfoType()) ? draft.getInfoType() : "POSITION");
        calendar.setOpenDate(draft.getOpenDate());
        calendar.setDeadline(draft.getDeadline());
        calendar.setApplyUrl(draft.getApplyUrl());
        calendar.setSourceUrl(draft.getSourceUrl());
        calendar.setSourceType("AI");
        calendar.setLastCheckedAt(LocalDateTime.now());
        calendar.setTags(draft.getTags());
        calendar.setDescription(draft.getDescription());

        if (draft.getPublishedId() != null && companyMapper.selectById(draft.getPublishedId()) != null) {
            calendar.setId(draft.getPublishedId());
            companyMapper.updateById(calendar);
        }
        else {
            companyMapper.insert(calendar);
            draft.setPublishedId(calendar.getId());
        }

        draft.setStatus(STATUS_APPROVED);
        draft.setReviewedAt(LocalDateTime.now());
        draftMapper.updateById(draft);
        return toDraftVO(draft, importedKeysOf(draft.getUserId()));
    }

    @Override
    @Transactional
    public CrawlDraftVO reject(Long id) {
        CrawlDraft draft = requireDraft(id);
        draft.setStatus(STATUS_REJECTED);
        draft.setReviewedAt(LocalDateTime.now());
        draftMapper.updateById(draft);
        return toDraftVO(draft, importedKeysOf(draft.getUserId()));
    }

    @Override
    @Transactional
    public List<CrawlDraftVO> approveBatch(ApproveBatchRequest request) {
        List<CrawlDraftVO> approved = new ArrayList<>();
        for (Long id : request.ids()) {
            try {
                approved.add(approve(id));
            }
            catch (BusinessException ex) {
                log.warn("批量确认跳过 {}：{}", id, ex.getMessage());
            }
        }
        return approved;
    }

    // ---------------------------------------------------------------- 通用

    private CrawlWatchCompany requireWatch(Long id) {
        Long userId = SecurityUtils.currentUserId();
        CrawlWatchCompany watch = watchMapper.selectById(id);
        if (watch == null || !watch.getUserId().equals(userId)) {
            throw BusinessException.notFound("关注公司不存在");
        }
        return watch;
    }

    private CrawlDraft requireDraft(Long id) {
        Long userId = SecurityUtils.currentUserId();
        CrawlDraft draft = draftMapper.selectById(id);
        if (draft == null || !draft.getUserId().equals(userId)) {
            throw BusinessException.notFound("采集候选不存在");
        }
        return draft;
    }

    private long draftCountOf(Long watchId) {
        Long count = draftMapper.selectCount(new LambdaQueryWrapper<CrawlDraft>()
                .eq(CrawlDraft::getWatchCompanyId, watchId)
                .eq(CrawlDraft::getStatus, STATUS_DRAFT));
        return count == null ? 0 : count;
    }

    private int graduationYearOf(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user != null && user.getGraduationYear() != null) {
            return user.getGraduationYear();
        }
        return LocalDate.now().getYear() + 1;
    }

    private Set<String> importedKeysOf(Long userId) {
        return jobMapper.selectList(new LambdaQueryWrapper<Job>()
                        .eq(Job::getUserId, userId))
                .stream()
                .map(job -> job.getCompany() + "|" + job.getTitle())
                .collect(Collectors.toSet());
    }

    /**
     * 先按标题精确匹配；模型偶尔会改写空格，再去空格比对一次，避免同一岗位重复入库。
     */
    private CrawlDraft findExistingDraft(Long watchId, String jobTitle, String batch) {
        CrawlDraft exact = draftMapper.selectOne(new LambdaQueryWrapper<CrawlDraft>()
                .eq(CrawlDraft::getWatchCompanyId, watchId)
                .eq(CrawlDraft::getJobTitle, jobTitle)
                .eq(CrawlDraft::getBatch, batch));
        if (exact != null) {
            return exact;
        }
        String normalized = jobTitle.replaceAll("\\s+", "");
        List<CrawlDraft> sameBatch = draftMapper.selectList(new LambdaQueryWrapper<CrawlDraft>()
                .eq(CrawlDraft::getWatchCompanyId, watchId)
                .eq(CrawlDraft::getBatch, batch));
        for (CrawlDraft draft : sameBatch) {
            if (draft.getJobTitle() != null
                    && draft.getJobTitle().replaceAll("\\s+", "").equalsIgnoreCase(normalized)) {
                return draft;
            }
        }
        return null;
    }

    private CrawlDraft toDraft(CrawlWatchCompany watch, CrawlEngine.ExtractedItem item) {
        CrawlDraft draft = new CrawlDraft();
        draft.setUserId(watch.getUserId());
        draft.setWatchCompanyId(watch.getId());
        draft.setInfoType(item.infoType());
        draft.setCompany(watch.getCompany());
        draft.setIndustry(watch.getIndustry());
        draft.setCity(StringUtils.hasText(item.city()) ? item.city() : watch.getCity());
        draft.setJobTitle(item.jobTitle());
        draft.setBatch(item.batch());
        draft.setOpenDate(item.openDate());
        draft.setDeadline(item.deadline());
        draft.setApplyUrl(item.applyUrl());
        draft.setSourceUrl(item.sourceUrl());
        draft.setTags(item.tags());
        draft.setDescription(item.description());
        draft.setEvidenceJson(item.evidence());
        draft.setConfidence(BigDecimal.valueOf(item.confidence()));
        draft.setStatus(STATUS_DRAFT);
        return draft;
    }

    /**
     * 用最新采集结果刷新候选。只有截止日期、开启日期、投递链接这类关键字段变化
     * 才返回 true（需要用户重新确认）；描述、证据每次措辞都可能不同，只更新不触发复核。
     */
    private boolean applyItem(CrawlDraft draft, CrawlEngine.ExtractedItem item) {
        boolean critical = false;
        // 模型偶尔漏抽字段，null 不覆盖已有值，避免把确认过的截止时间抹掉
        if (item.openDate() != null && !Objects.equals(draft.getOpenDate(), item.openDate())) {
            draft.setOpenDate(item.openDate());
            critical = true;
        }
        if (item.deadline() != null && !Objects.equals(draft.getDeadline(), item.deadline())) {
            draft.setDeadline(item.deadline());
            critical = true;
        }
        if (item.applyUrl() != null && !Objects.equals(draft.getApplyUrl(), item.applyUrl())) {
            draft.setApplyUrl(item.applyUrl());
            critical = true;
        }
        if (!Objects.equals(draft.getCity(), item.city()) && StringUtils.hasText(item.city())) {
            draft.setCity(item.city());
        }
        if (!Objects.equals(draft.getDescription(), item.description())) {
            draft.setDescription(item.description());
        }
        if (!Objects.equals(draft.getTags(), item.tags())) {
            draft.setTags(item.tags());
        }
        if (!Objects.equals(draft.getSourceUrl(), item.sourceUrl())) {
            draft.setSourceUrl(item.sourceUrl());
        }
        if (!Objects.equals(draft.getEvidenceJson(), item.evidence())) {
            draft.setEvidenceJson(item.evidence());
        }
        BigDecimal confidence = BigDecimal.valueOf(item.confidence());
        if (!Objects.equals(draft.getConfidence(), confidence)) {
            draft.setConfidence(confidence);
        }
        return critical;
    }

    private WatchCompanyVO toWatchVO(CrawlWatchCompany watch, long draftCount) {
        return new WatchCompanyVO(watch.getId(), watch.getCompany(), watch.getOfficialDomain(),
                watch.getIndustry(), watch.getCity(), watch.getTargetGraduationYear(),
                Boolean.TRUE.equals(watch.getEnabled()), watch.getLastCrawledAt(), draftCount,
                watch.getCreatedAt());
    }

    private CrawlDraftVO toDraftVO(CrawlDraft draft, Set<String> importedKeys) {
        List<String> tags = StringUtils.hasText(draft.getTags())
                ? List.of(draft.getTags().split(","))
                : List.of();
        boolean imported = importedKeys.contains(draft.getCompany() + "|" + draft.getJobTitle());
        return new CrawlDraftVO(draft.getId(), draft.getWatchCompanyId(), draft.getCompany(),
                draft.getInfoType(), draft.getIndustry(), draft.getCity(), draft.getJobTitle(),
                draft.getBatch(), draft.getOpenDate(), draft.getDeadline(), draft.getApplyUrl(),
                draft.getSourceUrl(), tags, draft.getDescription(), draft.getEvidenceJson(),
                draft.getConfidence() == null ? null : draft.getConfidence().doubleValue(),
                draft.getStatus(), draft.getCrawledAt(), imported);
    }

    private String cleanDomain(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String domain = value.trim().toLowerCase().replaceFirst("^https?://", "");
        int slash = domain.indexOf('/');
        if (slash >= 0) {
            domain = domain.substring(0, slash);
        }
        return domain.isEmpty() ? null : domain;
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String industryFallback(CrawlWatchCompany watch) {
        return watch != null && StringUtils.hasText(watch.getIndustry()) ? watch.getIndustry() : "其他";
    }

    private String cityFallback(CrawlWatchCompany watch) {
        return watch != null && StringUtils.hasText(watch.getCity()) ? watch.getCity() : "其他";
    }

    /** 高校就业网、新闻站、招聘聚合站通常不是公司招聘官网 */
    private boolean isUnlikelyOfficial(String host) {
        String h = host.toLowerCase();
        return h.endsWith(".edu.cn") || h.endsWith(".edu")
                || h.startsWith("news.") || h.endsWith("news.cn")
                || h.contains("weixin") || h.contains("bysjy") || h.contains("gxbys")
                || h.contains("smartedu") || h.contains("ncss")
                || h.endsWith(".zhipin.com") || h.endsWith(".nowcoder.com")
                || h.endsWith(".yingjiesheng.com") || h.endsWith(".shixiseng.com")
                || h.endsWith(".liepin.com") || h.endsWith(".51job.com")
                || h.endsWith(".zhaopin.com") || h.endsWith(".zhihu.com")
                || h.endsWith(".weibo.com") || h.contains("36kr");
    }

}
