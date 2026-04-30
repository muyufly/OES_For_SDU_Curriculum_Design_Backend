package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "student_exam_attempt",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"person_id", "exam_id"})
        })
public class StudentExamAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Integer attemptId;

    @ManyToOne
    @JoinColumn(name = "person_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Size(max = 20)
    @Column(nullable = false)
    private String status;

    @Size(max = 20)
    @Column(name = "start_time")
    private String startTime;

    @Size(max = 20)
    @Column(name = "last_save_time")
    private String lastSaveTime;

    @Size(max = 20)
    @Column(name = "submit_time")
    private String submitTime;

    @Column(name = "auto_ended", nullable = false)
    private Integer autoEnded;
}
