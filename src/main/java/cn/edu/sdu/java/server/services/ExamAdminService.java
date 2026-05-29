package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.services.ai.AiGradingService;
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
    private final ExamRepository examRepository;
    private final CourseRepository courseRepository;
    private final StudentExamAttemptRepository studentExamAttemptRepository;
    private final StudentExamRecordRepository studentExamRecordRepository;
    private final AiGradingService aiGradingService;

    public ExamAdminService(UserRepository userRepository,
                            UserTypeRepository userTypeRepository,
                            TeacherRepository teacherRepository,
                            TeacherClassRepository teacherClassRepository,
                            ScoreRepository scoreRepository,
                            StudentRepository studentRepository,
                            ExamRepository examRepository,
                            CourseRepository courseRepository,
                            StudentExamAttemptRepository studentExamAttemptRepository,
                            StudentExamRecordRepository studentExamRecordRepository,
                            AiGradingService aiGradingService) {
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.teacherRepository = teacherRepository;
        this.teacherClassRepository = teacherClassRepository;
        this.scoreRepository = scoreRepository;
        this.studentRepository = studentRepository;
        this.examRepository = examRepository;
        this.courseRepository = courseRepository;
        this.studentExamAttemptRepository = studentExamAttemptRepository;
        this.studentExamRecordRepository = studentExamRecordRepository;
        this.aiGradingService = aiGradingService;
    }

    public DataResponse getAiProviders() {
        return CommonMethod.getReturnData(aiGradingService.listAdminProviders());
    }

    public DataResponse saveAiProvider(Integer id, Map<String, Object> form) {
        try {
            return CommonMethod.getReturnData(aiGradingService.saveProvider(id, form), "AI API配置已保存");
        } catch (Exception e) {
            return CommonMethod.getReturnMessageError(e.getMessage());
        }
    }

    public DataResponse deleteAiProvider(Integer id) {
        try {
            aiGradingService.deleteProvider(id);
            return CommonMethod.getReturnMessageOK("AI API配置已删除");
        } catch (Exception e) {
            return CommonMethod.getReturnMessageError(e.getMessage());
        }
    }

    public DataResponse getOverview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userRepository.count());
        data.put("studentCount", studentRepository.count());
        data.put("teacherCount", teacherRepository.count());
        data.put("courseCount", courseRepository.count());
        data.put("examCount", examRepository.count());
        data.put("scoreCount", scoreRepository.count());
        data.put("attemptCount", studentExamAttemptRepository.count());
        data.put("endedAttemptCount", studentExamAttemptRepository.findAll().stream()
                .filter(a -> "ENDED".equals(a.getStatus())).count());
        data.put("draftAttemptCount", studentExamAttemptRepository.findAll().stream()
                .filter(a -> "DRAFT".equals(a.getStatus())).count());
        data.put("openExamCount", examRepository.findAll().stream()
                .filter(e -> "OPEN".equals(e.getStatus())).count());

        List<Map<String, Object>> statusRows = new ArrayList<>();
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (Exam exam : examRepository.findAll()) {
            statusCount.put(exam.getStatus(), statusCount.getOrDefault(exam.getStatus(), 0L) + 1);
        }
        for (Map.Entry<String, Long> entry : statusCount.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", entry.getKey());
            row.put("count", entry.getValue());
            statusRows.add(row);
        }
        data.put("examStatusRows", statusRows);
        return CommonMethod.getReturnData(data);
    }

    public DataResponse getUsers(String keyword, String roleName) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String role = normalizeRoleFilter(roleName);
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            Map<String, Object> map = userToMap(user);
            String roleValue = Objects.toString(map.get("roleName"), "");
            if (role != null && !role.equals(roleValue)) {
                continue;
            }
            String haystack = (Objects.toString(map.get("userName"), "") + " "
                    + Objects.toString(map.get("personNum"), "") + " "
                    + Objects.toString(map.get("personName"), "") + " "
                    + Objects.toString(map.get("dept"), "")).toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && !haystack.contains(key)) {
                continue;
            }
            result.add(map);
        }
        result.sort(Comparator.comparing(m -> Objects.toString(m.get("userName"), "")));
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getTeachers(String keyword) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Teacher teacher : teacherRepository.findAll()) {
            Map<String, Object> map = teacherToMap(teacher);
            String haystack = (Objects.toString(map.get("teacherNum"), "") + " "
                    + Objects.toString(map.get("teacherName"), "") + " "
                    + Objects.toString(map.get("dept"), "")).toLowerCase(Locale.ROOT);
            if (key.isEmpty() || haystack.contains(key)) {
                result.add(map);
            }
        }
        result.sort(Comparator.comparing(m -> Objects.toString(m.get("teacherNum"), "")));
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getClasses() {
        Set<String> classes = new TreeSet<>();
        for (Student student : studentRepository.findAll()) {
            if (student.getClassName() != null && !student.getClassName().isBlank()) {
                classes.add(student.getClassName());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String className : classes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("className", className);
            row.put("studentCount", studentRepository.findByClassName(className).size());
            result.add(row);
        }
        return CommonMethod.getReturnData(result);
    }

    public DataResponse getTeacherClasses(Integer teacherId) {
        List<TeacherClass> bindings = teacherId == null || teacherId <= 0
                ? teacherClassRepository.findAll()
                : teacherClassRepository.findByTeacherPersonId(teacherId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeacherClass binding : bindings) {
            result.add(teacherClassToMap(binding));
        }
        result.sort(Comparator.comparing(m -> Objects.toString(m.get("teacherName"), "")));
        return CommonMethod.getReturnData(result);
    }

    public DataResponse deleteTeacherClass(Integer id) {
        if (id == null) {
            return CommonMethod.getReturnMessageError("绑定ID不能为空");
        }
        if (!teacherClassRepository.existsById(id)) {
            return CommonMethod.getReturnMessageError("绑定关系不存在");
        }
        teacherClassRepository.deleteById(id);
        return CommonMethod.getReturnMessageOK("绑定关系已删除");
    }

    public DataResponse getExams(String keyword, String status) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Exam exam : examRepository.findAll()) {
            if (normalizedStatus != null && !normalizedStatus.equals(exam.getStatus())) {
                continue;
            }
            Map<String, Object> map = examToAdminMap(exam);
            String haystack = (Objects.toString(map.get("title"), "") + " "
                    + Objects.toString(map.get("courseName"), "") + " "
                    + Objects.toString(map.get("creatorName"), "")).toLowerCase(Locale.ROOT);
            if (key.isEmpty() || haystack.contains(key)) {
                result.add(map);
            }
        }
        result.sort((a, b) -> Integer.compare(
                asInt(b.get("examId")),
                asInt(a.get("examId"))));
        return CommonMethod.getReturnData(result);
    }

    public DataResponse updateExamStatus(Integer examId, String status) {
        if (examId == null) {
            return CommonMethod.getReturnMessageError("试卷ID不能为空");
        }
        String normalized = normalizeExamStatus(status);
        if (normalized == null) {
            return CommonMethod.getReturnMessageError("状态必须为 DRAFT、OPEN 或 CLOSED");
        }
        Optional<Exam> examOp = examRepository.findById(examId);
        if (examOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("试卷不存在");
        }
        Exam exam = examOp.get();
        exam.setStatus(normalized);
        examRepository.save(exam);
        return CommonMethod.getReturnData(examToAdminMap(exam), "试卷状态已更新");
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

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("personId", user.getPersonId());
        map.put("userName", user.getUserName());
        map.put("roleName", user.getUserType() == null ? "" : user.getUserType().getName());
        map.put("loginCount", user.getLoginCount());
        map.put("lastLoginTime", user.getLastLoginTime());
        map.put("createTime", user.getCreateTime());
        Person person = user.getPerson();
        if (person != null) {
            map.put("personNum", person.getNum());
            map.put("personName", person.getName());
            map.put("personType", person.getType());
            map.put("dept", person.getDept());
            map.put("email", person.getEmail());
            map.put("phone", person.getPhone());
        }
        return map;
    }

    private Map<String, Object> teacherToMap(Teacher teacher) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("teacherId", teacher.getPersonId());
        Person person = teacher.getPerson();
        if (person != null) {
            map.put("teacherNum", person.getNum());
            map.put("teacherName", person.getName());
            map.put("dept", person.getDept());
            map.put("email", person.getEmail());
            map.put("phone", person.getPhone());
        }
        map.put("title", teacher.getTitle());
        map.put("degree", teacher.getDegree());
        map.put("classCount", teacherClassRepository.findByTeacherPersonId(teacher.getPersonId()).size());
        return map;
    }

    private Map<String, Object> teacherClassToMap(TeacherClass binding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", binding.getId());
        map.put("className", binding.getClassName());
        map.put("studentCount", studentRepository.findByClassName(binding.getClassName()).size());
        Teacher teacher = binding.getTeacher();
        if (teacher != null) {
            map.put("teacherId", teacher.getPersonId());
            Person person = teacher.getPerson();
            if (person != null) {
                map.put("teacherNum", person.getNum());
                map.put("teacherName", person.getName());
                map.put("dept", person.getDept());
            }
        }
        return map;
    }

    private Map<String, Object> examToAdminMap(Exam exam) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("examId", exam.getExamId());
        map.put("title", exam.getTitle());
        map.put("startTime", exam.getStartTime());
        map.put("endTime", exam.getEndTime());
        map.put("status", exam.getStatus());
        map.put("creatorId", exam.getCreatorId());
        map.put("createTime", exam.getCreateTime());
        if (exam.getCourse() != null) {
            map.put("courseId", exam.getCourse().getCourseId());
            map.put("courseName", exam.getCourse().getName());
            map.put("courseNum", exam.getCourse().getNum());
        }
        userRepository.findByPersonPersonId(exam.getCreatorId()).ifPresent(user -> {
            map.put("creatorUserName", user.getUserName());
            if (user.getPerson() != null) {
                map.put("creatorName", user.getPerson().getName());
            }
        });
        List<StudentExamAttempt> attempts = studentExamAttemptRepository.findByExamExamId(exam.getExamId());
        long ended = attempts.stream().filter(a -> "ENDED".equals(a.getStatus())).count();
        long draft = attempts.stream().filter(a -> "DRAFT".equals(a.getStatus())).count();
        long autoEnded = attempts.stream().filter(a -> Objects.equals(a.getAutoEnded(), 1)).count();
        map.put("attemptCount", attempts.size());
        map.put("endedCount", ended);
        map.put("draftCount", draft);
        map.put("autoEndedCount", autoEnded);
        map.put("recordCount", studentExamRecordRepository.findByExamExamId(exam.getExamId()).size());
        return map;
    }

    private String normalizeRoleFilter(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        String role = roleName.trim().toUpperCase(Locale.ROOT);
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    private String normalizeExamStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"DRAFT".equals(normalized) && !"OPEN".equals(normalized) && !"CLOSED".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (Exception e) {
            return 0;
        }
    }
}
