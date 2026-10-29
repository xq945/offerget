package com.offerget.dto.interview;

/**
 * 面试进度：主问题完成数与当前题追问层数。
 */
public record InterviewProgressVO(
        int answered,
        int total,
        int followUpCount) {
}
