package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtBankAcc;
import com.interchange.platform.dyg.entity.BtBankAccCur;
import com.interchange.platform.dyg.vo.StandardBankAccVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 银行账户 {@code BT_BANK_ACC} 及其币种子表 {@code BT_BANK_ACC_CUR} 的存取。
 *
 * <p>写法参照 dyg-erp 的 {@code BtBankAccDao}：实体用 {@code @Entity} 映射，
 * DAO 里写 HQL，由 Hibernate 拼 SQL 与绑定参数 —— 不再手写 30 多个占位符，
 * 增删字段也不会漏掉对应位置。
 */
@Repository
public class BankAccDao extends BaseDao {

    /** 按 mdId 查账户 */
    public Optional<BtBankAcc> findByMdId(String mdId) {
        return Optional.ofNullable(this.<BtBankAcc>oneBy("from BtBankAcc where mdId = :mdId", Map.of("mdId", mdId)));
    }

    /** 新增或更新账户：有主键且库中已存在则更新，否则新增 */
    public BtBankAcc save(BtBankAcc acc) {
        return super.save(acc);
    }

    /* ---------- 币种子表 ---------- */

    /** 清空某账户的币种，返回删除行数 */
    public int deleteAccCur(String bankAccId) {
        return updateByHql("delete from BtBankAccCur where bankAccId = :id", Map.of("id", bankAccId));
    }

    /** 新增账户币种 */
    public void insertAccCur(String id, String bankAccId, String curId) {
        BtBankAccCur row = new BtBankAccCur();
        row.setId(id);
        row.setBankAccId(bankAccId);
        row.setCurId(curId);
        save(row);
    }

    /* ---------- 标准接口：银行账号查询 ---------- */

    /**
     * 标准接口「银行账号查询」：bankAcc / corpCode 精确、accName / corpName 模糊、
     * updateDate 大于等于；空条件跳过。
     * 取视图 {@code standard_bankacc_view}，SQL 别名与 {@link StandardBankAccVO} 字段名一致。
     */
    public List<StandardBankAccVO> listStandardBankAcc(String bankAcc, String accName, String corpCode,
                                                         String corpName, String updateDate) {
        StringBuilder sql = new StringBuilder(
                "select id, bankAcc, accName, corpCode, corpName, curCode, curName,"
                        + " bankCode, bankName, isOnline, validSign, updateDate"
                        + " from standard_bankacc_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(bankAcc)) {
            sql.append(" and bankAcc = :bankAcc");
            args.put("bankAcc", bankAcc.trim());
        }
        if (StringUtil.isNotBlank(accName)) {
            sql.append(" and accName like :accName");
            args.put("accName", "%" + accName.trim() + "%");
        }
        if (StringUtil.isNotBlank(corpCode)) {
            sql.append(" and corpCode = :corpCode");
            args.put("corpCode", corpCode.trim());
        }
        if (StringUtil.isNotBlank(corpName)) {
            sql.append(" and corpName like :corpName");
            args.put("corpName", "%" + corpName.trim() + "%");
        }
        if (StringUtil.isNotBlank(updateDate)) {
            sql.append(" and updateDate >= :updateDate");
            args.put("updateDate", updateDate.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardBankAccVO.class);
    }
}
