package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamTeacherService {
    private static final Logger log = LoggerFactory.getLogger(ExamTeacherService.class);

    private final TeacherClassRepository teacherClassRepository;
    private final StudentRepository studentRepository;
    private final StudentExamRecordRepository studentExamRecordRepository;
    private final ExamRepository examRepository;
    private final ScoreRepository scoreRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final ExamQuestionRelRepository examQuestionRelRepository;
    private final StudentExamAttemptRepository studentExamAttemptRepository;
    private final ExamPaperParser examPaperParser = new ExamPaperParser();

    public ExamTeacherService(TeacherClassRepository teacherClassRepository,
                              StudentRepository studentRepository,
                              StudentExamRecordRepository studentExamRecordRepository,
                              ExamRepository examRepository,
                              ScoreRepository scoreRepository,
                              CourseRepository courseRepository,
                              QuestionRepository questionRepository,
                              ExamQuestionRelRepository examQuestionRelRepository,
                              StudentExamAttemptRepository studentExamAttemptRepository) {
        this.teacherClassRepository = teacherClassRepository;
        this.studentRepository = studentRepository;
        this.studentExamRecordRepository = studentExamRecordRepository;
        this.examRepository = examRepository;
        this.scoreRepository = scoreRepository;
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
        this.examQuestionRelRepository = examQuestionRelRepository;
        this.studentExamAttemptRepository = studentExamAttemptRepository;
    }

    public DataResponse getTeacherExams() {
        Integer teacherId = CommonMethod.getPersonId();
        if (teacherId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前教师信息");
        }
        List<Map<String, Object>> result = examRepository.findByCreatorId(teacherId).stream()
                .map(this::examToMap)
                .collect(Collectors.toList());
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getExamDetail(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("试卷不存在");
        }
        Exam exam = examOp.get();
        if (!ownsExam(exam)) {
            return CommonMethod.getReturnMessageError("无权查看该试卷");
        }
        Map<String, Object> data = examToMap(exam);
        List<Map<String, Object>> questions = examQuestionRelRepository.findByExamExamIdOrderBySortOrderAsc(examId)
                .stream()
                .map(rel -> questionToMap(rel.getQuestion(), rel.getSortOrder()))
                .collect(Collectors.toList());
        data.put("questions", questions);
        data.put("hasRecords", !studentExamRecordRepository.findByExamExamId(examId).isEmpty());
        return CommonMethod.getReturnData(data);
    }

    @Transactional
    public DataResponse updateExam(Integer examId, DataRequest request) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("试卷不存在");
        }
        Exam exam = examOp.get();
        if (!ownsExam(exam)) {
            return CommonMethod.getReturnMessageError("无权修改该试卷");
        }
        String title = request.getString("title");
        Integer courseId = request.getInteger("courseId");
        String startTime = request.getString("startTime");
        String endTime = request.getString("endTime");
        String status = normalizeStatus(request.getString("status"));
        if (isBlank(title) || courseId == null || isBlank(startTime) || isBlank(endTime) || status == null) {
            return CommonMethod.getReturnMessageError("试卷标题、课程、时间和状态不能为空");
        }
        Optional<Course> courseOp = courseRepository.findById(courseId);
        if (courseOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("课程不存在");
        }
        exam.setTitle(title.trim());
        exam.setCourse(courseOp.get());
        exam.setStartTime(startTime.trim());
        exam.setEndTime(endTime.trim());
        exam.setStatus(status);
        examRepository.save(exam);
        return CommonMethod.getReturnData(examToMap(exam), "试卷修改成功");
    }

    @Transactional
    public DataResponse updateQuestion(Integer questionId, DataRequest request) {
        Optional<Question> questionOp = questionRepository.findById(questionId);
        if (questionOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("题目不存在");
        }
        Question question = questionOp.get();
        if (question.getExam() == null || !ownsExam(question.getExam())) {
            return CommonMethod.getReturnMessageError("无权修改该题目");
        }
        String type = normalizeQuestionType(request.getString("questionType"));
        String content = request.getString("content");
        Integer score = request.getInteger("score");
        if (type == null || isBlank(content) || score == null || score <= 0) {
            return CommonMethod.getReturnMessageError("题型、题干和分值不能为空");
        }
        question.setQuestionType(type);
        question.setContent(content.trim());
        question.setOptionA(emptyToNull(request.getString("optionA")));
        question.setOptionB(emptyToNull(request.getString("optionB")));
        question.setOptionC(emptyToNull(request.getString("optionC")));
        question.setOptionD(emptyToNull(request.getString("optionD")));
        question.setAnswer(emptyToNull(request.getString("answer")));
        question.setScore(score);
        String validation = validateQuestion(question);
        if (validation != null) {
            return CommonMethod.getReturnMessageError(validation);
        }
        questionRepository.save(question);
        return CommonMethod.getReturnData(questionToMap(question, null), "题目修改成功");
    }

    @Transactional
    public DataResponse deleteQuestion(Integer questionId) {
        Optional<Question> questionOp = questionRepository.findById(questionId);
        if (questionOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("题目不存在");
        }
        Question question = questionOp.get();
        if (question.getExam() == null || !ownsExam(question.getExam())) {
            return CommonMethod.getReturnMessageError("无权删除该题目");
        }
        if (studentExamRecordRepository.existsByQuestionQuestionId(questionId)) {
            return CommonMethod.getReturnMessageError("该题目已有学生提交记录，不能删除");
        }
        examQuestionRelRepository.deleteByQuestionQuestionId(questionId);
        questionRepository.delete(question);
        return CommonMethod.getReturnMessageOK("题目删除成功");
    }

    @Transactional
    public DataResponse uploadExamPaper(MultipartFile file, ExamPaperParser.CsvMeta csvMeta) {
        Integer teacherId = CommonMethod.getPersonId();
        if (teacherId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前教师信息");
        }
        try {
            String fileName = file.getOriginalFilename();
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ExamPaperParser.ParsedExam parsed = examPaperParser.parse(fileName, content, csvMeta);
            Optional<Course> courseOp = courseRepository.findById(parsed.courseId);
            if (courseOp.isEmpty()) {
                return CommonMethod.getReturnMessageError("课程不存在：" + parsed.courseId);
            }

            Exam exam = new Exam();
            exam.setTitle(parsed.title);
            exam.setCourse(courseOp.get());
            exam.setStartTime(parsed.startTime);
            exam.setEndTime(parsed.endTime);
            exam.setStatus(parsed.status);
            exam.setCreatorId(teacherId);
            exam.setCreateTime(DateTimeTool.parseDateTime(new Date()));
            examRepository.save(exam);

            int sortOrder = 1;
            for (ExamPaperParser.ParsedQuestion parsedQuestion : parsed.questions) {
                Question question = new Question();
                question.setExam(exam);
                question.setQuestionType(parsedQuestion.questionType);
                question.setContent(parsedQuestion.content);
                question.setOptionA(emptyToNull(parsedQuestion.optionA));
                question.setOptionB(emptyToNull(parsedQuestion.optionB));
                question.setOptionC(emptyToNull(parsedQuestion.optionC));
                question.setOptionD(emptyToNull(parsedQuestion.optionD));
                question.setAnswer(emptyToNull(parsedQuestion.answer));
                question.setScore(parsedQuestion.score);
                questionRepository.save(question);

                ExamQuestionRel rel = new ExamQuestionRel();
                rel.setExam(exam);
                rel.setQuestion(question);
                rel.setSortOrder(sortOrder++);
                examQuestionRelRepository.save(rel);
            }
            return CommonMethod.getReturnData(examToMap(exam), "试卷上传成功");
        } catch (Exception e) {
            log.error("试卷上传解析失败", e);
            return CommonMethod.getReturnMessageError(e.getMessage());
        }
    }

    public DataResponse getExamRecords(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("试卷不存在");
        }
        if (!ownsExam(examOp.get())) {
            return CommonMethod.getReturnMessageError("无权查看该试卷记录");
        }
        Set<Integer> managedStudentIds = getManagedStudentIds();
        List<Map<String, Object>> result = studentExamRecordRepository.findByExamExamId(examId).stream()
                .filter(record -> managedStudentIds.contains(record.getStudent().getPersonId()))
                .map(this::recordToMap)
                .collect(Collectors.toList());
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getEndedAttempts(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("试卷不存在");
        }
        if (!ownsExam(examOp.get())) {
            return CommonMethod.getReturnMessageError("无权查看该试卷");
        }
        ensureLegacyAttempts(examId);
        closeExpiredDrafts(examId);
        Set<Integer> managedStudentIds = getManagedStudentIds();
        List<Map<String, Object>> result = studentExamAttemptRepository.findByExamExamIdAndStatus(examId, "ENDED")
                .stream()
                .filter(attempt -> managedStudentIds.contains(attempt.getStudent().getPersonId()))
                .map(this::attemptToMap)
                .collect(Collectors.toList());
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getAttemptRecords(Integer attemptId) {
        Optional<StudentExamAttempt> attemptOp = studentExamAttemptRepository.findById(attemptId);
        if (attemptOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试记录不存在");
        }
        StudentExamAttempt attempt = attemptOp.get();
        closeExpiredDrafts(attempt.getExam().getExamId());
        if (!"ENDED".equals(attempt.getStatus())) {
            return CommonMethod.getReturnMessageError("只能批改已结束试卷");
        }
        if (!ownsExam(attempt.getExam()) || !getManagedStudentIds().contains(attempt.getStudent().getPersonId())) {
            return CommonMethod.getReturnMessageError("无权查看该记录");
        }
        List<Map<String, Object>> result = studentExamRecordRepository
                .findByStudentPersonIdAndExamExamId(attempt.getStudent().getPersonId(), attempt.getExam().getExamId())
                .stream()
                .map(this::recordToMap)
                .collect(Collectors.toList());
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getExamStats(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("试卷不存在");
        }
        if (!ownsExam(examOp.get())) {
            return CommonMethod.getReturnMessageError("无权查看该试卷统计");
        }
        ensureLegacyAttempts(examId);
        closeExpiredDrafts(examId);
        Set<Integer> managedStudentIds = getManagedStudentIds();
        List<StudentExamAttempt> attempts = studentExamAttemptRepository.findByExamExamIdAndStudentPersonIdIn(examId, new ArrayList<>(managedStudentIds));
        Map<Integer, StudentExamAttempt> attemptByStudent = attempts.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getPersonId(), a -> a, (a, b) -> a));
        int endedCount = 0;
        int draftCount = 0;
        int gradedCount = 0;
        int pendingGradeCount = 0;
        int maxScore = 0;
        int minScore = 0;
        int scoreSum = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer studentId : managedStudentIds) {
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) continue;
            StudentExamAttempt attempt = attemptByStudent.get(studentId);
            Map<String, Object> row = studentToMap(student);
            row.put("examId", examId);
            if (attempt == null) {
                row.put("studentExamStatus", "未交");
                rows.add(row);
                continue;
            }
            String status = "ENDED".equals(attempt.getStatus()) ? "已结束" : "草稿";
            row.put("attemptId", attempt.getAttemptId());
            row.put("studentExamStatus", status);
            row.put("submitTime", attempt.getSubmitTime());
            if ("ENDED".equals(attempt.getStatus())) {
                endedCount++;
                List<StudentExamRecord> records = studentExamRecordRepository.findByStudentPersonIdAndExamExamId(studentId, examId);
                int total = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
                boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
                row.put("totalScore", total);
                row.put("allGraded", allGraded);
                scoreSum += total;
                maxScore = endedCount == 1 ? total : Math.max(maxScore, total);
                minScore = endedCount == 1 ? total : Math.min(minScore, total);
                if (allGraded) gradedCount++; else pendingGradeCount++;
            } else {
                draftCount++;
            }
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("averageScore", endedCount == 0 ? 0 : Math.round(scoreSum * 100.0 / endedCount) / 100.0);
        data.put("maxScore", endedCount == 0 ? 0 : maxScore);
        data.put("minScore", endedCount == 0 ? 0 : minScore);
        data.put("submittedCount", endedCount);
        data.put("notSubmittedCount", Math.max(0, managedStudentIds.size() - attempts.size()));
        data.put("draftCount", draftCount);
        data.put("gradedCount", gradedCount);
        data.put("pendingGradeCount", pendingGradeCount);
        data.put("rows", rows);
        return CommonMethod.getReturnData(data);
    }

    public DataResponse getTeacherScores(Integer examId, String className, String keyword) {
        Set<Integer> managedStudentIds = getManagedStudentIds();
        List<Map<String, Object>> result = new ArrayList<>();
        if (examId != null && examId > 0) {
            Map<Integer, List<StudentExamRecord>> groups = studentExamRecordRepository.findByExamExamId(examId).stream()
                    .filter(record -> managedStudentIds.contains(record.getStudent().getPersonId()))
                    .collect(Collectors.groupingBy(record -> record.getStudent().getPersonId()));
            for (List<StudentExamRecord> records : groups.values()) {
                Student student = records.get(0).getStudent();
                if (!matchesStudent(student, className, keyword)) {
                    continue;
                }
                int total = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
                boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
                Map<String, Object> map = studentToMap(student);
                map.put("examId", examId);
                map.put("examTitle", records.get(0).getExam().getTitle());
                map.put("totalScore", total);
                map.put("allGraded", allGraded);
                map.put("submitTime", records.get(0).getSubmitTime());
                result.add(map);
            }
            return CommonMethod.getReturnData(result);
        }
        for (Integer studentId : managedStudentIds) {
            for (Score score : scoreRepository.findByStudentPersonId(studentId)) {
                Student student = score.getStudent();
                if (!matchesStudent(student, className, keyword)) {
                    continue;
                }
                Map<String, Object> map = studentToMap(student);
                map.put("scoreId", score.getScoreId());
                map.put("courseId", score.getCourse() == null ? null : score.getCourse().getCourseId());
                map.put("courseNum", score.getCourse() == null ? "" : score.getCourse().getNum());
                map.put("courseName", score.getCourse() == null ? "" : score.getCourse().getName());
                map.put("mark", score.getMark());
                map.put("ranking", score.getRanking());
                result.add(map);
            }
        }
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getClassStudentExamRecords() {
        List<Map<String, Object>> resultList = new ArrayList<>();
        List<String> classNames = getTeacherClassNames();
        for (String cn : classNames) {
            List<Student> students = studentRepository.findByClassName(cn);
            for (Student s : students) {
                List<StudentExamRecord> records = studentExamRecordRepository.findByStudentPersonIdIn(List.of(s.getPersonId()));
                Map<Integer, List<StudentExamRecord>> examGroups = records.stream()
                        .collect(Collectors.groupingBy(r -> r.getExam().getExamId()));
                for (Map.Entry<Integer, List<StudentExamRecord>> entry : examGroups.entrySet()) {
                    List<StudentExamRecord> examRecords = entry.getValue();
                    int totalScore = examRecords.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
                    boolean allGraded = examRecords.stream().allMatch(r -> r.getGraded() == 1);
                    Map<String, Object> m = studentToMap(s);
                    m.put("examId", entry.getKey());
                    m.put("examTitle", examRecords.get(0).getExam().getTitle());
                    m.put("totalScore", totalScore);
                    m.put("allGraded", allGraded);
                    m.put("submitTime", examRecords.get(0).getSubmitTime());
                    resultList.add(m);
                }
            }
        }
        return CommonMethod.getReturnData(resultList);
    }

    public DataResponse gradeRecord(Integer recordId, Integer gradeScore) {
        Optional<StudentExamRecord> recordOp = studentExamRecordRepository.findById(recordId);
        if (recordOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("答题记录不存在");
        }
        StudentExamRecord record = recordOp.get();
        Optional<StudentExamAttempt> attemptOp = studentExamAttemptRepository
                .findByStudentPersonIdAndExamExamId(record.getStudent().getPersonId(), record.getExam().getExamId());
        if (attemptOp.isEmpty() || !"ENDED".equals(attemptOp.get().getStatus())) {
            return CommonMethod.getReturnMessageError("只能批改已结束试卷");
        }
        if (!getManagedStudentIds().contains(record.getStudent().getPersonId())) {
            return CommonMethod.getReturnMessageError("无权批改该学生记录");
        }
        if (!"READ".equals(record.getQuestion().getQuestionType())) {
            return CommonMethod.getReturnMessageError("该题为客观题，已自动判分");
        }
        if (gradeScore == null || gradeScore < 0) {
            return CommonMethod.getReturnMessageError("分数不能小于 0");
        }
        if (gradeScore > record.getQuestion().getScore()) {
            return CommonMethod.getReturnMessageError("打分不能超过题目满分 " + record.getQuestion().getScore());
        }
        record.setScore(gradeScore);
        record.setGraded(1);
        studentExamRecordRepository.save(record);

        Integer personId = record.getStudent().getPersonId();
        Integer examId = record.getExam().getExamId();
        List<StudentExamRecord> allRecords = studentExamRecordRepository.findByStudentPersonIdAndExamExamId(personId, examId);
        boolean allGraded = allRecords.stream().allMatch(r -> r.getGraded() == 1);
        if (allGraded) {
            int totalScore = allRecords.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
            syncToScoreTable(personId, record.getExam(), totalScore);
        }
        return CommonMethod.getReturnMessageOK("批阅成功");
    }

    private void syncToScoreTable(Integer personId, Exam exam, int totalScore) {
        if (exam.getCourse() == null) return;
        Score targetScore = null;
        for (Score s : scoreRepository.findByStudentPersonId(personId)) {
            if (s.getCourse() != null && s.getCourse().getCourseId().equals(exam.getCourse().getCourseId())) {
                targetScore = s;
                break;
            }
        }
        if (targetScore == null) {
            Optional<Student> sOp = studentRepository.findById(personId);
            if (sOp.isEmpty()) return;
            targetScore = new Score();
            targetScore.setStudent(sOp.get());
            targetScore.setCourse(exam.getCourse());
        }
        targetScore.setMark(totalScore);
        scoreRepository.save(targetScore);
    }

    public void exportExamScores(Integer examId, HttpServletResponse response) {
        try {
            Optional<Exam> examOp = examRepository.findById(examId);
            if (examOp.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            List<StudentExamRecord> allRecords = studentExamRecordRepository.findByExamExamId(examId);
            Map<Integer, List<StudentExamRecord>> studentGroups = allRecords.stream()
                    .collect(Collectors.groupingBy(r -> r.getStudent().getPersonId()));
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=exam_" + examId + "_scores.csv");
            response.setCharacterEncoding("UTF-8");
            PrintWriter writer = response.getWriter();
            writer.write('\uFEFF');
            writer.println("学号,姓名,班级,总分,批阅状态,提交时间");
            for (List<StudentExamRecord> records : studentGroups.values()) {
                Student student = records.get(0).getStudent();
                int totalScore = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
                boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
                writer.printf("%s,%s,%s,%d,%s,%s%n",
                        student.getPerson() != null ? student.getPerson().getNum() : "",
                        student.getPerson() != null ? student.getPerson().getName() : "",
                        student.getClassName() != null ? student.getClassName() : "",
                        totalScore,
                        allGraded ? "已批阅" : "待批阅",
                        records.get(0).getSubmitTime() != null ? records.get(0).getSubmitTime() : "");
            }
            writer.flush();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private boolean ownsExam(Exam exam) {
        Integer teacherId = CommonMethod.getPersonId();
        return teacherId != null && exam.getCreatorId() != null && exam.getCreatorId().equals(teacherId);
    }

    private void closeExpiredDrafts(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty() || !isAfterEnd(examOp.get())) {
            return;
        }
        for (StudentExamAttempt attempt : studentExamAttemptRepository.findByExamExamIdAndStatus(examId, "DRAFT")) {
            finalizeExpiredAttempt(attempt);
        }
    }

    private void ensureLegacyAttempts(Integer examId) {
        Map<Integer, List<StudentExamRecord>> groups = studentExamRecordRepository.findByExamExamId(examId).stream()
                .collect(Collectors.groupingBy(r -> r.getStudent().getPersonId()));
        for (Map.Entry<Integer, List<StudentExamRecord>> entry : groups.entrySet()) {
            Integer studentId = entry.getKey();
            if (studentExamAttemptRepository.findByStudentPersonIdAndExamExamId(studentId, examId).isPresent()) {
                continue;
            }
            List<StudentExamRecord> records = entry.getValue();
            StudentExamAttempt attempt = new StudentExamAttempt();
            attempt.setStudent(records.get(0).getStudent());
            attempt.setExam(records.get(0).getExam());
            attempt.setStatus("ENDED");
            attempt.setStartTime(records.get(0).getSubmitTime());
            attempt.setLastSaveTime(records.get(0).getSubmitTime());
            attempt.setSubmitTime(records.get(0).getSubmitTime());
            attempt.setAutoEnded(0);
            studentExamAttemptRepository.save(attempt);
        }
    }

    private void finalizeExpiredAttempt(StudentExamAttempt attempt) {
        for (ExamQuestionRel rel : examQuestionRelRepository.findByExamExamIdOrderBySortOrderAsc(attempt.getExam().getExamId())) {
            Question question = rel.getQuestion();
            StudentExamRecord record = studentExamRecordRepository
                    .findByStudentPersonIdAndExamExamIdAndQuestionQuestionId(attempt.getStudent().getPersonId(), attempt.getExam().getExamId(), question.getQuestionId())
                    .orElseGet(() -> {
                        StudentExamRecord r = new StudentExamRecord();
                        r.setStudent(attempt.getStudent());
                        r.setExam(attempt.getExam());
                        r.setQuestion(question);
                        r.setAnswer("");
                        return r;
                    });
            if ("CHOICE".equals(question.getQuestionType())) {
                record.setScore(record.getAnswer() != null && record.getAnswer().equalsIgnoreCase(question.getAnswer()) ? question.getScore() : 0);
                record.setGraded(1);
            } else {
                record.setScore(record.getScore() == null ? 0 : record.getScore());
                record.setGraded(0);
            }
            record.setSubmitTime(DateTimeTool.parseDateTime(new Date()));
            studentExamRecordRepository.save(record);
        }
        attempt.setStatus("ENDED");
        attempt.setAutoEnded(1);
        attempt.setSubmitTime(attempt.getSubmitTime() == null ? DateTimeTool.parseDateTime(new Date()) : attempt.getSubmitTime());
        attempt.setLastSaveTime(DateTimeTool.parseDateTime(new Date()));
        studentExamAttemptRepository.save(attempt);
    }

    private boolean isAfterEnd(Exam exam) {
        try {
            java.time.LocalDateTime end = java.time.LocalDateTime.parse(exam.getEndTime().trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return !java.time.LocalDateTime.now().isBefore(end);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> attemptToMap(StudentExamAttempt attempt) {
        List<StudentExamRecord> records = studentExamRecordRepository
                .findByStudentPersonIdAndExamExamId(attempt.getStudent().getPersonId(), attempt.getExam().getExamId());
        int total = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
        boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
        Map<String, Object> map = studentToMap(attempt.getStudent());
        map.put("attemptId", attempt.getAttemptId());
        map.put("examId", attempt.getExam().getExamId());
        map.put("examTitle", attempt.getExam().getTitle());
        map.put("studentExamStatus", "已结束");
        map.put("totalScore", total);
        map.put("allGraded", allGraded);
        map.put("submitTime", attempt.getSubmitTime());
        map.put("autoEnded", attempt.getAutoEnded());
        return map;
    }

    private Set<Integer> getManagedStudentIds() {
        Set<Integer> ids = new HashSet<>();
        for (String className : getTeacherClassNames()) {
            for (Student student : studentRepository.findByClassName(className)) {
                ids.add(student.getPersonId());
            }
        }
        return ids;
    }

    private List<String> getTeacherClassNames() {
        Integer teacherId = CommonMethod.getPersonId();
        if (teacherId == null) {
            return List.of();
        }
        return teacherClassRepository.findByTeacherPersonId(teacherId).stream()
                .map(TeacherClass::getClassName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, Object> examToMap(Exam exam) {
        Map<String, Object> map = new HashMap<>();
        map.put("examId", exam.getExamId());
        map.put("title", exam.getTitle());
        map.put("startTime", exam.getStartTime());
        map.put("endTime", exam.getEndTime());
        map.put("status", exam.getStatus());
        map.put("creatorId", exam.getCreatorId());
        map.put("createTime", exam.getCreateTime());
        if (exam.getCourse() != null) {
            map.put("courseId", exam.getCourse().getCourseId());
            map.put("courseNum", exam.getCourse().getNum());
            map.put("courseName", exam.getCourse().getName());
        }
        return map;
    }

    private Map<String, Object> questionToMap(Question question, Integer sortOrder) {
        Map<String, Object> map = new HashMap<>();
        map.put("questionId", question.getQuestionId());
        map.put("examId", question.getExam() == null ? null : question.getExam().getExamId());
        map.put("questionType", question.getQuestionType());
        map.put("content", question.getContent());
        map.put("optionA", question.getOptionA());
        map.put("optionB", question.getOptionB());
        map.put("optionC", question.getOptionC());
        map.put("optionD", question.getOptionD());
        map.put("answer", question.getAnswer());
        map.put("score", question.getScore());
        map.put("sortOrder", sortOrder);
        return map;
    }

    private Map<String, Object> recordToMap(StudentExamRecord record) {
        Map<String, Object> map = studentToMap(record.getStudent());
        map.put("recordId", record.getRecordId());
        map.put("examId", record.getExam().getExamId());
        map.put("examTitle", record.getExam().getTitle());
        map.put("questionId", record.getQuestion().getQuestionId());
        map.put("questionType", record.getQuestion().getQuestionType());
        map.put("questionContent", record.getQuestion().getContent());
        map.put("standardAnswer", record.getQuestion().getAnswer());
        map.put("answer", record.getAnswer());
        map.put("score", record.getScore());
        map.put("maxScore", record.getQuestion().getScore());
        map.put("graded", record.getGraded());
        map.put("submitTime", record.getSubmitTime());
        return map;
    }

    private Map<String, Object> studentToMap(Student student) {
        Map<String, Object> map = new HashMap<>();
        map.put("personId", student.getPersonId());
        map.put("className", student.getClassName());
        if (student.getPerson() != null) {
            map.put("studentNum", student.getPerson().getNum());
            map.put("studentName", student.getPerson().getName());
        }
        return map;
    }

    private boolean matchesStudent(Student student, String className, String keyword) {
        if (!isBlank(className) && !className.equals(student.getClassName())) {
            return false;
        }
        if (isBlank(keyword)) {
            return true;
        }
        String text = keyword.trim();
        String num = student.getPerson() == null ? "" : student.getPerson().getNum();
        String name = student.getPerson() == null ? "" : student.getPerson().getName();
        return num.contains(text) || name.contains(text);
    }

    private String validateQuestion(Question question) {
        if ("CHOICE".equals(question.getQuestionType())) {
            if (isBlank(question.getOptionA()) || isBlank(question.getOptionB()) ||
                    isBlank(question.getOptionC()) || isBlank(question.getOptionD())) {
                return "选择题必须填写 A/B/C/D 四个选项";
            }
            if (isBlank(question.getAnswer()) || !question.getAnswer().matches("(?i)[ABCD]")) {
                return "选择题答案必须为 A/B/C/D";
            }
            question.setAnswer(question.getAnswer().toUpperCase(Locale.ROOT));
        }
        return null;
    }

    private String normalizeQuestionType(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ("CHOICE".equals(normalized) || "READ".equals(normalized)) ? normalized : null;
    }

    private String normalizeStatus(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ("DRAFT".equals(normalized) || "OPEN".equals(normalized) || "CLOSED".equals(normalized)) ? normalized : null;
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
