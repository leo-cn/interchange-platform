package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.ReceiveApi;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 接收接口清单的存取。 */
@Repository
public class ReceiveApiDao extends BaseDao {

    public Optional<ReceiveApi> findById(Long id) {
        return find(ReceiveApi.class, id);
    }

    public ReceiveApi get(Long id, String notFoundMessage) {
        return load(ReceiveApi.class, id, notFoundMessage);
    }

    public Optional<ReceiveApi> findByApiCode(String apiCode) {
        return Optional.ofNullable(one("from ReceiveApi where apiCode = ?1", apiCode));
    }

    public List<ReceiveApi> findAll() {
        return list("from ReceiveApi order by id asc");
    }

    /** 已启用的接口（按名称排序，页面与缓存都用它） */
    public List<ReceiveApi> findByStatus(Integer status) {
        return list("from ReceiveApi where status = ?1 order by name asc", status);
    }

    public ReceiveApi save(ReceiveApi entity) {
        return super.save(entity);
    }

    public void delete(ReceiveApi entity) {
        remove(entity);
    }
}
