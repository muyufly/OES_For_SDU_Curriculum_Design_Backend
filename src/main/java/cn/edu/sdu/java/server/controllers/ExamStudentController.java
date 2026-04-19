package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.ExamStudentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ExamStudentController 学生端考试 API
 * 提供考试列表查询、题目获取、试卷提交等 Web 服务
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/student/exams")
public class ExamStudentController {

    private final ExamStudentService examStudentService;

    public ExamStudentController(ExamStudentService examStudentService) {
        this.examStudentService = examStudentService;
    }

    /**
     * 获取当前可参与的考试列表
     * GET /api/student/exams
     */
    @GetMapping("")
    @RequireRole("STUDENT")
    public DataResponse getAvailableExams() {
        return examStudentService.getAvailableExams();
    }

    /**
     * 获取考试的具体题目（隐藏标准答案）
     * GET /api/student/exams/{examId}/questions
     */
    @GetMapping("/{examId}/questions")
    @RequireRole("STUDENT")
    public DataResponse getExamQuestions(@PathVariable Integer examId) {
        return examStudentService.getExamQuestions(examId);
    }

    /**
     * 提交试卷作答内容
     * POST /api/student/exams/{examId}/submit
     * 请求体: DataRequest，data 中包含 "answers" 列表，每个元素有 questionId 和 answer
     */
    @PostMapping("/{examId}/submit")
    @RequireRole("STUDENT")
    public DataResponse submitExam(@PathVariable Integer examId,
                                   @Valid @RequestBody DataRequest dataRequest) {
        List<?> answers = dataRequest.getList("answers");
        return examStudentService.submitExam(examId, answers);
    }
}
