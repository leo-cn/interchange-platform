package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtInputBankInfo;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 银行网点 {@code BT_INPUT_BANK_INFO} 的存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class BankInputDao extends BaseDao {

    /** 按 mdId 批量预加载：mdId → 网点 */
    public Map<String, BtInputBankInfo> mapByMdId(Collection<String> mdIds) {
        List<String> ids = clean(mdIds);
        Map<String, BtInputBankInfo> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (BtInputBankInfo row : this.<BtInputBankInfo>listBy("from BtInputBankInfo where mdId in (:ids)", Map.of("ids", ids))) {
            map.put(row.getMdId(), row);
        }
        return map;
    }

    public Optional<BtInputBankInfo> findByMdId(String mdId) {
        return Optional.ofNullable(this.<BtInputBankInfo>oneBy("from BtInputBankInfo where mdId = :mdId", Map.of("mdId", mdId)));
    }

    public BtInputBankInfo save(BtInputBankInfo entity) {
        return super.save(entity);
    }

    /** 删除同联行号、md_id 为空的历史脏数据，返回删除行数 */
    public int deleteDirtyByBankCodes(List<String> bankCodes) {
        if (bankCodes == null || bankCodes.isEmpty()) {
            return 0;
        }
        return updateByHql("delete from BtInputBankInfo where mdId is null and sysBankCode in (:codes)",
                Map.of("codes", bankCodes));
    }
}
