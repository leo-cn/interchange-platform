package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.SysCorp;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 组织机构 {@code SYS_CORP} 的存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class CorpDao extends BaseDao {

    /** 按 mdId 批量预加载：mdId → 单位 */
    public Map<String, SysCorp> mapByMdId(Collection<String> mdIds) {
        List<String> ids = clean(mdIds);
        Map<String, SysCorp> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (SysCorp row : this.<SysCorp>listBy("from SysCorp where mdId in (:ids)", Map.of("ids", ids))) {
            map.put(row.getMdId(), row);
        }
        return map;
    }

    public Optional<SysCorp> findByMdId(String mdId) {
        return Optional.ofNullable(this.<SysCorp>oneBy("from SysCorp where mdId = :mdId", Map.of("mdId", mdId)));
    }

    public SysCorp save(SysCorp entity) {
        return super.save(entity);
    }
}
