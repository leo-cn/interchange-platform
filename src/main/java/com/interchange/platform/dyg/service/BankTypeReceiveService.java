package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.BankTypeDao;
import com.interchange.platform.dyg.entity.BisBifInit;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.dyg.vo.BankTypeVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 银行类别接收：{@code POST /api/receive/mdm-bank-type-receive}。
 * 报文为 JSON 数组，字段见 {@link BankTypeVO}。必须最先接收，网点按 mdId 关联它取人行前缀。
 */
@Component
@ReceiveApi(code = MdmConstant.API_BANK_TYPE,
        desc = "MDM 银行类别接收：写入 BT_BANK_TYPE，新增时级联 BIS_BIF_INIT")
public class BankTypeReceiveService extends MdmReceiveSupport<BankTypeVO> implements ReceiveService {

    @Resource
    private BankTypeDao bankTypeDao;

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return receive(body, BankTypeVO.class,
                "银行类别报文为空（请以 JSON 数组 POST）", "解析银行类别报文出错：");
    }

    @Override
    protected String mdIdOf(BankTypeVO bean) {
        return bean.getMdId();
    }

    @Override
    protected String mdCodeOf(BankTypeVO bean) {
        return bean.getMdCode();
    }

    @Override
    protected String mdDescriptionOf(BankTypeVO bean) {
        return bean.getMdDescription();
    }

    @Override
    protected String validate(BankTypeVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getCode())) {
            sb.append("银行类别编码不能为空;");
        }
        if (StringUtil.isBlank(b.getName())) {
            sb.append("银行类别名称不能为空;");
        }
        if (StringUtil.isBlank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }
        return sb.toString();
    }

    @Override
    protected void save(BankTypeVO bean) {
        BtBankType exist = bankTypeDao.findByMdId(bean.getMdId()).orElse(null);
        String validSign = MdmConstant.ACTIVE.equals(bean.getMdStatusCode())
                ? MdmConstant.Y : MdmConstant.N;
        boolean isNew = exist == null;

        BtBankType entity = isNew ? new BtBankType() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(bean.getMdId());
            entity.setIsSystem(MdmConstant.N);
            entity.setCreateDate(DateUtil.now());
            entity.setCreateBy(MdmConstant.CREATE_BY);
        } else {
            entity.setUpdateDate(DateUtil.now());
            entity.setUpdateBy(MdmConstant.CREATE_BY);
        }
        entity.setBankType(bean.getCode());
        entity.setTypeName(bean.getName());
        entity.setBankPrefix(bean.getCombinecode());
        entity.setValidSign(validSign);
        bankTypeDao.save(entity);

        // 新增银行类别时级联插一条接口初始化记录
        if (isNew) {
            BisBifInit bif = new BisBifInit();
            bif.setId(StringUtil.uuid());
            bif.setBifCode(bean.getCode());
            bif.setName(bean.getName());
            bif.setBankTypeId(entity.getId());
            bif.setValidSign(MdmConstant.Y);
            bif.setIsSystem(MdmConstant.N);
            bif.setCreateDate(DateUtil.now());
            bif.setCreateBy(MdmConstant.CREATE_BY);
            bankTypeDao.saveBifInit(bif);
        }
    }
}
