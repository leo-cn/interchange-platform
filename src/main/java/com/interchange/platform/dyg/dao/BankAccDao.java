package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.BtBankAcc;
import com.interchange.platform.dyg.entity.BtBankAccCur;
import com.interchange.platform.standard.core.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
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

    /** 按 mdId 批量预加载：mdId → 账户 */
    public Map<String, BtBankAcc> mapByMdId(Collection<String> mdIds) {
        List<String> ids = clean(mdIds);
        Map<String, BtBankAcc> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        List<BtBankAcc> rows = listBy("from BtBankAcc where mdId in (:ids)", Map.of("ids", ids));
        for (BtBankAcc acc : rows) {
            map.put(acc.getMdId(), acc);
        }
        return map;
    }

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
}
