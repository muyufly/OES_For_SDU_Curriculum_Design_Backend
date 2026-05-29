package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.ExamAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * ExamAdminController 管理员端 API
 * 提供用户角色管理、教师班级绑定、全局成绩查询等 Web 服务
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
public class ExamAdminController {

    private final ExamAdminService examAdminService;

    public ExamAdminController(ExamAdminService examAdminService) {
        this.examAdminService = examAdminService;
    }

    @GetMapping("/overview")
    @RequireRole("ADMIN")
    public DataResponse getOverview() {
        return examAdminService.getOverview();
    }

    @GetMapping("/ai/providers")
    @RequireRole("ADMIN")
    public DataResponse getAiProviders() {
        return examAdminService.getAiProviders();
    }

    @PostMapping("/ai/providers")
    @RequireRole("ADMIN")
    public DataResponse createAiProvider(@Valid @RequestBody DataRequest dataRequest) {
        return examAdminService.saveAiProvider(null, dataRequest.getMap("provider"));
    }

    @PutMapping("/ai/providers/{id}")
    @RequireRole("ADMIN")
    public DataResponse updateAiProvider(@PathVariable Integer id,
                                         @Valid @RequestBody DataRequest dataRequest) {
        return examAdminService.saveAiProvider(id, dataRequest.getMap("provider"));
    }

    @DeleteMapping("/ai/providers/{id}")
    @RequireRole("ADMIN")
    public DataResponse deleteAiProvider(@PathVariable Integer id) {
        return examAdminService.deleteAiProvider(id);
    }

    @GetMapping("/users")
    @RequireRole("ADMIN")
    public DataResponse getUsers(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String roleName) {
        return examAdminService.getUsers(keyword, roleName);
    }

    @GetMapping("/teachers")
    @RequireRole("ADMIN")
    public DataResponse getTeachers(@RequestParam(required = false) String keyword) {
        return examAdminService.getTeachers(keyword);
    }

    @GetMapping("/classes")
    @RequireRole("ADMIN")
    public DataResponse getClasses() {
        return examAdminService.getClasses();
    }

    @GetMapping("/teacher-classes")
    @RequireRole("ADMIN")
    public DataResponse getTeacherClasses(@RequestParam(required = false) Integer teacherId) {
        return examAdminService.getTeacherClasses(teacherId);
    }

    @DeleteMapping("/teacher-classes/{id}")
    @RequireRole("ADMIN")
    public DataResponse deleteTeacherClass(@PathVariable Integer id) {
        return examAdminService.deleteTeacherClass(id);
    }

    @GetMapping("/exams")
    @RequireRole("ADMIN")
    public DataResponse getExams(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String status) {
        return examAdminService.getExams(keyword, status);
    }

    @PutMapping("/exams/{examId}/status")
    @RequireRole("ADMIN")
    public DataResponse updateExamStatus(@PathVariable Integer examId,
                                         @Valid @RequestBody DataRequest dataRequest) {
        return examAdminService.updateExamStatus(examId, dataRequest.getString("status"));
    }

    /**
     * 修改用户的系统权限
     * PUT /api/admin/users/{userId}/role
     * 请求体: DataRequest，data 中包含 "roleName" (如 "ROLE_TEACHER")
     */
    @PutMapping("/users/{userId}/role")
    @RequireRole("ADMIN")
    public DataResponse updateUserRole(@PathVariable Integer userId,
                                       @Valid @RequestBody DataRequest dataRequest) {
        String roleName = dataRequest.getString("roleName");
        return examAdminService.updateUserRole(userId, roleName);
    }

    /**
     * 设置教师与班级的绑定关系
     * POST /api/admin/teachers/assign
     * 请求体: DataRequest，data 中包含 "teacherId" 和 "className"
     */
    @PostMapping("/teachers/assign")
    @RequireRole("ADMIN")
    public DataResponse assignTeacherClass(@Valid @RequestBody DataRequest dataRequest) {
        Integer teacherId = dataRequest.getInteger("teacherId");
        String className = dataRequest.getString("className");
        return examAdminService.assignTeacherClass(teacherId, className);
    }

    /**
     * 全局分页查询所有学生的成绩数据
     * GET /api/admin/scores/all
     * 查询参数: currentPage (页码), pageSize (每页条数)
     */
    @GetMapping("/scores/all")
    @RequireRole("ADMIN")
    public DataResponse getAllScores(@RequestParam(defaultValue = "0") Integer currentPage,
                                    @RequestParam(defaultValue = "40") Integer pageSize) {
        return examAdminService.getAllScores(currentPage, pageSize);
    }
}
