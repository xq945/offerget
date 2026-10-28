package com.offerget.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 本地规则匹配引擎：技能词典 + 加权命中率，不依赖外部大模型。
 * 后续接入大模型时，保持 MatchReport 结构不变即可替换实现。
 */
@Component
public class AiMatchEngine {

    private static final Map<String, Integer> SKILL_DICTIONARY = new LinkedHashMap<>();

    static {
        // 后端
        put("Java", 3);
        put("Spring Boot", 3);
        put("Spring", 2);
        put("MyBatis", 2);
        put("MySQL", 3);
        put("Redis", 3);
        put("Linux", 2);
        put("Docker", 2);
        put("Git", 1);
        put("微服务", 2);
        put("分布式", 2);
        put("消息队列", 2);
        put("Kafka", 2);
        put("RabbitMQ", 2);
        put("JVM", 2);
        put("并发", 2);
        // 前端
        put("JavaScript", 3);
        put("TypeScript", 2);
        put("Vue", 3);
        put("React", 3);
        put("HTML", 1);
        put("CSS", 1);
        put("Webpack", 1);
        put("Vite", 1);
        put("Node.js", 2);
        // 数据
        put("SQL", 3);
        put("Python", 3);
        put("Excel", 1);
        put("数据分析", 3);
        put("数据可视化", 2);
        put("统计学", 2);
        put("AB 实验", 3);
        put("埋点", 2);
        put("Tableau", 1);
        put("Power BI", 1);
        // 产品 / 运营
        put("用户调研", 3);
        put("需求分析", 3);
        put("PRD", 3);
        put("竞品分析", 2);
        put("Axure", 2);
        put("Figma", 2);
        put("原型设计", 2);
        put("用户增长", 3);
        put("活动运营", 2);
        put("内容运营", 2);
        put("社群运营", 2);
        put("项目管理", 2);
        put("跨部门协作", 2);
        put("沟通表达", 1);
        // 通用
        put("实习", 2);
        put("项目经历", 2);
        put("团队协作", 1);
        put("英语", 1);
        put("CET-6", 1);
        put("竞赛", 1);
        put("开源", 1);
    }

    private static void put(String keyword, int weight) {
        SKILL_DICTIONARY.put(keyword, weight);
    }

    public MatchReport analyze(String jdText, String resumeText) {
        String jd = jdText == null ? "" : jdText;
        String resume = resumeText == null ? "" : resumeText;

        if (!StringUtils.hasText(jd)) {
            return new MatchReport(50, List.of(), List.of(),
                    List.of("岗位还没有填写 JD 文本，补全后可以得到更准确的关键词匹配结果"),
                    "缺少 JD 文本，当前为默认匹配结果。");
        }
        if (!StringUtils.hasText(resume)) {
            return new MatchReport(35, List.of(),
                    extractJdKeywords(jd).stream().map(k -> new Missing(k.label, missingSuggestion(k.label))).toList(),
                    List.of("当前简历没有可解析的文本，请上传 PDF / DOCX 格式的简历或补充简历内容"),
                    "简历缺少可解析文本，暂时无法评估匹配度。");
        }

        List<Keyword> jdKeywords = extractJdKeywords(jd);
        List<String> matched = new ArrayList<>();
        List<Missing> missing = new ArrayList<>();
        int totalWeight = 0;
        int hitWeight = 0;

        for (Keyword keyword : jdKeywords) {
            totalWeight += keyword.weight;
            if (containsKeyword(resume, keyword.label)) {
                matched.add(keyword.label);
                hitWeight += keyword.weight;
            }
            else {
                missing.add(new Missing(keyword.label, missingSuggestion(keyword.label)));
            }
        }

        // 缺失关键词按权重从高到低，最多展示 8 个
        missing.sort((a, b) -> Integer.compare(
                weightOf(b.keyword), weightOf(a.keyword)));
        if (missing.size() > 8) {
            missing = new ArrayList<>(missing.subList(0, 8));
        }

        double ratio = totalWeight == 0 ? 0.5 : (double) hitWeight / totalWeight;
        int score = 25 + (int) Math.round(55 * ratio);
        String normalizedResume = resume.toLowerCase(Locale.ROOT);
        if (normalizedResume.contains("实习")) {
            score += 8;
        }
        if (normalizedResume.contains("项目")) {
            score += 5;
        }
        if (normalizedResume.contains("竞赛") || normalizedResume.contains("开源")) {
            score += 2;
        }
        score = Math.min(score, 100);

        return new MatchReport(score, matched, missing,
                buildSuggestions(score, missing), buildSummary(score));
    }

    private List<Keyword> extractJdKeywords(String jd) {
        List<Keyword> keywords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : SKILL_DICTIONARY.entrySet()) {
            if (containsKeyword(jd, entry.getKey())) {
                keywords.add(new Keyword(entry.getKey(), entry.getValue()));
            }
        }
        return keywords;
    }

    /** 供简历优化、自动填充等场景复用 */
    public boolean containsKeyword(String text, String keyword) {
        if (keyword.matches(".*[\\u4e00-\\u9fa5].*")) {
            return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
        }
        // 英文关键词按单词边界匹配，避免 Java 命中 JavaScript
        Pattern pattern = Pattern.compile(
                "(?i)(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])");
        return pattern.matcher(text).find();
    }

    public int weightOf(String keyword) {
        return SKILL_DICTIONARY.getOrDefault(keyword, 1);
    }

    /** 技能词典中的全部关键词，供自动填充识别技能 */
    public List<String> allKeywords() {
        return List.copyOf(SKILL_DICTIONARY.keySet());
    }

    private String missingSuggestion(String keyword) {
        return "在简历中补充「" + keyword + "」相关的项目或实习细节，并用量化结果说明你的贡献。";
    }

    private List<String> buildSuggestions(int score, List<Missing> missing) {
        Set<String> suggestions = new LinkedHashSet<>();
        if (!missing.isEmpty()) {
            String top = missing.stream().limit(2)
                    .map(Missing::keyword)
                    .reduce((a, b) -> a + "」「" + b)
                    .orElse("");
            suggestions.add("优先补齐「" + top + "」：把相关实践写进项目经历，并补充可量化的结果。");
            suggestions.add("将 JD 中反复出现的能力要求，对应到简历里最接近的一段经历，放在更靠前的位置。");
        }
        if (score >= 80) {
            suggestions.add("匹配度较高，建议尽快投递，并围绕简历中的核心项目准备 2-3 个面试案例。");
        }
        else if (score < 60) {
            suggestions.add("当前匹配度偏低，建议先确认岗位方向是否与目标一致，再决定是否投递。");
        }
        else {
            suggestions.add("补充 1-2 项目标岗位相关的实践后，再投递会更有竞争力。");
        }
        suggestions.add("删减与岗位无关的信息，让最匹配的经历在前 10 秒内被看到。");
        return new ArrayList<>(suggestions);
    }

    private String buildSummary(int score) {
        if (score >= 85) {
            return "简历与岗位要求高度匹配，建议优先投递。";
        }
        if (score >= 70) {
            return "整体匹配度不错，补齐少量关键词后竞争力更强。";
        }
        if (score >= 55) {
            return "具备基础匹配，需要补充核心技能或相关经历。";
        }
        return "匹配度偏低，建议评估岗位方向或大幅调整简历表达。";
    }

    private record Keyword(String label, int weight) {
    }

    public record Missing(String keyword, String suggestion) {
    }

    public record MatchReport(int score, List<String> matched, List<Missing> missing,
                              List<String> suggestions, String summary) {
    }

}
