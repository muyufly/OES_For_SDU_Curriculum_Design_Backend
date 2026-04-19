package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExamTeacherService 教师端考试业务逻辑
 * 提供查看所带班级的考试记录、主观题批阅打分、成绩单导出等功能
 */
@Service
public class ExamTeacherService {

    private final TeacherClassRepository teacherClassRepository;
    private final StudentRepository studentRepository;
    private final StudentExamRecordRepository studentExamRecordRepository;
    private final ExamRepository examRepository;
    private final ScoreRepository scoreRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;

    public ExamTeacherService(TeacherClassRepository teacherClassRepository,
                              StudentRepository studentRepository,
                              StudentExamRecordRepository studentExamRecordRepository,
                              ExamRepository examRepository,
                              ScoreRepository scoreRepository,
                              CourseRepository courseRepository,
                              QuestionRepository questionRepository) {
        this.teacherClassRepository = teacherClassRepository;
        this.studentRepository = studentRepository;
        this.studentExamRecordRepository = studentExamRecordRepository;
        this.examRepository = examRepository;
        this.scoreRepository = scoreRepository;
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * 查询教师所带班级学生的考试提交记录
     */
    public DataResponse getClassStudentExamRecords() {
        Integer personId = CommonMethod.getPersonId();
        if (personId == null) {
            return CommonMethod.getReturnMessageError("无法获取当前用户信息");
        }

        // 获取教师绑定的班级列表
        List<TeacherClass> teacherClasses = teacherClassRepository.findByTeacherPersonId(personId);
        if (teacherClasses.isEmpty()) {
            return CommonMethod.getReturnMessageError("您尚未绑定任何班级");
        }

        List<String> classNames = teacherClasses.stream()
                .map(TeacherClass::getClassName)
                .collect(Collectors.toList());

        // 获取这些班级的所有学生
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (String className : classNames) {
            List<Student> students = studentRepository.findByClassName(className);
            for (Student s : students) {
                // 查询该学生的所有考试记录（按考试分组汇总）
                List<Integer> singleList = List.of(s.getPersonId());
                List<StudentExamRecord> records = studentExamRecordRepository
                        .findByStudentPersonIdIn(singleList);

                // 按考试ID分组
                Map<Integer, List<StudentExamRecord>> examGroups = records.stream()
                        .collect(Collectors.groupingBy(r -> r.getExam().getExamId()));

                for (Map.Entry<Integer, List<StudentExamRecord>> entry : examGroups.entrySet()) {
                    List<StudentExamRecord> examRecords = entry.getValue();
                    int totalScore = 0;
                    boolean allGraded = true;
                    for (StudentExamRecord r : examRecords) {
                        if (r.getScore() != null) totalScore += r.getScore();
                        if (r.getGraded() == 0) allGraded = false;
                    }

                    Map<String, Object> m = new HashMap<>();
                    m.put("personId", s.getPersonId());
                    m.put("studentName", s.getPerson() != null ? s.getPerson().getName() : "");
                    m.put("studentNum", s.getPerson() != null ? s.getPerson().getNum() : "");
                    m.put("className", className);
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

    /**
     * 为指定学生的阅读题进行主观打分
     * 并在所有题目批阅完成后，将总成绩更新到现有的 score 表中
     */
    public DataResponse gradeRecord(Integer recordId, Integer gradeScore) {
        Optional<StudentExamRecord> recordOp = studentExamRecordRepository.findById(recordId);
        if (recordOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("答题记录不存在");
        }

        StudentExamRecord record = recordOp.get();
        if (!"READ".equals(record.getQuestion().getQuestionType())) {
            return CommonMethod.getReturnMessageError("该题为客观题，已自动判分");
        }

        // 检查分值不超过题目满分
        if (gradeScore != null && gradeScore > record.getQuestion().getScore()) {
            return CommonMethod.getReturnMessageError("打分不能超过题目满分 " + record.getQuestion().getScore());
        }

        record.setScore(gradeScore != null ? gradeScore : 0);
        record.setGraded(1);
        studentExamRecordRepository.save(record);

        // 检查该学生该考试的所有题目是否全部批阅完毕
        Integer personId = record.getStudent().getPersonId();
        Integer examId = record.getExam().getExamId();
        List<StudentExamRecord> allRecords = studentExamRecordRepository
                .findByStudentPersonIdAndExamExamId(personId, examId);

        boolean allGraded = allRecords.stream().allMatch(r -> r.getGraded() == 1);
        if (allGraded) {
            // 计算总分并同步到 score 表
            int totalScore = allRecords.stream()
                    .mapToInt(r -> r.getScore() != null ? r.getScore() : 0)
                    .sum();
            syncToScoreTable(personId, record.getExam(), totalScore);
        }

        return CommonMethod.getReturnMessageOK("批阅成功");
    }

    /**
     * 将考试总成绩同步到现有的 score 表
     */
    private void syncToScoreTable(Integer personId, Exam exam, int totalScore) {
        if (exam.getCourse() == null) return;

        // 查找或创建 score 记录
        List<Score> existingScores = scoreRepository.findByStudentPersonId(personId);
        Score targetScore = null;
        for (Score s : existingScores) {
            if (s.getCourse().getCourseId().equals(exam.getCourse().getCourseId())) {
                targetScore = s;
                break;
            }
        }

        if (targetScore == null) {
            targetScore = new Score();
            Optional<Student> sOp = studentRepository.findById(personId);
            if (sOp.isEmpty()) return;
            targetScore.setStudent(sOp.get());
            targetScore.setCourse(exam.getCourse());
        }
        targetScore.setMark(totalScore);
        scoreRepository.save(targetScore);
    }

    /**
     * 导出当前考试的成绩单（CSV 格式）
     */
    public void exportExamScores(Integer examId, HttpServletResponse response) {
        try {
            Optional<Exam> examOp = examRepository.findById(examId);
            if (examOp.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            Exam exam = examOp.get();

            List<StudentExamRecord> allRecords = studentExamRecordRepository.findByExamExamId(examId);
            // 按学生分组
            Map<Integer, List<StudentExamRecord>> studentGroups = allRecords.stream()
                    .collect(Collectors.groupingBy(r -> r.getStudent().getPersonId()));

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=exam_" + examId + "_scores.csv");
            response.setCharacterEncoding("UTF-8");

            PrintWriter writer = response.getWriter();
            // 写入 BOM 以支持 Excel 中文
            writer.write('\uFEFF');
            // CSV 表头
            writer.println("学号,姓名,班级,总分,批阅状态,提交时间");

            for (Map.Entry<Integer, List<StudentExamRecord>> entry : studentGroups.entrySet()) {
                List<StudentExamRecord> records = entry.getValue();
                StudentExamRecord first = records.get(0);
                Student student = first.getStudent();
                String num = student.getPerson() != null ? student.getPerson().getNum() : "";
                String name = student.getPerson() != null ? student.getPerson().getName() : "";
                String className = student.getClassName() != null ? student.getClassName() : "";

                int totalScore = records.stream()
                        .mapToInt(r -> r.getScore() != null ? r.getScore() : 0)
                        .sum();
                boolean allGraded = records.stream().allMatch(r -> r.getGraded() == 1);
                String submitTime = first.getSubmitTime() != null ? first.getSubmitTime() : "";

                writer.printf("%s,%s,%s,%d,%s,%s%n",
                        num, name, className, totalScore,
                        allGraded ? "已批阅" : "待批阅", submitTime);
            }
            writer.flush();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
