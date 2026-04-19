package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * StudentExamRecord 学生答题记录表实体类
 * Integer recordId 答题记录主键 record_id
 * Student student 关联学生
 * Exam exam 关联考试
 * Question question 关联题目
 * String answer 学生作答内容
 * Integer score 该题得分
 * Integer graded 是否已批阅: 0=未批阅, 1=已批阅
 * String submitTime 提交时间
 */
@Getter
@Setter
@Entity
@Table(name = "student_exam_record")
public class StudentExamRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Integer recordId;

    @ManyToOne
    @JoinColumn(name = "person_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(columnDefinition = "TEXT", name = "answer")
    private String answer;

    @Column(name = "score")
    private Integer score;

    @Column(nullable = false, name = "graded")
    private Integer graded;

    @Size(max = 20)
    @Column(name = "submit_time")
    private String submitTime;
}
