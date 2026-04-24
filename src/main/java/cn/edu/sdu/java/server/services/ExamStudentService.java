package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamStudentService {
    private final ExamRepository examRepository;
    private final ExamQuestionRelRepository examQuestionRelRepository;
    private final QuestionRepository questionRepository;
    private final StudentExamRecordRepository studentExamRecordRepository;
    private final StudentRepository studentRepository;
    private final ScoreRepository scoreRepository;

    public ExamStudentService(ExamRepository examRepository,
                              ExamQuestionRelRepository examQuestionRelRepository,
                              QuestionRepository questionRepository,
                              StudentExamRecordRepository studentExamRecordRepository,
                              StudentRepository studentRepository,
                              ScoreRepository scoreRepository) {
        this.examRepository = examRepository;
        this.examQuestionRelRepository = examQuestionRelRepository;
        this.questionRepository = questionRepository;
        this.studentExamRecordRepository = studentExamRecordRepository;
        this.studentRepository = studentRepository;
        this.scoreRepository = scoreRepository;
    }

    public DataResponse getAvailableExams() {
        List<Exam> exams = examRepository.findOpenExams();
        List<Map<String, Object>> dataList = new ArrayList<>();
        Integer personId = CommonMethod.getPersonId();
        for (Exam e : exams) {
            Map<String, Object> m = new HashMap<>();
            m.put("examId", e.getExamId());
            m.put("title", e.getTitle());
            m.put("startTime", e.getStartTime());
            m.put("endTime", e.getEndTime());
            m.put("status", e.getStatus());
            if (e.getCourse() != null) {
                m.put("courseId", e.getCourse().getCourseId());
                m.put("courseName", e.getCourse().getName());
            }
            if (personId != null) {
                boolean submitted = studentExamRecordRepository.existsByStudentPersonIdAndExamExamId(personId, e.getExamId());
                m.put("submitted", submitted);
            }
            dataList.add(m);
        }
        return CommonMethod.getReturnData(dataList);
    }

    public DataResponse getExamQuestions(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        Exam exam = examOp.get();
        if (!"OPEN".equals(exam.getStatus())) {
            return CommonMethod.getReturnMessageError("考试未开放");
        }
        Integer personId = CommonMethod.getPersonId();
        if (personId != null && studentExamRecordRepository.existsByStudentPersonIdAndExamExamId(personId, examId)) {
            return CommonMethod.getReturnMessageError("您已提交过该考试，不能重复作答");
        }
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (ExamQuestionRel rel : examQuestionRelRepository.findByExamExamIdOrderBySortOrderAsc(examId)) {
            Question q = rel.getQuestion();
            Map<String, Object> m = new HashMap<>();
            m.put("questionId", q.getQuestionId());
            m.put("questionType", q.getQuestionType());
            m.put("content", q.getContent());
            m.put("score", q.getScore());
            m.put("sortOrder", rel.getSortOrder());
            if ("CHOICE".equals(q.getQuestionType())) {
                m.put("optionA", q.getOptionA());
                m.put("optionB", q.getOptionB());
                m.put("optionC", q.getOptionC());
                m.put("optionD", q.getOptionD());
            }
            questionList.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("examId", exam.getExamId());
        data.put("title", exam.getTitle());
        data.put("endTime", exam.getEndTime());
        data.put("questions", questionList);
        return CommonMethod.getReturnData(data);
    }

    public DataResponse submitExam(Integer examId, List<?> answers) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        Exam exam = examOp.get();
        if (!"OPEN".equals(exam.getStatus())) {
            return CommonMethod.getReturnMessageError("考试未开放或已结束");
        }
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前用户信息");
        }
        if (studentExamRecordRepository.existsByStudentPersonIdAndExamExamId(personId, examId)) {
            return CommonMethod.getReturnMessageError("您已提交过该考试，不能重复提交");
        }
        Optional<Student> studentOp = studentRepository.findById(personId);
        if (studentOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("学生信息不存在");
        }
        Student student = studentOp.get();
        String submitTime = DateTimeTool.parseDateTime(new Date());
        int totalAutoScore = 0;
        List<StudentExamRecord> records = new ArrayList<>();
        for (Object ansObj : answers) {
            if (!(ansObj instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> ansMap = (Map<String, Object>) rawMap;
            Integer questionId = CommonMethod.getInteger(ansMap, "questionId");
            String studentAnswer = CommonMethod.getString(ansMap, "answer");
            if (questionId == null) {
                continue;
            }
            Optional<Question> qOp = questionRepository.findById(questionId);
            if (qOp.isEmpty()) {
                continue;
            }
            Question question = qOp.get();
            if (question.getExam() == null || !question.getExam().getExamId().equals(examId)) {
                continue;
            }
            StudentExamRecord record = new StudentExamRecord();
            record.setStudent(student);
            record.setExam(exam);
            record.setQuestion(question);
            record.setAnswer(studentAnswer);
            record.setSubmitTime(submitTime);
            if ("CHOICE".equals(question.getQuestionType())) {
                if (studentAnswer != null && studentAnswer.equalsIgnoreCase(question.getAnswer())) {
                    record.setScore(question.getScore());
                    totalAutoScore += question.getScore();
                } else {
                    record.setScore(0);
                }
                record.setGraded(1);
            } else {
                record.setScore(0);
                record.setGraded(0);
            }
            records.add(record);
        }
        if (records.isEmpty()) {
            return CommonMethod.getReturnMessageError("没有有效答题记录");
        }
        studentExamRecordRepository.saveAll(records);
        if (records.stream().allMatch(r -> r.getGraded() == 1)) {
            syncToScoreTable(student.getPersonId(), exam, totalAutoScore);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalAutoScore", totalAutoScore);
        result.put("recordCount", records.size());
        return CommonMethod.getReturnData(result, "提交成功");
    }

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
        Map<Integer, List<StudentExamRecord>> examGroups = studentExamRecordRepository.findByStudentPersonIdIn(List.of(personId))
                .stream()
                .collect(Collectors.groupingBy(r -> r.getExam().getExamId()));
        List<Map<String, Object>> examScores = new ArrayList<>();
        for (List<StudentExamRecord> records : examGroups.values()) {
            int totalScore = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
            boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
            StudentExamRecord first = records.get(0);
            Map<String, Object> map = new HashMap<>();
            map.put("examId", first.getExam().getExamId());
            map.put("examTitle", first.getExam().getTitle());
            map.put("totalScore", totalScore);
            map.put("allGraded", allGraded);
            map.put("submitTime", first.getSubmitTime());
            if (first.getExam().getCourse() != null) {
                map.put("courseName", first.getExam().getCourse().getName());
            }
            examScores.add(map);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("courseScores", courseScores);
        result.put("examScores", examScores);
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getExamResult(Integer examId) {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前学生信息");
        }
        List<StudentExamRecord> records = studentExamRecordRepository.findByStudentPersonIdAndExamExamId(personId, examId);
        if (records.isEmpty()) {
            return CommonMethod.getReturnMessageError("尚未提交该试卷");
        }
        int totalScore = records.stream().mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
        boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
        List<Map<String, Object>> questionResults = new ArrayList<>();
        for (StudentExamRecord record : records) {
            Question question = record.getQuestion();
            Map<String, Object> map = new HashMap<>();
            map.put("recordId", record.getRecordId());
            map.put("questionId", question.getQuestionId());
            map.put("questionType", question.getQuestionType());
            map.put("content", question.getContent());
            map.put("answer", record.getAnswer());
            map.put("score", record.getScore());
            map.put("maxScore", question.getScore());
            map.put("graded", record.getGraded());
            if (allGraded) {
                map.put("standardAnswer", question.getAnswer());
            }
            questionResults.add(map);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("examId", examId);
        result.put("examTitle", records.get(0).getExam().getTitle());
        result.put("totalScore", totalScore);
        result.put("allGraded", allGraded);
        result.put("submitTime", records.get(0).getSubmitTime());
        result.put("questions", questionResults);
        return CommonMethod.getReturnData(result);
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
}
