package com.interchange.platform.repository;

import com.interchange.platform.entity.InterfaceTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface InterfaceTaskRepository extends JpaRepository<InterfaceTask, Long>,
        JpaSpecificationExecutor<InterfaceTask> {

    Optional<InterfaceTask> findByTaskCode(String taskCode);

    boolean existsByTaskCode(String taskCode);

    List<InterfaceTask> findByEnabledOrderByIdAsc(Boolean enabled);

    List<InterfaceTask> findAllByOrderByIdDesc();

    long countByEnabled(Boolean enabled);

    List<InterfaceTask> findByPartnerId(Long partnerId);
}
