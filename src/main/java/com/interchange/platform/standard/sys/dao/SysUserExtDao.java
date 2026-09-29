package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.SysUserExt;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 平台用户扩展属性的存取。
 * 只存平台专有字段，账号主数据在业务库。
 */
@Repository
public class SysUserExtDao extends BaseDao {

    /** 按业务系统用户主键查扩展属性 */
    public Optional<SysUserExt> findByExtId(String extId) {
        return Optional.ofNullable(one("from SysUserExt where extId = ?1", extId));
    }

    /** 按接口令牌反查（接收接口鉴权用） */
    public Optional<SysUserExt> findByApiToken(String apiToken) {
        return Optional.ofNullable(one("from SysUserExt where apiToken = ?1", apiToken));
    }

    public List<SysUserExt> findAll() {
        return list("from SysUserExt");
    }

    public SysUserExt save(SysUserExt entity) {
        return super.save(entity);
    }
}
