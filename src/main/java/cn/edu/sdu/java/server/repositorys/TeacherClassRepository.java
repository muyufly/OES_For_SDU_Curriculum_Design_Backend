package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.TeacherClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherClassRepository extends JpaRepository<TeacherClass, Integer> {

    /**
     * 根据教师ID查找其绑定的所有班级
     */
    List<TeacherClass> findByTeacherPersonId(Integer teacherId);

    /**
     * 根据教师ID和班级名称查找绑定记录
     */
    Optional<TeacherClass> findByTeacherPersonIdAndClassName(Integer teacherId, String className);
}
