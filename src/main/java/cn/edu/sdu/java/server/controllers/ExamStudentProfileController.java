package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.ExamStudentService;
import jakarta.validation.Valid;
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

    @GetMapping("/course-classes")
    @RequireRole("STUDENT")
    public DataResponse getMyCourseClasses() {
        return examStudentService.getMyCourseClasses();
    }

    @PostMapping("/course-classes")
    @RequireRole("STUDENT")
    public DataResponse addMyCourseClass(@Valid @RequestBody DataRequest dataRequest) {
        return examStudentService.saveMyCourseClass(null, dataRequest.getInteger("courseId"), dataRequest.getString("className"));
    }

    @PutMapping("/course-classes/{id}")
    @RequireRole("STUDENT")
    public DataResponse updateMyCourseClass(@PathVariable Integer id,
                                            @Valid @RequestBody DataRequest dataRequest) {
        return examStudentService.saveMyCourseClass(id, dataRequest.getInteger("courseId"), dataRequest.getString("className"));
    }

    @DeleteMapping("/course-classes/{id}")
    @RequireRole("STUDENT")
    public DataResponse deleteMyCourseClass(@PathVariable Integer id) {
        return examStudentService.deleteMyCourseClass(id);
    }
}
