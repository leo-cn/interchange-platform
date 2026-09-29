package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.SysCorp;
import com.interchange.platform.dyg.vo.StandardCorpVO;
import com.interchange.platform.dyg.vo.StandardUserVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 组织机构 {@code SYS_CORP} 的存取。写法参照 dyg-erp：实体映射 + HQL。
 *
 * <p>标准接口的单位查询、用户查询（用户挂在单位下，按 corpCode / corpName 过滤）
 * 也放在这里，与 dyg-erp 的 {@code StandardCorpDao} 一致。
 */
@Repository
public class CorpDao extends BaseDao {

    public Optional<SysCorp> findByMdId(String mdId) {
        return Optional.ofNullable(this.<SysCorp>oneBy("from SysCorp where mdId = :mdId", Map.of("mdId", mdId)));
    }

    public SysCorp save(SysCorp entity) {
        return super.save(entity);
    }

    /* ---------- 标准接口：单位 / 用户查询 ---------- */

    /**
     * 标准接口「单位查询」：code 精确、name 模糊、updateDate 大于等于；空条件跳过。
     * 取视图 {@code standard_corp_view}，SQL 别名与 {@link StandardCorpVO} 字段名一致。
     */
    public List<StandardCorpVO> listStandardCorp(String code, String name, String updateDate) {
        StringBuilder sql = new StringBuilder(
                "select code, name, status, updateDate from standard_corp_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(code)) {
            sql.append(" and code = :code");
            args.put("code", code.trim());
        }
        if (StringUtil.isNotBlank(name)) {
            sql.append(" and name like :name");
            args.put("name", "%" + name.trim() + "%");
        }
        if (StringUtil.isNotBlank(updateDate)) {
            sql.append(" and updateDate >= :updateDate");
            args.put("updateDate", updateDate.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardCorpVO.class);
    }

    /**
     * 标准接口「用户查询」：corpCode / corpName / loginName 精确
     * （单位名称原实现就不是模糊）、username 模糊、updateDate 大于等于；空条件跳过。
     * 取视图 {@code standard_user_view}，SQL 别名与 {@link StandardUserVO} 字段名一致。
     */
    public List<StandardUserVO> listStandardUser(String corpCode, String corpName, String loginName,
                                                   String username, String updateDate) {
        StringBuilder sql = new StringBuilder(
                "select id, loginName, username, corpCode, corpName, status, updateDate"
                        + " from standard_user_view where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(corpCode)) {
            sql.append(" and corpCode = :corpCode");
            args.put("corpCode", corpCode.trim());
        }
        if (StringUtil.isNotBlank(corpName)) {
            sql.append(" and corpName = :corpName");
            args.put("corpName", corpName.trim());
        }
        if (StringUtil.isNotBlank(loginName)) {
            sql.append(" and loginName = :loginName");
            args.put("loginName", loginName.trim());
        }
        if (StringUtil.isNotBlank(username)) {
            sql.append(" and username like :username");
            args.put("username", "%" + username.trim() + "%");
        }
        if (StringUtil.isNotBlank(updateDate)) {
            sql.append(" and updateDate >= :updateDate");
            args.put("updateDate", updateDate.trim());
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardUserVO.class);
    }
}
