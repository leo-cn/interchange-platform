package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtCurrency;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 币种 {@code BT_CURRENCY} 的只读存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class CurrencyDao extends BaseDao {

    /** 按币种编码（english_code）批量预加载 */
    public Map<String, BtCurrency> mapByCode(Collection<String> codes) {
        List<String> keys = clean(codes);
        Map<String, BtCurrency> map = new HashMap<>();
        if (keys.isEmpty()) {
            return map;
        }
        for (BtCurrency row : this.<BtCurrency>listBy("from BtCurrency where englishCode in (:codes)", Map.of("codes", keys))) {
            map.put(row.getEnglishCode(), row);
        }
        return map;
    }
}
