package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.dao.BankAccDao;
import com.interchange.platform.dyg.dao.BankInputDao;
import com.interchange.platform.dyg.dao.CorpDao;
import com.interchange.platform.dyg.dao.CurrencyDao;
import com.interchange.platform.dyg.dao.ExternalCorpDao;
import com.interchange.platform.dyg.dao.ItemDao;
import com.interchange.platform.dyg.dao.RegionDao;
import com.interchange.platform.dyg.vo.StandardBankAccVO;
import com.interchange.platform.dyg.vo.StandardBankInputVO;
import com.interchange.platform.dyg.vo.StandardCorpVO;
import com.interchange.platform.dyg.vo.StandardCurVO;
import com.interchange.platform.dyg.vo.StandardExternalCorpAccVO;
import com.interchange.platform.dyg.vo.StandardExternalCorpVO;
import com.interchange.platform.dyg.vo.StandardItemVO;
import com.interchange.platform.dyg.vo.StandardRegionVO;
import com.interchange.platform.dyg.vo.StandardUserVO;
import com.interchange.platform.standard.exception.BusinessException;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 标准接口 9 个查询接口的业务逻辑（币种 / 单位 / 用户 / 省市 / 银行账号 / 网点 / 客商 / 客商账号 / 科目）。
 *
 * <p>只做业务逻辑：解析报文、校验入参、调各业务 DAO 取数。SQL 在各自的业务 DAO 里，
 * 回执（status / message / date）由 {@code dyg.controller.BaseController} 包。
 *
 * <p>入参不合法（报文为空、日期格式错、省市四个条件全空…）抛 {@link BusinessException}，
 * 由控制器转成 status = 1 的失败回执。
 */
@Service
@Transactional(readOnly = true)
public class StandardQueryService {

    @Resource
    private CurrencyDao currencyDao;
    @Resource
    private CorpDao corpDao;
    @Resource
    private RegionDao regionDao;
    @Resource
    private BankAccDao bankAccDao;
    @Resource
    private BankInputDao bankInputDao;
    @Resource
    private ExternalCorpDao externalCorpDao;
    @Resource
    private ItemDao itemDao;

    /* ===================== 9 个查询接口 ===================== */

    /** 币种查询：curCode 精确、curName / englishCode 模糊、updateDate 大于等于（空值不校验格式） */
    public List<StandardCurVO> cur(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        if (!DateUtil.isValidDate(p.get("updateDate"))) {
            throw new BusinessException("更新日期格式错误，应为 yyyy-MM-dd");
        }
        return currencyDao.listStandardCur(p.get("curCode"), p.get("curName"),
                p.get("englishCode"), p.get("updateDate"));
    }

    /** 单位查询：code 精确、name 模糊、updateDate 大于等于 */
    public List<StandardCorpVO> corp(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return corpDao.listStandardCorp(p.get("code"), p.get("name"), p.get("updateDate"));
    }

    /** 用户查询：corpCode / corpName / loginName 精确、username 模糊、updateDate 大于等于 */
    public List<StandardUserVO> user(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return corpDao.listStandardUser(p.get("corpCode"), p.get("corpName"), p.get("loginName"),
                p.get("username"), p.get("updateDate"));
    }

    /** 省市查询：四个条件至少填一个 */
    public List<StandardRegionVO> region(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        if (StringUtil.isBlank(p.get("cityCode")) && StringUtil.isBlank(p.get("cityName"))
                && StringUtil.isBlank(p.get("provCode")) && StringUtil.isBlank(p.get("provName"))) {
            throw new BusinessException("市代码、市名称、省代码、省名称至少填写一个");
        }
        return regionDao.listStandardRegion(p.get("cityCode"), p.get("cityName"),
                p.get("provCode"), p.get("provName"));
    }

    /** 银行账号查询：bankAcc / corpCode 精确、accName / corpName 模糊、updateDate 大于等于 */
    public List<StandardBankAccVO> bankAcc(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return bankAccDao.listStandardBankAcc(p.get("bankAcc"), p.get("accName"), p.get("corpCode"),
                p.get("corpName"), p.get("updateDate"));
    }

    /** 网点（联行号）查询：bankCode 精确、bankName 模糊、updateDate 大于等于 */
    public List<StandardBankInputVO> bankInput(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return bankInputDao.listStandardBankInput(p.get("bankCode"), p.get("bankName"), p.get("updateDate"));
    }

    /** 客商查询：code 精确、name 模糊 */
    public List<StandardExternalCorpVO> externalCorp(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return externalCorpDao.listStandardExternalCorp(p.get("code"), p.get("name"));
    }

    /** 客商账号查询：code / externalAcc 精确、name 模糊 */
    public List<StandardExternalCorpAccVO> externalCorpAcc(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return externalCorpDao.listStandardExternalCorpAcc(p.get("code"), p.get("name"), p.get("externalAcc"));
    }

    /** 科目查询：itemCode 精确、itemName 模糊、updateDate 大于等于 */
    public List<StandardItemVO> item(String body) {
        Map<String, String> p = JsonUtil.toStringMap(body);
        if (p == null) {
            throw new BusinessException("报文为空或不是 JSON 对象");
        }
        return itemDao.listStandardItem(p.get("itemCode"), p.get("itemName"), p.get("updateDate"));
    }
}
