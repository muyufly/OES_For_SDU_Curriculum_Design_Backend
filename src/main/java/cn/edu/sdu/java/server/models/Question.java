package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Question 题库表实体类
 * Integer questionId 题目表 question 主键 question_id
 * Exam exam 关联考试（可选，题目可复用）
 * String questionType 题目类型: CHOICE(单选)/READ(阅读)
 * String content 题目内容
 * String optionA/B/C/D 单选题选项
 * String answer 标准答案
 * Integer score 分值
 */
@Getter
@Setter
@Entity
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Integer questionId;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Size(max = 10)
    @Column(nullable = false, name = "question_type")
    private String questionType;

    @Column(nullable = false, columnDefinition = "TEXT", name = "content")
    private String content;

    @Size(max = 200)
    @Column(name = "option_a")
    private String optionA;

    @Size(max = 200)
    @Column(name = "option_b")
    private String optionB;

    @Size(max = 200)
    @Column(name = "option_c")
    private String optionC;

    @Size(max = 200)
    @Column(name = "option_d")
    private String optionD;

    @Size(max = 500)
    @Column(name = "answer")
    private String answer;

    @Column(nullable = false, name = "score")
    private Integer score;
}
