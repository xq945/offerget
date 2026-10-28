package com.offerget.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.offerget.dto.resume.AutofillVO;
import com.offerget.dto.resume.ResumeBuilderRequest;
import com.offerget.dto.resume.ResumeUpdateRequest;
import com.offerget.dto.resume.ResumeVO;

public interface ResumeService {

    List<ResumeVO> list();

    ResumeVO detail(Long id);

    ResumeVO upload(MultipartFile file, String name, String versionLabel);

    ResumeVO update(Long id, ResumeUpdateRequest request);

    ResumeVO saveBuilder(Long id, ResumeBuilderRequest request);

    /** 把结构化简历渲染成纯文本，与保存时写入的 content_text 保持一致 */
    String renderText(ResumeBuilderRequest request);

    /** 用纯文本新建一个简历版本，供 AI 定制等场景使用 */
    ResumeVO createTextVersion(String name, String versionLabel, String contentText);

    AutofillVO autofill(Long id);

    void setDefault(Long id);

    void delete(Long id);

}
