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
