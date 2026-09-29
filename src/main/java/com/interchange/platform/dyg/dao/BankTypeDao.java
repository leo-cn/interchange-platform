package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BisBifInit;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 银行类别 {@code BT_BANK_TYPE} 及关联的接口初始化 {@code BIS_BIF_INIT} 的存取。
 * 写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class BankTypeDao extends BaseDao {

    /** 按 mdId 批量预加载：mdId → 银行类别 */
    public Map<String, BtBankType> mapByMdId(Collection<String> mdIds) {
        List<String> ids = clean(mdIds);
        Map<String, BtBankType> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (BtBankType row : this.<BtBankType>listBy("from BtBankType where mdId in (:ids)", Map.of("ids", ids))) {
            map.put(row.getMdId(), row);
        }
        return map;
    }

    public Optional<BtBankType> findByMdId(String mdId) {
        return Optional.ofNullable(this.<BtBankType>oneBy("from BtBankType where mdId = :mdId", Map.of("mdId", mdId)));
    }

    public Optional<BtBankType> findById(String id) {
        return find(BtBankType.class, id);
    }

    public BtBankType save(BtBankType entity) {
        return super.save(entity);
    }

    /** 接口初始化：按银行类别主键取 BIF 主键（账户落库时填 bif_code） */
    public String findBifIdByBankTypeId(String bankTypeId) {
        BisBifInit row = this.<BisBifInit>oneBy(
                "from BisBifInit where bankTypeId = :id and validSign = :valid",
                Map.of("id", bankTypeId, "valid", "1"));
        return row == null ? null : row.getId();
    }

    public void saveBifInit(BisBifInit entity) {
        save(entity);
    }
}
