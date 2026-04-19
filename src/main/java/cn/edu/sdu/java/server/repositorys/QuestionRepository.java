package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Integer> {

    /**
     * 根据考试ID查找所有题目
     */
    List<Question> findByExamExamId(Integer examId);
}
