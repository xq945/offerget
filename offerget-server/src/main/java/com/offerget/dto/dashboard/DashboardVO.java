package com.offerget.dto.dashboard;

import java.util.List;

public record DashboardVO(
        Stats stats,
        List<TrendPoint> trend,
        List<DeadlineItem> deadlines,
        List<TodoItem> todos,
        List<String> suggestions,
        List<CalendarEvent> upcoming) {

    /** 总览顶部四项统计 */
    public record Stats(
            long totalJobs,
            long appliedCount,
            long interviewingCount,
            long offerCount,
            long weeklyApplied) {
    }

    /** 本周投递趋势（周一到周日） */
    public record TrendPoint(String label, String date, long count) {
    }

    /** 即将截止的岗位 */
    public record DeadlineItem(
            Long jobId,
            String company,
            String title,
            long daysLeft,
            Integer matchScore,
            String status) {
    }

    /** 今日待办 */
    public record TodoItem(
            Long id,
            Long jobId,
            String title,
            String type,
            String time,
            String tag) {
    }

    /** 台历事件 */
    public record CalendarEvent(
            Long id,
            Long jobId,
            String title,
            String type,
            String date,
            String time,
            String company) {
    }

}
