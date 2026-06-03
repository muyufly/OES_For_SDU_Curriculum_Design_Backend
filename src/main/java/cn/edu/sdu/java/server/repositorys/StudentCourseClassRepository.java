package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.StudentCourseClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentCourseClassRepository extends JpaRepository<StudentCourseClass, Integer> {
    List<StudentCourseClass> findByCourseCourseId(Integer courseId);
    List<StudentCourseClass> findByCourseCourseIdAndClassName(Integer courseId, String className);
    List<StudentCourseClass> findByStudentPersonId(Integer personId);
    Optional<StudentCourseClass> findByStudentPersonIdAndCourseCourseId(Integer personId, Integer courseId);
    List<StudentCourseClass> findByClassName(String className);
}
