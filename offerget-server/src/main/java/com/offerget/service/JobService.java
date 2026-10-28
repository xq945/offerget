package com.offerget.service;

import com.offerget.common.PageQuery;
import com.offerget.common.PageResult;
import com.offerget.dto.job.JobRequest;
import com.offerget.dto.job.JobVO;

public interface JobService {

    PageResult<JobVO> page(PageQuery pageQuery, String status, String keyword);

    JobVO detail(Long id);

    JobVO create(JobRequest request);

    JobVO update(Long id, JobRequest request);

    void changeStatus(Long id, String status);

    void delete(Long id);

}
