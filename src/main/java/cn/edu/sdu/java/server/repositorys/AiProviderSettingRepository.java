package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.AiProviderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiProviderSettingRepository extends JpaRepository<AiProviderSetting, Integer> {
    List<AiProviderSetting> findAllByOrderByProviderIdAsc();

    List<AiProviderSetting> findByEnabledOrderByProviderIdAsc(Integer enabled);
}
