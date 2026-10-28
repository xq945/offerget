package com.offerget.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 简历优化引擎：复用匹配引擎的词典与命中判断，把 JD 中缺失的关键词映射到简历段落，
 * 生成逐段补充建议，并估算补齐后的匹配分。
 */
@Component
@RequiredArgsConstructor
public class ResumeOptimizeEngine {

    private static final List<String> SECTION_TITLES = List.of(
            "教育背景", "教育经历", "实习经历", "工作经历", "项目经历", "校园经历",
            "专业技能", "技能", "自我评价", "个人信息");

    private static final Set<String> TECH_KEYWORDS = Set.of(
            "Java", "Spring Boot", "Spring", "MyBatis", "MySQL", "Redis", "Linux", "Docker", "Git",
            "微服务", "分布式", "消息队列", "Kafka", "RabbitMQ", "JVM", "并发",
            "JavaScript", "TypeScript", "Vue", "React", "HTML", "CSS", "Webpack", "Vite", "Node.js",
            "SQL", "Python", "数据可视化", "Tableau", "Power BI");

    private final AiMatchEngine matchEngine;

    public Report optimize(String jdText, String resumeText) {
        String jd = jdText == null ? "" : jdText;
        String resume = resumeText == null ? "" : resumeText;

        AiMatchEngine.MatchReport report = matchEngine.analyze(jd, resume);
        int scoreBefore = report.score();

        if (!StringUtils.hasText(resume)) {
            return new Report(scoreBefore, scoreBefore, List.of(), List.of(),
                    "简历没有可解析的文本，请先上传文字版简历或使用简历制作器创建一份。");
        }
        if (!StringUtils.hasText(jd)) {
            return new Report(scoreBefore, scoreBefore, List.of(), List.of(),
                    "岗位还没有填写 JD 文本，补全后可以得到更准确的优化建议。");
        }

        List<RawSection> rawSections = splitSections(resume);

        Map<String, List<String>> assignments = new LinkedHashMap<>();
        for (AiMatchEngine.Missing missing : report.missing()) {
            String target = targetTitle(missing.keyword());
            assignments.computeIfAbsent(target, key -> new ArrayList<>()).add(missing.keyword());
        }

        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : assignments.entrySet()) {
            String title = entry.getKey();
            sections.add(new Section(title, contentOf(rawSections, title),
                    suggestionFor(title, entry.getValue()), entry.getValue()));
            if (sections.size() >= 4) {
                break;
            }
        }

        List<String> addedKeywords = report.missing().stream()
                .limit(6)
                .map(AiMatchEngine.Missing::keyword)
                .toList();

        int gain = report.missing().stream()
                .mapToInt(missing -> matchEngine.weightOf(missing.keyword()))
                .sum();
        int boost = Math.min(28, (int) Math.round(gain * 1.5));
        int scoreAfter = Math.min(100, scoreBefore + boost);

        String summary;
        if (report.missing().isEmpty()) {
            summary = "简历已覆盖 JD 中的主要关键词，建议把最相关的项目前置，并补充量化结果。";
        }
        else {
            String top = report.missing().stream().limit(2)
                    .map(AiMatchEngine.Missing::keyword)
                    .collect(Collectors.joining("」「"));
            summary = "补齐「" + top + "」等 " + report.missing().size()
                    + " 个关键词后，预估匹配分可从 " + scoreBefore + " 提升到 " + scoreAfter + "。";
        }

        return new Report(scoreBefore, scoreAfter, addedKeywords, sections, summary);
    }

    /** 按简历常见标题行切分段落，供 AI 定制等场景复用 */
    public List<RawSection> splitSections(String text) {
        List<RawSection> sections = new ArrayList<>();
        String currentTitle = "个人信息";
        StringBuilder buffer = new StringBuilder();

        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            RawSection titleLine = detectTitleLine(trimmed);
            if (titleLine != null) {
                flush(sections, currentTitle, buffer);
                currentTitle = titleLine.title();
                buffer = new StringBuilder(titleLine.content());
                if (!titleLine.content().isEmpty()) {
                    buffer.append('\n');
                }
            }
            else {
                buffer.append(trimmed).append('\n');
            }
        }
        flush(sections, currentTitle, buffer);

        if (sections.isEmpty()) {
            sections.add(new RawSection("简历全文", text));
        }
        return sections;
    }

    private RawSection detectTitleLine(String line) {
        for (String title : SECTION_TITLES) {
            if (line.equals(title) || line.equals(title + "：") || line.equals(title + ":")) {
                return new RawSection(title, "");
            }
            if (line.startsWith(title + "：") || line.startsWith(title + ":")) {
                return new RawSection(title, line.substring(title.length() + 1).trim());
            }
        }
        return null;
    }

    private void flush(List<RawSection> sections, String title, StringBuilder buffer) {
        String content = buffer.toString().trim();
        if (!content.isEmpty()) {
            sections.add(new RawSection(title, content));
        }
    }

    private String contentOf(List<RawSection> sections, String targetTitle) {
        for (RawSection section : sections) {
            if (section.title().contains(targetTitle) || targetTitle.contains(section.title())) {
                String content = section.content();
                return content.length() > 180 ? content.substring(0, 180) + "…" : content;
            }
        }
        return "";
    }

    private String targetTitle(String keyword) {
        if (TECH_KEYWORDS.contains(keyword)) {
            return "技能";
        }
        return "实习经历";
    }

    private String suggestionFor(String title, List<String> keywords) {
        String joined = keywords.stream()
                .map(keyword -> "「" + keyword + "」")
                .collect(Collectors.joining("、"));
        if (title.contains("技能")) {
            return "在技能列表中补充 " + joined + "，并在项目或实习描述中给出对应使用场景与量化结果。";
        }
        if (title.contains("实习") || title.contains("工作")) {
            return "在实习经历中补充 " + joined + " 相关职责，写清动作、方法与结果，尽量用量化指标说明。";
        }
        if (title.contains("项目")) {
            return "在项目经历中补充 " + joined + " 相关的实现或分析，说明你的具体动作与产出。";
        }
        return "在简历中补充 " + joined + " 相关实践，并用结果数据说明你的贡献。";
    }

    public record RawSection(String title, String content) {
    }

    public record Section(String title, String original, String suggestion, List<String> keywords) {
    }

    public record Report(int scoreBefore, int scoreAfter, List<String> addedKeywords,
                         List<Section> sections, String summary) {
    }

}
