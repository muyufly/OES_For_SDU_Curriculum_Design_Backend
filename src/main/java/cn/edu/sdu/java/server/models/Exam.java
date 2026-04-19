package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Exam 考试主表实体类
 * Integer examId 考试表 exam 主键 exam_id
 * String title 考试名称
 * Course course 关联课程（可选）
 * String startTime 考试开始时间
 * String endTime 考试结束时间
 * String status 考试状态: DRAFT/OPEN/CLOSED
 * Integer creatorId 创建人 person_id
 * String createTime 创建时间
 */
@Getter
@Setter
@Entity
@Table(name = "exam")
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer examId;

    @Size(max = 100)
    @Column(nullable = false)
    private String title;

    @ManyToOne
    @JoinColumn(name = "courseId")
    private Course course;

    @Column(nullable = false)
    private String startTime;

    @Column(nullable = false)
    private String endTime;

    @Size(max = 10)
    @Column(nullable = false)
    private String status;

    private Integer creatorId;

    @Size(max = 20)
    private String createTime;
}
