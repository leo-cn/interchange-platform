package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.ApiToken;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 接入方令牌的存取。 */
@Repository
public class ApiTokenDao extends BaseDao {

    public Optional<ApiToken> findById(Long id) {
        return find(ApiToken.class, id);
    }

    public ApiToken get(Long id, String notFoundMessage) {
        return load(ApiToken.class, id, notFoundMessage);
    }

    public Optional<ApiToken> findByAppKey(String appKey) {
        return Optional.ofNullable(one("from ApiToken where appKey = ?1", appKey));
    }

    public List<ApiToken> findAll() {
        return list("from ApiToken order by id asc");
    }

    public ApiToken save(ApiToken entity) {
        return super.save(entity);
    }

    public void delete(ApiToken entity) {
        remove(entity);
    }
}
