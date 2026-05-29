package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.ExamPaperParser;
import cn.edu.sdu.java.server.services.ExamTeacherService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * ExamTeacherController 教师端考试 API
 * 提供查看班级学生考试记录、主观题批阅、成绩导出等 Web 服务
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/teacher")
public class ExamTeacherController {

    private final ExamTeacherService examTeacherService;

    public ExamTeacherController(ExamTeacherService examTeacherService) {
        this.examTeacherService = examTeacherService;
    }

    @GetMapping("/exams")
    @RequireRole("TEACHER")
    public DataResponse getTeacherExams() {
        return examTeacherService.getTeacherExams();
    }

    @GetMapping("/ai/providers")
    @RequireRole("TEACHER")
    public DataResponse getAiProviders() {
        return examTeacherService.getAiProviders();
    }

    @PostMapping(value = "/exams/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole("TEACHER")
    public DataResponse uploadExam(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) Integer courseId,
                                   @RequestParam(required = false) String startTime,
                                   @RequestParam(required = false) String endTime,
                                   @RequestParam(required = false) String status) {
        ExamPaperParser.CsvMeta meta = new ExamPaperParser.CsvMeta();
        meta.title = title;
        meta.courseId = courseId;
        meta.startTime = startTime;
        meta.endTime = endTime;
        meta.status = status;
        return examTeacherService.uploadExamPaper(file, meta);
    }

    @GetMapping("/exams/{examId}")
    @RequireRole("TEACHER")
    public DataResponse getExamDetail(@PathVariable Integer examId) {
        return examTeacherService.getExamDetail(examId);
    }

    @PutMapping("/exams/{examId}")
    @RequireRole("TEACHER")
    public DataResponse updateExam(@PathVariable Integer examId,
                                   @Valid @RequestBody DataRequest dataRequest) {
        return examTeacherService.updateExam(examId, dataRequest);
    }

    @PutMapping("/questions/{questionId}")
    @RequireRole("TEACHER")
    public DataResponse updateQuestion(@PathVariable Integer questionId,
                                       @Valid @RequestBody DataRequest dataRequest) {
        return examTeacherService.updateQuestion(questionId, dataRequest);
    }

    @DeleteMapping("/questions/{questionId}")
    @RequireRole("TEACHER")
    public DataResponse deleteQuestion(@PathVariable Integer questionId) {
        return examTeacherService.deleteQuestion(questionId);
    }

    @GetMapping("/exams/{examId}/records")
    @RequireRole("TEACHER")
    public DataResponse getExamRecords(@PathVariable Integer examId) {
        return examTeacherService.getExamRecords(examId);
    }

    @GetMapping("/exams/{examId}/ended-attempts")
    @RequireRole("TEACHER")
    public DataResponse getEndedAttempts(@PathVariable Integer examId) {
        return examTeacherService.getEndedAttempts(examId);
    }

    @GetMapping("/attempts/{attemptId}/records")
    @RequireRole("TEACHER")
    public DataResponse getAttemptRecords(@PathVariable Integer attemptId) {
        return examTeacherService.getAttemptRecords(attemptId);
    }

    @GetMapping("/exams/{examId}/stats")
    @RequireRole("TEACHER")
    public DataResponse getExamStats(@PathVariable Integer examId) {
        return examTeacherService.getExamStats(examId);
    }

    @GetMapping("/scores")
    @RequireRole("TEACHER")
    public DataResponse getTeacherScores(@RequestParam(required = false) Integer examId,
                                         @RequestParam(required = false) String className,
                                         @RequestParam(required = false) String keyword) {
        return examTeacherService.getTeacherScores(examId, className, keyword);
    }

    /**
     * 查询教师所带班级学生的考试提交记录
     * GET /api/teacher/classes/students/exams
     */
    @GetMapping("/classes/students/exams")
    @RequireRole("TEACHER")
    public DataResponse getClassStudentExamRecords() {
        return examTeacherService.getClassStudentExamRecords();
    }

    /**
     * 为指定学生的阅读题进行主观打分
     * POST /api/teacher/records/{recordId}/grade
     * 请求体: DataRequest，data 中包含 "score" 分值
     */
    @PostMapping("/records/{recordId}/grade")
    @RequireRole("TEACHER")
    public DataResponse gradeRecord(@PathVariable Integer recordId,
                                    @Valid @RequestBody DataRequest dataRequest) {
        Integer score = dataRequest.getInteger("score");
        return examTeacherService.gradeRecord(recordId, score);
    }

    @PostMapping("/records/{recordId}/ai-grade")
    @RequireRole("TEACHER")
    public DataResponse suggestAiGrade(@PathVariable Integer recordId,
                                       @Valid @RequestBody DataRequest dataRequest) {
        return examTeacherService.suggestAiGrade(recordId, dataRequest.getString("provider"));
    }

    @PostMapping("/records/{recordId}/ai-grade/apply")
    @RequireRole("TEACHER")
    public DataResponse applyAiGrade(@PathVariable Integer recordId,
                                     @Valid @RequestBody DataRequest dataRequest) {
        return examTeacherService.applyAiGrade(recordId, dataRequest.getString("provider"));
    }

    /**
     * 导出当前考试的成绩单（CSV 格式）
     * GET /api/teacher/exams/{examId}/export
     */
    @GetMapping("/exams/{examId}/export")
    @RequireRole("TEACHER")
    public void exportExamScores(@PathVariable Integer examId,
                                 HttpServletResponse response) {
        examTeacherService.exportExamScores(examId, response);
    }
}
