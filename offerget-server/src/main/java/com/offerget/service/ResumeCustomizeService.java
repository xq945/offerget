package com.offerget.service;

import java.util.List;

import com.offerget.dto.ai.ApplyCustomizeRequest;
import com.offerget.dto.ai.CustomizeRequest;
import com.offerget.dto.ai.CustomizeResultVO;
import com.offerget.dto.resume.ResumeVO;

public interface ResumeCustomizeService {

    CustomizeResultVO customize(CustomizeRequest request);

    List<CustomizeResultVO> history(Long jobId);

    ResumeVO apply(Long id, ApplyCustomizeRequest request);

}
