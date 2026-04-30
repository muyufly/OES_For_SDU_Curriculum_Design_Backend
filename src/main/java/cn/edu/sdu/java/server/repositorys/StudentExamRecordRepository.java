package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.StudentExamRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentExamRecordRepository extends JpaRepository<StudentExamRecord, Integer> {

    /**
     * 根据学生ID和考试ID查找作答记录
     */
    List<StudentExamRecord> findByStudentPersonIdAndExamExamId(Integer personId, Integer examId);

    /**
     * 根据考试ID查找所有作答记录
     */
    List<StudentExamRecord> findByExamExamId(Integer examId);

    Optional<StudentExamRecord> findByStudentPersonIdAndExamExamIdAndQuestionQuestionId(Integer personId, Integer examId, Integer questionId);

    /**
     * 查询某个学生是否已提交过某场考试
     */
    boolean existsByStudentPersonIdAndExamExamId(Integer personId, Integer examId);

    /**
     * 根据学生ID列表和考试ID查找作答记录
     */
    @Query("SELECT r FROM StudentExamRecord r WHERE r.student.personId IN :personIds AND r.exam.examId = :examId")
    List<StudentExamRecord> findByStudentPersonIdInAndExamExamId(List<Integer> personIds, Integer examId);

    /**
     * 根据学生ID列表查找所有作答记录
     */
    @Query("SELECT r FROM StudentExamRecord r WHERE r.student.personId IN :personIds")
    List<StudentExamRecord> findByStudentPersonIdIn(List<Integer> personIds);

    boolean existsByQuestionQuestionId(Integer questionId);
}
