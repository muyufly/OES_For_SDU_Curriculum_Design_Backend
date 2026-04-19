package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ExamStudentService 学生端考试业务逻辑
 * 提供考试列表查询、题目获取、试卷提交等功能
 */
@Service
public class ExamStudentService {

    private final ExamRepository examRepository;
    private final ExamQuestionRelRepository examQuestionRelRepository;
    private final QuestionRepository questionRepository;
    private final StudentExamRecordRepository studentExamRecordRepository;
    private final StudentRepository studentRepository;

    public ExamStudentService(ExamRepository examRepository,
                              ExamQuestionRelRepository examQuestionRelRepository,
                              QuestionRepository questionRepository,
                              StudentExamRecordRepository studentExamRecordRepository,
                              StudentRepository studentRepository) {
        this.examRepository = examRepository;
        this.examQuestionRelRepository = examQuestionRelRepository;
        this.questionRepository = questionRepository;
        this.studentExamRecordRepository = studentExamRecordRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * 获取当前可参与的考试列表（状态为 OPEN）
     */
    public DataResponse getAvailableExams() {
        List<Exam> exams = examRepository.findOpenExams();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Exam e : exams) {
            Map<String, Object> m = new HashMap<>();
            m.put("examId", e.getExamId());
            m.put("title", e.getTitle());
            m.put("startTime", e.getStartTime());
            m.put("endTime", e.getEndTime());
            m.put("status", e.getStatus());
            if (e.getCourse() != null) {
                m.put("courseName", e.getCourse().getName());
            }
            // 检查当前学生是否已提交
            Integer personId = CommonMethod.getPersonId();
            if (personId != null) {
                boolean submitted = studentExamRecordRepository
                        .existsByStudentPersonIdAndExamExamId(personId, e.getExamId());
                m.put("submitted", submitted);
            }
            dataList.add(m);
        }
        return CommonMethod.getReturnData(dataList);
    }

    /**
     * 获取考试的具体题目（隐藏标准答案）
     */
    public DataResponse getExamQuestions(Integer examId) {
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("考试不存在");
        }
        Exam exam = examOp.get();
        if (!"OPEN".equals(exam.getStatus())) {
            return CommonMethod.getReturnMessageError("考试未开放");
        }

        // 检查是否已提交
        Integer personId = CommonMethod.getPersonId();
        if (personId != null && studentExamRecordRepository
                .existsByStudentPersonIdAndExamExamId(personId, examId)) {
            return CommonMethod.getReturnMessageError("您已提交过该考试，不能重复作答");
        }

        List<ExamQuestionRel> rels = examQuestionRelRepository
                .findByExamExamIdOrderBySortOrderAsc(examId);
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (ExamQuestionRel rel : rels) {
            Question q = rel.getQuestion();
            Map<String, Object> m = new HashMap<>();
            m.put("questionId", q.getQuestionId());
            m.put("questionType", q.getQuestionType());
            m.put("content", q.getContent());
            m.put("score", q.getScore());
            m.put("sortOrder", rel.getSortOrder());
            // 单选题返回选项，但不返回标准答案
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

    /**
     * 提交试卷作答内容
     * 请求体中 answers 为 List，每个元素包含 questionId 和 answer
     * 单选题自动判分，阅读题标记为未批阅
     */
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

        // 检查是否已提交
        if (studentExamRecordRepository.existsByStudentPersonIdAndExamExamId(personId, examId)) {
            return CommonMethod.getReturnMessageError("您已提交过该考试，不能重复提交");
        }

        Optional<Student> studentOp = studentRepository.findById(personId);
        if (studentOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("学生信息不存在");
        }
        Student student = studentOp.get();
        String submitTime = DateTimeTool.parseDateTime(new Date());

        int totalScore = 0;
        List<StudentExamRecord> records = new ArrayList<>();

        for (Object ansObj : answers) {
            if (!(ansObj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> ansMap = (Map<String, Object>) ansObj;
            Integer questionId = CommonMethod.getInteger(ansMap, "questionId");
            String studentAnswer = CommonMethod.getString(ansMap, "answer");

            if (questionId == null) continue;
            Optional<Question> qOp = questionRepository.findById(questionId);
            if (qOp.isEmpty()) continue;
            Question question = qOp.get();

            StudentExamRecord record = new StudentExamRecord();
            record.setStudent(student);
            record.setExam(exam);
            record.setQuestion(question);
            record.setAnswer(studentAnswer);
            record.setSubmitTime(submitTime);

            // 单选题自动判分
            if ("CHOICE".equals(question.getQuestionType())) {
                if (studentAnswer != null && studentAnswer.equalsIgnoreCase(question.getAnswer())) {
                    record.setScore(question.getScore());
                    totalScore += question.getScore();
                } else {
                    record.setScore(0);
                }
                record.setGraded(1); // 单选题自动批阅完成
            } else {
                // 阅读题等待教师批阅
                record.setScore(0);
                record.setGraded(0);
            }
            records.add(record);
        }

        studentExamRecordRepository.saveAll(records);

        Map<String, Object> result = new HashMap<>();
        result.put("totalAutoScore", totalScore);
        result.put("recordCount", records.size());
        result.put("msg", "试卷提交成功，单选题已自动判分，阅读题等待教师批阅");
        return CommonMethod.getReturnData(result, "提交成功");
    }
}
