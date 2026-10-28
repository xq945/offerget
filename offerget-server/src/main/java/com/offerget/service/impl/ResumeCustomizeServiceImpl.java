package com.offerget.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.ai.AiClient;
import com.offerget.common.BusinessException;
import com.offerget.config.DeepSeekProperties;
import com.offerget.dto.ai.ApplyCustomizeRequest;
import com.offerget.dto.ai.CustomizeRequest;
import com.offerget.dto.ai.CustomizeResultVO;
import com.offerget.dto.ai.ResumeSection;
import com.offerget.dto.resume.ResumeBuilderRequest;
import com.offerget.dto.resume.ResumeVO;
import com.offerget.entity.Job;
import com.offerget.entity.Resume;
import com.offerget.entity.ResumeCustomization;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.ResumeCustomizationMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.ResumeCustomizeService;
import com.offerget.service.ResumeService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 简历定制：按 JD 逐段改写，用户确认后生成新版本。
 * 模型输出一律视为不可信输入，落库前经过 key 对齐与事实校验。
 */
@Service
@RequiredArgsConstructor
public class ResumeCustomizeServiceImpl implements ResumeCustomizeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeCustomizeServiceImpl.class);

    private static final String TYPE_BUILDER = "BUILDER";

    private static final String STATUS_DRAFT = "DRAFT";

    private static final String STATUS_APPLIED = "APPLIED";

    private static final String TYPE_INTERNSHIP = "INTERNSHIP";

    private static final String TYPE_PROJECT = "PROJECT";

    private static final String TYPE_SKILLS = "SKILLS";

    private static final String TYPE_SUMMARY = "SUMMARY";

    private static final TypeReference<List<ResumeSection>> SECTION_LIST = new TypeReference<>() {
    };

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String SYSTEM_PROMPT = """
            你是一名资深校招简历顾问，负责根据目标岗位 JD 调整简历表达。

            任务：在【不改变任何事实】的前提下，重组、压缩、调整顺序、更换措辞，让简历更贴合 JD。

            改写要求：
            - 每个段落的第一行（公司/职位/起止时间）必须原样保留，不要删除、不要改写
            - 只改写以「- 」开头的 bullet
            - 每条 bullet 结果前置：先说产出与影响，再说做法
            - 删除「负责」「参与」「协助」等弱表述，能换成具体动作就换
            - 按 JD 相关性重排 bullet，最相关的放第一条
            - 把 JD 中的能力要求与简历中【确实存在】的相关经历对齐表述
            - 措辞具体、克制，不堆形容词

            红线（违反任意一条即失败）：
            1. 不得新增原简历中不存在的技能、工具、项目、公司、学校、时间、数字。
            2. 原有量化数据保留原值，不得放大、缩小或改写百分比。
            3. 不得把“完成/参与/协助”升级为“负责/主导/牵头”，动词强度必须与原文一致；不得把“了解”升级为“精通”。
            4. 不得堆砌 JD 关键词；只有原经历确实相关时才自然融入。
            5. 保留阿拉伯数字写法。
            6. 与 JD 不相关的内容保持原样，不要强行改写。

            输出 JSON：{"sections":[{"key":"与输入一致","rewritten":"多行用\\n分隔","rationale":"30字以内"}],"summary":"80字以内"}
            只输出 JSON，不要输出其他内容。
            """;

    private final ResumeCustomizationMapper customizationMapper;

    private final JobMapper jobMapper;

    private final ResumeMapper resumeMapper;

    private final AiClient aiClient;

    private final AiMatchEngine matchEngine;

    private final ResumeOptimizeEngine optimizeEngine;

    private final FactValidator factValidator;

    private final ResumeService resumeService;

    private final DeepSeekProperties deepSeekProperties;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CustomizeResultVO customize(CustomizeRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Job job = requireJob(request.jobId(), userId);
        Resume resume = requireResume(request.resumeId(), userId);

        String jd = job.getJdText();
        if (!StringUtils.hasText(jd)) {
            throw BusinessException.badRequest("岗位还没有填写 JD 文本，补全后即可定制简历");
        }
        String resumeText = resume.getContentText();
        if (!StringUtils.hasText(resumeText)) {
            throw BusinessException.badRequest("简历没有可解析的文本，请上传文字版简历或使用简历制作器");
        }

        boolean builder = StringUtils.hasText(resume.getContentJson());
        ResumeBuilderRequest builderRequest = builder ? readBuilder(resume) : null;
        List<ResumeSection> drafts = builder ? draftsFromBuilder(builderRequest) : draftsFromText(resumeText);
        if (drafts.isEmpty()) {
            throw BusinessException.badRequest("简历里没有可改写的实习、项目、技能或自我评价");
        }

        String raw = aiClient.chatJson(SYSTEM_PROMPT, buildUserPrompt(job, jd, drafts));
        ModelOutput output = readModelOutput(raw, drafts);

        List<ResumeSection> sections = new ArrayList<>();
        for (ResumeSection draft : drafts) {
            ModelSection model = output.byKey().get(draft.key());
            String rewritten = model == null || !StringUtils.hasText(model.rewritten())
                    ? draft.original() : model.rewritten();
            rewritten = withOriginalHeader(draft.type(), draft.original(), rewritten);
            String rationale = model == null ? "模型未改写该段，保持原文" : model.rationale();
            sections.add(new ResumeSection(draft.key(), draft.path(), draft.type(), draft.label(),
                    draft.original(), rewritten, rationale,
                    factValidator.validate(draft.original(), rewritten)));
        }

        String customizedText = builder
                ? textAfterBuilder(builderRequest, sections)
                : textAfterPlain(resumeText, sections);

        int scoreBefore = matchEngine.analyze(jd, resumeText).score();
        int scoreAfter = matchEngine.analyze(jd, customizedText).score();

        ResumeCustomization entity = new ResumeCustomization();
        entity.setUserId(userId);
        entity.setJobId(job.getId());
        entity.setSourceResumeId(resume.getId());
        entity.setResumeType(builder ? TYPE_BUILDER : "TEXT");
        entity.setModel(deepSeekProperties.model());
        entity.setSectionsJson(objectMapper.writeValueAsString(sections));
        entity.setWarningsJson(objectMapper.writeValueAsString(collectWarnings(sections)));
        entity.setScoreBefore(scoreBefore);
        entity.setScoreAfter(scoreAfter);
        entity.setSummary(output.summary());
        entity.setStatus(STATUS_DRAFT);
        customizationMapper.insert(entity);

        return toVO(entity, job, resume, sections);
    }

    @Override
    public List<CustomizeResultVO> history(Long jobId) {
        Long userId = SecurityUtils.currentUserId();
        List<ResumeCustomization> records = customizationMapper.selectList(
                new LambdaQueryWrapper<ResumeCustomization>()
                        .eq(ResumeCustomization::getUserId, userId)
                        .eq(jobId != null, ResumeCustomization::getJobId, jobId)
                        .orderByDesc(ResumeCustomization::getCreatedAt)
                        .last("limit 20"));
        if (records.isEmpty()) {
            return List.of();
        }

        Set<Long> jobIds = records.stream().map(ResumeCustomization::getJobId).collect(Collectors.toSet());
        Set<Long> resumeIds = records.stream().map(ResumeCustomization::getSourceResumeId)
                .collect(Collectors.toSet());
        Map<Long, Job> jobMap = jobMapper.selectBatchIds(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, Resume> resumeMap = resumeMapper.selectBatchIds(resumeIds).stream()
                .collect(Collectors.toMap(Resume::getId, Function.identity()));

        return records.stream()
                .map(record -> toVO(record, jobMap.get(record.getJobId()),
                        resumeMap.get(record.getSourceResumeId()), readSections(record.getSectionsJson())))
                .toList();
    }

    @Override
    @Transactional
    public ResumeVO apply(Long id, ApplyCustomizeRequest request) {
        Long userId = SecurityUtils.currentUserId();
        ResumeCustomization record = customizationMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw BusinessException.notFound("定制记录不存在");
        }
        if (!STATUS_DRAFT.equals(record.getStatus())) {
            throw BusinessException.conflict("该定制记录已应用或已作废");
        }

        Job job = requireJob(record.getJobId(), userId);
        Resume source = requireResume(record.getSourceResumeId(), userId);
        List<ResumeSection> sections = readSections(record.getSectionsJson());

        Map<String, String> accepted = new LinkedHashMap<>();
        for (ApplyCustomizeRequest.AppliedSection applied : request.sections()) {
            if (StringUtils.hasText(applied.rewritten())) {
                accepted.put(applied.key(), applied.rewritten().trim());
            }
        }
        if (accepted.isEmpty()) {
            throw BusinessException.badRequest("没有选择任何要应用的改写段落");
        }

        String versionLabel = versionLabelOf(job);
        ResumeVO target;
        if (TYPE_BUILDER.equals(record.getResumeType())) {
            ResumeBuilderRequest original = readBuilder(source);
            ResumeBuilderRequest merged = mergeBuilder(original, accepted, sections);
            ResumeBuilderRequest renamed = new ResumeBuilderRequest(merged.name(), versionLabel,
                    merged.basic(), merged.educations(), merged.internships(), merged.projects(),
                    merged.skills(), merged.summary());
            target = resumeService.saveBuilder(null, renamed);
        }
        else {
            String text = applyToPlain(source.getContentText(), sections, accepted);
            target = resumeService.createTextVersion(source.getName() + "（定制）", versionLabel, text);
        }

        record.setTargetResumeId(target.id());
        record.setStatus(STATUS_APPLIED);
        record.setAppliedAt(LocalDateTime.now());
        customizationMapper.updateById(record);
        return target;
    }

    // ---------------------------------------------------------------- 段落提取

    private List<ResumeSection> draftsFromBuilder(ResumeBuilderRequest request) {
        List<ResumeSection> drafts = new ArrayList<>();
        int seq = 0;
        if (request.internships() != null) {
            for (int i = 0; i < request.internships().size(); i++) {
                ResumeBuilderRequest.ExperienceItem item = request.internships().get(i);
                if (item == null) {
                    continue;
                }
                String original = experienceText(item);
                if (!StringUtils.hasText(original)) {
                    continue;
                }
                drafts.add(new ResumeSection("s" + (++seq), "internships[" + i + "]", TYPE_INTERNSHIP,
                        "实习经历 · " + experienceLabel(item), original, null, null, List.of()));
            }
        }
        if (request.projects() != null) {
            for (int i = 0; i < request.projects().size(); i++) {
                ResumeBuilderRequest.ExperienceItem item = request.projects().get(i);
                if (item == null) {
                    continue;
                }
                String original = experienceText(item);
                if (!StringUtils.hasText(original)) {
                    continue;
                }
                drafts.add(new ResumeSection("s" + (++seq), "projects[" + i + "]", TYPE_PROJECT,
                        "项目经历 · " + experienceLabel(item), original, null, null, List.of()));
            }
        }
        if (request.skills() != null) {
            String skills = request.skills().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.joining("、"));
            if (!skills.isEmpty()) {
                drafts.add(new ResumeSection("s" + (++seq), "skills", TYPE_SKILLS, "技能",
                        skills, null, null, List.of()));
            }
        }
        if (StringUtils.hasText(request.summary())) {
            drafts.add(new ResumeSection("s" + (++seq), "summary", TYPE_SUMMARY, "自我评价",
                    request.summary().trim(), null, null, List.of()));
        }
        return drafts;
    }

    private List<ResumeSection> draftsFromText(String text) {
        List<ResumeSection> drafts = new ArrayList<>();
        int seq = 0;
        for (ResumeOptimizeEngine.RawSection raw : optimizeEngine.splitSections(text)) {
            if (!StringUtils.hasText(raw.content())) {
                continue;
            }
            String type = plainType(raw.title());
            if (type == null) {
                continue;
            }
            drafts.add(new ResumeSection("s" + (++seq), "text:" + raw.title(), type,
                    raw.title(), raw.content().trim(), null, null, List.of()));
        }
        return drafts;
    }

    private String plainType(String title) {
        if (title.contains("实习") || title.contains("工作")) {
            return TYPE_INTERNSHIP;
        }
        if (title.contains("项目")) {
            return TYPE_PROJECT;
        }
        if (title.contains("技能")) {
            return TYPE_SKILLS;
        }
        if (title.contains("自我评价")) {
            return TYPE_SUMMARY;
        }
        return null;
    }

    private String experienceText(ResumeBuilderRequest.ExperienceItem item) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.organization())) {
            parts.add(item.organization().trim());
        }
        if (StringUtils.hasText(item.title())) {
            parts.add(item.title().trim());
        }
        String period = periodOf(item.startDate(), item.endDate());
        if (StringUtils.hasText(period)) {
            parts.add(period);
        }
        StringBuilder sb = new StringBuilder(String.join(" | ", parts));
        if (item.bullets() != null) {
            for (String bullet : item.bullets()) {
                if (StringUtils.hasText(bullet)) {
                    sb.append('\n').append("- ").append(bullet.trim());
                }
            }
        }
        return sb.toString();
    }

    private String experienceLabel(ResumeBuilderRequest.ExperienceItem item) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.organization())) {
            parts.add(item.organization().trim());
        }
        if (StringUtils.hasText(item.title())) {
            parts.add(item.title().trim());
        }
        return parts.isEmpty() ? "未命名经历" : String.join(" ", parts);
    }

    private String periodOf(String start, String end) {
        boolean hasStart = StringUtils.hasText(start);
        boolean hasEnd = StringUtils.hasText(end);
        if (hasStart && hasEnd) {
            return start.trim() + "-" + end.trim();
        }
        if (hasStart) {
            return start.trim();
        }
        return hasEnd ? end.trim() : "";
    }

    // ---------------------------------------------------------------- Prompt 与解析

    private String buildUserPrompt(Job job, String jd, List<ResumeSection> drafts) {
        StringBuilder sb = new StringBuilder();
        sb.append("【目标岗位】\n")
                .append(job.getCompany()).append(" · ").append(job.getTitle()).append("\n\n");
        sb.append("【JD】\n").append(jd.trim()).append("\n\n");
        sb.append("【简历段落】\n");
        for (ResumeSection draft : drafts) {
            sb.append("\nkey: ").append(draft.key()).append('\n')
                    .append("label: ").append(draft.label()).append('\n')
                    .append("original:\n").append(draft.original()).append('\n');
        }
        sb.append("\n请按 JSON 输出全部段落的改写结果，key 必须原样返回。");
        return sb.toString();
    }

    private ModelOutput readModelOutput(String raw, List<ResumeSection> drafts) {
        try {
            Map<String, Object> root = objectMapper.readValue(raw, MAP_TYPE);
            List<ModelSection> returned = new ArrayList<>();
            if (root.get("sections") instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> map) {
                        returned.add(new ModelSection(normalizeKey(map.get("key")),
                                textOf(map.get("rewritten")), textOf(map.get("rationale"))));
                    }
                }
            }

            Map<String, ModelSection> byKey = new LinkedHashMap<>();
            for (ModelSection section : returned) {
                if (section.key() != null) {
                    byKey.putIfAbsent(section.key(), section);
                }
            }
            // 模型偶尔会改坏 key：数量一致时按顺序兜底
            if (returned.size() == drafts.size()) {
                for (int i = 0; i < drafts.size(); i++) {
                    String expected = drafts.get(i).key();
                    if (!byKey.containsKey(expected)) {
                        ModelSection candidate = returned.get(i);
                        if (candidate.key() == null || !byKey.containsKey(candidate.key())) {
                            byKey.put(expected, candidate);
                        }
                    }
                }
            }
            return new ModelOutput(byKey, textOf(root.get("summary")));
        }
        catch (Exception ex) {
            log.warn("AI 返回的改写结果解析失败：{}", ex.getMessage());
            throw new BusinessException(50200, "AI 返回格式异常，请重试", HttpStatus.BAD_GATEWAY);
        }
    }

    private String normalizeKey(Object value) {
        if (value == null) {
            return null;
        }
        String key = value.toString().trim().replace("\"", "");
        return key.isEmpty() ? null : key;
    }

    private String textOf(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    // ---------------------------------------------------------------- 全文与回写

    private String textAfterBuilder(ResumeBuilderRequest request, List<ResumeSection> sections) {
        Map<String, String> accepted = new LinkedHashMap<>();
        for (ResumeSection section : sections) {
            accepted.put(section.key(), section.rewritten());
        }
        return resumeService.renderText(mergeBuilder(request, accepted, sections));
    }

    private String textAfterPlain(String text, List<ResumeSection> sections) {
        Map<String, String> accepted = new LinkedHashMap<>();
        for (ResumeSection section : sections) {
            accepted.put(section.key(), section.rewritten());
        }
        return applyToPlain(text, sections, accepted);
    }

    private ResumeBuilderRequest mergeBuilder(ResumeBuilderRequest request,
                                              Map<String, String> accepted,
                                              List<ResumeSection> sections) {
        List<ResumeBuilderRequest.ExperienceItem> internships = request.internships() == null
                ? new ArrayList<>() : new ArrayList<>(request.internships());
        List<ResumeBuilderRequest.ExperienceItem> projects = request.projects() == null
                ? new ArrayList<>() : new ArrayList<>(request.projects());
        List<String> skills = request.skills() == null ? new ArrayList<>() : new ArrayList<>(request.skills());
        String summary = request.summary();

        for (ResumeSection section : sections) {
            String text = accepted.get(section.key());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            switch (section.type()) {
                case TYPE_INTERNSHIP -> {
                    int index = indexOfPath(section.path());
                    if (index >= 0 && index < internships.size()) {
                        internships.set(index, withBullets(internships.get(index), text));
                    }
                }
                case TYPE_PROJECT -> {
                    int index = indexOfPath(section.path());
                    if (index >= 0 && index < projects.size()) {
                        projects.set(index, withBullets(projects.get(index), text));
                    }
                }
                case TYPE_SKILLS -> skills = splitSkills(text);
                case TYPE_SUMMARY -> summary = text;
                default -> {
                }
            }
        }
        return new ResumeBuilderRequest(request.name(), request.versionLabel(), request.basic(),
                request.educations(), internships, projects, skills, summary);
    }

    private int indexOfPath(String path) {
        int open = path == null ? -1 : path.indexOf('[');
        int close = path == null ? -1 : path.indexOf(']');
        if (open < 0 || close <= open) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(open + 1, close).trim());
        }
        catch (NumberFormatException ex) {
            return -1;
        }
    }

    private ResumeBuilderRequest.ExperienceItem withBullets(ResumeBuilderRequest.ExperienceItem item,
                                                            String text) {
        return new ResumeBuilderRequest.ExperienceItem(item.organization(), item.title(),
                item.startDate(), item.endDate(), bulletsOf(text));
    }

    private List<String> bulletsOf(String text) {
        List<String> bullets = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                bullets.add(trimmed.substring(2).trim());
            }
        }
        if (!bullets.isEmpty()) {
            return bullets;
        }
        // 模型没用列表符号时，跳过首行标题，其余按条收录
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.contains(" | ")) {
                bullets.add(trimmed);
            }
        }
        return bullets;
    }

    private List<String> splitSkills(String text) {
        return Arrays.stream(text.split("[、,，;；\\n]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String applyToPlain(String text, List<ResumeSection> sections, Map<String, String> accepted) {
        List<Replacement> replacements = new ArrayList<>();
        for (ResumeSection section : sections) {
            String rewritten = accepted.get(section.key());
            if (!StringUtils.hasText(rewritten) || rewritten.equals(section.original())) {
                continue;
            }
            int start = text.indexOf(section.original());
            if (start < 0) {
                continue;
            }
            replacements.add(new Replacement(start, start + section.original().length(),
                    withOriginalHeader(section.type(), section.original(), rewritten)));
        }
        // 从后往前替换，避免前面的偏移失效
        replacements.sort((a, b) -> Integer.compare(b.start(), a.start()));
        StringBuilder result = new StringBuilder(text);
        for (Replacement replacement : replacements) {
            result.replace(replacement.start(), replacement.end(), replacement.value());
        }
        return result.toString();
    }

    /**
     * 模型有时会丢掉经历首行（公司/职位/时间）。回写时强制沿用原文首行，
     * 避免改写之后简历里连公司名都没有。
     */
    private String withOriginalHeader(String type, String original, String rewritten) {
        if (!TYPE_INTERNSHIP.equals(type) && !TYPE_PROJECT.equals(type)) {
            return rewritten;
        }
        String header = firstLine(original);
        if (header.isEmpty() || header.startsWith("- ") || rewritten.startsWith(header)) {
            return rewritten;
        }
        return header + "\n" + rewritten;
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int index = text.indexOf('\n');
        return (index < 0 ? text : text.substring(0, index)).trim();
    }

    // ---------------------------------------------------------------- 通用

    private Job requireJob(Long jobId, Long userId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            throw BusinessException.notFound("岗位不存在");
        }
        return job;
    }

    private Resume requireResume(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.notFound("简历不存在");
        }
        return resume;
    }

    private ResumeBuilderRequest readBuilder(Resume resume) {
        try {
            return objectMapper.readValue(resume.getContentJson(), ResumeBuilderRequest.class);
        }
        catch (Exception ex) {
            throw BusinessException.badRequest("结构化简历解析失败，请重新保存简历制作器内容");
        }
    }

    private List<ResumeSection> readSections(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SECTION_LIST);
        }
        catch (Exception ex) {
            log.warn("定制记录解析失败：{}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> readWarnings(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        }
        catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> collectWarnings(List<ResumeSection> sections) {
        List<String> warnings = new ArrayList<>();
        for (ResumeSection section : sections) {
            if (section.warnings() == null) {
                continue;
            }
            for (String warning : section.warnings()) {
                warnings.add(section.label() + "：" + warning);
            }
        }
        return warnings;
    }

    private String versionLabelOf(Job job) {
        String label = job.getCompany() + "-" + job.getTitle() + "-定制";
        return label.length() > 30 ? label.substring(0, 30) : label;
    }

    private CustomizeResultVO toVO(ResumeCustomization entity, Job job, Resume resume,
                                   List<ResumeSection> sections) {
        return new CustomizeResultVO(
                entity.getId(),
                entity.getJobId(),
                job == null ? "" : job.getTitle(),
                job == null ? "" : job.getCompany(),
                entity.getSourceResumeId(),
                resume == null ? "" : resume.getName(),
                entity.getResumeType(),
                entity.getStatus(),
                entity.getScoreBefore() == null ? 0 : entity.getScoreBefore(),
                entity.getScoreAfter() == null ? 0 : entity.getScoreAfter(),
                sections,
                readWarnings(entity.getWarningsJson()),
                entity.getSummary(),
                entity.getCreatedAt());
    }

    private record ModelSection(String key, String rewritten, String rationale) {
    }

    private record ModelOutput(Map<String, ModelSection> byKey, String summary) {
    }

    private record Replacement(int start, int end, String value) {
    }

}
