package com.offerget.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offerget.common.BusinessException;
import com.offerget.config.WebMvcConfig;
import com.offerget.dto.resume.AutofillVO;
import com.offerget.dto.resume.ResumeBuilderRequest;
import com.offerget.dto.resume.ResumeUpdateRequest;
import com.offerget.dto.resume.ResumeVO;
import com.offerget.entity.Job;
import com.offerget.entity.Resume;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.ResumeService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");

    private static final int PREVIEW_LENGTH = 200;

    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d{9})");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)");

    private static final Pattern SCHOOL_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{1,15}(?:大学|学院))");

    private static final Pattern MAJOR_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,12})专业");

    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})\\s*届");

    private static final Pattern PLAIN_YEAR_PATTERN = Pattern.compile("(20\\d{2})");

    private static final List<String> CITIES = List.of(
            "北京", "上海", "深圳", "杭州", "广州", "南京", "成都", "武汉",
            "西安", "合肥", "苏州", "天津", "重庆", "长沙", "厦门");

    private static final List<String> STOP_TITLES = List.of(
            "教育背景", "教育经历", "实习经历", "工作经历", "项目经历", "项目经验",
            "技能", "专业技能", "自我评价", "个人信息", "校园经历", "获奖经历", "获奖");

    /** 词典中属于"经历类"而非"技能"的词，不放进自动填充的技能列表 */
    private static final Set<String> NON_SKILL_KEYWORDS = Set.of("实习", "项目经历", "团队协作");

    private final ResumeMapper resumeMapper;

    private final JobMapper jobMapper;

    private final WebMvcConfig webMvcConfig;

    private final ResumeTextExtractor textExtractor;

    private final AiMatchEngine matchEngine;

    private final ObjectMapper objectMapper;

    @Override
    public List<ResumeVO> list() {
        Long userId = SecurityUtils.currentUserId();
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getIsDefault)
                        .orderByDesc(Resume::getCreatedAt))
                .stream()
                .map(resume -> toVO(resume, false))
                .toList();
    }

    @Override
    public ResumeVO detail(Long id) {
        return toVO(requireOwnedResume(id), true);
    }

    @Override
    @Transactional
    public ResumeVO upload(MultipartFile file, String name, String versionLabel) {
        Long userId = SecurityUtils.currentUserId();
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的简历文件");
        }

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("仅支持 PDF、DOC、DOCX、TXT 格式");
        }

        String contentText = extractQuietly(file);

        Path root = webMvcConfig.getRoot();
        String relativePath = "resumes/" + userId + "/" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        }
        catch (IOException ex) {
            log.error("保存简历文件失败", ex);
            throw new BusinessException(50000, "简历文件保存失败，请重试",
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean firstResume = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)) == 0;

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(StringUtils.hasText(name) ? name.trim() : stripExtension(originalName));
        resume.setVersionLabel(StringUtils.hasText(versionLabel) ? versionLabel.trim() : "自定义");
        resume.setFileName(originalName);
        resume.setFilePath(relativePath);
        resume.setFileSize(file.getSize());
        resume.setContentText(contentText);
        resume.setIsDefault(firstResume);
        resumeMapper.insert(resume);

        return toVO(resume, true);
    }

    @Override
    public ResumeVO update(Long id, ResumeUpdateRequest request) {
        Resume resume = requireOwnedResume(id);
        resume.setName(request.name().trim());
        resume.setVersionLabel(StringUtils.hasText(request.versionLabel())
                ? request.versionLabel().trim() : null);
        resumeMapper.updateById(resume);
        return toVO(resume, true);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Resume resume = requireOwnedResume(id);
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getUserId, resume.getUserId())
                .set(Resume::getIsDefault, false));
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getIsDefault, true));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Resume resume = requireOwnedResume(id);
        resumeMapper.deleteById(id);

        // 解绑引用该版本的岗位
        jobMapper.update(null, new LambdaUpdateWrapper<Job>()
                .eq(Job::getUserId, resume.getUserId())
                .eq(Job::getResumeId, id)
                .set(Job::getResumeId, null));

        // 删除文件（演示种子数据没有实体文件）
        if (StringUtils.hasText(resume.getFilePath())) {
            Path target = webMvcConfig.getRoot().resolve(resume.getFilePath()).normalize();
            if (target.startsWith(webMvcConfig.getRoot())) {
                try {
                    Files.deleteIfExists(target);
                }
                catch (IOException ex) {
                    log.warn("删除简历文件失败：{}", target, ex);
                }
            }
        }

        // 删掉的是默认版本时，把最近一份设置为默认
        if (Boolean.TRUE.equals(resume.getIsDefault())) {
            Resume latest = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getUserId, resume.getUserId())
                    .orderByDesc(Resume::getCreatedAt)
                    .last("limit 1"));
            if (latest != null) {
                resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                        .eq(Resume::getId, latest.getId())
                        .set(Resume::getIsDefault, true));
            }
        }
    }

    @Override
    @Transactional
    public ResumeVO saveBuilder(Long id, ResumeBuilderRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String contentText = buildContentText(request);
        String contentJson = objectMapper.writeValueAsString(request);

        Resume resume;
        if (id == null) {
            boolean firstResume = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getUserId, userId)) == 0;
            resume = new Resume();
            resume.setUserId(userId);
            resume.setName(request.name().trim());
            resume.setVersionLabel(StringUtils.hasText(request.versionLabel())
                    ? request.versionLabel().trim() : "制作器版本");
            resume.setFileName("在线简历");
            resume.setFileSize(null);
            resume.setContentText(contentText);
            resume.setContentJson(contentJson);
            resume.setIsDefault(firstResume);
            resumeMapper.insert(resume);
        }
        else {
            resume = requireOwnedResume(id);
            resume.setName(request.name().trim());
            resume.setVersionLabel(StringUtils.hasText(request.versionLabel())
                    ? request.versionLabel().trim() : resume.getVersionLabel());
            resume.setContentText(contentText);
            resume.setContentJson(contentJson);
            resumeMapper.updateById(resume);
        }
        return toVO(resume, true);
    }

    @Override
    public String renderText(ResumeBuilderRequest request) {
        return buildContentText(request);
    }

    @Override
    @Transactional
    public ResumeVO createTextVersion(String name, String versionLabel, String contentText) {
        Long userId = SecurityUtils.currentUserId();
        boolean firstResume = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)) == 0;
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(StringUtils.hasText(name) ? limit(name.trim(), 100) : "定制简历");
        resume.setVersionLabel(StringUtils.hasText(versionLabel)
                ? limit(versionLabel.trim(), 30) : "AI 定制版本");
        resume.setFileName(null);
        resume.setFileSize(null);
        resume.setContentText(contentText);
        resume.setContentJson(null);
        resume.setIsDefault(firstResume);
        resumeMapper.insert(resume);
        return toVO(resume, true);
    }

    private String limit(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    @Override
    public AutofillVO autofill(Long id) {
        Resume resume = requireOwnedResume(id);
        if (StringUtils.hasText(resume.getContentJson())) {
            try {
                ResumeBuilderRequest request = objectMapper.readValue(
                        resume.getContentJson(), ResumeBuilderRequest.class);
                return fromBuilder(resume, request);
            }
            catch (Exception ex) {
                log.warn("结构化简历解析失败，回退到文本提取：{}", ex.getMessage());
            }
        }
        return fromText(resume);
    }

    private String buildContentText(ResumeBuilderRequest request) {
        StringBuilder sb = new StringBuilder();
        ResumeBuilderRequest.BasicInfo basic = request.basic();
        if (basic != null) {
            List<String> header = new ArrayList<>();
            addIfPresent(header, basic.name());
            if (StringUtils.hasText(basic.jobIntention())) {
                header.add("求职意向：" + basic.jobIntention().trim());
            }
            if (StringUtils.hasText(basic.phone())) {
                header.add("电话：" + basic.phone().trim());
            }
            if (StringUtils.hasText(basic.email())) {
                header.add("邮箱：" + basic.email().trim());
            }
            if (StringUtils.hasText(basic.city())) {
                header.add("城市：" + basic.city().trim());
            }
            if (!header.isEmpty()) {
                sb.append(String.join(" | ", header)).append('\n');
            }
        }

        appendEducations(sb, request.educations());
        appendExperiences(sb, "实习经历", request.internships());
        appendExperiences(sb, "项目经历", request.projects());

        if (request.skills() != null && !request.skills().isEmpty()) {
            String skills = request.skills().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.joining("、"));
            if (!skills.isEmpty()) {
                sb.append("\n技能：").append(skills).append('\n');
            }
        }
        if (StringUtils.hasText(request.summary())) {
            sb.append("\n自我评价：").append(request.summary().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private void appendEducations(StringBuilder sb, List<ResumeBuilderRequest.EducationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("\n教育背景：\n");
        for (ResumeBuilderRequest.EducationItem item : items) {
            String line = joinNonBlank(" | ", item.school(), item.major(), item.degree(),
                    joinPeriod(item.startDate(), item.endDate()));
            if (!line.isBlank()) {
                sb.append(line).append('\n');
            }
            if (StringUtils.hasText(item.description())) {
                sb.append(item.description().trim()).append('\n');
            }
        }
    }

    private void appendExperiences(StringBuilder sb, String title,
                                   List<ResumeBuilderRequest.ExperienceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append("：\n");
        for (ResumeBuilderRequest.ExperienceItem item : items) {
            String line = joinNonBlank(" | ", item.organization(), item.title(),
                    joinPeriod(item.startDate(), item.endDate()));
            if (!line.isBlank()) {
                sb.append(line).append('\n');
            }
            if (item.bullets() != null) {
                for (String bullet : item.bullets()) {
                    if (StringUtils.hasText(bullet)) {
                        sb.append("- ").append(bullet.trim()).append('\n');
                    }
                }
            }
        }
    }

    private AutofillVO fromBuilder(Resume resume, ResumeBuilderRequest request) {
        ResumeBuilderRequest.BasicInfo basic = request.basic();
        String name = basic == null ? null : trimOrNull(basic.name());
        String phone = basic == null ? null : trimOrNull(basic.phone());
        String email = basic == null ? null : trimOrNull(basic.email());
        String city = basic == null ? null : trimOrNull(basic.city());

        String school = null;
        String major = null;
        String degree = null;
        String graduationYear = null;
        if (request.educations() != null && !request.educations().isEmpty()) {
            ResumeBuilderRequest.EducationItem education = request.educations().get(0);
            school = trimOrNull(education.school());
            major = trimOrNull(education.major());
            degree = trimOrNull(education.degree());
            graduationYear = firstMatch(PLAIN_YEAR_PATTERN, education.endDate());
        }

        return new AutofillVO(resume.getId(), resume.getName(), name, phone, email, city,
                school, major, degree, graduationYear,
                request.skills() == null ? List.of() : request.skills(),
                mapExperiences(request.internships()),
                mapExperiences(request.projects()));
    }

    private AutofillVO fromText(Resume resume) {
        String text = resume.getContentText() == null ? "" : resume.getContentText();
        String name = extractName(text);
        String phone = firstMatch(PHONE_PATTERN, text);
        String email = firstMatch(EMAIL_PATTERN, text);
        String school = firstMatch(SCHOOL_PATTERN, text);
        String major = firstMatch(MAJOR_PATTERN, text);
        String degree = extractDegree(text);
        String graduationYear = firstMatch(YEAR_PATTERN, text);
        String city = CITIES.stream().filter(text::contains).findFirst().orElse(null);
        List<String> skills = matchEngine.allKeywords().stream()
                .filter(keyword -> !NON_SKILL_KEYWORDS.contains(keyword))
                .filter(keyword -> matchEngine.containsKeyword(text, keyword))
                .limit(12)
                .toList();

        return new AutofillVO(resume.getId(), resume.getName(), name, phone, email, city,
                school, major, degree, graduationYear, skills,
                extractExperiences(text, List.of("实习经历", "工作经历")),
                extractExperiences(text, List.of("项目经历", "项目经验")));
    }

    private List<AutofillVO.AutofillExperience> mapExperiences(
            List<ResumeBuilderRequest.ExperienceItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new AutofillVO.AutofillExperience(
                        trimOrNull(item.organization()),
                        trimOrNull(item.title()),
                        joinPeriod(item.startDate(), item.endDate()),
                        item.bullets() == null ? List.of()
                                : item.bullets().stream().filter(StringUtils::hasText).toList()))
                .toList();
    }

    private List<AutofillVO.AutofillExperience> extractExperiences(String text, List<String> startTitles) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        List<String> block = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            boolean isStart = startTitles.stream()
                    .anyMatch(title -> line.startsWith(title) && line.length() <= 60);
            if (isStart) {
                if (inside) {
                    break;
                }
                inside = true;
                continue;
            }
            if (inside) {
                boolean isStop = STOP_TITLES.stream()
                        .anyMatch(title -> line.startsWith(title) && line.length() <= 60);
                if (isStop) {
                    break;
                }
                block.add(line);
                if (block.size() >= 8) {
                    break;
                }
            }
        }
        if (block.isEmpty()) {
            return List.of();
        }
        return List.of(new AutofillVO.AutofillExperience(null, null, null, block));
    }

    private String extractName(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        String token = firstLine.split("[|｜]")[0].trim();
        if (token.length() >= 2 && token.length() <= 4 && token.matches("[\\u4e00-\\u9fa5]+")) {
            return token;
        }
        return null;
    }

    private String extractDegree(String text) {
        for (String degree : List.of("博士", "硕士", "本科", "大专", "专科")) {
            if (text.contains(degree)) {
                return degree;
            }
        }
        return null;
    }

    private String firstMatch(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void addIfPresent(List<String> list, String value) {
        if (StringUtils.hasText(value)) {
            list.add(value.trim());
        }
    }

    private String joinNonBlank(String delimiter, String... values) {
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(delimiter));
    }

    private String joinPeriod(String start, String end) {
        String from = StringUtils.hasText(start) ? start.trim() : "";
        String to = StringUtils.hasText(end) ? end.trim() : "";
        if (from.isEmpty() && to.isEmpty()) {
            return null;
        }
        if (from.isEmpty()) {
            return to;
        }
        if (to.isEmpty()) {
            return from;
        }
        return from + " - " + to;
    }

    private String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Resume requireOwnedResume(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || !resume.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BusinessException.notFound("简历不存在");
        }
        return resume;
    }

    private String extractQuietly(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return textExtractor.extract(inputStream);
        }
        catch (Exception ex) {
            log.warn("简历文本解析失败，将保存文件但不参与 AI 匹配：{}", ex.getMessage());
            return "";
        }
    }

    private ResumeVO toVO(Resume resume, boolean withFullText) {
        String text = resume.getContentText() == null ? "" : resume.getContentText();
        String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
        String fileUrl = StringUtils.hasText(resume.getFilePath())
                ? "/uploads/" + resume.getFilePath() : null;
        return new ResumeVO(resume.getId(), resume.getName(), resume.getVersionLabel(),
                resume.getFileName(), resume.getFileSize(), resume.getIsDefault(),
                preview, withFullText ? text : null,
                withFullText ? resume.getContentJson() : null,
                fileUrl, resume.getCreatedAt());
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

}
