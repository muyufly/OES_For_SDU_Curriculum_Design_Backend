package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.EUserType;
import cn.edu.sdu.java.server.models.Person;
import cn.edu.sdu.java.server.models.Student;
import cn.edu.sdu.java.server.models.Teacher;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.payload.response.JwtResponse;
import cn.edu.sdu.java.server.repositorys.PersonRepository;
import cn.edu.sdu.java.server.repositorys.StudentRepository;
import cn.edu.sdu.java.server.repositorys.TeacherRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.repositorys.UserTypeRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import cn.edu.sdu.java.server.util.LoginControlUtil;
import jakarta.validation.Valid;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {
    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;

    public AuthService(PersonRepository personRepository,
                       UserRepository userRepository,
                       UserTypeRepository userTypeRepository,
                       StudentRepository studentRepository,
                       TeacherRepository teacherRepository,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       PasswordEncoder encoder,
                       ResourceLoader resourceLoader) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.encoder = encoder;
    }

    public ResponseEntity<?> authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        Optional<User> op = userRepository.findByUserName(loginRequest.getUsername());
        if (op.isPresent()) {
            User user = op.get();
            user.setLastLoginTime(DateTimeTool.parseDateTime(new Date()));
            Integer count = user.getLoginCount();
            if (count == null) {
                count = 1;
            } else {
                count += 1;
            }
            user.setLoginCount(count);
            userRepository.save(user);
        }
        String jwt = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getPerName(),
                roles.getFirst()));
    }

    public DataResponse getValidateCode(DataRequest dataRequest) {
        return CommonMethod.getReturnData(LoginControlUtil.getInstance().getValidateCodeDataMap());
    }

    public DataResponse testValidateInfo(DataRequest dataRequest) {
        Integer validateCodeId = dataRequest.getInteger("validateCodeId");
        String validateCode = dataRequest.getString("validateCode");
        LoginControlUtil loginControl = LoginControlUtil.getInstance();
        if (validateCodeId == null || validateCode == null || validateCode.isEmpty()) {
            return CommonMethod.getReturnMessageError("验证码不能为空！");
        }
        String value = loginControl.getValidateCode(validateCodeId);
        if (!validateCode.equals(value)) {
            return CommonMethod.getReturnMessageError("验证码不正确！");
        }
        return CommonMethod.getReturnMessageOK();
    }

    @PostMapping("/registerUser")
    public DataResponse registerUser(@Valid @RequestBody DataRequest dataRequest) {
        String username = dataRequest.getString("username");
        String password = dataRequest.getString("password");
        String perName = dataRequest.getString("perName");
        String email = dataRequest.getString("email");
        String role = dataRequest.getString("role");
        UserType userType = null;

        if (username == null || username.isBlank()) {
            return CommonMethod.getReturnMessageError("用户名不能为空！");
        }
        username = username.trim();
        if (username.length() > 20) {
            return CommonMethod.getReturnMessageError("用户名不能超过20个字符！");
        }
        if (password == null || password.isBlank()) {
            return CommonMethod.getReturnMessageError("密码不能为空！");
        }
        if (perName == null || perName.isBlank()) {
            return CommonMethod.getReturnMessageError("姓名不能为空！");
        }
        perName = perName.trim();
        if (perName.length() > 50) {
            return CommonMethod.getReturnMessageError("姓名不能超过50个字符！");
        }
        if (email != null) {
            email = email.trim();
            if (email.length() > 60) {
                return CommonMethod.getReturnMessageError("邮箱不能超过60个字符！");
            }
            if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                return CommonMethod.getReturnMessageError("邮箱格式不正确！");
            }
        }
        if (!"ADMIN".equals(role) && !"STUDENT".equals(role) && !"TEACHER".equals(role)) {
            return CommonMethod.getReturnMessageError("注册角色不正确！");
        }

        Optional<User> existingUser = userRepository.findByUserName(username);
        if (existingUser.isPresent()) {
            return CommonMethod.getReturnMessageError("用户已经存在，不能注册！");
        }

        Person person = new Person();
        person.setNum(username);
        person.setName(perName);
        person.setEmail(email);
        if ("ADMIN".equals(role)) {
            person.setType("0");
            userType = userTypeRepository.findByName(EUserType.ROLE_ADMIN.name());
        } else if ("STUDENT".equals(role)) {
            person.setType("1");
            userType = userTypeRepository.findByName(EUserType.ROLE_STUDENT.name());
        } else if ("TEACHER".equals(role)) {
            person.setType("2");
            userType = userTypeRepository.findByName(EUserType.ROLE_TEACHER.name());
        }

        personRepository.saveAndFlush(person);

        User user = new User();
        user.setPersonId(person.getPersonId());
        user.setUserType(userType);
        user.setUserName(username);
        user.setPassword(encoder.encode(password));
        user.setCreateTime(DateTimeTool.parseDateTime(new Date()));
        user.setCreatorId(person.getPersonId());
        user.setLoginCount(0);
        userRepository.saveAndFlush(user);

        if ("STUDENT".equals(role)) {
            Student student = new Student();
            student.setPersonId(person.getPersonId());
            studentRepository.saveAndFlush(student);
        } else if ("TEACHER".equals(role)) {
            Teacher teacher = new Teacher();
            teacher.setPersonId(person.getPersonId());
            teacherRepository.saveAndFlush(teacher);
        }

        return CommonMethod.getReturnMessageOK();
    }
}
