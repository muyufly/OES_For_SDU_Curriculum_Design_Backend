package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * ExamQuestionRel 考试-题目关联表实体类
 * Integer relId 关联表主键 rel_id
 * Exam exam 关联考试
 * Question question 关联题目
 * Integer sortOrder 题目排序序号
 */
@Getter
@Setter
@Entity
@Table(name = "exam_question_rel",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"examId", "questionId"})
        })
public class ExamQuestionRel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rel_id")
    private Integer relId;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, name = "sort_order")
    private Integer sortOrder;
}
