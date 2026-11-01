package org.example.diaryservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.common.annotation.RequireIdentity;
import org.example.diaryservice.DTO.response.UserActivityItem;
import org.example.diaryservice.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人主页贡献日历。
 * <p>
 * 原来挂在 web 的 /api/user/activity 上，属于用户域；
 * 但数据源是日记库，user 拆出去以后就该回到 diary-service。
 * 前端暂时还调老路径，网关用 RewritePath 兼容到这里的 /emotion-diary/activity。
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ActivityController {

    /** 贡献图默认跨度，13 周 ≈ 三个月，和前端 profile.vue 的格数对齐 */
    private static final int DEFAULT_DAYS = 91;

    @Autowired
    private ActivityService activityService;

    @RequireIdentity
    @GetMapping("/emotion-diary/activity")
    public Result<List<UserActivityItem>> myActivity(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        log.debug("Id={}查询个人主页活跃记录", userId);
        return Result.success(activityService.listRecentActivity(userId, DEFAULT_DAYS));
    }
}
