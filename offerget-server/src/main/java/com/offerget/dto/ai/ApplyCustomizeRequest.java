package com.offerget.dto.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 只提交用户接受的段落，未提交的段落保持原文。
 */
public record ApplyCustomizeRequest(
        @NotEmpty(message = "没有可应用的段落")
        @Valid
        List<AppliedSection> sections) {

    public record AppliedSection(
            @NotBlank(message = "段落标识不能为空") String key,
            @NotBlank(message = "改写内容不能为空") String rewritten) {
    }

}
