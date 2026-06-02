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
import org.springframework.transaction.annotation.Transactional;

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
    private final ClassInfoRepository classInfoRepository;
    private final StudentCourseClassRepository studentCourseClassRepository;
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
                            ClassInfoRepository classInfoRepository,
                            StudentCourseClassRepository studentCourseClassRepository,
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
        this.classInfoRepository = classInfoRepository;
        this.studentCourseClassRepository = studentCourseClassRepository;
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

    @Transactional
    public DataResponse getTeachers(String keyword) {
        repairTeacherProfiles();
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
        for (ClassInfo classInfo : classInfoRepository.findAll()) {
            if (classInfo.getClassName() != null && !classInfo.getClassName().isBlank()) {
                classes.add(classInfo.getClassName());
            }
        }
        for (Student student : studentRepository.findAll()) {
            if (student.getClassName() != null && !student.getClassName().isBlank()) {
                classes.add(student.getClassName());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String className : classes) {
            result.add(classToMap(className));
        }
        return CommonMethod.getReturnData(result);
    }

    @Transactional
    public DataResponse saveClass(Integer classId, Map<String, Object> form) {
        String className = Objects.toString(form == null ? null : form.get("className"), "").trim();
        if (className.isBlank()) {
            return CommonMethod.getReturnMessageError("班级名称不能为空");
        }
        if (className.length() > 50) {
            return CommonMethod.getReturnMessageError("班级名称不能超过50个字符");
        }

        Optional<ClassInfo> sameName = classInfoRepository.findByClassName(className);
        if (sameName.isPresent() && (classId == null || !sameName.get().getClassId().equals(classId))) {
            return CommonMethod.getReturnMessageError("班级名称已存在");
        }

        ClassInfo classInfo;
        String oldName = null;
        if (classId == null) {
            classInfo = new ClassInfo();
        } else {
            Optional<ClassInfo> op = classInfoRepository.findById(classId);
            if (op.isEmpty()) {
                return CommonMethod.getReturnMessageError("班级不存在");
            }
            classInfo = op.get();
            oldName = classInfo.getClassName();
        }

        classInfo.setClassName(className);
        classInfo.setCollege(text(form, "college"));
        classInfo.setMajor(text(form, "major"));
        classInfo.setGrade(text(form, "grade"));
        classInfoRepository.save(classInfo);

        if (oldName != null && !oldName.equals(className)) {
            renameClassReferences(oldName, className);
        }
        return CommonMethod.getReturnData(classToMap(className), "班级已保存");
    }

    @Transactional
    public DataResponse deleteClass(Integer classId) {
        if (classId == null) {
            return CommonMethod.getReturnMessageError("班级ID不能为空");
        }
        Optional<ClassInfo> op = classInfoRepository.findById(classId);
        if (op.isEmpty()) {
            return CommonMethod.getReturnMessageError("班级不存在");
        }
        String className = op.get().getClassName();
        int studentCount = studentRepository.findByClassName(className).size();
        int enrollmentCount = studentCourseClassRepository.findByClassName(className).size();
        int bindingCount = teacherClassRepository.findByClassName(className).size();
        if (studentCount > 0 || enrollmentCount > 0 || bindingCount > 0) {
            return CommonMethod.getReturnMessageError("该班级已有学生或教师绑定，不能删除");
        }
        classInfoRepository.deleteById(classId);
        return CommonMethod.getReturnMessageOK("班级已删除");
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

    public DataResponse getCourseClassStudents(Integer courseId, String className) {
        if (courseId == null || className == null || className.isBlank()) {
            return CommonMethod.getReturnMessageError("参数不完整");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentCourseClass enrollment : studentCourseClassRepository.findByCourseCourseIdAndClassName(courseId, className)) {
            result.add(enrollmentToMap(enrollment));
        }
        return CommonMethod.getReturnData(result);
    }

    @Transactional
    public DataResponse assignStudentCourseClass(Integer studentId, Integer courseId, String className) {
        if (studentId == null || courseId == null || className == null || className.isBlank()) {
            return CommonMethod.getReturnMessageError("参数不完整");
        }
        Optional<Student> studentOp = studentRepository.findById(studentId);
        if (studentOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("学生不存在");
        }
        Optional<Course> courseOp = courseRepository.findById(courseId);
        if (courseOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("课程不存在");
        }
        StudentCourseClass enrollment = studentCourseClassRepository
                .findByStudentPersonIdAndCourseCourseId(studentId, courseId)
                .orElseGet(StudentCourseClass::new);
        enrollment.setStudent(studentOp.get());
        enrollment.setCourse(courseOp.get());
        enrollment.setClassName(className.trim());
        studentCourseClassRepository.save(enrollment);
        return CommonMethod.getReturnData(enrollmentToMap(enrollment), "学生已加入课程班级");
    }

    public DataResponse deleteStudentCourseClass(Integer id) {
        if (id == null) {
            return CommonMethod.getReturnMessageError("关系ID不能为空");
        }
        if (!studentCourseClassRepository.existsById(id)) {
            return CommonMethod.getReturnMessageError("关系不存在");
        }
        studentCourseClassRepository.deleteById(id);
        return CommonMethod.getReturnMessageOK("学生课程班级关系已删除");
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
    @Transactional
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
        syncPersonType(user, roleName);
        ensureRoleProfile(user, roleName);
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
    @Transactional
    public DataResponse assignTeacherClass(Integer teacherId, Integer courseId, String className) {
        if (teacherId == null || courseId == null || className == null || className.isEmpty()) {
            return CommonMethod.getReturnMessageError("参数不完整");
        }

        ensureTeacherProfileForUser(teacherId);
        Optional<Teacher> teacherOp = teacherRepository.findById(teacherId);
        if (teacherOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("教师不存在");
        }
        Optional<Course> courseOp = courseRepository.findById(courseId);
        if (courseOp.isEmpty()) {
            return CommonMethod.getReturnMessageError("课程不存在");
        }

        // 检查是否已存在绑定
        Optional<TeacherClass> existOp = teacherClassRepository
                .findByTeacherPersonIdAndCourseCourseIdAndClassName(teacherId, courseId, className);
        if (existOp.isPresent()) {
            return CommonMethod.getReturnMessageError("该教师已绑定此课程班级");
        }

        TeacherClass tc = new TeacherClass();
        tc.setTeacher(teacherOp.get());
        tc.setCourse(courseOp.get());
        tc.setClassName(className);
        teacherClassRepository.save(tc);

        Map<String, Object> data = new HashMap<>();
        data.put("id", tc.getId());
        data.put("teacherId", teacherId);
        data.put("courseId", courseId);
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

    private Map<String, Object> classToMap(String className) {
        Map<String, Object> row = new LinkedHashMap<>();
        Optional<ClassInfo> classInfo = classInfoRepository.findByClassName(className);
        row.put("classId", classInfo.map(ClassInfo::getClassId).orElse(null));
        row.put("className", className);
        row.put("college", classInfo.map(ClassInfo::getCollege).orElse(""));
        row.put("major", classInfo.map(ClassInfo::getMajor).orElse(""));
        row.put("grade", classInfo.map(ClassInfo::getGrade).orElse(""));
        Set<Integer> studentIds = new HashSet<>();
        studentRepository.findByClassName(className).forEach(s -> studentIds.add(s.getPersonId()));
        studentCourseClassRepository.findByClassName(className).forEach(scc -> studentIds.add(scc.getStudent().getPersonId()));
        row.put("studentCount", studentIds.size());
        row.put("teacherCount", teacherClassRepository.findByClassName(className).size());
        row.put("managed", classInfo.isPresent());
        return row;
    }

    private void renameClassReferences(String oldName, String newName) {
        List<Student> students = studentRepository.findByClassName(oldName);
        for (Student student : students) {
            student.setClassName(newName);
        }
        studentRepository.saveAll(students);

        List<TeacherClass> bindings = teacherClassRepository.findByClassName(oldName);
        for (TeacherClass binding : bindings) {
            binding.setClassName(newName);
        }
        teacherClassRepository.saveAll(bindings);

        List<StudentCourseClass> enrollments = studentCourseClassRepository.findByClassName(oldName);
        for (StudentCourseClass enrollment : enrollments) {
            enrollment.setClassName(newName);
        }
        studentCourseClassRepository.saveAll(enrollments);
    }

    private String text(Map<String, Object> form, String key) {
        Object value = form == null ? null : form.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private void repairTeacherProfiles() {
        for (User user : userRepository.findAll()) {
            String roleName = user.getUserType() == null ? "" : user.getUserType().getName();
            if ("ROLE_TEACHER".equals(roleName)) {
                syncPersonType(user, roleName);
                ensureRoleProfile(user, roleName);
            }
        }
    }

    private void ensureTeacherProfileForUser(Integer personId) {
        Optional<User> userOp = userRepository.findById(personId);
        if (userOp.isEmpty()) {
            return;
        }
        User user = userOp.get();
        String roleName = user.getUserType() == null ? "" : user.getUserType().getName();
        if ("ROLE_TEACHER".equals(roleName)) {
            syncPersonType(user, roleName);
            ensureRoleProfile(user, roleName);
        }
    }

    private void ensureRoleProfile(User user, String roleName) {
        if (user == null || user.getPersonId() == null) {
            return;
        }
        if ("ROLE_TEACHER".equals(roleName) && !teacherRepository.existsById(user.getPersonId())) {
            Teacher teacher = new Teacher();
            teacher.setPersonId(user.getPersonId());
            teacherRepository.saveAndFlush(teacher);
        }
        if ("ROLE_STUDENT".equals(roleName) && !studentRepository.existsById(user.getPersonId())) {
            Student student = new Student();
            student.setPersonId(user.getPersonId());
            studentRepository.saveAndFlush(student);
        }
    }

    private void syncPersonType(User user, String roleName) {
        if (user == null || user.getPerson() == null) {
            return;
        }
        if ("ROLE_ADMIN".equals(roleName)) {
            user.getPerson().setType("0");
        } else if ("ROLE_STUDENT".equals(roleName)) {
            user.getPerson().setType("1");
        } else if ("ROLE_TEACHER".equals(roleName)) {
            user.getPerson().setType("2");
        }
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
        if (binding.getCourse() != null) {
            map.put("courseId", binding.getCourse().getCourseId());
            map.put("courseNum", binding.getCourse().getNum());
            map.put("courseName", binding.getCourse().getName());
            map.put("studentCount", studentCourseClassRepository
                    .findByCourseCourseIdAndClassName(binding.getCourse().getCourseId(), binding.getClassName()).size());
        } else {
            map.put("courseId", null);
            map.put("courseNum", "");
            map.put("courseName", "");
            map.put("studentCount", studentRepository.findByClassName(binding.getClassName()).size());
        }
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

    private Map<String, Object> enrollmentToMap(StudentCourseClass enrollment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", enrollment.getId());
        map.put("className", enrollment.getClassName());
        if (enrollment.getCourse() != null) {
            map.put("courseId", enrollment.getCourse().getCourseId());
            map.put("courseNum", enrollment.getCourse().getNum());
            map.put("courseName", enrollment.getCourse().getName());
        }
        Student student = enrollment.getStudent();
        if (student != null) {
            map.put("studentId", student.getPersonId());
            if (student.getPerson() != null) {
                map.put("studentNum", student.getPerson().getNum());
                map.put("studentName", student.getPerson().getName());
            }
            map.put("legacyClassName", student.getClassName());
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
