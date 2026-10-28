package com.offerget.service;

import java.util.List;

import com.offerget.dto.ai.MatchRequest;
import com.offerget.dto.ai.MatchResultVO;

public interface AiMatchService {

    MatchResultVO match(MatchRequest request);

    List<MatchResultVO> history(Long jobId);

    MatchResultVO latest(Long jobId);

}
