package com.seeex.tech.web.navigation;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前登录员工的统一导航接口，不接收浏览器传入的员工标识。 */
@RestController
@RequestMapping("/api/me")
public class NavigationController {
    private final NavigationService navigationService;

    /** 注入导航聚合服务。 */
    public NavigationController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    /**
     * 返回工作台、基础平台、各业务应用的授权导航树。
     *
     * @param authentication 已通过服务端会话验证的身份
     * @return 统一导航节点列表
     */
    @GetMapping("/navigation")
    public ApiResponse<List<NavigationService.NavNode>> navigation(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        try {
            long employeeId = Long.parseLong(authentication.getName());
            if (employeeId <= 0) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "登录身份无效");
            }
            return ApiResponse.ok(navigationService.navigation(employeeId));
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录身份无效");
        }
    }
}
