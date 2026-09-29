package com.interchange.platform.standard.sys.service;

import com.interchange.platform.standard.sys.dao.BizUserDao;
import com.interchange.platform.standard.sys.vo.BizUserRow;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 业务系统用户表读取（账号主数据）。
 * 表名与数据源由 {@code app.user.*} 配置，取数逻辑见 {@link BizUserDao}。
 */
@Service
public class BizUserService {

    @Resource
    private BizUserDao bizUserDao;

    /** 业务系统里的一个账号 */
    public record BizUser(String id,
                          String loginName,
                          String username,
                          String passwordHash,
                          String salt,
                          int status,
                          String corpId) {

        /** 展示名：优先中文姓名 */
        public String displayName() {
            return (username == null || username.isBlank()) ? loginName : username;
        }
    }

    /** 按登录名查账号 */
    public Optional<BizUser> findByLoginName(String loginName) {
        return bizUserDao.findByLoginName(loginName).map(BizUserService::toBizUser);
    }

    /** 按业务系统主键查账号 */
    public Optional<BizUser> findById(String id) {
        return bizUserDao.findById(id).map(BizUserService::toBizUser);
    }

    /** 业务系统里的全部账号（按登录名排序） */
    public List<BizUser> listAll() {
        return bizUserDao.listAll().stream().map(BizUserService::toBizUser).toList();
    }

    /** 业务系统里这个账号是不是启用状态 */
    public boolean isActive(BizUser user) {
        return user != null && user.status() == 1;
    }

    private static BizUser toBizUser(BizUserRow row) {
        return new BizUser(row.getId(), row.getLoginName(), row.getUsername(),
                row.getPassword(), row.getSalt(), row.getStatus(), row.getCorpId());
    }
}
