package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.ExamStudentService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/student")
public class ExamStudentProfileController {
    private final ExamStudentService examStudentService;

    public ExamStudentProfileController(ExamStudentService examStudentService) {
        this.examStudentService = examStudentService;
    }

    @GetMapping("/scores")
    @RequireRole("STUDENT")
    public DataResponse getMyScores() {
        return examStudentService.getMyScores();
    }
}
