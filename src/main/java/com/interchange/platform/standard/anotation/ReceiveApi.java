package com.interchange.platform.standard.anotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 接收接口元信息，由 ReceiveDispatchService 启动时读取并登记进 receive_api。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReceiveApi {

    /** 接口编码，对应 receive_api.api_code 与 URL /api/receive/{apiCode} */
    String code();

    /** 用途说明 */
    String desc() default "";
}
