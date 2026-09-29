package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.vo.StandardItemVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科目 {@code standard_item_view} 的只读存取（标准接口科目查询）。
 *
 * <p>科目在本平台没有对应的业务写入表，只有这一个标准视图查询，所以单独一个 DAO。
 */
@Repository
public class ItemDao extends BaseDao {

    /**
     * 标准接口「科目查询」：itemCode 精确、itemName 模糊、updateDate 大于等于；空条件跳过。
     * 取视图 {@code standard_item_view}，SQL 别名与 {@link StandardItemVO} 字段名一致。
     */
    public List<StandardItemVO> listStandardItem(String itemCode, String itemName, String updateDate) {
        StringBuilder sql = new StringBuilder(
                "select id, item_code as itemCode, item_name as itemName, updateDate"
                        + " from standard_item_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(itemCode)) {
            sql.append(" and item_code = :itemCode");
            args.put("itemCode", itemCode.trim());
        }
        if (StringUtil.isNotBlank(itemName)) {
            sql.append(" and item_name like :itemName");
            args.put("itemName", "%" + itemName.trim() + "%");
        }
        if (StringUtil.isNotBlank(updateDate)) {
            sql.append(" and updateDate >= :updateDate");
            args.put("updateDate", updateDate.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardItemVO.class);
    }
}
