package com.offerget.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.dto.dashboard.DashboardVO;
import com.offerget.entity.Job;
import com.offerget.entity.JobEvent;
import com.offerget.enums.EventType;
import com.offerget.enums.JobStatus;
import com.offerget.mapper.JobEventMapper;
import com.offerget.mapper.JobMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.DashboardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] WEEKDAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final JobMapper jobMapper;

    private final JobEventMapper jobEventMapper;

    @Override
    public DashboardVO overview() {
        Long userId = SecurityUtils.currentUserId();
        LocalDate today = LocalDate.now();
        List<Job> jobs = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                .eq(Job::getUserId, userId));

        long totalJobs = jobs.size();
        long appliedCount = jobs.stream().filter(j -> !JobStatus.PENDING.name().equals(j.getStatus())).count();
        long interviewingCount = jobs.stream().filter(j -> JobStatus.INTERVIEW.name().equals(j.getStatus())).count();
        long offerCount = jobs.stream().filter(j -> JobStatus.OFFER.name().equals(j.getStatus())).count();

        LocalDate monday = today.with(DayOfWeek.MONDAY);
        long weeklyApplied = jobs.stream()
                .filter(j -> j.getCreatedAt() != null)
                .filter(j -> !j.getCreatedAt().toLocalDate().isBefore(monday))
                .count();

        DashboardVO.Stats stats = new DashboardVO.Stats(
                totalJobs, appliedCount, interviewingCount, offerCount, weeklyApplied);

        Map<LocalDate, Long> createdByDate = jobs.stream()
                .filter(j -> j.getCreatedAt() != null)
                .collect(Collectors.groupingBy(j -> j.getCreatedAt().toLocalDate(), Collectors.counting()));
        List<DashboardVO.TrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            trend.add(new DashboardVO.TrendPoint(WEEKDAY_LABELS[i], date.toString(),
                    createdByDate.getOrDefault(date, 0L)));
        }

        List<DashboardVO.DeadlineItem> deadlines = jobs.stream()
                .filter(j -> j.getDeadline() != null)
                .filter(j -> !j.getDeadline().isBefore(today) && !j.getDeadline().isAfter(today.plusDays(3)))
                .filter(j -> !JobStatus.OFFER.name().equals(j.getStatus())
                        && !JobStatus.REJECTED.name().equals(j.getStatus()))
                .sorted(Comparator.comparing(Job::getDeadline))
                .limit(5)
                .map(j -> new DashboardVO.DeadlineItem(j.getId(), j.getCompany(), j.getTitle(),
                        ChronoUnit.DAYS.between(today, j.getDeadline()), j.getMatchScore(), j.getStatus()))
                .toList();

        List<JobEvent> todayEvents = jobEventMapper.selectList(new LambdaQueryWrapper<JobEvent>()
                .eq(JobEvent::getUserId, userId)
                .ge(JobEvent::getEventTime, today.atStartOfDay())
                .lt(JobEvent::getEventTime, today.plusDays(1).atStartOfDay())
                .orderByAsc(JobEvent::getEventTime));

        Map<Long, Job> jobMap = jobs.stream().collect(Collectors.toMap(Job::getId, j -> j));
        List<DashboardVO.TodoItem> todos = buildTodos(todayEvents, jobs, jobMap, today);

        LocalDateTime rangeStart = today.atStartOfDay();
        LocalDateTime rangeEnd = today.plusDays(8).atStartOfDay();
        List<DashboardVO.CalendarEvent> upcoming = jobEventMapper.selectList(
                        new LambdaQueryWrapper<JobEvent>()
                                .eq(JobEvent::getUserId, userId)
                                .ge(JobEvent::getEventTime, rangeStart)
                                .lt(JobEvent::getEventTime, rangeEnd)
                                .orderByAsc(JobEvent::getEventTime))
                .stream()
                .limit(10)
                .map(event -> toCalendarEvent(event, jobMap.get(event.getJobId())))
                .toList();

        long matchedJobs = jobs.stream().filter(j -> j.getMatchScore() != null).count();
        List<String> suggestions = buildSuggestions(jobs, todayEvents, jobMap, today, matchedJobs, offerCount);

        return new DashboardVO(stats, trend, deadlines, todos, suggestions, upcoming);
    }

    @Override
    public List<DashboardVO.CalendarEvent> calendar(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw BusinessException.badRequest("年月参数不合法");
        }
        Long userId = SecurityUtils.currentUserId();
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<JobEvent> events = jobEventMapper.selectList(new LambdaQueryWrapper<JobEvent>()
                .eq(JobEvent::getUserId, userId)
                .ge(JobEvent::getEventTime, start)
                .lt(JobEvent::getEventTime, end)
                .orderByAsc(JobEvent::getEventTime));

        Map<Long, Job> jobMap = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                        .eq(Job::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(Job::getId, j -> j));

        return events.stream()
                .map(event -> toCalendarEvent(event, jobMap.get(event.getJobId())))
                .toList();
    }

    private List<DashboardVO.TodoItem> buildTodos(List<JobEvent> todayEvents, List<Job> jobs,
                                                  Map<Long, Job> jobMap, LocalDate today) {
        if (!todayEvents.isEmpty()) {
            return todayEvents.stream()
                    .map(event -> {
                        Job job = jobMap.get(event.getJobId());
                        return new DashboardVO.TodoItem(event.getId(), event.getJobId(),
                                event.getTitle(), event.getEventType(),
                                event.getEventTime().format(TIME_FORMAT),
                                job == null ? "" : job.getCompany());
                    })
                    .toList();
        }

        // 今天没有显式事件时，把 3 天内截止且待投递的岗位当作待办
        return jobs.stream()
                .filter(j -> JobStatus.PENDING.name().equals(j.getStatus()))
                .filter(j -> j.getDeadline() != null)
                .filter(j -> !j.getDeadline().isBefore(today) && !j.getDeadline().isAfter(today.plusDays(3)))
                .sorted(Comparator.comparing(Job::getDeadline))
                .map(j -> new DashboardVO.TodoItem(-j.getId(), j.getId(),
                        j.getCompany() + "「" + j.getTitle() + "」待投递",
                        EventType.DEADLINE.name(),
                        j.getDeadline().format(DateTimeFormatter.ofPattern("MM-dd")),
                        "距截止 " + ChronoUnit.DAYS.between(today, j.getDeadline()) + " 天"))
                .toList();
    }

    private List<String> buildSuggestions(List<Job> jobs, List<JobEvent> todayEvents,
                                          Map<Long, Job> jobMap, LocalDate today,
                                          long matchedJobs, long offerCount) {
        List<String> suggestions = new ArrayList<>();

        jobs.stream()
                .filter(j -> JobStatus.PENDING.name().equals(j.getStatus()))
                .filter(j -> j.getDeadline() != null)
                .filter(j -> !j.getDeadline().isBefore(today) && !j.getDeadline().isAfter(today.plusDays(3)))
                .min(Comparator.comparing(Job::getDeadline))
                .ifPresent(j -> suggestions.add(j.getCompany() + "「" + j.getTitle() + "」将在 "
                        + ChronoUnit.DAYS.between(today, j.getDeadline()) + " 天后截止，建议优先完成投递。"));

        todayEvents.stream()
                .filter(e -> EventType.INTERVIEW.name().equals(e.getEventType()))
                .findFirst()
                .ifPresent(e -> {
                    Job job = jobMap.get(e.getJobId());
                    suggestions.add((job == null ? "" : job.getCompany() + " ")
                            + "今天 " + e.getEventTime().format(TIME_FORMAT)
                            + " 有面试安排，提前准备 2-3 个项目复盘案例。");
                });

        long unmatched = jobs.stream().filter(j -> j.getMatchScore() == null).count();
        if (unmatched > 0) {
            suggestions.add("有 " + unmatched + " 个岗位还没有做 AI 匹配，上传简历后可以快速评估匹配度。");
        }
        if (offerCount > 0) {
            suggestions.add("已收获 " + offerCount + " 个 Offer，记得综合比较城市、薪资与成长性再决定。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("保持每周 5-8 个高质量投递，比集中海投更有效；优先选择匹配度 70 分以上的岗位。");
        }
        return suggestions.stream().limit(4).toList();
    }

    private DashboardVO.CalendarEvent toCalendarEvent(JobEvent event, Job job) {
        return new DashboardVO.CalendarEvent(
                event.getId(),
                event.getJobId(),
                event.getTitle(),
                event.getEventType(),
                event.getEventTime().toLocalDate().toString(),
                event.getEventTime().format(TIME_FORMAT),
                job == null ? "" : job.getCompany());
    }

}
