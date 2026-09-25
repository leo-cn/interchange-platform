package com.interchange.platform.repository;

import com.interchange.platform.entity.ReceiveApi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiveApiRepository extends JpaRepository<ReceiveApi, Long> {

    Optional<ReceiveApi> findByApiCode(String apiCode);

    /** 已启用的接口（按名称排序，页面与缓存都用它） */
    List<ReceiveApi> findAllByStatusOrderByNameAsc(Integer status);

    List<ReceiveApi> findAllByOrderByIdAsc();
}
