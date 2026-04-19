package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Integer> {

    /**
     * 查找状态为指定值的考试列表
     */
    List<Exam> findByStatus(String status);

    /**
     * 查找当前时间范围内且状态为 OPEN 的考试（使用数据库当前时间）
     */
    @Query("SELECT e FROM Exam e WHERE e.status = 'OPEN'")
    List<Exam> findOpenExams();

    /**
     * 根据创建人查找考试
     */
    List<Exam> findByCreatorId(Integer creatorId);
}
