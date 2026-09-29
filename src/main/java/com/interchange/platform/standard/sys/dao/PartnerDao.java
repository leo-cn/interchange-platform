package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.Partner;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 第三方系统配置的存取。 */
@Repository
public class PartnerDao extends BaseDao {

    public Optional<Partner> findById(Long id) {
        return find(Partner.class, id);
    }

    public Partner get(Long id, String notFoundMessage) {
        return load(Partner.class, id, notFoundMessage);
    }

    public Optional<Partner> findByPartnerCode(String partnerCode) {
        return Optional.ofNullable(one("from Partner where partnerCode = ?1", partnerCode));
    }

    public List<Partner> findAllDesc() {
        return list("from Partner order by id desc");
    }

    public List<Partner> findByStatus(Integer status) {
        return list("from Partner where status = ?1 order by id asc", status);
    }

    public long countAll() {
        return count("select count(p) from Partner p");
    }

    public long countByStatus(Integer status) {
        return count("select count(p) from Partner p where p.status = ?1", status);
    }

    public Partner save(Partner entity) {
        return super.save(entity);
    }

    public void delete(Partner entity) {
        remove(entity);
    }
}
