package com.offerget.service;

import java.util.List;

import com.offerget.dto.ai.OptimizeRequest;
import com.offerget.dto.ai.OptimizeResultVO;

public interface ResumeOptimizeService {

    OptimizeResultVO optimize(OptimizeRequest request);

    List<OptimizeResultVO> history(Long jobId);

}
