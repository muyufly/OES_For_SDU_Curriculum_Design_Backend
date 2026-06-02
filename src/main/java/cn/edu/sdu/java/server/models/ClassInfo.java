package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "class_info",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "class_name")
        })
public class ClassInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "class_id")
    private Integer classId;

    @Size(max = 50)
    @Column(nullable = false, name = "class_name")
    private String className;

    @Size(max = 80)
    private String college;

    @Size(max = 80)
    private String major;

    @Size(max = 20)
    private String grade;
}
