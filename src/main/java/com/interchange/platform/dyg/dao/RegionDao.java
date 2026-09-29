package com.interchange.platform.dyg.dao;

import com.interchange.platform.dyg.entity.SysRegion;
import com.interchange.platform.dyg.vo.StandardRegionVO;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 行政区划 {@code SYS_REGION} 的只读存取。写法参照 dyg-erp：实体映射 + HQL。
 */
@Repository
public class RegionDao extends BaseDao {

    /** 按银行城市码取区划 */
    public Optional<SysRegion> findByBankInputCity(String bankInputCity) {
        return Optional.ofNullable(oneBy("from SysRegion where bankInputCity = :city",
                Map.of("city", bankInputCity)));
    }

    /* ---------- 标准接口：省市查询 ---------- */

    /**
     * 标准接口「省市查询」：cityCode → bank_input_city、provCode → bank_prov 精确，
     * cityName / provName 模糊；空条件跳过。
     * 取基表 {@code SYS_REGION}（这一支没有走视图），SQL 别名与 {@link StandardRegionVO} 字段名一致。
     */
    public List<StandardRegionVO> listStandardRegion(String cityCode, String cityName,
                                                       String provCode, String provName) {
        StringBuilder sql = new StringBuilder(
                "select id, bank_input_city as bankInputCity, name,"
                        + " bank_prov as bankProv, prov_input_name as provInputName"
                        + " from SYS_REGION where 1=1");
        Map<String, Object> args = new LinkedHashMap<>();
        if (StringUtil.isNotBlank(cityCode)) {
            sql.append(" and bank_input_city = :bankInputCity");
            args.put("bankInputCity", cityCode.trim());
        }
        if (StringUtil.isNotBlank(cityName)) {
            sql.append(" and name like :name");
            args.put("name", "%" + cityName.trim() + "%");
        }
        if (StringUtil.isNotBlank(provCode)) {
            sql.append(" and bank_prov = :bankProv");
            args.put("bankProv", provCode.trim());
        }
        if (StringUtil.isNotBlank(provName)) {
            sql.append(" and prov_input_name like :provInputName");
            args.put("provInputName", "%" + provName.trim() + "%");
        }
        return listBySQLAliasToBean(sql.toString(), args, StandardRegionVO.class);
    }
}
