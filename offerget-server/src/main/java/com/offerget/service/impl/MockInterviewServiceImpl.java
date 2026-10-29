package com.offerget.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.ai.AiClient;
import com.offerget.common.BusinessException;
import com.offerget.dto.interview.AnswerInterviewRequest;
import com.offerget.dto.interview.CreateInterviewRequest;
import com.offerget.dto.interview.InterviewAnswerVO;
import com.offerget.dto.interview.InterviewFollowUpVO;
import com.offerget.dto.interview.InterviewHistoryVO;
import com.offerget.dto.interview.InterviewIssueVO;
import com.offerget.dto.interview.InterviewPointVO;
import com.offerget.dto.interview.InterviewProgressVO;
import com.offerget.dto.interview.InterviewQuestionVO;
import com.offerget.dto.interview.InterviewReportVO;
import com.offerget.dto.interview.InterviewReverseVO;
import com.offerget.dto.interview.InterviewScoreVO;
import com.offerget.dto.interview.InterviewSessionVO;
import com.offerget.dto.interview.ReverseInterviewRequest;
import com.offerget.entity.Job;
import com.offerget.entity.MockInterviewQuestion;
import com.offerget.entity.MockInterviewSession;
import com.offerget.entity.Resume;
import com.offerget.mapper.JobMapper;
import com.offerget.mapper.MockInterviewQuestionMapper;
import com.offerget.mapper.MockInterviewSessionMapper;
import com.offerget.mapper.ResumeMapper;
import com.offerget.security.SecurityUtils;
import com.offerget.service.MockInterviewService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 模拟面试实现。
 *
 * 设计要点：
 * - 面试官回复与评分解耦，对话中不展示分数；
 * - 题目完成后异步评分，结束报告时兜底补评；
 * - 模型输出一律经过依据回溯、分数范围、风险枚举等确定性校验。
 */
@Service
public class MockInterviewServiceImpl implements MockInterviewService {

    private static final Logger log = LoggerFactory.getLogger(MockInterviewServiceImpl.class);

    private static final String TYPE_INTRO = "INTRO";

    private static final String TYPE_PROJECT = "PROJECT";

    private static final String TYPE_TECHNICAL = "TECHNICAL";

    private static final String TYPE_BEHAVIORAL = "BEHAVIORAL";

    private static final String TYPE_PRESSURE = "PRESSURE";

    private static final String TYPE_REVERSE = "REVERSE";

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final String STATUS_ABANDONED = "ABANDONED";

    private static final String Q_PENDING = "PENDING";

    private static final String Q_ASKED = "ASKED";

    private static final String Q_ANSWERED = "ANSWERED";

    private static final String Q_SKIPPED = "SKIPPED";

    private static final String ACTION_FOLLOW_UP = "FOLLOW_UP";

    private static final String ACTION_NEXT = "NEXT";

    private static final String ACTION_CLOSE = "CLOSE";

    private static final int MAX_MAIN_QUESTIONS = 7;

    private static final int MIN_MAIN_QUESTIONS = 6;

    private static final int MAX_FOLLOW_UPS = 2;

    private static final int MAX_REVERSE_QUESTIONS = 2;

    private static final int MAX_JD_CHARS = 3000;

    private static final int MAX_RESUME_CHARS = 6000;

    private static final int MAX_ANSWER_CHARS = 1000;

    private static final Set<String> MAIN_TYPES = Set.of(
            TYPE_INTRO, TYPE_PROJECT, TYPE_TECHNICAL, TYPE_BEHAVIORAL, TYPE_PRESSURE);

    private static final Set<String> RISK_FLAGS = Set.of(
            "OFF_TOPIC", "NO_METRICS", "VAGUE", "EXAGGERATED", "LOGIC_JUMP");

    private static final List<String> DIMENSIONS = List.of(
            "jobUnderstanding", "projectDepth", "technicalFoundation", "logic", "starCompleteness");

    private static final Map<String, Integer> TYPE_WEIGHTS = Map.of(
            TYPE_PROJECT, 30,
            TYPE_TECHNICAL, 30,
            TYPE_BEHAVIORAL, 20,
            TYPE_INTRO, 10,
            TYPE_PRESSURE, 10);

