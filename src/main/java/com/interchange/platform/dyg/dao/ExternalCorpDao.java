package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.SysExternalCorp;
import com.interchange.platform.dyg.entity.SysExternalCorpBankacc;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客商 {@code SYS_EXTERNAL_CORP} 及其账号子表 {@code SYS_EXTERNAL_CORP_BANKACC} 的存取。
 * 写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class ExternalCorpDao extends BaseDao {

    /** 按 mdId 批量预加载：mdId → 客商 */
    public Map<String, SysExternalCorp> mapByMdId(Collection<String> mdIds) {
        List<String> ids = clean(mdIds);
        Map<String, SysExternalCorp> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (SysExternalCorp row : this.<SysExternalCorp>listBy("from SysExternalCorp where mdId in (:ids)", Map.of("ids", ids))) {
            map.put(row.getMdId(), row);
        }
        return map;
    }

    public Optional<SysExternalCorp> findByMdId(String mdId) {
        return Optional.ofNullable(this.<SysExternalCorp>oneBy("from SysExternalCorp where mdId = :mdId", Map.of("mdId", mdId)));
    }

    public SysExternalCorp save(SysExternalCorp entity) {
        return super.save(entity);
    }

    /* ---------- 客商账号子表 ---------- */

    /** 清空某客商的账号，返回删除行数 */
    public int deleteBankAcc(String externalCorpId) {
        return updateByHql("delete from SysExternalCorpBankacc where externalCorpId = :id",
                Map.of("id", externalCorpId));
    }

    public void saveBankAcc(SysExternalCorpBankacc entity) {
        save(entity);
    }
}
