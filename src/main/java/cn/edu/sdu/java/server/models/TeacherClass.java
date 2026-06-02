package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * TeacherClass 教师-班级绑定表实体类
 * Integer id 绑定表主键
 * Teacher teacher 关联教师
 * String className 班级名称（对应 student.class_name）
 */
@Getter
@Setter
@Entity
@Table(name = "teacher_class",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"teacher_id", "course_id", "class_name"})
        })
public class TeacherClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @Size(max = 50)
    @Column(nullable = false, name = "class_name")
    private String className;
}
