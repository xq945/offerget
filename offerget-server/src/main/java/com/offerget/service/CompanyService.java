package com.offerget.service;

import com.offerget.common.PageQuery;
import com.offerget.common.PageResult;
import com.offerget.dto.company.CompanyVO;
import com.offerget.dto.job.JobVO;

public interface CompanyService {

    PageResult<CompanyVO> page(PageQuery pageQuery, String keyword, String industry, String city);

    JobVO importToJobs(Long id);

}
