package com.interchange.platform.repository;

import com.interchange.platform.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    Optional<Partner> findByPartnerCode(String partnerCode);

    List<Partner> findAllByOrderByIdDesc();

    List<Partner> findByStatusOrderByIdAsc(Integer status);

    long countByStatus(Integer status);
}
