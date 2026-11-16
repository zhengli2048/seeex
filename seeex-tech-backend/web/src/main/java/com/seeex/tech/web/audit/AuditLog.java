package com.seeex.tech.web.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 审计注解，标注在需要记录审计的管理方法上。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 动作，如 CREATE_DEPARTMENT。 */
    String action();

    /** 资源类型，如 DEPARTMENT。 */
    String resourceType() default "";
}
