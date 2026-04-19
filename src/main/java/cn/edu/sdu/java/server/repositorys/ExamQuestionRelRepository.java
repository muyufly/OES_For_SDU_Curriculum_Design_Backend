package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.ExamQuestionRel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamQuestionRelRepository extends JpaRepository<ExamQuestionRel, Integer> {

    /**
     * 根据考试ID查找所有关联的题目，按排序序号排序
     */
    List<ExamQuestionRel> findByExamExamIdOrderBySortOrderAsc(Integer examId);

    /**
     * 根据考试ID和题目ID查询关联记录
     */
    ExamQuestionRel findByExamExamIdAndQuestionQuestionId(Integer examId, Integer questionId);
}
