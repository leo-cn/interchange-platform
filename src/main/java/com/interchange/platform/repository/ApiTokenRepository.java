package com.interchange.platform.repository;

import com.interchange.platform.entity.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByAppKey(String appKey);

    List<ApiToken> findAllByOrderByIdAsc();
}
