package com.offerget.service.impl;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.dto.review.ReviewVO;
import com.offerget.entity.AiMatchRecord;
import com.offerget.entity.Job;
import com.offerget.entity.JobEvent;
import com.offerget.enums.EventType;
import com.offerget.enums.JobStatus;
import com.offerget.mapper.AiMatchRecordMapper;
import com.offerget.mapper.JobEventMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.ReviewService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final Set<String> WRITTEN_STATUSES = Set.of(
            JobStatus.WRITTEN_TEST.name(), JobStatus.INTERVIEW.name(), JobStatus.OFFER.name());

    private static final Set<String> INTERVIEW_STATUSES = Set.of(
            JobStatus.INTERVIEW.name(), JobStatus.OFFER.name());

    private final JobMapper jobMapper;

    private final JobEventMapper jobEventMapper;

    private final AiMatchRecordMapper matchRecordMapper;

    private final ObjectMapper objectMapper;

    @Override
    public ReviewVO overview() {
        Long userId = SecurityUtils.currentUserId();
        List<Job> jobs = jobMapper.selectList(new LambdaQueryWrapper<Job>().eq(Job::getUserId, userId));
        List<JobEvent> events = jobEventMapper.selectList(new LambdaQueryWrapper<JobEvent>()
                .eq(JobEvent::getUserId, userId));
        List<AiMatchRecord> matches = matchRecordMapper.selectList(new LambdaQueryWrapper<AiMatchRecord>()
                .eq(AiMatchRecord::getUserId, userId));

        Set<Long> applied = new HashSet<>();
        Set<Long> written = new HashSet<>();
        Set<Long> interview = new HashSet<>();
        Set<Long> offer = new HashSet<>();

        for (Job job : jobs) {
            if (job.getStatus() == null) {
                continue;
            }
            if (!JobStatus.PENDING.name().equals(job.getStatus())) {
                applied.add(job.getId());
            }
            if (WRITTEN_STATUSES.contains(job.getStatus())) {
                written.add(job.getId());
            }
            if (INTERVIEW_STATUSES.contains(job.getStatus())) {
                interview.add(job.getId());
            }
            if (JobStatus.OFFER.name().equals(job.getStatus())) {
                offer.add(job.getId());
            }
        }
        for (JobEvent event : events) {
            switch (event.getEventType()) {
                case "APPLY" -> applied.add(event.getJobId());
                case "WRITTEN_TEST" -> written.add(event.getJobId());
                case "INTERVIEW" -> interview.add(event.getJobId());
                default -> {
                }
            }
        }

        long total = jobs.size();
        ReviewVO.Funnel funnel = new ReviewVO.Funnel(
                total, applied.size(), written.size(), interview.size(), offer.size(),
                rate(applied.size(), total),
                rate(written.size(), applied.size()),
                rate(interview.size(), written.size()),
                rate(offer.size(), interview.size()));

        ReviewVO.MonthlyPoint[] monthly = buildMonthly(jobs);

        OptionalDouble average = jobs.stream()
                .filter(job -> job.getMatchScore() != null)
                .mapToInt(Job::getMatchScore)
                .average();
        Integer averageScore = average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;

        List<ReviewVO.KeywordCount> missingKeywords = buildMissingKeywords(matches);
        List<String> insights = buildInsights(jobs, funnel, averageScore, missingKeywords);

        return new ReviewVO(funnel, List.of(monthly), averageScore, missingKeywords, insights);
    }

    private ReviewVO.MonthlyPoint[] buildMonthly(List<Job> jobs) {
        YearMonth current = YearMonth.now();
        ReviewVO.MonthlyPoint[] points = new ReviewVO.MonthlyPoint[6];
        for (int i = 0; i < 6; i++) {
            YearMonth month = current.minusMonths(5 - i);
            long count = jobs.stream()
                    .filter(job -> job.getCreatedAt() != null
                            && YearMonth.from(job.getCreatedAt()).equals(month))
                    .count();
            points[i] = new ReviewVO.MonthlyPoint(month.toString(), month.getMonthValue() + " 月", count);
        }
        return points;
    }

    private List<ReviewVO.KeywordCount> buildMissingKeywords(List<AiMatchRecord> matches) {
        Map<String, Long> counter = new LinkedHashMap<>();
        for (AiMatchRecord match : matches) {
            for (String keyword : readJson(match.getMissingKeywords())) {
                counter.merge(keyword, 1L, Long::sum);
            }
        }
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new ReviewVO.KeywordCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<String> buildInsights(List<Job> jobs, ReviewVO.Funnel funnel,
                                       Integer averageScore, List<ReviewVO.KeywordCount> missingKeywords) {
        List<String> insights = new ArrayList<>();
        if (jobs.isEmpty()) {
            insights.add("还没有岗位记录，先添加 3-5 个目标岗位，复盘页会随着投递逐步生成结论。");
            return insights;
        }

        insights.add("累计管理 " + funnel.total() + " 个岗位，其中 " + funnel.applied()
                + " 个已投递，整体投递覆盖率 " + funnel.applyRate() + "%。");

        if (funnel.writtenTest() > 0) {
            insights.add("投递 → 笔试转化率 " + funnel.writtenRate() + "%，笔试 → 面试转化率 "
                    + funnel.interviewRate() + "%，重点关注笔试后的复盘与补强。");
        }
        else {
            insights.add("当前还没有进入笔试阶段，建议优先选择匹配分 70 分以上的岗位再投递。");
        }

        if (funnel.offer() > 0) {
            insights.add("已收获 " + funnel.offer() + " 个 Offer，面试 → Offer 转化率 "
                    + funnel.offerRate() + "%，后续可重点比较城市、薪资与成长性。");
        }
        else if (funnel.interview() > 0) {
            insights.add("已有 " + funnel.interview() + " 个岗位进入面试，下一阶段重点打磨项目复盘与岗位业务理解。");
        }
        else {
            insights.add("还没有岗位进入面试，建议先补齐简历关键词，再集中投递匹配度更高的岗位。");
        }

        if (averageScore != null) {
            insights.add("已匹配岗位平均匹配分 " + averageScore
                    + (averageScore < 65 ? "，低于建议投递线，先改简历再投递。" : "，整体与目标岗位比较契合。"));
        }
        if (!missingKeywords.isEmpty()) {
            String top = missingKeywords.stream().limit(3)
                    .map(ReviewVO.KeywordCount::keyword)
                    .collect(Collectors.joining("」「"));
            insights.add("高频缺失关键词：「" + top + "」，优先在项目经历中补充相关实践与量化结果。");
        }
        return insights.stream().limit(5).toList();
    }

    private double rate(long part, long base) {
        if (base == 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / base) / 10.0;
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        }
        catch (Exception ex) {
            return List.of();
        }
    }

}
