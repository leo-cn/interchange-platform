package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.SysExternalCorp;
import com.interchange.platform.dyg.entity.SysExternalCorpBankacc;
import com.interchange.platform.dyg.vo.StandardExternalCorpAccVO;
import com.interchange.platform.dyg.vo.StandardExternalCorpVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客商 {@code SYS_EXTERNAL_CORP} 及其账号子表 {@code SYS_EXTERNAL_CORP_BANKACC} 的存取。
 * 写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class ExternalCorpDao extends BaseDao {

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

    /* ---------- 标准接口：客商 / 客商账号查询 ---------- */

    /**
     * 标准接口「客商查询」：code 精确、name 模糊；空条件跳过。
     * 取视图 {@code standard_externalcorp_view}，SQL 别名与 {@link StandardExternalCorpVO} 字段名一致。
     */
    public List<StandardExternalCorpVO> listStandardExternalCorp(String code, String name) {
        StringBuilder sql = new StringBuilder(
                "select id, code, name, externalType, bwType, status"
                        + " from standard_externalcorp_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(code)) {
            sql.append(" and code = :code");
            args.put("code", code.trim());
        }
        if (StringUtil.isNotBlank(name)) {
            sql.append(" and name like :name");
            args.put("name", "%" + name.trim() + "%");
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardExternalCorpVO.class);
    }

    /**
     * 标准接口「客商账号查询」：code / externalAcc 精确、name 模糊；空条件跳过。
     * 取视图 {@code standard_externalcorp_acc_view}，SQL 别名与 {@link StandardExternalCorpAccVO} 字段名一致。
     */
    public List<StandardExternalCorpAccVO> listStandardExternalCorpAcc(String code, String name, String externalAcc) {
        StringBuilder sql = new StringBuilder(
                "select id, code, name, externalType, externalAcc, bankCode, bankName"
                        + " from standard_externalcorp_acc_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(code)) {
            sql.append(" and code = :code");
            args.put("code", code.trim());
        }
        if (StringUtil.isNotBlank(name)) {
            sql.append(" and name like :name");
            args.put("name", "%" + name.trim() + "%");
        }
        if (StringUtil.isNotBlank(externalAcc)) {
            sql.append(" and externalAcc = :externalAcc");
            args.put("externalAcc", externalAcc.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardExternalCorpAccVO.class);
    }
}