    private static final String RISK_EXAGGERATED = "EXAGGERATED";

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:19|20)\\d{2}");

    private static final Pattern DONT_KNOW_PATTERN = Pattern.compile(
            "不知道|不清楚|不会|没做过|没有相关经验|跳过|不太了解|不了解");

    private static final DateTimeFormatter TREND_DATE = DateTimeFormatter.ofPattern("MM-dd");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<List<InterviewFollowUpVO>> FOLLOW_UP_LIST = new TypeReference<>() {
    };

    private static final String INTRO_QUESTION =
            "请先做一个 1 分钟左右的自我介绍，重点讲和这个岗位最相关的经历。";

    private static final String REVERSE_QUESTION =
            "我的问题问完了，你有什么想问我的吗？";

    private static final String PROJECT_FALLBACK_QUESTION =
            "请挑一个你简历里最相关的项目或实习，按背景、你的职责、关键方案和结果讲一下。";

    private static final String TECHNICAL_FALLBACK_QUESTION =
            "请挑一项你在这个岗位方向上的核心能力，讲讲你如何学习和实际使用它。";

    private static final String BEHAVIORAL_FALLBACK_QUESTION =
            "请讲一次你和队友意见不一致的经历，你当时是怎么推动达成一致的？";

    private static final String PRESSURE_FALLBACK_QUESTION =
            "你刚才提到的项目结果，能具体说明哪些部分是你个人完成的吗？";

    private static final String GENERATE_SYSTEM_PROMPT = """
            你是一名校招面试官，负责为候选人生成一场模拟面试的题单。

            只输出 JSON：
            {"opening":"面试官开场白，1-2 句","questions":[
              {"type":"INTRO","question":"...","basisSource":null,"basisQuote":null,"focus":"...","difficulty":"EASY"}
            ]}

            规则：
            1. 生成 7 道主问题，必须包含 1 道 INTRO、2-3 道 PROJECT（简历中有实习或项目时）、2 道 TECHNICAL、1 道 BEHAVIORAL；难度为 HARD 时可额外增加 1 道 PRESSURE。
            2. 除 INTRO 外，每道题必须给出 basisSource（JD 或 RESUME）与 basisQuote。basisQuote 必须从对应原文中原样复制 20-80 字，不得改写、不得概括。
            3. PROJECT 题必须指向简历中真实存在的项目或实习，并追问背景、个人职责、方案取舍或结果。
            4. TECHNICAL 题只能落在 JD 或简历中出现过的技术、业务范围内，不出偏题怪题。
            5. 不要生成 REVERSE，服务端会追加反问环节。
            6. 不询问年龄、婚育、家庭、健康、身份证号等隐私，不做人身攻击。
            7. 问题具体、口语化、可回答，避免「谈谈你对行业的看法」这类空泛问题。
            8. 不要给出参考答案。
            """;

    private static final String DECIDE_SYSTEM_PROMPT = """
            你是校招面试官，正在对候选人进行模拟面试。根据当前问题和候选人的回答，决定下一步。

            只输出 JSON：
            {"action":"FOLLOW_UP|NEXT|CLOSE","reply":"面试官口语化回应，1-3 句","question":"追问问题","anchor":"追问依据的回答原文片段","intent":"内部意图"}

            规则：
            1. FOLLOW_UP 仅在还有追问次数、且回答中存在值得深挖的模糊点、缺失的 STAR 要素或未解释的取舍时使用；追问必须基于候选人的回答，不得引入新事实。
            2. 回答过短、空泛、明确表示不知道时，不要追问，action 用 NEXT。
            3. 追问最多 2 层，已达到上限必须 NEXT。
            4. 还有下一道题时 action 用 NEXT；没有下一道题时 action 用 CLOSE。
            5. 对话中不评价回答、不给分数、不提供参考答案，只做真实面试官会做的追问与过渡。
            """;

    private static final String SCORE_SYSTEM_PROMPT = """
            你是校招面试评估员。根据岗位、简历、面试问题与候选人回答，输出结构化评分。

            只输出 JSON：
            {"dimensions":{"jobUnderstanding":0,"projectDepth":0,"technicalFoundation":0,"logic":0,"starCompleteness":0},
             "highlights":[{"quote":"回答原文片段","comment":"..."}],
             "issues":[{"quote":"回答原文片段","comment":"...","severity":"HIGH|MEDIUM|LOW"}],
             "suggestions":["..."],
             "riskFlags":["..."],
             "confidence":0.0}

            评分维度（0-100）：
            - jobUnderstanding 岗位理解：0-30 完全偏离；31-50 泛泛而谈；51-70 基本理解；71-85 准确且结合 JD；86-100 深入并能联系业务。
            - projectDepth 项目深度：是否讲清背景、个人职责、方案、难点与结果；追问下是否经得起验证。
            - technicalFoundation 技术基础：概念准确、能解释取舍、不编造。
            - logic 表达逻辑：结论先行、层次清楚、不堆砌。
            - starCompleteness STAR 完整性：S/T/A/R 是否齐全，是否有量化结果。

            规则：
            1. 每条 highlight / issue 的 quote 必须原样引用回答或追问回答中的片段，不得改写；引用不到的内容不要输出。
            2. 只评价本次问答，不推测候选人没说的内容。
            3. 评分要有区分度，避免所有维度都给 80 分以上；表达朴素但内容扎实不应压低分。
            4. riskFlags 只能从 OFF_TOPIC、NO_METRICS、VAGUE、EXAGGERATED、LOGIC_JUMP 中选择；没有则为空数组。
            5. 无法评估的维度返回 null。
            """;

    private static final String REVERSE_SYSTEM_PROMPT = """
            你是目标公司的校招面试官，面试已进入反问环节，候选人在向你提问。
            只输出 JSON：{"reply":"你的回答，2-4 句"}

            规则：
            1. 结合岗位 JD 与已知信息回答，不编造薪资、HC、内推、面试结果等无法确认的信息。
            2. 涉及个人隐私或敏感信息时，礼貌说明无法回答，并引导到岗位相关话题。
            3. 语气专业、友好，像真实面试官。
            """;

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是校招面试教练。根据本次模拟面试的逐题评分和主要问题，写一份简短的总结。

            只输出 JSON：{"summary":"80 字以内","suggestions":["3 条可执行的改进建议"]}

            要求：
            - 只依据给定信息，不推测候选人未展示的能力。
            - 建议具体、可执行，避免「多加练习」这类空话。
            - 不提供标准答案。
            """;

    private final MockInterviewSessionMapper sessionMapper;

    private final MockInterviewQuestionMapper questionMapper;

    private final JobMapper jobMapper;

    private final ResumeMapper resumeMapper;

    private final AiClient aiClient;

    private final AiMatchEngine matchEngine;

    private final ObjectMapper objectMapper;

    private final Executor interviewExecutor;

    public MockInterviewServiceImpl(MockInterviewSessionMapper sessionMapper,
                                    MockInterviewQuestionMapper questionMapper,
                                    JobMapper jobMapper,
                                    ResumeMapper resumeMapper,
                                    AiClient aiClient,
                                    AiMatchEngine matchEngine,
                                    ObjectMapper objectMapper,
                                    @Qualifier("interviewExecutor") Executor interviewExecutor) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.aiClient = aiClient;
        this.matchEngine = matchEngine;
        this.objectMapper = objectMapper;
        this.interviewExecutor = interviewExecutor;
    }

    // ---------------------------------------------------------------- 会话

    @Override
    public InterviewSessionVO create(CreateInterviewRequest request) {
        Long userId = SecurityUtils.currentUserId();
        Job job = requireJob(request.jobId(), userId);
        Resume resume = requireResume(request.resumeId(), userId);

        String jd = trim(job.getJdText());
        if (!StringUtils.hasText(jd)) {
            throw BusinessException.badRequest("岗位还没有填写 JD 文本，补全后即可模拟面试");
        }
        String resumeText = trim(resume.getContentText());
        if (!StringUtils.hasText(resumeText)) {
            throw BusinessException.badRequest("简历没有可解析的文本，请上传文字版简历或使用简历制作器");
        }

        Long active = sessionMapper.selectCount(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getUserId, userId)
                .eq(MockInterviewSession::getJobId, job.getId())
                .eq(MockInterviewSession::getStatus, STATUS_IN_PROGRESS));
        if (active != null && active > 0) {
            throw BusinessException.conflict("该岗位已有进行中的模拟面试，请先继续完成或放弃");
        }

        String interviewType = normalizeType(request.interviewType());
        String difficulty = normalizeDifficulty(request.difficulty());

        Generated generated;
        try {
            generated = generate(job, jd, resumeText, interviewType, difficulty);
        }
        catch (Exception ex) {
            log.warn("模拟面试题单首次生成失败，准备重试：{}", ex.getMessage());
            generated = new Generated(null, List.of());
        }
        if (generated.questions().size() < 4) {
            generated = generate(job, jd, resumeText, interviewType, difficulty);
        }
        if (generated.questions().size() < 4) {
            throw new BusinessException(50200, "面试题生成失败，请补充 JD 或简历后重试",
                    HttpStatus.BAD_GATEWAY);
        }

        List<QuestionDraft> drafts = finalizeQuestions(
                generated.questions(), jd, resumeText, difficulty);

        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setJobId(job.getId());
        session.setResumeId(resume.getId());
        session.setInterviewType(interviewType);
        session.setDifficulty(difficulty);
        session.setStatus(STATUS_IN_PROGRESS);
        session.setOpening(StringUtils.hasText(generated.opening())
                ? limit(generated.opening(), 300) : defaultOpening(interviewType));
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        int seq = 1;
        for (QuestionDraft draft : drafts) {
            MockInterviewQuestion question = new MockInterviewQuestion();
            question.setSessionId(session.getId());
            question.setSeq(seq++);
            question.setQuestionType(draft.type());
            question.setQuestion(draft.question());
            question.setBasisSource(draft.basisSource());
            question.setBasisQuote(draft.basisQuote());
            question.setFocus(draft.focus());
            question.setDifficulty(draft.difficulty());
            question.setFollowupCount(0);
            question.setFollowupsJson("[]");
            question.setStatus(Q_PENDING);
            questionMapper.insert(question);
        }
        return detail(session.getId());
    }

    @Override
    public InterviewSessionVO detail(Long id) {
        Long userId = SecurityUtils.currentUserId();
        MockInterviewSession session = requireSession(id, userId);
        Job job = jobMapper.selectById(session.getJobId());
        Resume resume = resumeMapper.selectById(session.getResumeId());
        List<MockInterviewQuestion> questions = listQuestions(session.getId());

        boolean completed = STATUS_COMPLETED.equals(session.getStatus());
        List<InterviewQuestionVO> questionVOs = questions.stream()
                .map(question -> toQuestionVO(question, completed))
                .toList();

        MockInterviewQuestion current = completed ? null : findCurrentMain(questions);
        MockInterviewQuestion reverse = questions.stream()
                .filter(question -> TYPE_REVERSE.equals(question.getQuestionType()))
                .findFirst().orElse(null);
        Long currentQuestionId = current != null ? current.getId()
                : (reverse != null && !completed ? reverse.getId() : null);
        String currentFollowUp = current != null ? pendingFollowUp(current) : null;

        InterviewReportVO report = completed ? readReport(session.getReportJson()) : null;
        return new InterviewSessionVO(
                session.getId(),
                session.getJobId(),
                job == null ? "" : job.getCompany(),
                job == null ? "" : job.getTitle(),
                session.getResumeId(),
                resume == null ? "已删除简历" : resume.getName(),
                session.getInterviewType(),
                session.getDifficulty(),
                session.getStatus(),
                session.getOpening(),
                session.getTotalScore(),
                progressOf(questions),
                currentQuestionId,
                currentFollowUp,
                questionVOs,
                report,
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getCreatedAt());
    }

    @Override
    public List<InterviewHistoryVO> history(Long jobId) {
        Long userId = SecurityUtils.currentUserId();
        List<MockInterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewSession>()
                        .eq(MockInterviewSession::getUserId, userId)
                        .eq(jobId != null, MockInterviewSession::getJobId, jobId)
                        .orderByDesc(MockInterviewSession::getCreatedAt)
                        .last("limit 30"));
        if (sessions.isEmpty()) {
            return List.of();
        }

        Set<Long> jobIds = sessions.stream().map(MockInterviewSession::getJobId).collect(Collectors.toSet());
        Set<Long> resumeIds = sessions.stream().map(MockInterviewSession::getResumeId)
                .collect(Collectors.toSet());
        Map<Long, Job> jobMap = jobMapper.selectBatchIds(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, Resume> resumeMap = resumeMapper.selectBatchIds(resumeIds).stream()
                .collect(Collectors.toMap(Resume::getId, Function.identity()));

        List<Long> sessionIds = sessions.stream().map(MockInterviewSession::getId).toList();
        List<MockInterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .in(MockInterviewQuestion::getSessionId, sessionIds));
        Map<Long, List<MockInterviewQuestion>> bySession = questions.stream()
                .collect(Collectors.groupingBy(MockInterviewQuestion::getSessionId));

        return sessions.stream().map(session -> {
            List<MockInterviewQuestion> items = bySession.getOrDefault(session.getId(), List.of());
            List<MockInterviewQuestion> mains = items.stream()
                    .filter(question -> !TYPE_REVERSE.equals(question.getQuestionType()))
                    .toList();
            int answered = (int) mains.stream()
                    .filter(question -> Q_ANSWERED.equals(question.getStatus())
                            || Q_SKIPPED.equals(question.getStatus()))
                    .count();
            Job job = jobMap.get(session.getJobId());
            Resume resume = resumeMap.get(session.getResumeId());
            return new InterviewHistoryVO(
                    session.getId(),
                    session.getJobId(),
                    job == null ? "" : job.getCompany(),
                    job == null ? "" : job.getTitle(),
                    resume == null ? "已删除简历" : resume.getName(),
                    session.getInterviewType(),
                    session.getDifficulty(),
                    session.getStatus(),
                    session.getTotalScore(),
                    answered,
                    mains.size(),
                    session.getCreatedAt(),
                    session.getFinishedAt());
        }).toList();
    }

    @Override
    public InterviewAnswerVO answer(Long id, AnswerInterviewRequest request) {
        Long userId = SecurityUtils.currentUserId();
        MockInterviewSession session = requireSession(id, userId);
        requireInProgress(session);

        List<MockInterviewQuestion> questions = listQuestions(session.getId());
        MockInterviewQuestion current = findCurrentMain(questions);
        MockInterviewQuestion question = questions.stream()
                .filter(item -> item.getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("题目不存在"));
        if (TYPE_REVERSE.equals(question.getQuestionType())) {
            throw BusinessException.badRequest("请使用反问接口");
        }
        if (current == null || !current.getId().equals(question.getId())) {
            throw BusinessException.conflict("请按顺序作答当前题目");
        }

        Job job = requireJob(session.getJobId(), userId);
        Resume resume = resumeMapper.selectById(session.getResumeId());
        String resumeText = resume == null ? "" : trim(resume.getContentText());

        boolean skip = Boolean.TRUE.equals(request.skip()) || !StringUtils.hasText(request.answer());
        String answer = skip ? "" : limit(request.answer().trim(), MAX_ANSWER_CHARS);

        List<InterviewFollowUpVO> followups = readFollowUps(question);
        boolean answeringFollowUp = !followups.isEmpty()
                && !StringUtils.hasText(followups.get(followups.size() - 1).answer());

        if (answeringFollowUp) {
            return answerFollowUp(session, question, answer, skip, followups, job, resumeText);
        }
        return answerMain(session, question, answer, skip, followups, job, resumeText);
    }

    private InterviewAnswerVO answerMain(MockInterviewSession session,
                                         MockInterviewQuestion question,
                                         String answer,
                                         boolean skip,
                                         List<InterviewFollowUpVO> followups,
                                         Job job,
                                         String resumeText) {
        if (skip) {
            question.setAnswer("（跳过）");
            question.setStatus(Q_SKIPPED);
            question.setAnsweredAt(LocalDateTime.now());
            question.setFollowupCount(0);
            question.setFollowupsJson(writeJson(followups));
            questionMapper.updateById(question);
            return nextAfterQuestion(session, question, "好的，我们继续下一题。");
        }

        question.setAnswer(answer);
        question.setStatus(Q_ASKED);
        question.setAnsweredAt(LocalDateTime.now());
        questionMapper.updateById(question);

        Decision decision = isDontKnow(answer)
                ? new Decision(ACTION_NEXT, "好的，没关系，我们继续。", null, null)
                : safeDecide(session, question, followups, hasNextMain(session, question), job, resumeText);

        if (ACTION_FOLLOW_UP.equals(decision.action())
                && followups.size() < MAX_FOLLOW_UPS
                && StringUtils.hasText(decision.question())) {
            followups.add(new InterviewFollowUpVO(
                    limit(decision.question(), 300), null, LocalDateTime.now().toString()));
            question.setFollowupsJson(writeJson(followups));
            question.setFollowupCount(followups.size());
            question.setStatus(Q_ASKED);
            questionMapper.updateById(question);
            return new InterviewAnswerVO(
                    ACTION_FOLLOW_UP,
                    StringUtils.hasText(decision.reply()) ? decision.reply() : "能具体展开讲讲吗？",
                    decision.question(),
                    null,
                    progressOf(session.getId()));
        }

        question.setStatus(Q_ANSWERED);
        question.setFollowupCount(followups.size());
        questionMapper.updateById(question);
        scoreAsync(question, job, resumeText);
        return nextAfterQuestion(session, question, decision.reply());
    }

    private InterviewAnswerVO answerFollowUp(MockInterviewSession session,
                                             MockInterviewQuestion question,
                                             String answer,
                                             boolean skip,
                                             List<InterviewFollowUpVO> followups,
                                             Job job,
                                             String resumeText) {
        int lastIndex = followups.size() - 1;
        InterviewFollowUpVO last = followups.get(lastIndex);
        followups.set(lastIndex, new InterviewFollowUpVO(
                last.question(), skip ? "（跳过）" : answer, LocalDateTime.now().toString()));
        question.setFollowupsJson(writeJson(followups));
        question.setFollowupCount(followups.size());
        questionMapper.updateById(question);

        if (skip) {
            question.setStatus(Q_ANSWERED);
            questionMapper.updateById(question);
            scoreAsync(question, job, resumeText);
            return nextAfterQuestion(session, question, "好的，我们继续下一题。");
        }

        Decision decision = isDontKnow(answer)
                ? new Decision(ACTION_NEXT, "好的，没关系，我们继续。", null, null)
                : safeDecide(session, question, followups, hasNextMain(session, question), job, resumeText);

        if (ACTION_FOLLOW_UP.equals(decision.action())
                && followups.size() < MAX_FOLLOW_UPS
                && StringUtils.hasText(decision.question())) {
            followups.add(new InterviewFollowUpVO(
                    limit(decision.question(), 300), null, LocalDateTime.now().toString()));
            question.setFollowupsJson(writeJson(followups));
            question.setFollowupCount(followups.size());
            question.setStatus(Q_ASKED);
            questionMapper.updateById(question);
            return new InterviewAnswerVO(
                    ACTION_FOLLOW_UP,
                    StringUtils.hasText(decision.reply()) ? decision.reply() : "能再具体一点吗？",
                    decision.question(),
                    null,
                    progressOf(session.getId()));
        }

        question.setStatus(Q_ANSWERED);
        question.setFollowupCount(followups.size());
        questionMapper.updateById(question);
        scoreAsync(question, job, resumeText);
        return nextAfterQuestion(session, question, decision.reply());
    }

    private InterviewAnswerVO nextAfterQuestion(MockInterviewSession session,
                                                MockInterviewQuestion question,
                                                String reply) {
        MockInterviewQuestion next = listQuestions(session.getId()).stream()
                .filter(item -> !TYPE_REVERSE.equals(item.getQuestionType()))
                .filter(item -> item.getSeq() > question.getSeq())
                .filter(item -> !Q_ANSWERED.equals(item.getStatus())
                        && !Q_SKIPPED.equals(item.getStatus()))
                .min(Comparator.comparing(MockInterviewQuestion::getSeq))
                .orElse(null);

        if (next == null) {
            String closing = StringUtils.hasText(reply) ? reply
                    : "好的，我的问题问完了，你有什么想问我的吗？";
            return new InterviewAnswerVO(ACTION_CLOSE, closing, null, null,
                    progressOf(session.getId()));
        }

        next.setStatus(Q_ASKED);
        questionMapper.updateById(next);
        return new InterviewAnswerVO(
                ACTION_NEXT,
                StringUtils.hasText(reply) ? reply : "好的，我们进入下一题。",
                null,
                toQuestionVO(next, false),
                progressOf(session.getId()));
    }

    @Override
    public InterviewReverseVO reverse(Long id, ReverseInterviewRequest request) {
        Long userId = SecurityUtils.currentUserId();
        MockInterviewSession session = requireSession(id, userId);
        requireInProgress(session);

        List<MockInterviewQuestion> questions = listQuestions(session.getId());
        MockInterviewQuestion reverse = questions.stream()
                .filter(question -> TYPE_REVERSE.equals(question.getQuestionType()))
                .findFirst()
                .orElseThrow(() -> BusinessException.badRequest("当前会话没有反问环节"));
        boolean mainsDone = questions.stream()
                .filter(question -> !TYPE_REVERSE.equals(question.getQuestionType()))
                .allMatch(question -> Q_ANSWERED.equals(question.getStatus())
                        || Q_SKIPPED.equals(question.getStatus()));
        if (!mainsDone) {
            throw BusinessException.badRequest("请先完成全部面试问题，再进入反问环节");
        }

        List<InterviewFollowUpVO> items = readFollowUps(reverse);
        if (items.size() >= MAX_REVERSE_QUESTIONS) {
            throw BusinessException.conflict("反问环节最多提问 2 个问题");
        }

        Job job = requireJob(session.getJobId(), userId);
        String reply = requestReverseReply(job, reverse, request.question().trim());
        items.add(new InterviewFollowUpVO(request.question().trim(), reply,
                LocalDateTime.now().toString()));

        reverse.setFollowupsJson(writeJson(items));
        reverse.setAnswer(request.question().trim());
        reverse.setAnswerFeedback(reply);
        reverse.setStatus(Q_ASKED);
        reverse.setAnsweredAt(LocalDateTime.now());
        questionMapper.updateById(reverse);

        return new InterviewReverseVO(reply, items.size() < MAX_REVERSE_QUESTIONS, items.size());
    }

    @Override
    public InterviewSessionVO finish(Long id) {
        Long userId = SecurityUtils.currentUserId();
        MockInterviewSession session = requireSession(id, userId);
        if (STATUS_COMPLETED.equals(session.getStatus())) {
            return detail(session.getId());
        }
        requireInProgress(session);

        Job job = requireJob(session.getJobId(), userId);
        Resume resume = resumeMapper.selectById(session.getResumeId());
        String resumeText = resume == null ? "" : trim(resume.getContentText());
        List<MockInterviewQuestion> questions = listQuestions(session.getId());

        List<MockInterviewQuestion> mains = questions.stream()
                .filter(question -> !TYPE_REVERSE.equals(question.getQuestionType()))
                .toList();
        boolean anyAnswered = mains.stream()
                .anyMatch(question -> Q_ANSWERED.equals(question.getStatus())
                        || Q_SKIPPED.equals(question.getStatus()));
        if (!anyAnswered) {
            throw BusinessException.badRequest("还没有作答，无法生成面试报告");
        }

        // 兜底补评：异步评分可能尚未完成，结束前同步补齐
        for (MockInterviewQuestion question : mains) {
            if (Q_ANSWERED.equals(question.getStatus())
                    && !StringUtils.hasText(question.getScoreJson())) {
                try {
                    InterviewScoreVO score = requestScore(question, job, resumeText);
                    if (score != null) {
                        question.setScoreJson(writeJson(score));
                        question.setRiskFlags(String.join(",", score.riskFlags() == null
                                ? List.of() : score.riskFlags()));
                        questionMapper.updateById(question);
                    }
                }
                catch (Exception ex) {
                    log.warn("模拟面试补评失败：session={}，question={}，{}",
                            session.getId(), question.getId(), ex.getMessage());
                }
            }
        }

        MockInterviewQuestion reverse = questions.stream()
                .filter(question -> TYPE_REVERSE.equals(question.getQuestionType()))
                .findFirst().orElse(null);
        if (reverse != null && readFollowUps(reverse).isEmpty()) {
            reverse.setStatus(Q_SKIPPED);
            questionMapper.updateById(reverse);
        }

        List<MockInterviewQuestion> refreshed = listQuestions(session.getId());
        InterviewReportVO report = buildReport(session, job, refreshed);
        session.setTotalScore(report.totalScore());
        session.setReportJson(writeJson(report));
        session.setStatus(STATUS_COMPLETED);
        session.setFinishedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        return detail(session.getId());
    }

    @Override
    public void abandon(Long id) {
        Long userId = SecurityUtils.currentUserId();
        MockInterviewSession session = requireSession(id, userId);
        requireInProgress(session);
        session.setStatus(STATUS_ABANDONED);
        session.setFinishedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    // ---------------------------------------------------------------- 出题

    private Generated generate(Job job, String jd, String resumeText,
                               String interviewType, String difficulty) {
        String userPrompt = buildGeneratePrompt(job, jd, resumeText, interviewType, difficulty);
        String raw = aiClient.chatJson(List.of(
                new AiClient.Message("system", GENERATE_SYSTEM_PROMPT),
                new AiClient.Message("user", userPrompt)), 0.7);
        Generated generated = parseGenerated(raw, difficulty);
        return new Generated(generated.opening(),
                validateDrafts(generated.questions(), jd, resumeText, difficulty));
    }

    private String buildGeneratePrompt(Job job, String jd, String resumeText,
                                       String interviewType, String difficulty) {
        StringBuilder sb = new StringBuilder();
        sb.append("【公司】").append(job.getCompany()).append('\n');
        sb.append("【岗位】").append(job.getTitle()).append('\n');
        sb.append("【面试类型】").append(typeLabel(interviewType)).append('\n');
        sb.append("【难度】").append(difficultyLabel(difficulty)).append("\n\n");
        sb.append("【JD】\n").append(limit(jd, MAX_JD_CHARS)).append("\n\n");
        sb.append("【简历】\n").append(limit(resumeText, MAX_RESUME_CHARS)).append("\n\n");
        sb.append("请生成题单。basisQuote 必须从上面的 JD 或简历中原样复制。");
        return sb.toString();
    }

    private Generated parseGenerated(String raw, String difficulty) {
        try {
            Map<String, Object> root = objectMapper.readValue(raw, MAP_TYPE);
            List<QuestionDraft> drafts = new ArrayList<>();
            if (root.get("questions") instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String type = upper(textOf(map.get("type")));
                    if (type == null || !MAIN_TYPES.contains(type)) {
                        continue;
                    }
                    String question = limit(textOf(map.get("question")), 200);
                    if (!StringUtils.hasText(question)) {
                        continue;
                    }
                    String basisSource = upper(textOf(map.get("basisSource")));
                    if (!"JD".equals(basisSource) && !"RESUME".equals(basisSource)) {
                        basisSource = null;
                    }
                    drafts.add(new QuestionDraft(
                            type,
                            question,
                            basisSource,
                            limit(textOf(map.get("basisQuote")), 400),
                            limit(textOf(map.get("focus")), 100),
                            normalizeDifficulty(textOf(map.get("difficulty")))));
                }
            }
            return new Generated(limit(textOf(root.get("opening")), 300), drafts);
        }
        catch (Exception ex) {
            log.warn("模拟面试题单解析失败：{}", ex.getMessage());
            throw new BusinessException(50200, "AI 返回格式异常，请重试", HttpStatus.BAD_GATEWAY);
        }
    }

    private List<QuestionDraft> validateDrafts(List<QuestionDraft> drafts, String jd,
                                               String resumeText, String difficulty) {
        List<String> keywords = matchEngine.allKeywords().stream()
                .filter(keyword -> matchEngine.containsKeyword(jd, keyword)
                        || matchEngine.containsKeyword(resumeText, keyword))
                .toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        List<QuestionDraft> valid = new ArrayList<>();

        for (QuestionDraft draft : drafts) {
            boolean intro = TYPE_INTRO.equals(draft.type());
            if (!intro) {
                if (!StringUtils.hasText(draft.basisQuote()) || draft.basisSource() == null) {
                    continue;
                }
                String source = "JD".equals(draft.basisSource()) ? jd : resumeText;
                if (!containsNormalized(source, draft.basisQuote())) {
                    continue;
                }
            }
            if (TYPE_TECHNICAL.equals(draft.type()) && !keywords.isEmpty()) {
                boolean inScope = keywords.stream().anyMatch(keyword ->
                        matchEngine.containsKeyword(draft.question(), keyword)
                                || matchEngine.containsKeyword(draft.basisQuote(), keyword));
                if (!inScope) {
                    continue;
                }
            }

            int cap = switch (draft.type()) {
                case TYPE_INTRO -> 1;
                case TYPE_BEHAVIORAL -> 1;
                case TYPE_PROJECT -> 3;
                case TYPE_TECHNICAL -> 3;
                case TYPE_PRESSURE -> "HARD".equals(difficulty) ? 1 : 0;
                default -> 0;
            };
            if (counts.getOrDefault(draft.type(), 0) >= cap) {
                continue;
            }

            String normalized = normalize(draft.question());
            if (!seen.add(normalized)) {
                continue;
            }
            counts.merge(draft.type(), 1, Integer::sum);
            valid.add(draft);
        }
        return valid;
    }

    private List<QuestionDraft> finalizeQuestions(List<QuestionDraft> source, String jd,
                                                  String resumeText, String difficulty) {
        List<QuestionDraft> drafts = new ArrayList<>(source);

        boolean hasIntro = drafts.stream().anyMatch(item -> TYPE_INTRO.equals(item.type()));
        if (!hasIntro) {
            drafts.add(0, new QuestionDraft(TYPE_INTRO, INTRO_QUESTION, null, null,
                    "基本信息与岗位相关性", difficulty));
        }
        else {
            drafts.sort(Comparator.comparing(item -> !TYPE_INTRO.equals(item.type())));
        }

        Set<String> existingQuestions = drafts.stream()
                .map(item -> normalize(item.question()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> existingTypes = drafts.stream().map(QuestionDraft::type)
                .collect(Collectors.toSet());
        List<String> keywords = matchEngine.allKeywords().stream()
                .filter(keyword -> matchEngine.containsKeyword(jd, keyword)
                        || matchEngine.containsKeyword(resumeText, keyword))
                .toList();

        if (!existingTypes.contains(TYPE_PROJECT)) {
            addFallback(drafts, existingQuestions, new QuestionDraft(TYPE_PROJECT,
                    PROJECT_FALLBACK_QUESTION, null, null, "项目真实性与 STAR 完整性", difficulty));
        }
        if (!existingTypes.contains(TYPE_TECHNICAL)) {
            String keyword = keywords.stream()
                    .filter(item -> existingQuestions.stream()
                            .noneMatch(question -> normalize(question).contains(normalize(item))))
                    .findFirst().orElse(null);
            QuestionDraft technical = keyword == null
                    ? new QuestionDraft(TYPE_TECHNICAL, TECHNICAL_FALLBACK_QUESTION,
                            null, null, "技术基础与实战", difficulty)
                    : new QuestionDraft(TYPE_TECHNICAL,
                            "你的岗位或简历中提到了「" + keyword + "」，请结合具体场景讲讲它的核心机制和使用取舍。",
                            matchEngine.containsKeyword(jd, keyword) ? "JD" : "RESUME",
                            keyword, "技术理解与实战", difficulty);
            addFallback(drafts, existingQuestions, technical);
        }
        if (!existingTypes.contains(TYPE_BEHAVIORAL)) {
            addFallback(drafts, existingQuestions, new QuestionDraft(TYPE_BEHAVIORAL,
                    BEHAVIORAL_FALLBACK_QUESTION, null, null, "协作与冲突处理", difficulty));
        }
        if ("HARD".equals(difficulty) && !existingTypes.contains(TYPE_PRESSURE)) {
            addFallback(drafts, existingQuestions, new QuestionDraft(TYPE_PRESSURE,
                    PRESSURE_FALLBACK_QUESTION, null, null, "个人贡献与抗压", difficulty));
        }

        // 补足到 6 题；不足时使用通用题，避免因模型输出不稳定导致会话不可用
        List<QuestionDraft> pool = List.of(
                new QuestionDraft(TYPE_PROJECT, PROJECT_FALLBACK_QUESTION, null, null,
                        "项目真实性与 STAR 完整性", difficulty),
                new QuestionDraft(TYPE_BEHAVIORAL, BEHAVIORAL_FALLBACK_QUESTION, null, null,
                        "协作与冲突处理", difficulty),
                new QuestionDraft(TYPE_TECHNICAL, TECHNICAL_FALLBACK_QUESTION, null, null,
                        "技术基础与实战", difficulty),
                new QuestionDraft(TYPE_PROJECT,
                        "你简历里的经历中，哪一个最能体现你在这个岗位上的优势？请具体讲讲。",
                        null, null, "岗位匹配与自我认知", difficulty),
                new QuestionDraft(TYPE_BEHAVIORAL,
                        "请讲一次你主动推动某件事的经历，过程中遇到什么阻力，最后结果如何？",
                        null, null, "主动性、推动力与复盘", difficulty),
                new QuestionDraft(TYPE_TECHNICAL,
                        "如果在项目中遇到一个你之前没接触过的技术问题，你通常怎么定位和解决？",
                        null, null, "问题定位与学习能力", difficulty));
        int index = 0;
        while (drafts.size() < MIN_MAIN_QUESTIONS && index < pool.size()) {
            addFallback(drafts, existingQuestions, pool.get(index++));
        }

        while (drafts.size() > MAX_MAIN_QUESTIONS) {
            int removeIndex = -1;
            for (int i = drafts.size() - 1; i >= 0; i--) {
                if (!TYPE_INTRO.equals(drafts.get(i).type())) {
                    removeIndex = i;
                    break;
                }
            }
            if (removeIndex < 0) {
                break;
            }
            drafts.remove(removeIndex);
        }

        drafts.add(new QuestionDraft(TYPE_REVERSE, REVERSE_QUESTION, null, null,
                "反问与岗位理解", difficulty));
        return drafts;
    }

    private void addFallback(List<QuestionDraft> drafts, Set<String> existing,
                             QuestionDraft draft) {
        if (existing.add(normalize(draft.question()))) {
            drafts.add(draft);
        }
    }

    // ---------------------------------------------------------------- 追问决策

    private Decision safeDecide(MockInterviewSession session,
                                MockInterviewQuestion question,
                                List<InterviewFollowUpVO> followups,
                                boolean hasNext,
                                Job job,
                                String resumeText) {
        try {
            String raw = aiClient.chatJson(List.of(
                    new AiClient.Message("system", DECIDE_SYSTEM_PROMPT),
                    new AiClient.Message("user", buildDecidePrompt(session, question, followups,
                            hasNext, job, resumeText))), 0.5);
            Decision decision = parseDecision(raw);
            return normalizeDecision(decision, hasNext, followups.size());
        }
        catch (Exception ex) {
            log.warn("模拟面试追问决策失败：session={}，question={}，{}",
                    session.getId(), question.getId(), ex.getMessage());
            return new Decision(hasNext ? ACTION_NEXT : ACTION_CLOSE,
                    hasNext ? "好的，我们进入下一题。"
                            : "好的，我的问题问完了，你有什么想问我的吗？",
                    null, null);
        }
    }

    private String buildDecidePrompt(MockInterviewSession session,
                                     MockInterviewQuestion question,
                                     List<InterviewFollowUpVO> followups,
                                     boolean hasNext,
                                     Job job,
                                     String resumeText) {
        StringBuilder sb = new StringBuilder();
        sb.append("【公司】").append(job.getCompany()).append('\n');
        sb.append("【岗位】").append(job.getTitle()).append('\n');
        sb.append("【当前题目】").append(question.getQuestion()).append('\n');
        if (StringUtils.hasText(question.getBasisQuote())) {
            sb.append("【题目依据】").append(question.getBasisQuote()).append('\n');
        }
        sb.append("【候选人主回答】\n").append(question.getAnswer()).append("\n\n");
        if (!followups.isEmpty()) {
            sb.append("【追问与回答】\n");
            int index = 1;
            for (InterviewFollowUpVO item : followups) {
                sb.append("追问 ").append(index++).append("：").append(item.question()).append('\n');
                sb.append("回答：").append(StringUtils.hasText(item.answer())
                        ? item.answer() : "（未回答）").append('\n');
            }
        }
        sb.append("\n【已追问层数】").append(followups.size())
                .append("（上限 ").append(MAX_FOLLOW_UPS).append("）\n");
        sb.append("【是否还有下一题】").append(hasNext ? "是" : "否").append('\n');
        sb.append("\n请决定下一步。");
        return sb.toString();
    }

    private Decision parseDecision(String raw) {
        try {
            Map<String, Object> root = objectMapper.readValue(raw, MAP_TYPE);
            return new Decision(
                    upper(textOf(root.get("action"))),
                    limit(textOf(root.get("reply")), 300),
                    limit(textOf(root.get("question")), 300),
                    limit(textOf(root.get("anchor")), 200));
        }
        catch (Exception ex) {
            log.warn("模拟面试追问结果解析失败：{}", ex.getMessage());
            throw new BusinessException(50200, "AI 返回格式异常，请重试", HttpStatus.BAD_GATEWAY);
        }
    }

    private Decision normalizeDecision(Decision decision, boolean hasNext, int followUpCount) {
        String action = decision.action();
        if (action == null
                || !Set.of(ACTION_FOLLOW_UP, ACTION_NEXT, ACTION_CLOSE).contains(action)) {
            action = hasNext ? ACTION_NEXT : ACTION_CLOSE;
        }
        if (ACTION_FOLLOW_UP.equals(action)) {
            if (followUpCount >= MAX_FOLLOW_UPS || !StringUtils.hasText(decision.question())) {
                action = hasNext ? ACTION_NEXT : ACTION_CLOSE;
            }
        }
        if (ACTION_CLOSE.equals(action) && hasNext) {
            action = ACTION_NEXT;
        }
        if (ACTION_NEXT.equals(action) && !hasNext) {
            action = ACTION_CLOSE;
        }
        return new Decision(action, decision.reply(), decision.question(), decision.anchor());
    }

    private boolean hasNextMain(MockInterviewSession session, MockInterviewQuestion question) {
        return listQuestions(session.getId()).stream()
                .filter(item -> !TYPE_REVERSE.equals(item.getQuestionType()))
                .anyMatch(item -> item.getSeq() > question.getSeq()
                        && !Q_ANSWERED.equals(item.getStatus())
                        && !Q_SKIPPED.equals(item.getStatus()));
    }

    private boolean isDontKnow(String answer) {
        if (!StringUtils.hasText(answer) || answer.length() < 8) {
            return true;
        }
        return answer.length() <= 30 && DONT_KNOW_PATTERN.matcher(answer).find();
    }

    private void scoreAsync(MockInterviewQuestion question, Job job, String resumeText) {
        if (question.getScoreJson() != null) {
            return;
        }
        Long questionId = question.getId();
        MockInterviewQuestion snapshot = copyForScore(question);
        try {
            interviewExecutor.execute(() -> {
                try {
                    InterviewScoreVO score = requestScore(snapshot, job, resumeText);
                    if (score == null) {
                        return;
                    }
                    MockInterviewQuestion update = new MockInterviewQuestion();
                    update.setId(questionId);
                    update.setScoreJson(writeJson(score));
                    update.setRiskFlags(String.join(",", score.riskFlags() == null
                            ? List.of() : score.riskFlags()));
                    questionMapper.updateById(update);
                }
                catch (Exception ex) {
                    log.warn("模拟面试异步评分失败：question={}，{}", questionId, ex.getMessage());
                }
            });
        }
        catch (Exception ex) {
            // 线程池拒绝时留待 finish 兜底补评
            log.warn("模拟面试评分任务提交失败：question={}，{}", questionId, ex.getMessage());
        }
    }

    private MockInterviewQuestion copyForScore(MockInterviewQuestion question) {
        MockInterviewQuestion copy = new MockInterviewQuestion();
        copy.setId(question.getId());
        copy.setQuestionType(question.getQuestionType());
        copy.setQuestion(question.getQuestion());
        copy.setBasisSource(question.getBasisSource());
        copy.setBasisQuote(question.getBasisQuote());
        copy.setAnswer(question.getAnswer());
        copy.setFollowupsJson(question.getFollowupsJson());
        return copy;
    }

    // ---------------------------------------------------------------- 评分

    private InterviewScoreVO requestScore(MockInterviewQuestion question, Job job,
                                          String resumeText) {
        String raw = aiClient.chatJson(List.of(
                new AiClient.Message("system", SCORE_SYSTEM_PROMPT),
                new AiClient.Message("user", buildScorePrompt(question, job, resumeText))), 0.2);
        return parseScore(raw, question, resumeText);
    }

    private String buildScorePrompt(MockInterviewQuestion question, Job job, String resumeText) {
        List<InterviewFollowUpVO> followups = readFollowUps(question);
        StringBuilder sb = new StringBuilder();
        sb.append("【公司】").append(job.getCompany()).append('\n');
        sb.append("【岗位】").append(job.getTitle()).append('\n');
        sb.append("【JD 摘要】\n").append(limit(trim(job.getJdText()), 2000)).append("\n\n");
        sb.append("【简历摘要】\n").append(limit(resumeText, 4000)).append("\n\n");
        sb.append("【题目类型】").append(question.getQuestionType()).append('\n');
        sb.append("【题目】").append(question.getQuestion()).append('\n');
        if (StringUtils.hasText(question.getBasisQuote())) {
            sb.append("【题目依据】").append(question.getBasisQuote()).append('\n');
        }
        sb.append("【主回答】\n").append(question.getAnswer()).append("\n\n");
        if (!followups.isEmpty()) {
            sb.append("【追问与回答】\n");
            for (InterviewFollowUpVO item : followups) {
                sb.append("追问：").append(item.question()).append('\n');
                sb.append("回答：").append(StringUtils.hasText(item.answer())
                        ? item.answer() : "（未回答）").append('\n');
            }
        }
        sb.append("\n请评分。所有 quote 必须来自上面的回答原文。");
        return sb.toString();
    }

    private InterviewScoreVO parseScore(String raw, MockInterviewQuestion question,
                                        String resumeText) {
        try {
            Map<String, Object> root = objectMapper.readValue(raw, MAP_TYPE);
            String corpus = answerCorpus(question);

            Map<String, Integer> dimensions = new LinkedHashMap<>();
            if (root.get("dimensions") instanceof Map<?, ?> rawDimensions) {
                for (String dimension : DIMENSIONS) {
                    Integer value = intOf(rawDimensions.get(dimension));
                    dimensions.put(dimension, value == null ? null : clamp(value));
                }
            }

            List<InterviewPointVO> highlights = new ArrayList<>();
            if (root.get("highlights") instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String quote = limit(textOf(map.get("quote")), 200);
                    String comment = limit(textOf(map.get("comment")), 300);
                    if (validQuote(corpus, quote) && StringUtils.hasText(comment)) {
                        highlights.add(new InterviewPointVO(quote, comment));
                    }
                }
            }

            List<InterviewIssueVO> issues = new ArrayList<>();
            if (root.get("issues") instanceof List<?> items) {
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String quote = limit(textOf(map.get("quote")), 200);
                    String comment = limit(textOf(map.get("comment")), 300);
                    if (!validQuote(corpus, quote) || !StringUtils.hasText(comment)) {
                        continue;
                    }
                    String severity = upper(textOf(map.get("severity")));
                    if (!Set.of("HIGH", "MEDIUM", "LOW").contains(severity)) {
                        severity = "MEDIUM";
                    }
                    issues.add(new InterviewIssueVO(quote, comment, severity));
                }
            }

            List<String> suggestions = new ArrayList<>();
            if (root.get("suggestions") instanceof List<?> items) {
                for (Object item : items) {
                    String suggestion = limit(textOf(item), 200);
                    if (StringUtils.hasText(suggestion)) {
                        suggestions.add(suggestion);
                    }
                }
            }

            List<String> riskFlags = new ArrayList<>();
            if (root.get("riskFlags") instanceof List<?> items) {
                for (Object item : items) {
                    String flag = upper(textOf(item));
                    if (flag != null && RISK_FLAGS.contains(flag) && !riskFlags.contains(flag)) {
                        riskFlags.add(flag);
                    }
                }
            }

            Double confidence = doubleOf(root.get("confidence"));
            if (confidence != null) {
                confidence = Math.max(0, Math.min(confidence, 1));
            }

            riskFlags = appendExaggeration(riskFlags, question, resumeText);

            boolean empty = dimensions.values().stream().allMatch(value -> value == null)
                    && highlights.isEmpty() && issues.isEmpty() && suggestions.isEmpty();
            if (empty) {
                return null;
            }
            return new InterviewScoreVO(dimensions, highlights, issues, suggestions,
                    riskFlags, confidence);
        }
        catch (Exception ex) {
            log.warn("模拟面试评分解析失败：question={}，{}",
                    question.getId(), ex.getMessage());
            return null;
        }
    }

    private List<String> appendExaggeration(List<String> riskFlags,
                                            MockInterviewQuestion question,
                                            String resumeText) {
        String type = question.getQuestionType();
        if (!Set.of(TYPE_INTRO, TYPE_PROJECT, TYPE_BEHAVIORAL, TYPE_PRESSURE).contains(type)) {
            return riskFlags;
        }
        String corpus = answerCorpus(question);
        Set<String> answerNumbers = extractNumbers(corpus);
        Set<String> resumeNumbers = extractNumbers(resumeText);
        boolean added = answerNumbers.stream().anyMatch(number -> !resumeNumbers.contains(number));
        if (added && !riskFlags.contains(RISK_EXAGGERATED)) {
            List<String> updated = new ArrayList<>(riskFlags);
            updated.add(RISK_EXAGGERATED);
            return updated;
        }
        return riskFlags;
    }

    private Set<String> extractNumbers(String text) {
        Set<String> values = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return values;
        }
        Matcher numberMatcher = NUMBER_PATTERN.matcher(text);
        while (numberMatcher.find()) {
            values.add(numberMatcher.group());
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(text);
        while (yearMatcher.find()) {
            values.add(yearMatcher.group());
        }
        return values;
    }

    // ---------------------------------------------------------------- 反问

    private String requestReverseReply(Job job, MockInterviewQuestion reverse, String question) {
        try {
            String userPrompt = "【公司】" + job.getCompany() + "\n【岗位】" + job.getTitle()
                    + "\n【JD 摘要】\n" + limit(trim(job.getJdText()), 1500)
                    + "\n\n【候选人的问题】\n" + question;
            String raw = aiClient.chatJson(List.of(
                    new AiClient.Message("system", REVERSE_SYSTEM_PROMPT),
                    new AiClient.Message("user", userPrompt)), 0.5);
            Map<String, Object> root = objectMapper.readValue(raw, MAP_TYPE);
            String reply = limit(textOf(root.get("reply")), 500);
            if (!StringUtils.hasText(reply)) {
                throw new BusinessException(50200, "AI 返回格式异常，请重试", HttpStatus.BAD_GATEWAY);
            }
            return reply;
        }
        catch (BusinessException ex) {
            throw ex;
        }
        catch (Exception ex) {
            log.warn("模拟面试反问失败：{}", ex.getMessage());
            throw new BusinessException(50200, "AI 服务暂不可用，请稍后重试",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    // ---------------------------------------------------------------- 报告

    private InterviewReportVO buildReport(MockInterviewSession session, Job job,
                                          List<MockInterviewQuestion> questions) {
        List<MockInterviewQuestion> mains = questions.stream()
                .filter(question -> !TYPE_REVERSE.equals(question.getQuestionType()))
                .toList();

        List<InterviewQuestionVO> questionVOs = mains.stream()
                .map(question -> toQuestionVO(question, true))
                .toList();

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        for (String dimension : DIMENSIONS) {
            List<Integer> values = mains.stream()
                    .map(question -> readScore(question.getScoreJson()))
                    .filter(score -> score != null && score.dimensions() != null)
                    .map(score -> score.dimensions().get(dimension))
                    .filter(value -> value != null)
                    .toList();
            dimensions.put(dimension, values.isEmpty() ? null
                    : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0)));
        }

        int totalScore = weightedTotal(mains);

        List<String> highlights = new ArrayList<>();
        List<InterviewIssueVO> allIssues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        Map<String, List<String>> riskQuotes = new LinkedHashMap<>();

        for (MockInterviewQuestion question : mains) {
            InterviewScoreVO score = readScore(question.getScoreJson());
            if (score == null) {
                continue;
            }
            if (score.highlights() != null) {
                for (InterviewPointVO point : score.highlights()) {
                    if (highlights.size() < 6) {
                        highlights.add("「" + point.quote() + "」" + point.comment());
                    }
                }
            }
            if (score.issues() != null) {
                allIssues.addAll(score.issues());
            }
            if (score.suggestions() != null) {
                for (String suggestion : score.suggestions()) {
                    if (!suggestions.contains(suggestion) && suggestions.size() < 6) {
                        suggestions.add(suggestion);
                    }
                }
            }
            if (score.riskFlags() != null) {
                String quote = firstQuote(score);
                for (String flag : score.riskFlags()) {
                    List<String> quotes = riskQuotes.computeIfAbsent(flag, key -> new ArrayList<>());
                    boolean duplicate = StringUtils.hasText(quote) && quotes.stream()
                            .anyMatch(existing -> normalize(existing).equals(normalize(quote)));
                    if (StringUtils.hasText(quote) && quotes.size() < 3 && !duplicate) {
                        quotes.add(quote);
                    }
                }
            }
        }

        List<InterviewReportVO.InterviewRiskVO> risks = riskQuotes.entrySet().stream()
                .map(entry -> new InterviewReportVO.InterviewRiskVO(entry.getKey(), entry.getValue()))
                .toList();

        List<InterviewIssueVO> topImprovements = allIssues.stream()
                .sorted(Comparator.comparingInt(issue -> severityWeight(issue.severity())))
                .filter(issue -> issue.comment() != null)
                .collect(Collectors.toMap(
                        InterviewIssueVO::comment,
                        issue -> issue,
                        (left, right) -> severityWeight(left.severity()) <= severityWeight(right.severity())
                                ? left : right,
                        LinkedHashMap::new))
                .values().stream()
                .limit(5)
                .toList();

        boolean hasAnyScore = mains.stream()
                .anyMatch(question -> readScore(question.getScoreJson()) != null);
        SummaryResult summary = hasAnyScore
                ? requestSummary(job, mains, dimensions, totalScore, topImprovements)
                : new SummaryResult("本次没有产生有效评分，建议至少完整回答一道题后再复盘。", List.of());
        if (summary.suggestions() != null && !summary.suggestions().isEmpty()) {
            suggestions = summary.suggestions();
        }

        InterviewReportVO.InterviewReverseReportVO reverseReport = buildReverseReport(questions);
        List<InterviewReportVO.InterviewTrendVO> trend = buildTrend(session, totalScore);

        return new InterviewReportVO(
                totalScore,
                dimensions,
                questionVOs,
                highlights,
                risks,
                topImprovements,
                suggestions,
                summary.summary(),
                reverseReport,
                trend);
    }

    private int weightedTotal(List<MockInterviewQuestion> questions) {
        double weighted = 0;
        double weightSum = 0;
        for (MockInterviewQuestion question : questions) {
            InterviewScoreVO score = readScore(question.getScoreJson());
            if (score == null || score.dimensions() == null) {
                continue;
            }
            List<Integer> values = score.dimensions().values().stream()
                    .filter(value -> value != null).toList();
            if (values.isEmpty()) {
                continue;
            }
            double questionScore = values.stream().mapToInt(Integer::intValue).average().orElse(0);
            int weight = TYPE_WEIGHTS.getOrDefault(question.getQuestionType(), 10);
            weighted += questionScore * weight;
            weightSum += weight;
        }
        return weightSum == 0 ? 0 : (int) Math.round(weighted / weightSum);
    }

    private String firstQuote(InterviewScoreVO score) {
        if (score.issues() != null && !score.issues().isEmpty()) {
            return score.issues().get(0).quote();
        }
        if (score.highlights() != null && !score.highlights().isEmpty()) {
            return score.highlights().get(0).quote();
        }
        return null;
    }

    private int severityWeight(String severity) {
        return switch (severity == null ? "" : severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private SummaryResult requestSummary(Job job,
                                         List<MockInterviewQuestion> questions,
                                         Map<String, Integer> dimensions,
                                         int totalScore,
                                         List<InterviewIssueVO> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("【公司】").append(job.getCompany()).append('\n');
        sb.append("【岗位】").append(job.getTitle()).append('\n');
        sb.append("【完成题数】").append(questions.size()).append('\n');
        sb.append("【总分】").append(totalScore).append('\n');
        sb.append("【维度分】").append(dimensions).append('\n');
        sb.append("【主要问题】\n");
        for (InterviewIssueVO issue : issues) {
            sb.append("- ").append(issue.comment()).append("（").append(issue.severity())
                    .append("）\n");
        }
        if (issues.isEmpty()) {
            sb.append("- 暂无明显问题\n");
        }

        try {
            String raw = aiClient.chatJson(List.of(
                    new AiClient.Message("system", SUMMARY_SYSTEM_PROMPT),
                    new AiClient.Message("user", sb.toString())), 0.3);
            Map<String, Object> root = objectMapper.readValue(raw, MAP_TYPE);
            String summary = limit(textOf(root.get("summary")), 500);
            List<String> suggestions = new ArrayList<>();
            if (root.get("suggestions") instanceof List<?> items) {
                for (Object item : items) {
                    String suggestion = limit(textOf(item), 200);
                    if (StringUtils.hasText(suggestion) && suggestions.size() < 5) {
                        suggestions.add(suggestion);
                    }
                }
            }
            if (StringUtils.hasText(summary)) {
                return new SummaryResult(summary, suggestions);
            }
        }
        catch (Exception ex) {
            log.warn("模拟面试总评生成失败，使用本地模板：{}", ex.getMessage());
        }

        boolean hasScore = questions.stream()
                .anyMatch(question -> readScore(question.getScoreJson()) != null);
        String summary = fallbackSummary(hasScore, totalScore, questions.size(), issues);
        List<String> suggestions = issues.stream()
                .map(InterviewIssueVO::comment)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
        return new SummaryResult(summary, suggestions);
    }

    private String fallbackSummary(boolean hasScore, int totalScore, int questionCount,
                                   List<InterviewIssueVO> issues) {
        if (questionCount == 0 || !hasScore) {
            return "本次没有产生有效评分，建议至少完整回答一道题后再复盘。";
        }
        String base = "本次模拟面试完成 " + questionCount + " 题，总分 " + totalScore + " 分。";
        if (issues.isEmpty()) {
            return base + "整体表达较完整，建议继续保持结构化表达并补充量化结果。";
        }
        return base + "主要短板：" + issues.get(0).comment() + "。";
    }

    private InterviewReportVO.InterviewReverseReportVO buildReverseReport(
            List<MockInterviewQuestion> questions) {
        MockInterviewQuestion reverse = questions.stream()
                .filter(question -> TYPE_REVERSE.equals(question.getQuestionType()))
                .findFirst().orElse(null);
        List<InterviewFollowUpVO> items = reverse == null ? List.of() : readFollowUps(reverse);
        List<InterviewReportVO.InterviewReverseReportVO.Item> reportItems = items.stream()
                .map(item -> new InterviewReportVO.InterviewReverseReportVO.Item(
                        item.question(), item.answer()))
                .toList();
        String comment;
        if (reportItems.isEmpty()) {
            comment = "本次没有提问。真实面试中建议准备 2 个与岗位或团队相关的问题。";
        }
        else {
            comment = "本次提问 " + reportItems.size()
                    + " 个，建议优先关注团队分工、业务挑战与成长路径，避免只问薪资福利。";
        }
        return new InterviewReportVO.InterviewReverseReportVO(reportItems, comment);
    }

    private List<InterviewReportVO.InterviewTrendVO> buildTrend(MockInterviewSession session,
                                                                int currentScore) {
        List<MockInterviewSession> previous = sessionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewSession>()
                        .eq(MockInterviewSession::getUserId, session.getUserId())
                        .eq(MockInterviewSession::getJobId, session.getJobId())
                        .eq(MockInterviewSession::getStatus, STATUS_COMPLETED)
                        .isNotNull(MockInterviewSession::getTotalScore)
                        .orderByDesc(MockInterviewSession::getFinishedAt)
                        .last("limit 6"));

        List<InterviewReportVO.InterviewTrendVO> points = new ArrayList<>();
        for (MockInterviewSession item : previous) {
            points.add(new InterviewReportVO.InterviewTrendVO(
                    trendDate(item.getFinishedAt() == null ? item.getCreatedAt() : item.getFinishedAt()),
                    item.getTotalScore()));
        }
        points.add(new InterviewReportVO.InterviewTrendVO(
                trendDate(LocalDateTime.now()), currentScore));
        return points.stream()
                .filter(point -> point.totalScore() != null)
                .sorted(Comparator.comparing(InterviewReportVO.InterviewTrendVO::date))
                .limit(6)
                .toList();
    }

    private String trendDate(LocalDateTime time) {
        return time == null ? "" : time.format(TREND_DATE);
    }

    // ---------------------------------------------------------------- 会话与题目装载

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

    private MockInterviewSession requireSession(Long id, Long userId) {
        MockInterviewSession session = sessionMapper.selectById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.notFound("模拟面试记录不存在");
        }
        return session;
    }

    private void requireInProgress(MockInterviewSession session) {
        if (STATUS_COMPLETED.equals(session.getStatus())) {
            throw BusinessException.conflict("本次模拟面试已结束");
        }
        if (STATUS_ABANDONED.equals(session.getStatus())) {
            throw BusinessException.conflict("本次模拟面试已放弃");
        }
    }

    private List<MockInterviewQuestion> listQuestions(Long sessionId) {
        return questionMapper.selectList(new LambdaQueryWrapper<MockInterviewQuestion>()
                .eq(MockInterviewQuestion::getSessionId, sessionId)
                .orderByAsc(MockInterviewQuestion::getSeq));
    }

    private MockInterviewQuestion findCurrentMain(List<MockInterviewQuestion> questions) {
        return questions.stream()
                .filter(question -> !TYPE_REVERSE.equals(question.getQuestionType()))
                .filter(question -> !Q_ANSWERED.equals(question.getStatus())
                        && !Q_SKIPPED.equals(question.getStatus()))
                .min(Comparator.comparing(MockInterviewQuestion::getSeq))
                .orElse(null);
    }

    private String pendingFollowUp(MockInterviewQuestion question) {
        List<InterviewFollowUpVO> followups = readFollowUps(question);
        if (followups.isEmpty()) {
            return null;
        }
        InterviewFollowUpVO last = followups.get(followups.size() - 1);
        return StringUtils.hasText(last.answer()) ? null : last.question();
    }

    private InterviewProgressVO progressOf(Long sessionId) {
        return progressOf(listQuestions(sessionId));
    }

    private InterviewProgressVO progressOf(List<MockInterviewQuestion> questions) {
        List<MockInterviewQuestion> mains = questions.stream()
                .filter(question -> !TYPE_REVERSE.equals(question.getQuestionType()))
                .toList();
        int answered = (int) mains.stream()
                .filter(question -> Q_ANSWERED.equals(question.getStatus())
                        || Q_SKIPPED.equals(question.getStatus()))
                .count();
        MockInterviewQuestion current = findCurrentMain(questions);
        int followUpCount = current == null ? 0 : current.getFollowupCount();
        return new InterviewProgressVO(answered, mains.size(), followUpCount);
    }

    private InterviewQuestionVO toQuestionVO(MockInterviewQuestion question, boolean includeScore) {
        List<String> riskFlags = StringUtils.hasText(question.getRiskFlags())
                ? List.of(question.getRiskFlags().split(","))
                : List.of();
        InterviewScoreVO score = includeScore ? readScore(question.getScoreJson()) : null;
        return new InterviewQuestionVO(
                question.getId(),
                question.getSeq(),
                question.getQuestionType(),
                question.getQuestion(),
                question.getBasisSource(),
                question.getBasisQuote(),
                question.getFocus(),
                question.getDifficulty(),
                question.getStatus(),
                question.getAnswer(),
                readFollowUps(question),
                question.getAnswerFeedback(),
                riskFlags,
                score);
    }

    // ---------------------------------------------------------------- JSON 与文本工具

    private List<InterviewFollowUpVO> readFollowUps(MockInterviewQuestion question) {
        if (!StringUtils.hasText(question.getFollowupsJson())) {
            return new ArrayList<>();
        }
        try {
            List<InterviewFollowUpVO> items = objectMapper.readValue(
                    question.getFollowupsJson(), FOLLOW_UP_LIST);
            return new ArrayList<>(items == null ? List.of() : items);
        }
        catch (Exception ex) {
            log.warn("模拟面试追问记录解析失败：question={}，{}",
                    question.getId(), ex.getMessage());
            return new ArrayList<>();
        }
    }

    private InterviewScoreVO readScore(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewScoreVO.class);
        }
        catch (Exception ex) {
            log.warn("模拟面试评分记录解析失败：{}", ex.getMessage());
            return null;
        }
    }

    private InterviewReportVO readReport(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewReportVO.class);
        }
        catch (Exception ex) {
            log.warn("模拟面试报告解析失败：{}", ex.getMessage());
            return null;
        }
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private String answerCorpus(MockInterviewQuestion question) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(question.getAnswer())) {
            sb.append(question.getAnswer());
        }
        for (InterviewFollowUpVO item : readFollowUps(question)) {
            if (StringUtils.hasText(item.answer())) {
                sb.append('\n').append(item.answer());
            }
        }
        return sb.toString();
    }

    private boolean validQuote(String corpus, String quote) {
        return StringUtils.hasText(quote) && quote.length() >= 2
                && containsNormalized(corpus, quote);
    }

    private boolean containsNormalized(String source, String quote) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(quote)) {
            return false;
        }
        String normalizedSource = normalize(source);
        String normalizedQuote = normalize(quote);
        return normalizedQuote.length() >= 4 && normalizedSource.contains(normalizedQuote);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeType(String value) {
        String type = upper(value);
        if ("TECHNICAL".equals(type) || "BEHAVIORAL".equals(type)) {
            return type;
        }
        return "COMPREHENSIVE";
    }

    private String normalizeDifficulty(String value) {
        String difficulty = upper(value);
        if (difficulty == null) {
            return "MEDIUM";
        }
        return Set.of("EASY", "MEDIUM", "HARD").contains(difficulty) ? difficulty : "MEDIUM";
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "TECHNICAL" -> "技术面";
            case "BEHAVIORAL" -> "行为面";
            default -> "综合面";
        };
    }

    private String difficultyLabel(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> "基础";
            case "HARD" -> "挑战";
            default -> "进阶";
        };
    }

    private String defaultOpening(String type) {
        return switch (type) {
            case "TECHNICAL" -> "你好，我是本次技术面试的面试官，今天主要围绕岗位相关的技术和项目聊一聊。";
            case "BEHAVIORAL" -> "你好，我是本次面试的面试官，今天主要想了解你的经历和做事方式。";
            default -> "你好，我是本次面试的面试官，今天大概 20 分钟，我们先从自我介绍开始。";
        };
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private String limit(String text, int max) {
        return text == null ? null : text.length() > max ? text.substring(0, max) : text;
    }

    private String upper(String text) {
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private String textOf(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double doubleOf(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ---------------------------------------------------------------- 内部记录

    private record QuestionDraft(
            String type,
            String question,
            String basisSource,
            String basisQuote,
            String focus,
            String difficulty) {
    }

    private record Generated(String opening, List<QuestionDraft> questions) {
    }

    private record Decision(String action, String reply, String question, String anchor) {
    }

    private record SummaryResult(String summary, List<String> suggestions) {
    }

}
