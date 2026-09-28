package com.interchange.platform.mdm;

import com.interchange.platform.mdm.constant.MdmConstant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interchange.platform.common.BizException;
import com.interchange.platform.config.AppProps;
import com.interchange.platform.config.DataSourceRegistry;
import com.interchange.platform.mdm.dto.MdmDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 主数据接收与落库（对齐 dyg-erp 的 5 个 {@code /rest/mdm/*Receive} 接口）。
 *
 * <p><b>本服务不建表</b>：SYS_CORP / SYS_EXTERNAL_CORP / BT_BANK_ACC 等是资金系统的基表，
 * 必须已存在于 {@code app.mdm.datasource} 指向的库里。本服务只负责按 mdId 幂等写入。
 *
 * <p>幂等策略与 dyg-erp 完全一致，<b>不是数据库 merge</b>：
 * <ol>
 *   <li>批量 {@code select ... where md_id in (...)} 建内存 Map；</li>
 *   <li>存在则 UPDATE，不存在则 INSERT；</li>
 *   <li>子表（客商账号、账户币种）先 DELETE 再 INSERT。</li>
 * </ol>
 *
 * <p>每条数据<b>独立事务</b>（原系统靠 {@code selfProxy} 自注入代理实现同效果）：
 * 一条失败只回滚这一条，不影响同批次的其它条。
 *
 * <p><b>两处刻意保留的反直觉逻辑</b>（与 dyg-erp 现有数据语义保持一致，勿改）：
 * <ul>
 *   <li>BT_BANK_ACC 的 VALID_SIGN 是反的：ACTIVE 写 "0"（停用才写 "1"）；</li>
 *   <li>组织机构只新增不更新，重复 mdId 直接判 FAIL「重复接收」。</li>
 * </ul>
 */
@Service
public class MdmReceiveService {

    private static final Logger log = LoggerFactory.getLogger(MdmReceiveService.class);

    private final AppProps appProps;
    private final DataSourceRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile TransactionTemplate tx;

    public MdmReceiveService(AppProps appProps, DataSourceRegistry registry) {
        this.appProps = appProps;
        this.registry = registry;
    }

    // ==================================================================
    //  五个接收入口：解析 → 校验 → 落库 → 组回执
    // ==================================================================

    /** 银行类别：最简单，无外键依赖，应最先接收 */
    public Map<String, Object> receiveBankType(String json) {
        List<MdmDto.BankType> list = parseArray(json, MdmDto.BankType.class, "银行类别");
        List<Map<String, Object>> items = new ArrayList<>();
        for (MdmDto.BankType bean : list) {
            String err = validate(bean);
            if (!err.isEmpty()) {
                items.add(item(bean.getMdId(), bean.getMdCode(), bean.getMdDescription(), false, err));
                continue;
            }
            items.add(run(bean, t -> upsertBankType(t, bean)));
        }
        return response("银行类别接收成功", items);
    }

    /** 银行网点：依赖已接收的银行类别（解析 bank_prefix，banks.row码） */
    public Map<String, Object> receiveBankBranch(String json) {
        List<MdmDto.BankBranch> list = parseArray(json, MdmDto.BankBranch.class, "银行网点");
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> savedCodes = new ArrayList<>();

        // 预加载：银行类别 mdId → id / bank_prefix；已有网点 mdId → id
        Map<String, Map<String, Object>> typeMap = loadByMdId(tbl("BT_BANK_TYPE"),
                "id, md_id, bank_prefix", mdIdOf(list, MdmDto.BankBranch::getBanktype));
        Map<String, Map<String, Object>> existMap = loadByMdId(tbl("BT_INPUT_BANK_INFO"),
                "id, md_id", mdIdOf(list, MdmDto.BankBranch::getMdId));

        for (MdmDto.BankBranch bean : list) {
            Map<String, Object> type = typeMap.get(bean.getBanktype());
            String err = validate(bean, type);
            if (!err.isEmpty()) {
                items.add(item(bean.getMdId(), bean.getMdCode(), bean.getMdDescription(), false, err));
                continue;
            }
            Map<String, Object> exist = existMap.get(bean.getMdId());
            items.add(run(bean, t -> {
                upsertBranch(t, bean, type, exist == null ? null : str(exist, "id"));
                if (bean.getCombinenum() != null) {
                    savedCodes.add(bean.getCombinenum());
                }
                return true;
            }));
        }
        // 收尾：清掉同联行号的历史脏数据（md_id 为空的旧记录），与原逻辑一致
        cleanupBranches(savedCodes);
        return response("银行网点接收成功", items);
    }

    /** 组织机构：只新增，不更新 */
    public Map<String, Object> receiveOrg(String json) {
        List<MdmDto.Org> list = parseArray(json, MdmDto.Org.class, "组织机构");
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Map<String, Object>> currencyMap = loadMap(
                tbl("BT_CURRENCY"), "id, english_code", "english_code",
                list.stream().map(MdmDto.Org::getPkCurrtype).toList());
        Map<String, Map<String, Object>> existMap = loadByMdId(tbl("SYS_CORP"),
                "id, md_id", mdIdOf(list, MdmDto.Org::getMdId));

        for (MdmDto.Org bean : list) {
            String err = validate(bean, currencyMap);
            if (!err.isEmpty()) {
                items.add(item(bean.getMdId(), bean.getMdCode(), bean.getMdDescription(), false, err));
                continue;
            }
            if (existMap.containsKey(bean.getMdId())) {
                items.add(item(bean.getMdId(), bean.getMdCode(), bean.getMdDescription(), false, "重复接收"));
                continue;
            }
            Map<String, Object> currency = currencyMap.get(bean.getPkCurrtype());
            items.add(run(bean, t -> insertCorp(t, bean, currency)));
        }
        return response("组织机构接收成功", items);
    }

    /** 银行账户：依赖银行类别 / 网点 / 组织 / 币种 四张表 */
    public Map<String, Object> receiveBankAcc(String json) {
        List<MdmDto.BankAcc> list = parseArray(json, MdmDto.BankAcc.class, "银行账户");
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Map<String, Object>> typeMap = loadByMdId(tbl("BT_BANK_TYPE"),
                "id, md_id", mdIdOf(list, MdmDto.BankAcc::getPkBanktype));
        Map<String, Map<String, Object>> branchMap = loadByMdId(tbl("BT_INPUT_BANK_INFO"),
                "id, md_id, sys_bank_code, bank_name, bank_city_code", mdIdOf(list, MdmDto.BankAcc::getPkBankdoc));
        Map<String, Map<String, Object>> corpMap = loadByMdId(tbl("SYS_CORP"),
                "id, md_id", mdIdOf(list, MdmDto.BankAcc::getFinanceorg));
        List<String> curCodes = list.stream()
                .filter(b -> b.getBdBankaccsub() != null)
                .flatMap(b -> b.getBdBankaccsub().stream())
                .map(MdmDto.BankAccCur::getPkCurrtypeCodeShow)
                .toList();
        Map<String, Map<String, Object>> currencyMap = loadMap(tbl("BT_CURRENCY"),
                "id, english_code", "english_code", curCodes);
        Map<String, Map<String, Object>> existMap = loadByMdId(tbl("BT_BANK_ACC"),
                "id, md_id", mdIdOf(list, MdmDto.BankAcc::getMdId));

        for (MdmDto.BankAcc bean : list) {
            Map<String, Object> type = typeMap.get(bean.getPkBanktype());
            Map<String, Object> branch = branchMap.get(bean.getPkBankdoc());
            Map<String, Object> corp = corpMap.get(bean.getFinanceorg());
            String err = validate(bean, type, branch, corp);
            if (!err.isEmpty()) {
                items.add(item(bean.getMdId(), bean.getMdCode(), bean.getMdDescription(), false, err));
                continue;
            }
            Map<String, Object> exist = existMap.get(bean.getMdId());
            items.add(run(bean, t -> {
                upsertBankAcc(t, bean, type, branch, corp, currencyMap, exist == null ? null : str(exist, "id"));
                return true;
            }));
        }
        return response("银行账户接收成功", items);
    }

    /** 客商：依赖银行网点 / 银行类别 / 币种 */
    public Map<String, Object> receivePartner(String json) {
        List<MdmDto.Partner> list = parseArray(json, MdmDto.Partner.class, "客商信息");
        List<Map<String, Object>> items = new ArrayList<>();

        List<String> branchIds = new ArrayList<>();
        List<String> typeIds = new ArrayList<>();
        List<String> curCodes = new ArrayList<>();
        for (MdmDto.Partner p : list) {
            if (p.getBdBankaccbas() == null) {
                continue;
            }
            for (MdmDto.PartnerBankAcc a : p.getBdBankaccbas()) {
                if (a.getPkBankdoc() != null) {
                    branchIds.add(a.getPkBankdoc());
                }
                if (a.getPkBankdocBanktypeShow() != null) {
                    typeIds.add(a.getPkBankdocBanktypeShow());
                }
                if (a.getBankaccsub() != null) {
                    a.getBankaccsub().stream()
                            .map(MdmDto.PartnerBankAccCur::getPkCurrtypeCodeShow)
                            .forEach(curCodes::add);
                }
            }
        }
        Map<String, Map<String, Object>> branchMap = loadByMdId(tbl("BT_INPUT_BANK_INFO"),
                "id, md_id, sys_bank_code, bank_name", branchIds);
        Map<String, Map<String, Object>> typeMap = loadByMdId(tbl("BT_BANK_TYPE"),
                "id, md_id, bank_type", typeIds);
        Map<String, Map<String, Object>> currencyMap = loadMap(tbl("BT_CURRENCY"),
                "id, english_code", "english_code", curCodes);
        Map<String, Map<String, Object>> existMap = loadByMdId(tbl("SYS_EXTERNAL_CORP"),
                "id, md_id", mdIdOf(list, MdmDto.Partner::getMdId));

        for (MdmDto.Partner bean : list) {
            String err = validate(bean);
            if (!err.isEmpty()) {
                items.add(item(bean.getMdId(), bean.getMdCode(), bean.getMdDescription(), false, err));
                continue;
            }
            Map<String, Object> exist = existMap.get(bean.getMdId());
            items.add(run(bean, t -> {
                upsertPartner(t, bean, exist == null ? null : str(exist, "id"), branchMap, typeMap, currencyMap);
                return true;
            }));
        }
        return response("客商信息接收成功", items);
    }

    // ==================================================================
    //  落库：银行类别
    // ==================================================================

    private Boolean upsertBankType(JdbcTemplate t, MdmDto.BankType bean) {
        String existId = findIdByMdId(t, tbl("BT_BANK_TYPE"), bean.getMdId());
        String validSign = MdmConstant.ACTIVE.equals(bean.getMdStatusCode()) ? MdmConstant.Y : MdmConstant.N;
        Timestamp now = now();
        String by = createBy();
        if (existId == null) {
            String id = uuid();
            t.update(sql("insert into %s (id, md_id, bank_type, type_name, bank_prefix, valid_sign,"
                            + " is_system, create_date, create_by) values (?,?,?,?,?,?,?,?,?)")
                            .formatted(tbl("BT_BANK_TYPE")),
                    id, bean.getMdId(), bean.getCode(), bean.getName(), bean.getCombinecode(),
                    validSign, MdmConstant.N, now, by);
            // 新增银行类别时级联插一条接口初始化记录（BIS_BIF_INIT），与原逻辑一致
            t.update(sql("insert into %s (id, bif_code, name, bank_type_id, valid_sign, is_system,"
                            + " create_date, create_by) values (?,?,?,?,?,?,?,?)")
                            .formatted(tbl("BIS_BIF_INIT")),
                    uuid(), bean.getCode(), bean.getName(), id, MdmConstant.Y, MdmConstant.N, now, by);
        } else {
            t.update(sql("update %s set bank_type=?, type_name=?, bank_prefix=?, valid_sign=?,"
                    + " update_date=?, update_by=? where id=?").formatted(tbl("BT_BANK_TYPE")),
                    bean.getCode(), bean.getName(), bean.getCombinecode(), validSign, now, by, existId);
        }
        return true;
    }

    // ==================================================================
    //  落库：银行网点
    // ==================================================================

    private Boolean upsertBranch(JdbcTemplate t, MdmDto.BankBranch bean,
                                 Map<String, Object> typeRow, String existId) {
        String code = bean.getCombinenum();
        String validSign = MdmConstant.ACTIVE.equals(bean.getMdStatusCode()) ? MdmConstant.Y : MdmConstant.N;
        // 城市码取联行号第 4~7 位（下标 3..7），联行号不足 7 位则不写
        String cityCode = code != null && code.length() >= 7 ? code.substring(3, 7) : null;
        // 前缀优先取银行类别上的人行前缀，没有才截联行号前三位，并回写到银行类别
        String prefix = typeRow == null ? null : str(typeRow, "bank_prefix");
        boolean fromCode = false;
        if (blank(prefix) && code != null && code.length() >= 3) {
            prefix = code.substring(0, 3);
            fromCode = true;
        }
        Timestamp now = now();
        String by = createBy();
        if (existId == null) {
            String id = uuid();
            t.update(sql("insert into %s (id, md_id, sys_bank_code, bank_name, short_name, bank_prefix,"
                            + " bank_city_code, valid_sign, create_date, create_by)"
                            + " values (?,?,?,?,?,?,?,?,?,?)").formatted(tbl("BT_INPUT_BANK_INFO")),
                    id, bean.getMdId(), code, bean.getName(), bean.getShortname(),
                    prefix, cityCode, validSign, now, by);
            if (fromCode && typeRow != null) {
                t.update(sql("update %s set bank_prefix=? where id=?").formatted(tbl("BT_BANK_TYPE")),
                        prefix, str(typeRow, "id"));
            }
        } else {
            t.update(sql("update %s set sys_bank_code=?, bank_name=?, short_name=?, bank_prefix=?,"
                            + " bank_city_code=?, valid_sign=?, update_date=?, update_by=? where id=?")
                            .formatted(tbl("BT_INPUT_BANK_INFO")),
                    code, bean.getName(), bean.getShortname(), prefix, cityCode, validSign, now, by, existId);
        }
        return true;
    }

    /** 删除同联行号、md_id 为空的历史脏数据 */
    private void cleanupBranches(List<String> bankCodes) {
        List<String> codes = bankCodes.stream().filter(c -> !blank(c)).distinct().toList();
        if (codes.isEmpty()) {
            return;
        }
        try {
            String sql = sql("delete from %s where md_id is null and sys_bank_code in (%s)")
                    .formatted(tbl("BT_INPUT_BANK_INFO"), placeholders(codes.size()));
            jdbc().update(sql, codes.toArray());
        } catch (Exception e) {
            // 清理失败不影响本次接收结果，记录即可
            log.warn("银行网点历史脏数据清理失败: {}", reason(e));
        }
    }

    // ==================================================================
    //  落库：组织机构（仅新增）
    // ==================================================================

    private Boolean insertCorp(JdbcTemplate t, MdmDto.Org bean, Map<String, Object> currency) {
        String id = uuid();
        String parentMdId = blank(bean.getLegalParentidIdShow()) ? null : bean.getLegalParentidIdShow();
        String unitAttribute = isTrue(bean.getOrgtype2()) ? "01" : "02";
        Timestamp now = now();
        t.update(sql("insert into %s (id, md_id, code, name, short_name, name_en, parent_md_id, cur_id,"
                        + " soc_code, unit_attribute, status, group_code, net_id, type, listed_company,"
                        + " rat_group, is_limit_quota, create_time, create_by, use_account_code)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)").formatted(tbl("SYS_CORP")),
                id, bean.getMdId(), bean.getOrgCode(), bean.getOrgName(), bean.getShortname(), bean.getDef5(),
                parentMdId, str(currency, "id"), bean.getTaxpayercode(), unitAttribute,
                isTrue(bean.getEnable()) ? 1 : 0, bean.getPkGroup(),
                appProps.getMdm().getNetId(), 1, 0, 1, MdmConstant.N, now, createBy(), id);
        return true;
    }

    // ==================================================================
    //  落库：银行账户
    // ==================================================================

    private Boolean upsertBankAcc(JdbcTemplate t, MdmDto.BankAcc bean, Map<String, Object> typeRow,
                                  Map<String, Object> branchRow, Map<String, Object> corpRow,
                                  Map<String, Map<String, Object>> currencyMap, String existId) {
        // 注意：银行账户的有效标志与原系统一致是「反」的 —— ACTIVE 写 0，非 ACTIVE 写 1
        String validSign = MdmConstant.ACTIVE.equals(bean.getMdStatusCode()) ? MdmConstant.N : MdmConstant.Y;
        String isOnline = "0".equals(bean.getNetqueryflag()) ? MdmConstant.N : MdmConstant.Y;
        Timestamp openDate = parseDate(bean.getAccopendate());
        Timestamp now = now();
        String by = createBy();

        String bifCode = null;
        String typeId = str(typeRow, "id");
        if (typeId != null) {
            bifCode = t.query(sql("select id from %s where bank_type_id = ? and valid_sign = ?")
                            .formatted(tbl("BIS_BIF_INIT")),
                    rs -> rs.next() ? rs.getString(1) : null, typeId, MdmConstant.Y);
        }
        String prov = null;
        String city = null;
        String cityCode = str(branchRow, "bank_city_code");
        if (!blank(cityCode)) {
            Map<String, Object> region = queryOne(t, "select id, parent_id from %s where bank_input_city = ?"
                    .formatted(tbl("SYS_REGION")), cityCode);
            if (region != null) {
                prov = str(region, "parent_id");
                city = str(region, "id");
            }
        }
        String natureId = ensureNature(t, bean.getAccattribute(), bean.getAccattributeDesc());

        if (existId == null) {
            String id = uuid();
            t.update(sql("insert into %s (id, md_id, name, bank_acc, acc_name, corp_id, acc_type, attribute_id,"
                            + " nature_id, bank_type_id, bank_code, bank_name, prov, city, bif_code,"
                            + " electric_bill, rates_float, interest_cycle, reg_date, is_online, valid_sign,"
                            + " status, is_capital_pool, is_domestic_bank, is_limit_quota, is_online_handle,"
                            + " is_offshore_account, is_rpa_escrow, basic_account_sign, create_date, create_by)"
                            + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                            .formatted(tbl("BT_BANK_ACC")),
                    id, bean.getMdId(), bean.getName(), bean.getAccNum(), bean.getAccName(), str(corpRow, "id"),
                    appProps.getMdm().getBankAccType(), appProps.getMdm().getBankAccAttribute(), natureId,
                    typeId, str(branchRow, "sys_bank_code"), str(branchRow, "bank_name"), prov, city, bifCode,
                    MdmConstant.N, MdmConstant.N, -1, openDate, isOnline, validSign,
                    MdmConstant.STATUS_APPROVED, MdmConstant.N, MdmConstant.Y, MdmConstant.N, MdmConstant.N,
                    MdmConstant.N, MdmConstant.N, MdmConstant.N, now, by);
            replaceAccCur(t, id, bean.getBdBankaccsub(), currencyMap);
        } else {
            t.update(sql("update %s set name=?, bank_acc=?, acc_name=?, corp_id=?, nature_id=?, bank_type_id=?,"
                            + " bank_code=?, bank_name=?, prov=?, city=?, bif_code=?, reg_date=?, is_online=?,"
                            + " valid_sign=?, update_date=?, update_by=? where id=?")
                            .formatted(tbl("BT_BANK_ACC")),
                    bean.getName(), bean.getAccNum(), bean.getAccName(), str(corpRow, "id"), natureId, typeId,
                    str(branchRow, "sys_bank_code"), str(branchRow, "bank_name"), prov, city, bifCode,
                    openDate, isOnline, validSign, now, by, existId);
            replaceAccCur(t, existId, bean.getBdBankaccsub(), currencyMap);
        }
        return true;
    }

    /** 账户性质：不存在则按报文自动新增 */
    private String ensureNature(JdbcTemplate t, String code, String name) {
        if (blank(code)) {
            return null;
        }
        // nature_code 在库里可能是定长 CHAR，用 trim 比较
        Map<String, Object> exist = queryOne(t, "select id from %s where trim(nature_code) = ?"
                .formatted(tbl("BT_ACC_NATURE")), code.trim());
        if (exist != null) {
            return str(exist, "id");
        }
        String id = uuid();
        t.update(sql("insert into %s (id, nature_code, nature_name, valid_sign, create_date, create_by)"
                        + " values (?,?,?,?,?,?)").formatted(tbl("BT_ACC_NATURE")),
                id, code, blank(name) ? code : name, MdmConstant.Y, now(), createBy());
        return id;
    }

    /** 账户币种：先删后插 */
    private void replaceAccCur(JdbcTemplate t, String accId, List<MdmDto.BankAccCur> list,
                               Map<String, Map<String, Object>> currencyMap) {
        t.update(sql("delete from %s where bank_acc_id = ?").formatted(tbl("BT_BANK_ACC_CUR")), accId);
        if (list == null) {
            return;
        }
        for (MdmDto.BankAccCur cur : list) {
            Map<String, Object> row = currencyMap.get(cur.getPkCurrtypeCodeShow());
            if (row == null) {
                log.warn("账户币种在 BT_CURRENCY 中不存在，跳过: {}", cur.getPkCurrtypeCodeShow());
                continue;
            }
            t.update(sql("insert into %s (id, bank_acc_id, cur_id) values (?,?,?)")
                            .formatted(tbl("BT_BANK_ACC_CUR")),
                    uuid(), accId, str(row, "id"));
        }
    }

    // ==================================================================
    //  落库：客商
    // ==================================================================

    private Boolean upsertPartner(JdbcTemplate t, MdmDto.Partner bean, String existId,
                                  Map<String, Map<String, Object>> branchMap,
                                  Map<String, Map<String, Object>> typeMap,
                                  Map<String, Map<String, Object>> currencyMap) {
        String externalType = convertBptype(bean.getBptype());
        if (existId == null) {
            String id = uuid();
            t.update(sql("insert into %s (id, md_id, code, name, name_en, abbreviate, soc_code,"
                            + " external_type, status, audit_status, is_native, source_system, supplier_id,"
                            + " bw_type) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                            .formatted(tbl("SYS_EXTERNAL_CORP")),
                    id, bean.getMdId(), bean.getCode(), bean.getName(), bean.getEname(), bean.getShortname(),
                    bean.getUnifiedSocialCode(), externalType, 1, MdmConstant.STATUS_APPROVED, 1,
                    "BFS", appProps.getMdm().getSupplierId(), "00");
            replacePartnerAcc(t, id, bean.getBdBankaccbas(), branchMap, typeMap, currencyMap);
        } else {
            t.update(sql("update %s set code=?, name=?, name_en=?, abbreviate=?, soc_code=?, external_type=?"
                            + " where id=?").formatted(tbl("SYS_EXTERNAL_CORP")),
                    bean.getCode(), bean.getName(), bean.getEname(), bean.getShortname(),
                    bean.getUnifiedSocialCode(), externalType, existId);
            replacePartnerAcc(t, existId, bean.getBdBankaccbas(), branchMap, typeMap, currencyMap);
        }
        return true;
    }

    /** 客商账号：先删后插 */
    private void replacePartnerAcc(JdbcTemplate t, String corpId, List<MdmDto.PartnerBankAcc> list,
                                   Map<String, Map<String, Object>> branchMap,
                                   Map<String, Map<String, Object>> typeMap,
                                   Map<String, Map<String, Object>> currencyMap) {
        t.update(sql("delete from %s where external_corp_id = ?")
                .formatted(tbl("SYS_EXTERNAL_CORP_BANKACC")), corpId);
        if (list == null) {
            return;
        }
        for (MdmDto.PartnerBankAcc acc : list) {
            Map<String, Object> branch = branchMap.get(acc.getPkBankdoc());
            Map<String, Object> type = typeMap.get(acc.getPkBankdocBanktypeShow());
            // 币种只取第一条（与原系统一致：多条只有第一条生效）
            String curId = null;
            if (acc.getBankaccsub() != null && !acc.getBankaccsub().isEmpty()) {
                Map<String, Object> row = currencyMap.get(acc.getBankaccsub().get(0).getPkCurrtypeCodeShow());
                curId = row == null ? null : str(row, "id");
            }
            t.update(sql("insert into %s (id, external_corp_id, external_acc, external_acc_name, bank,"
                            + " bank_type, bank_sourcecode, cur_id) values (?,?,?,?,?,?,?,?)")
                            .formatted(tbl("SYS_EXTERNAL_CORP_BANKACC")),
                    uuid(), corpId, acc.getAccnum01(), acc.getAccname(),
                    branch == null ? null : str(branch, "bank_name"),
                    type == null ? null : str(type, "bank_type"),
                    branch == null ? null : str(branch, "sys_bank_code"), curId);
        }
    }

    // ==================================================================
    //  校验
    // ==================================================================

    private String validate(MdmDto.BankType b) {
        StringBuilder sb = new StringBuilder();
        if (blank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (blank(b.getCode())) {
            sb.append("银行类别编码不能为空;");
        }
        if (blank(b.getName())) {
            sb.append("银行类别名称不能为空;");
        }
        if (blank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }
        return sb.toString();
    }

    private String validate(MdmDto.BankBranch b, Map<String, Object> typeRow) {
        StringBuilder sb = new StringBuilder();
        if (blank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (blank(b.getBanktype())) {
            sb.append("银行类型不能为空;");
        } else if (typeRow == null) {
            sb.append("银行类型未能接收，暂不接收网点信息;");
        }
        if (blank(b.getCombinenum())) {
            sb.append("联行号不能为空;");
        }
        if (blank(b.getName())) {
            sb.append("开户行名称不能为空;");
        }
        if (blank(b.getCategoryCode())) {
            sb.append("主数据分类不能为空;");
        } else if (appProps.getMdm().isInsideBranchOnly()
                && !MdmConstant.CATEGORY_INSIDE.equals(b.getCategoryCode())) {
            sb.append("非境内银行数据，暂不接收;");
        }
        if (blank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }
        if (blank(b.getShortname())) {
            sb.append("网点简称不能为空;");
        }
        return sb.toString();
    }

    private String validate(MdmDto.Org b, Map<String, Map<String, Object>> currencyMap) {
        StringBuilder sb = new StringBuilder();
        if (blank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (blank(b.getOrgCode())) {
            sb.append("组织编码不能为空;");
        }
        if (blank(b.getOrgName())) {
            sb.append("组织名称不能为空;");
        }
        if (blank(b.getPkCurrtype())) {
            sb.append("本位币不能为空;");
        } else if (!currencyMap.containsKey(b.getPkCurrtype())) {
            sb.append("本位币在币种表中不存在;");
        }
        return sb.toString();
    }

    private String validate(MdmDto.BankAcc b, Map<String, Object> typeRow,
                            Map<String, Object> branchRow, Map<String, Object> corpRow) {
        StringBuilder sb = new StringBuilder();
        if (blank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (blank(b.getAccNum())) {
            sb.append("银行账号不能为空;");
        }
        if (blank(b.getPkBanktype())) {
            sb.append("银行类别不能为空;");
        } else if (typeRow == null) {
            sb.append("银行类别未能接收，暂不接收账户信息;");
        }
        if (blank(b.getPkBankdoc())) {
            sb.append("银行网点不能为空;");
        } else if (branchRow == null) {
            sb.append("银行网点未能接收，暂不接收账户信息;");
        }
        if (blank(b.getFinanceorg())) {
            sb.append("组织机构不能为空;");
        } else if (corpRow == null) {
            sb.append("组织机构未能接收，暂不接收账户信息;");
        }
        if (blank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }
        return sb.toString();
    }

    private String validate(MdmDto.Partner b) {
        StringBuilder sb = new StringBuilder();
        if (blank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (blank(b.getCode())) {
            sb.append("客商编号不能为空;");
        }
        if (blank(b.getName())) {
            sb.append("客商名称不能为空;");
        }
        return sb.toString();
    }

    /** 客商类型转换：SUP供应商-1 CUS客户-0 BP客商-2 */
    private static String convertBptype(String bptype) {
        if (MdmConstant.BPTYPE_SUPPLIER.equals(bptype)) {
            return "1";
        }
        if (MdmConstant.BPTYPE_CUSTOMER.equals(bptype)) {
            return "0";
        }
        if (MdmConstant.BPTYPE_BOTH.equals(bptype)) {
            return "2";
        }
        return bptype;
    }

    // ==================================================================
    //  回执组装
    // ==================================================================

    private Map<String, Object> response(String message, List<Map<String, Object>> items) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", MdmConstant.S);
        res.put("message", message);
        res.put("responseData", items);
        return res;
    }

    private static Map<String, Object> item(String mdId, String mdCode, String mdDescription,
                                            boolean ok, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mdId", mdId);
        m.put("mdCode", mdCode);
        m.put("mdDescription", mdDescription);
        m.put("status", ok ? MdmConstant.S : MdmConstant.E);
        m.put("message", message);
        return m;
    }

    /** 单条执行并包成回执：失败只影响这一条 */
    private Map<String, Object> run(Object bean, Function<JdbcTemplate, Boolean> work) {
        String mdId = mdId(bean);
        String mdCode = mdCode(bean);
        String mdDesc = mdDescription(bean);
        try {
            inTx(work);
            return item(mdId, mdCode, mdDesc, true, "接收成功");
        } catch (Exception e) {
            log.error("主数据落库失败 mdId={}", mdId, e);
            return item(mdId, mdCode, mdDesc, false, "保存失败：" + reason(e));
        }
    }

    private static String mdId(Object bean) {
        return bean instanceof MdmDto.BankType b ? b.getMdId()
                : bean instanceof MdmDto.BankBranch b ? b.getMdId()
                : bean instanceof MdmDto.Org b ? b.getMdId()
                : bean instanceof MdmDto.BankAcc b ? b.getMdId()
                : bean instanceof MdmDto.Partner b ? b.getMdId() : null;
    }

    private static String mdCode(Object bean) {
        return bean instanceof MdmDto.BankType b ? b.getMdCode()
                : bean instanceof MdmDto.BankBranch b ? b.getMdCode()
                : bean instanceof MdmDto.Org b ? b.getMdCode()
                : bean instanceof MdmDto.BankAcc b ? b.getMdCode()
                : bean instanceof MdmDto.Partner b ? b.getMdCode() : null;
    }

    private static String mdDescription(Object bean) {
        return bean instanceof MdmDto.BankType b ? b.getMdDescription()
                : bean instanceof MdmDto.BankBranch b ? b.getMdDescription()
                : bean instanceof MdmDto.Org b ? b.getMdDescription()
                : bean instanceof MdmDto.BankAcc b ? b.getMdDescription()
                : bean instanceof MdmDto.Partner b ? b.getMdDescription() : null;
    }

    // ==================================================================
    //  基础设施
    // ==================================================================

    private DataSource dataSource() {
        String key = dsKey();
        if (!registry.exists(key)) {
            throw new BizException(500, "MDM 落库数据源不存在：" + key
                    + "（检查 app.mdm.datasource 与 app.extra-datasources 配置）");
        }
        return registry.template(key).getDataSource();
    }

    private String dsKey() {
        String k = appProps.getMdm().getDatasource();
        return blank(k) ? DataSourceRegistry.MAIN : k.trim();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource());
    }

    /** 每条数据一个独立事务，保证单条失败不拖垮整批 */
    private <T> T inTx(Function<JdbcTemplate, T> work) {
        TransactionTemplate template = tx();
        return template.execute(status -> work.apply(jdbc()));
    }

    private TransactionTemplate tx() {
        TransactionTemplate t = tx;
        if (t == null) {
            synchronized (this) {
                t = tx;
                if (t == null) {
                    tx = t = new TransactionTemplate(new DataSourceTransactionManager(dataSource()));
                }
            }
        }
        return t;
    }

    /**
     * 表名。Oracle 不区分大小写，配置无感；Linux MySQL 在 lower_case_table_names=0 时区分，
     * 基表若建成小写，把 app.mdm.lowercase-tables 打开即可。
     */
    private String tbl(String name) {
        return appProps.getMdm().isLowercaseTables() ? name.toLowerCase() : name;
    }

    /** SQL 里的表名占位统一走这里，避免拼串时绕过大小写开关 */
    private String sql(String template) {
        return template;
    }

    private String findIdByMdId(JdbcTemplate t, String table, String mdId) {
        Map<String, Object> row = queryOne(t, "select id from %s where md_id = ?".formatted(table), mdId);
        return row == null ? null : str(row, "id");
    }

    /** 按 mdId 批量预加载，返回 mdId → 行（key 统一转小写） */
    private Map<String, Map<String, Object>> loadByMdId(String table, String cols, Collection<String> mdIds) {
        List<String> ids = mdIds.stream().filter(c -> !blank(c)).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = "select %s from %s where md_id in (%s)".formatted(cols, table, placeholders(ids.size()));
        return indexByMdId(jdbc().queryForList(sql, ids.toArray()));
    }

    /** 按任意列批量预加载 */
    private Map<String, Map<String, Object>> loadMap(String table, String cols, String keyColumn,
                                                     Collection<String> keys) {
        List<String> ks = keys.stream().filter(c -> !blank(c)).distinct().toList();
        if (ks.isEmpty()) {
            return Map.of();
        }
        String sql = "select %s from %s where %s in (%s)"
                .formatted(cols, table, keyColumn, placeholders(ks.size()));
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : jdbc().queryForList(sql, ks.toArray())) {
            Map<String, Object> lower = lowerKeys(row);
            Object k = lower.get(keyColumn.toLowerCase());
            if (k != null) {
                result.put(String.valueOf(k), lower);
            }
        }
        return result;
    }

    private Map<String, Map<String, Object>> indexByMdId(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> lower = lowerKeys(row);
            Object id = lower.get("md_id");
            if (id != null) {
                map.put(String.valueOf(id), lower);
            }
        }
        return map;
    }

    private Map<String, Object> queryOne(JdbcTemplate t, String sql, Object... args) {
        List<Map<String, Object>> rows = t.queryForList(sql, args);
        return rows.isEmpty() ? null : lowerKeys(rows.get(0));
    }

    /**
     * 列名统一转小写。
     * Oracle 返回的列标签是大写（MD_ID），MySQL 通常保持原样（md_id），
     * 不统一的话同一套代码换库就取不到值。
     */
    private static Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> map = new HashMap<>();
        row.forEach((k, v) -> {
            if (k != null) {
                map.put(k.toLowerCase(), v);
            }
        });
        return map;
    }

    private <T> List<String> mdIdOf(List<T> list, Function<T, String> getter) {
        List<String> ids = new ArrayList<>();
        list.forEach(o -> {
            String v = o == null ? null : getter.apply(o);
            if (!blank(v)) {
                ids.add(v);
            }
        });
        return ids;
    }

    private <T> List<T> parseArray(String json, Class<T> clazz, String bizName) {
        if (blank(json)) {
            throw new com.interchange.platform.service.handler.ReceiveHandler.BusinessException(
                    400, bizName + "报文为空（请以 JSON 数组 POST）");
        }
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new com.interchange.platform.service.handler.ReceiveHandler.BusinessException(
                    400, "解析" + bizName + "报文出错：" + e.getMessage());
        }
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    private static Timestamp parseDate(String text) {
        if (blank(text)) {
            return null;
        }
        try {
            return Timestamp.valueOf(java.time.LocalDateTime.parse(
                    text.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (Exception e) {
            try {
                return Timestamp.valueOf(java.time.LocalDate.parse(text.trim()).atStartOfDay());
            } catch (Exception ignored) {
                log.warn("开户日期格式无法识别，按空处理: {}", text);
                return null;
            }
        }
    }

    /** 「是」的多种写法 */
    private static boolean isTrue(String v) {
        return "1".equals(v) || "Y".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v) || "是".equals(v);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Map<String, Object> row, String col) {
        if (row == null) {
            return null;
        }
        Object v = row.get(col);
        return v == null ? null : String.valueOf(v);
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Timestamp now() {
        return Timestamp.valueOf(java.time.LocalDateTime.now());
    }

    private String createBy() {
        String by = appProps.getMdm().getCreateBy();
        return blank(by) ? "admin" : by;
    }

    private static String reason(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }
}
