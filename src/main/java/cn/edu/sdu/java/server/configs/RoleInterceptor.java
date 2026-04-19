package cn.edu.sdu.java.server.configs;

import cn.edu.sdu.java.server.annotations.RequireRole;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * RoleInterceptor 角色拦截器
 * 配合 @RequireRole 注解实现轻量级的方法级权限校验
 * 从 SecurityContextHolder 中获取当前用户角色，与注解声明的角色列表比对
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 仅拦截 Controller 方法
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 优先检查方法级注解，再检查类级注解
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        // 无注解则放行
        if (requireRole == null) {
            return true;
        }

        // 获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或认证已过期");
            return false;
        }

        // 获取用户角色
        String userRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");

        // 校验角色：注解中的值会自动加上 ROLE_ 前缀进行比对
        String[] allowedRoles = requireRole.value();
        for (String role : allowedRoles) {
            String fullRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            if (fullRole.equals(userRole)) {
                return true;
            }
        }

        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "权限不足，需要角色: " + String.join("/", allowedRoles));
        return false;
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of("code", 1, "data", "", "msg", msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
