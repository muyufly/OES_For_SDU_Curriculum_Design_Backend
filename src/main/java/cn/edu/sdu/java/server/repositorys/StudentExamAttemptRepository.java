package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.StudentExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentExamAttemptRepository extends JpaRepository<StudentExamAttempt, Integer> {
    Optional<StudentExamAttempt> findByStudentPersonIdAndExamExamId(Integer personId, Integer examId);

    List<StudentExamAttempt> findByExamExamId(Integer examId);

    List<StudentExamAttempt> findByStudentPersonId(Integer personId);

    List<StudentExamAttempt> findByExamExamIdAndStatus(Integer examId, String status);

    @Query("SELECT a FROM StudentExamAttempt a WHERE a.exam.examId = :examId AND a.student.personId IN :personIds")
    List<StudentExamAttempt> findByExamExamIdAndStudentPersonIdIn(Integer examId, List<Integer> personIds);
}
