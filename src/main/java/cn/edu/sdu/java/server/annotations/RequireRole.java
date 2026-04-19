package cn.edu.sdu.java.server.annotations;

import java.lang.annotation.*;

/**
 * 自定义角色校验注解
 * 用于Controller方法级别的权限拦截
 * 使用示例：@RequireRole("STUDENT") 或 @RequireRole({"TEACHER","ADMIN"})
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /**
     * 允许访问的角色列表
     * 可选值: "STUDENT", "TEACHER", "ADMIN"
     */
    String[] value();
}
