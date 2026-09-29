package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.SysRegion;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * 行政区划 {@code SYS_REGION} 的只读存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class RegionDao extends BaseDao {

    /** 按银行城市码取区划 */
    public Optional<SysRegion> findByBankInputCity(String bankInputCity) {
        return Optional.ofNullable(oneBy("from SysRegion where bankInputCity = :city",
                Map.of("city", bankInputCity)));
    }
}
