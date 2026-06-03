package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamStudentService {
    private static final String ATTEMPT_DRAFT = "DRAFT";
    private static final String ATTEMPT_ENDED = "ENDED";

    private final ExamRepository examRepository;
    private final ExamQuestionRelRepository examQuestionRelRepository;
    private final QuestionRepository questionRepository;
    private final StudentExamRecordRepository studentExamRecordRepository;
    private final StudentRepository studentRepository;
    private final ScoreRepository scoreRepository;
    private final StudentExamAttemptRepository studentExamAttemptRepository;
    private final StudentCourseClassRepository studentCourseClassRepository;
    private final CourseRepository courseRepository;

    public ExamStudentService(ExamRepository examRepository,
                              ExamQuestionRelRepository examQuestionRelRepository,
                              QuestionRepository questionRepository,
                              StudentExamRecordRepository studentExamRecordRepository,
                              StudentRepository studentRepository,
                              ScoreRepository scoreRepository,
                              StudentExamAttemptRepository studentExamAttemptRepository,
                              StudentCourseClassRepository studentCourseClassRepository,
                              CourseRepository courseRepository) {
        this.examRepository = examRepository;
        this.examQuestionRelRepository = examQuestionRelRepository;
        this.questionRepository = questionRepository;
        this.studentExamRecordRepository = studentExamRecordRepository;
        this.studentRepository = studentRepository;
        this.scoreRepository = scoreRepository;
        this.studentExamAttemptRepository = studentExamAttemptRepository;
        this.studentCourseClassRepository = studentCourseClassRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public DataResponse getAvailableExams() {
        Integer personId = CommonMethod.getPersonId();
        List<Exam> exams = examRepository.findOpenExams();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Exam exam : exams) {
            if (!canStudentAccessExam(personId, exam)) {
                continue;
            }
            ensureLegacyAttempt(personId, exam);
            closeExpiredDrafts(exam.getExamId());
            Map<String, Object> map = examToStudentMap(exam, personId);
            dataList.add(map);
        }
        return CommonMethod.getReturnData(dataList);
    }

    @Transactional
    public DataResponse startExam(Integer examId) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        Exam exam = examOp.get();
        if (!canStudentAccessExam(personId, exam)) {
            return CommonMethod.getReturnMessageError("您尚未加入该试卷对应课程，不能进入考试");
        }
        if (parseTime(exam.getStartTime()) == null || parseTime(exam.getEndTime()) == null) {
            return CommonMethod.getReturnMessageError("试卷时间格式异常，请联系教师重新保存试卷时间");
        }
        closeExpiredDrafts(examId);
        if (!"OPEN".equals(exam.getStatus())) {
            return CommonMethod.getReturnMessageError("考试未开放");
        }
        if (isBeforeStart(exam)) {
            return CommonMethod.getReturnMessageError("考试未开始");
        }
        if (isAfterEnd(exam)) {
            return CommonMethod.getReturnMessageError("考试已结束");
        }
        Student student = getCurrentStudent(personId);
        if (student == null) {
            return CommonMethod.getReturnMessageError("学生信息不存在");
        }
        StudentExamAttempt attempt = studentExamAttemptRepository
                .findByStudentPersonIdAndExamExamId(personId, examId)
                .orElseGet(() -> createAttempt(student, exam));
        if (ATTEMPT_ENDED.equals(attempt.getStatus())) {
            return CommonMethod.getReturnMessageError("该试卷已结束，不能继续作答");
        }
        attempt.setLastSaveTime(now());
        studentExamAttemptRepository.save(attempt);
        return CommonMethod.getReturnData(buildExamSessionData(exam, attempt));
    }

    @Transactional
    public DataResponse saveDraft(Integer examId, List<?> answers) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        Exam exam = examOp.get();
        if (!canStudentAccessExam(personId, exam)) {
            return CommonMethod.getReturnMessageError("您尚未加入该试卷对应课程，不能保存草稿");
        }
        closeExpiredDrafts(examId);
        Optional<StudentExamAttempt> attemptOp = studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(personId, examId);
        if (attemptOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("请先进入考试");
        }
        StudentExamAttempt attempt = attemptOp.get();
        if (ATTEMPT_ENDED.equals(attempt.getStatus())) {
            return CommonMethod.getReturnMessageError("该试卷已结束，不能保存草稿");
        }
        if (isAfterEnd(exam)) {
            finalizeAttempt(attempt, true);
            return CommonMethod.getReturnMessageError("考试已结束，草稿已自动提交");
        }
        upsertAnswers(attempt.getStudent(), exam, answers, false);
        attempt.setStatus(ATTEMPT_DRAFT);
        attempt.setLastSaveTime(now());
        studentExamAttemptRepository.save(attempt);
        return CommonMethod.getReturnData(buildExamSessionData(exam, attempt), "草稿已保存");
    }

    @Transactional
    public DataResponse submitExam(Integer examId, List<?> answers) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        Exam exam = examOp.get();
        if (!canStudentAccessExam(personId, exam)) {
            return CommonMethod.getReturnMessageError("您尚未加入该试卷对应课程，不能提交考试");
        }
        closeExpiredDrafts(examId);
        Optional<StudentExamAttempt> attemptOp = studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(personId, examId);
        if (attemptOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("请先进入考试");
        }
        StudentExamAttempt attempt = attemptOp.get();
        if (ATTEMPT_ENDED.equals(attempt.getStatus())) {
            return CommonMethod.getReturnMessageError("该试卷已结束，不能重复提交");
        }
        upsertAnswers(attempt.getStudent(), exam, answers, false);
        finalizeAttempt(attempt, false);
        return CommonMethod.getReturnData(buildResultData(examId, personId), "提交成功");
    }

    @Transactional
    public DataResponse getExamResult(Integer examId) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        if (!canStudentAccessExam(personId, examOp.get())) {
            return CommonMethod.getReturnMessageError("您尚未加入该试卷对应课程，不能查看结果");
        }
        closeExpiredDrafts(examId);
        Optional<StudentExamAttempt> attemptOp = studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(personId, examId);
        if (attemptOp.isEmpty() || !ATTEMPT_ENDED.equals(attemptOp.get().getStatus())) {
            return CommonMethod.getReturnMessageError("该试卷尚未结束");
        }
        return CommonMethod.getReturnData(buildResultData(examId, personId));
    }

    @Transactional
    public DataResponse getExamQuestions(Integer examId) {
        return startExam(examId);
    }

    @Transactional
    public DataResponse getMyScores() {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        List<Map<String, Object>> courseScores = new ArrayList<>();
        for (Score score : scoreRepository.findByStudentPersonId(personId)) {
            Map<String, Object> map = new HashMap<>();
            map.put("scoreId", score.getScoreId());
            map.put("mark", score.getMark());
            map.put("ranking", score.getRanking());
            if (score.getCourse() != null) {
                map.put("courseId", score.getCourse().getCourseId());
                map.put("courseNum", score.getCourse().getNum());
                map.put("courseName", score.getCourse().getName());
            }
            courseScores.add(map);
        }

        List<Map<String, Object>> examScores = new ArrayList<>();
        for (StudentExamRecord record : studentExamRecordRepository.findByStudentPersonIdIn(List.of(personId))) {
            ensureLegacyAttempt(personId, record.getExam());
        }
        for (StudentExamAttempt attempt : studentExamAttemptRepository.findByStudentPersonId(personId)) {
            closeExpiredDrafts(attempt.getExam().getExamId());
            if (!ATTEMPT_ENDED.equals(attempt.getStatus())) {
                continue;
            }
            examScores.add(attemptSummary(attempt));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("courseScores", courseScores);
        result.put("examScores", examScores);
        return CommonMethod.getReturnData(result);
    }

    @Transactional
    public DataResponse getMyCourseClasses() {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        List<Map<String, Object>> result = studentCourseClassRepository.findByStudentPersonId(personId)
                .stream()
                .map(this::courseClassToMap)
                .collect(Collectors.toList());
        return CommonMethod.getReturnData(result);
    }

    @Transactional
    public DataResponse saveMyCourseClass(Integer id, Integer courseId, String className) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        if (courseId == null) {
            return CommonMethod.getReturnMessageError("请选择课序号。错误设置课序号会导致试卷和成绩不可用");
        }
        if (className == null || className.isBlank()) {
            className = studentRepository.findById(personId).map(Student::getClassName).orElse("");
        }
        Optional<Student> studentOp = studentRepository.findById(personId);
        if (studentOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("学生信息不存在");
        }
        Optional<Course> courseOp = courseRepository.findById(courseId);
        if (courseOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("课程不存在。错误设置课序号会导致试卷和成绩不可用");
        }
        StudentCourseClass enrollment;
        if (id == null) {
            enrollment = studentCourseClassRepository
                    .findByStudentPersonIdAndCourseCourseId(personId, courseId)
                    .orElseGet(StudentCourseClass::new);
        } else {
            Optional<StudentCourseClass> enrollmentOp = studentCourseClassRepository.findById(id);
            if (enrollmentOp.isEmpty() || enrollmentOp.get().getStudent() == null
                    || !personId.equals(enrollmentOp.get().getStudent().getPersonId())) {
                return CommonMethod.getReturnMessageError("课程绑定不存在或无权修改");
            }
            Optional<StudentCourseClass> sameCourse = studentCourseClassRepository.findByStudentPersonIdAndCourseCourseId(personId, courseId);
            if (sameCourse.isPresent() && !sameCourse.get().getId().equals(id)) {
                return CommonMethod.getReturnMessageError("你已绑定该课序号，不能重复设置");
            }
            enrollment = enrollmentOp.get();
        }
        enrollment.setStudent(studentOp.get());
        enrollment.setCourse(courseOp.get());
        enrollment.setClassName(className.trim());
        studentCourseClassRepository.save(enrollment);
        return CommonMethod.getReturnData(courseClassToMap(enrollment), "课程绑定已保存。请确认课序号正确，否则对应成绩可能无效");
    }

    @Transactional
    public DataResponse deleteMyCourseClass(Integer id) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        Optional<StudentCourseClass> enrollmentOp = studentCourseClassRepository.findById(id);
        if (enrollmentOp.isEmpty() || enrollmentOp.get().getStudent() == null
                || !personId.equals(enrollmentOp.get().getStudent().getPersonId())) {
            return CommonMethod.getReturnMessageError("课程绑定不存在或无权删除");
        }
        studentCourseClassRepository.deleteById(id);
        return CommonMethod.getReturnMessageOK("课程绑定已删除。删除错误课序号可能导致成绩和试卷不可见");
    }

    @Transactional
    public void closeExpiredDrafts(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty() || !isAfterEnd(examOp.get())) {
            return;
        }
        for (StudentExamAttempt attempt : studentExamAttemptRepository.findByExamExamIdAndStatus(examId, ATTEMPT_DRAFT)) {
            finalizeAttempt(attempt, true);
        }
    }

    private StudentExamAttempt createAttempt(Student student, Exam exam) {
        StudentExamAttempt attempt = new StudentExamAttempt();
        attempt.setStudent(student);
        attempt.setExam(exam);
        attempt.setStatus(ATTEMPT_DRAFT);
        attempt.setStartTime(now());
        attempt.setLastSaveTime(now());
        attempt.setAutoEnded(0);
        return studentExamAttemptRepository.save(attempt);
    }

    private void ensureLegacyAttempt(Integer personId, Exam exam) {
        if (personId == null || exam == null) {
            return;
        }
        if (studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(personId, exam.getExamId()).isPresent()) {
            return;
        }
        List<StudentExamRecord> records = studentExamRecordRepository.findByStudentPersonIdAndExamExamId(personId, exam.getExamId());
        if (records.isEmpty()) {
            return;
        }
        Optional<Student> studentOp = studentRepository.findById(personId);
        if (studentOp.isEmpty()) {
            return;
        }
        StudentExamAttempt attempt = new StudentExamAttempt();
        attempt.setStudent(studentOp.get());
        attempt.setExam(exam);
        attempt.setStatus(ATTEMPT_ENDED);
        attempt.setStartTime(records.get(0).getSubmitTime());
        attempt.setLastSaveTime(records.get(0).getSubmitTime());
        attempt.setSubmitTime(records.get(0).getSubmitTime());
        attempt.setAutoEnded(0);
        studentExamAttemptRepository.save(attempt);
    }

    private void upsertAnswers(Student student, Exam exam, List<?> answers, boolean gradeNow) {
        Map<Integer, String> answerMap = new HashMap<>();
        for (Object answerObj : answers == null ? List.of() : answers) {
            if (!(answerObj instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            Integer questionId = CommonMethod.getInteger(map, "questionId");
            String answer = CommonMethod.getString(map, "answer");
            if (questionId != null) {
                answerMap.put(questionId, answer);
            }
        }
        for (ExamQuestionRel rel : examQuestionRelRepository.findByExamExamIdOrderBySortOrderAsc(exam.getExamId())) {
            Question question = rel.getQuestion();
            if (!answerMap.containsKey(question.getQuestionId()) && !gradeNow) {
                continue;
            }
            StudentExamRecord record = studentExamRecordRepository
                    .findByStudentPersonIdAndExamExamIdAndQuestionQuestionId(student.getPersonId(), exam.getExamId(), question.getQuestionId())
                    .orElseGet(() -> newRecord(student, exam, question));
            record.setAnswer(answerMap.getOrDefault(question.getQuestionId(), record.getAnswer() == null ? "" : record.getAnswer()));
            if (gradeNow) {
                applyScore(record);
            }
            studentExamRecordRepository.save(record);
        }
    }

    private StudentExamRecord newRecord(Student student, Exam exam, Question question) {
        StudentExamRecord record = new StudentExamRecord();
        record.setStudent(student);
        record.setExam(exam);
        record.setQuestion(question);
        record.setAnswer("");
        record.setScore(0);
        record.setGraded(0);
        return record;
    }

    private void finalizeAttempt(StudentExamAttempt attempt, boolean autoEnded) {
        upsertAnswers(attempt.getStudent(), attempt.getExam(), List.of(), true);
        attempt.setStatus(ATTEMPT_ENDED);
        attempt.setSubmitTime(attempt.getSubmitTime() == null ? now() : attempt.getSubmitTime());
        attempt.setLastSaveTime(now());
        attempt.setAutoEnded(autoEnded ? 1 : 0);
        studentExamAttemptRepository.save(attempt);

        List<StudentExamRecord> records = studentExamRecordRepository
                .findByStudentPersonIdAndExamExamId(attempt.getStudent().getPersonId(), attempt.getExam().getExamId());
        if (records.stream().allMatch(r -> r.getGraded() == 1)) {
            int total = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
            syncToScoreTable(attempt.getStudent().getPersonId(), attempt.getExam(), total);
        }
    }

    private void applyScore(StudentExamRecord record) {
        Question question = record.getQuestion();
        if ("CHOICE".equals(question.getQuestionType())) {
            boolean correct = record.getAnswer() != null && record.getAnswer().equalsIgnoreCase(question.getAnswer());
            record.setScore(correct ? question.getScore() : 0);
            record.setGraded(1);
        } else {
            record.setScore(record.getScore() == null ? 0 : record.getScore());
            record.setGraded(0);
        }
        record.setSubmitTime(now());
    }

    private Map<String, Object> buildExamSessionData(Exam exam, StudentExamAttempt attempt) {
        Map<String, Object> data = new HashMap<>();
        data.put("attemptId", attempt.getAttemptId());
        data.put("examId", exam.getExamId());
        data.put("title", exam.getTitle());
        data.put("startTime", exam.getStartTime());
        data.put("endTime", exam.getEndTime());
        data.put("studentExamStatus", "草稿");
        data.put("remainingSeconds", remainingSeconds(exam));
        Map<Integer, String> savedAnswers = studentExamRecordRepository
                .findByStudentPersonIdAndExamExamId(attempt.getStudent().getPersonId(), exam.getExamId())
                .stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getQuestionId(), r -> r.getAnswer() == null ? "" : r.getAnswer(), (a, b) -> b));
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (ExamQuestionRel rel : examQuestionRelRepository.findByExamExamIdOrderBySortOrderAsc(exam.getExamId())) {
            Question q = rel.getQuestion();
            Map<String, Object> m = questionToStudentMap(q, rel.getSortOrder());
            m.put("answer", savedAnswers.getOrDefault(q.getQuestionId(), ""));
            questionList.add(m);
        }
        data.put("questions", questionList);
        return data;
    }

    private Map<String, Object> buildResultData(Integer examId, Integer personId) {
        List<StudentExamRecord> records = studentExamRecordRepository.findByStudentPersonIdAndExamExamId(personId, examId);
        int totalScore = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
        boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
        Map<String, Object> result = new HashMap<>();
        if (!records.isEmpty()) {
            StudentExamAttempt attempt = studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(personId, examId).orElse(null);
            result.put("attemptId", attempt == null ? null : attempt.getAttemptId());
            result.put("examId", examId);
            result.put("examTitle", records.get(0).getExam().getTitle());
            result.put("submitTime", attempt == null ? records.get(0).getSubmitTime() : attempt.getSubmitTime());
        }
        result.put("totalScore", totalScore);
        result.put("allGraded", allGraded);
        List<Map<String, Object>> questionResults = new ArrayList<>();
        for (StudentExamRecord record : records) {
            Map<String, Object> map = questionToStudentMap(record.getQuestion(), null);
            map.put("recordId", record.getRecordId());
            map.put("answer", record.getAnswer());
            map.put("score", record.getScore());
            map.put("maxScore", record.getQuestion().getScore());
            map.put("graded", record.getGraded());
            if (allGraded) {
                map.put("standardAnswer", record.getQuestion().getAnswer());
            }
            questionResults.add(map);
        }
        result.put("questions", questionResults);
        return result;
    }

    private Map<String, Object> examToStudentMap(Exam exam, Integer personId) {
        Map<String, Object> map = new HashMap<>();
        map.put("examId", exam.getExamId());
        map.put("title", exam.getTitle());
        map.put("startTime", exam.getStartTime());
        map.put("endTime", exam.getEndTime());
        map.put("status", exam.getStatus());
        map.put("remainingSeconds", remainingSeconds(exam));
        if (exam.getCourse() != null) {
            map.put("courseId", exam.getCourse().getCourseId());
            map.put("courseName", exam.getCourse().getName());
        }
        String studentStatus = "未做";
        Integer attemptId = null;
        if (isBeforeStart(exam)) {
            studentStatus = "未开始";
        } else if (isAfterEnd(exam)) {
            studentStatus = "已结束";
        }
        if (personId != null) {
            Optional<StudentExamAttempt> attemptOp = studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(personId, exam.getExamId());
            if (attemptOp.isPresent()) {
                StudentExamAttempt attempt = attemptOp.get();
                attemptId = attempt.getAttemptId();
                studentStatus = ATTEMPT_ENDED.equals(attempt.getStatus()) ? "已结束" : "草稿";
            }
        }
        map.put("attemptId", attemptId);
        map.put("studentExamStatus", studentStatus);
        map.put("submitted", "已结束".equals(studentStatus));
        return map;
    }

    private Map<String, Object> questionToStudentMap(Question q, Integer sortOrder) {
        Map<String, Object> m = new HashMap<>();
        m.put("questionId", q.getQuestionId());
        m.put("questionType", q.getQuestionType());
        m.put("content", q.getContent());
        m.put("score", q.getScore());
        m.put("sortOrder", sortOrder);
        if ("CHOICE".equals(q.getQuestionType())) {
            m.put("optionA", q.getOptionA());
            m.put("optionB", q.getOptionB());
            m.put("optionC", q.getOptionC());
            m.put("optionD", q.getOptionD());
        }
        return m;
    }

    private Map<String, Object> attemptSummary(StudentExamAttempt attempt) {
        List<StudentExamRecord> records = studentExamRecordRepository
                .findByStudentPersonIdAndExamExamId(attempt.getStudent().getPersonId(), attempt.getExam().getExamId());
        int totalScore = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
        boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
        Map<String, Object> map = new HashMap<>();
        map.put("attemptId", attempt.getAttemptId());
        map.put("examId", attempt.getExam().getExamId());
        map.put("examTitle", attempt.getExam().getTitle());
        map.put("studentExamStatus", "已结束");
        map.put("totalScore", totalScore);
        map.put("allGraded", allGraded);
        map.put("submitTime", attempt.getSubmitTime());
        if (attempt.getExam().getCourse() != null) {
            map.put("courseName", attempt.getExam().getCourse().getName());
        }
        return map;
    }

    private Map<String, Object> courseClassToMap(StudentCourseClass enrollment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", enrollment.getId());
        map.put("className", enrollment.getClassName());
        if (enrollment.getCourse() != null) {
            map.put("courseId", enrollment.getCourse().getCourseId());
            map.put("courseNum", enrollment.getCourse().getNum());
            map.put("courseName", enrollment.getCourse().getName());
        }
        return map;
    }

    private Student getCurrentStudent(Integer personId) {
        return studentRepository.findById(personId).orElse(null);
    }

    private boolean canStudentAccessExam(Integer personId, Exam exam) {
        if (personId == null || exam == null || exam.getCourse() == null || exam.getCourse().getCourseId() == null) {
            return false;
        }
        // Exam visibility is course-first; class is only display/filter metadata.
        return studentCourseClassRepository
                .findByStudentPersonIdAndCourseCourseId(personId, exam.getCourse().getCourseId())
                .isPresent();
    }

    private void syncToScoreTable(Integer personId, Exam exam, int totalScore) {
        if (exam.getCourse() == null) return;
        Score targetScore = null;
        for (Score score : scoreRepository.findByStudentPersonId(personId)) {
            if (score.getCourse() != null && score.getCourse().getCourseId().equals(exam.getCourse().getCourseId())) {
                targetScore = score;
                break;
            }
        }
        if (targetScore == null) {
            Optional<Student> studentOp = studentRepository.findById(personId);
            if (studentOp.isEmpty()) return;
            targetScore = new Score();
            targetScore.setStudent(studentOp.get());
            targetScore.setCourse(exam.getCourse());
        }
        targetScore.setMark(totalScore);
        scoreRepository.save(targetScore);
    }

    private boolean isBeforeStart(Exam exam) {
        LocalDateTime start = parseTime(exam.getStartTime());
        return start != null && LocalDateTime.now().isBefore(start);
    }

    private boolean isAfterEnd(Exam exam) {
        LocalDateTime end = parseTime(exam.getEndTime());
        return end != null && !LocalDateTime.now().isBefore(end);
    }

    private long remainingSeconds(Exam exam) {
        LocalDateTime end = parseTime(exam.getEndTime());
        if (end == null) return 0;
        return Math.max(0, Duration.between(LocalDateTime.now(), end).getSeconds());
    }

    private LocalDateTime parseTime(String value) {
        return ExamPaperParser.parseExamTime(value);
    }

    private String now() {
        return DateTimeTool.parseDateTime(new Date());
    }
}
