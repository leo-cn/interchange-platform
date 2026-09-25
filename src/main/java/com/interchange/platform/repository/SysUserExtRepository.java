package com.interchange.platform.repository;

import com.interchange.platform.entity.SysUserExt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 平台用户扩展属性。注意这里只存平台专有字段，账号主数据在业务库。
 */
public interface SysUserExtRepository extends JpaRepository<SysUserExt, Long> {

    /** 按业务系统用户主键查扩展属性 */
    Optional<SysUserExt> findByExtId(String extId);

    /** 按接口令牌反查（接收接口鉴权用） */
    Optional<SysUserExt> findByApiToken(String apiToken);
}
