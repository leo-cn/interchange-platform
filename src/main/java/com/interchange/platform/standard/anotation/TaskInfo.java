package com.interchange.platform.standard.anotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 任务元信息，由 TaskRegistry 启动时读取。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TaskInfo {

    /** 任务标识，对应 interface_task.handler_bean */
    String code();

    /** 用途说明，任务编辑页下拉展示 */
    String desc() default "";
}
