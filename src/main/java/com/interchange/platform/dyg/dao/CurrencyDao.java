package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtCurrency;
import com.interchange.platform.dyg.vo.StandardCurVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 币种 {@code BT_CURRENCY} 的只读存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class CurrencyDao extends BaseDao {

    /** 按币种编码（english_code）查单条 */
    public Optional<BtCurrency> findByCode(String code) {
        return Optional.ofNullable(
                this.<BtCurrency>oneBy("from BtCurrency where englishCode = :code", Map.of("code", code)));
    }

    /* ---------- 标准接口：币种查询 ---------- */

    /**
     * 标准接口「币种查询」：curCode 精确、curName / englishCode 模糊、updateDate 大于等于；空条件跳过。
     * 取视图 {@code standard_cur_view}，SQL 别名与 {@link StandardCurVO} 字段名一致。
     */
    public List<StandardCurVO> listStandardCur(String curCode, String curName,
                                                 String englishCode, String updateDate) {
        StringBuilder sql = new StringBuilder(
                "select id, cur_code as curCode, cur_name as curName, english_code as englishCode,"
                        + " cur_exrate as curExrate, valid_sign as validSign, updateDate"
                        + " from standard_cur_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(curCode)) {
            sql.append(" and cur_code = :curCode");
            args.put("curCode", curCode.trim());
        }
        if (StringUtil.isNotBlank(curName)) {
            sql.append(" and cur_name like :curName");
            args.put("curName", "%" + curName.trim() + "%");
        }
        if (StringUtil.isNotBlank(englishCode)) {
            sql.append(" and english_code like :englishCode");
            args.put("englishCode", "%" + englishCode.trim() + "%");
        }
        if (StringUtil.isNotBlank(updateDate)) {
            sql.append(" and updateDate >= :updateDate");
            args.put("updateDate", updateDate.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardCurVO.class);
    }
}
