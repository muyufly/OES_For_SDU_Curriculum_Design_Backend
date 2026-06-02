package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.ClassInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassInfoRepository extends JpaRepository<ClassInfo, Integer> {
    Optional<ClassInfo> findByClassName(String className);
    boolean existsByClassName(String className);
}
