package com.offerget.service;

import java.util.List;

import com.offerget.dto.interview.AnswerInterviewRequest;
import com.offerget.dto.interview.CreateInterviewRequest;
import com.offerget.dto.interview.InterviewAnswerVO;
import com.offerget.dto.interview.InterviewHistoryVO;
import com.offerget.dto.interview.InterviewReverseVO;
import com.offerget.dto.interview.InterviewSessionVO;
import com.offerget.dto.interview.ReverseInterviewRequest;

/**
 * AI 模拟面试：题单生成、逐题追问、静默评分与报告聚合。
 */
public interface MockInterviewService {

    InterviewSessionVO create(CreateInterviewRequest request);

    InterviewSessionVO detail(Long id);

    List<InterviewHistoryVO> history(Long jobId);

    InterviewAnswerVO answer(Long id, AnswerInterviewRequest request);

    InterviewReverseVO reverse(Long id, ReverseInterviewRequest request);

    InterviewSessionVO finish(Long id);

    void abandon(Long id);

}
