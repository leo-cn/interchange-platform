package com.interchange.platform.standard.core.base;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;

/**
 * 实体基类：统一主键 {@code id}。
 *
 * <p>写法参照 dyg-erp 的 {@code com.byttersoft.hibernate.common.entity.IdEntity}：
 * 主键声明在基类上，业务实体都继承它，不再各自写一遍 {@code id} 字段与 {@code @Id}。
 *
 * <p>id 由业务侧赋值（{@code StringUtil.uuid()}，32 位无连字符），所以这里不配生成器；
 * 注解放在字段上，与子类的字段访问方式保持一致。
 */
@MappedSuperclass
public abstract class IdEntity implements Serializable {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
