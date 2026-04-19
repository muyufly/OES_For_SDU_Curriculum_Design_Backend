package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.ExamTeacherService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
