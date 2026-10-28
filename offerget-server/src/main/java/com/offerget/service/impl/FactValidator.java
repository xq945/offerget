package com.offerget.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 事实校验：改写稿中出现原文没有的技能、数字、年份视为可疑，输出告警供用户判断。
 * 只做提示，不阻断生成。
 */
@Component
@RequiredArgsConstructor
public class FactValidator {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?%?");

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:19|20)\\d{2}");

    /** 强主动表述：原文没有却出现在改写稿里，通常意味着夸大 */
    private static final List<String> STRONG_VERBS = List.of(
            "主导", "牵头", "统筹", "操盘", "从0到1", "从零到一");

    private final AiMatchEngine matchEngine;

    public List<String> validate(String original, String rewritten) {
        if (rewritten == null || rewritten.isBlank()) {
            return List.of();
        }
        String source = original == null ? "" : original;
        List<String> warnings = new ArrayList<>();

        for (String keyword : matchEngine.allKeywords()) {
            if (matchEngine.containsKeyword(rewritten, keyword)
                    && !matchEngine.containsKeyword(source, keyword)) {
                warnings.add("新增「" + keyword + "」，原文未出现");
            }
        }

        Set<String> sourceNumbers = extract(NUMBER_PATTERN, source);
        for (String number : extract(NUMBER_PATTERN, rewritten)) {
            if (!sourceNumbers.contains(number)) {
                warnings.add("新增数字「" + number + "」，原文未出现");
            }
        }

        Set<String> sourceYears = extract(YEAR_PATTERN, source);
        for (String year : extract(YEAR_PATTERN, rewritten)) {
            if (!sourceYears.contains(year)) {
                warnings.add("新增年份「" + year + "」，原文未出现");
            }
        }

        for (String verb : STRONG_VERBS) {
            if (rewritten.contains(verb) && !source.contains(verb)) {
                warnings.add("新增「" + verb + "」，原文是较弱表述，请确认是否夸大");
            }
        }

        return warnings;
    }

    private Set<String> extract(Pattern pattern, String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

}
