package com.offerget.dto.interview;

/**
 * 一次作答后的推进结果。
 * action：FOLLOW_UP（追问）/ NEXT（进入下一题）/ CLOSE（进入反问或结束）。
 */
public record InterviewAnswerVO(
        String action,
        String reply,
        String followUp,
        InterviewQuestionVO nextQuestion,
        InterviewProgressVO progress) {
}
