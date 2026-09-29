package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtInputBankInfo;
import com.interchange.platform.dyg.vo.StandardBankInputVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 银行网点 {@code BT_INPUT_BANK_INFO} 的存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class BankInputDao extends BaseDao {

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

    /* ---------- 标准接口：网点（联行号）查询 ---------- */

    /**
     * 标准接口「网点查询」：bankCode 精确、bankName 模糊、updateDate 大于等于；空条件跳过。
     * 取视图 {@code standard_bankinput_view}，SQL 别名与 {@link StandardBankInputVO} 字段名一致。
     */
    public List<StandardBankInputVO> listStandardBankInput(String bankCode, String bankName, String updateDate) {
        StringBuilder sql = new StringBuilder(
                "select id, bankCode, bankName, cityCode, cityName, provCode, provName, updateDate"
                        + " from standard_bankinput_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(bankCode)) {
            sql.append(" and bankCode = :bankCode");
            args.put("bankCode", bankCode.trim());
        }
        if (StringUtil.isNotBlank(bankName)) {
            sql.append(" and bankName like :bankName");
            args.put("bankName", "%" + bankName.trim() + "%");
        }
        if (StringUtil.isNotBlank(updateDate)) {
            sql.append(" and updateDate >= :updateDate");
            args.put("updateDate", updateDate.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardBankInputVO.class);
    }
}
