package com.offerget.dto.interview;

/**
 * 反问结果。canAskMore 表示还能再问一个（最多 2 个）。
 */
public record InterviewReverseVO(
        String reply,
        boolean canAskMore,
        int asked) {
}
