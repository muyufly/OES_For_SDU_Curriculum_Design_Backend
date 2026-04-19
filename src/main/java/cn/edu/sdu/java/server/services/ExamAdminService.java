package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ExamAdminService 管理员端业务逻辑
 * 提供用户角色修改、教师班级绑定、全局成绩查询等功能
 */
@Service
public class ExamAdminService {

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherClassRepository teacherClassRepository;
    private final ScoreRepository scoreRepository;
    private final StudentRepository studentRepository;

    public ExamAdminService(UserRepository userRepository,
                            UserTypeRepository userTypeRepository,
                            TeacherRepository teacherRepository,
                            TeacherClassRepository teacherClassRepository,
                            ScoreRepository scoreRepository,
                            StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.teacherRepository = teacherRepository;
        this.teacherClassRepository = teacherClassRepository;
        this.scoreRepository = scoreRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * 修改用户的系统权限角色
     * @param userId 用户的 personId
     * @param roleName 新角色: ROLE_ADMIN / ROLE_STUDENT / ROLE_TEACHER
     */
    public DataResponse updateUserRole(Integer userId, String roleName) {
        if (userId == null || roleName == null || roleName.isEmpty()) {
            return CommonMethod.getReturnMessageError("参数不完整");
        }

        Optional<User> userOp = userRepository.findById(userId);
        if (userOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("用户不存在");
        }

        // 标准化角色名称
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        UserType userType = userTypeRepository.findByName(roleName);
        if (userType == null) {
            return CommonMethod.getReturnMessageError("角色不存在: " + roleName);
        }

        User user = userOp.get();
        user.setUserType(userType);
        userRepository.save(user);

        Map<String, Object> data = new HashMap<>();
        data.put("personId", userId);
        data.put("newRole", roleName);
        return CommonMethod.getReturnData(data, "角色修改成功");
    }

    /**
     * 设置教师与班级的绑定关系
     * @param teacherId 教师的 personId
     * @param className 班级名称
     */
    public DataResponse assignTeacherClass(Integer teacherId, String className) {
        if (teacherId == null || className == null || className.isEmpty()) {
            return CommonMethod.getReturnMessageError("参数不完整");
        }

        Optional<Teacher> teacherOp = teacherRepository.findById(teacherId);
        if (teacherOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("教师不存在");
        }

        // 检查是否已存在绑定
        Optional<TeacherClass> existOp = teacherClassRepository
                .findByTeacherPersonIdAndClassName(teacherId, className);
        if (existOp.isPresent()) {
            return CommonMethod.getReturnMessageError("该教师已绑定此班级");
        }

        TeacherClass tc = new TeacherClass();
        tc.setTeacher(teacherOp.get());
        tc.setClassName(className);
        teacherClassRepository.save(tc);

        Map<String, Object> data = new HashMap<>();
        data.put("id", tc.getId());
        data.put("teacherId", teacherId);
        data.put("className", className);
        return CommonMethod.getReturnData(data, "绑定成功");
    }

    /**
     * 全局分页查询所有学生的成绩数据
     * @param currentPage 当前页码（0-based）
     * @param pageSize 每页条数
     */
    public DataResponse getAllScores(Integer currentPage, Integer pageSize) {
        if (currentPage == null || currentPage < 0) currentPage = 0;
        if (pageSize == null || pageSize <= 0) pageSize = 40;

        Pageable pageable = PageRequest.of(currentPage, pageSize);
        Page<Score> page = scoreRepository.findAll(pageable);

        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Score s : page.getContent()) {
            Map<String, Object> m = new HashMap<>();
            m.put("scoreId", s.getScoreId());
            if (s.getStudent() != null) {
                m.put("personId", s.getStudent().getPersonId());
                if (s.getStudent().getPerson() != null) {
                    m.put("studentNum", s.getStudent().getPerson().getNum());
                    m.put("studentName", s.getStudent().getPerson().getName());
                }
                m.put("className", s.getStudent().getClassName());
            }
            if (s.getCourse() != null) {
                m.put("courseId", s.getCourse().getCourseId());
                m.put("courseName", s.getCourse().getName());
                m.put("courseNum", s.getCourse().getNum());
            }
            m.put("mark", s.getMark());
            m.put("ranking", s.getRanking());
            dataList.add(m);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dataTotal", (int) page.getTotalElements());
        data.put("pageSize", pageSize);
        data.put("dataList", dataList);
        return CommonMethod.getReturnData(data);
    }
}
