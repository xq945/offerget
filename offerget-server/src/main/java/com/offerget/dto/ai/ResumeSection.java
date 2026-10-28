package com.offerget.dto.ai;

import java.util.List;

/**
 * 简历段落与改写结果，结构化与纯文本两种简历共用。
 * key 形如 s1 / s2，仅供模型引用；path 是后端回写用的稳定定位，
 * 形如 internships[0] / skills / summary / text:实习经历。
 */
public record ResumeSection(
        String key,
        String path,
        String type,
        String label,
        String original,
        String rewritten,
        String rationale,
        List<String> warnings) {
}
