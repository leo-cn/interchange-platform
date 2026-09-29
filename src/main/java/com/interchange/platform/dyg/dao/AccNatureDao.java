package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtAccNature;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * 账户性质 {@code BT_ACC_NATURE} 的存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class AccNatureDao extends BaseDao {

    /**
     * 按性质编码取记录。
     * {@code nature_code} 在库里可能是定长 CHAR，必须用 {@code trim} 比较，
     * 否则等值匹配不上。
     */
    public Optional<BtAccNature> findByCode(String code) {
        return Optional.ofNullable(oneBy("from BtAccNature where trim(natureCode) = :code",
                Map.of("code", code.trim())));
    }

    public BtAccNature save(BtAccNature entity) {
        return super.save(entity);
    }
}
