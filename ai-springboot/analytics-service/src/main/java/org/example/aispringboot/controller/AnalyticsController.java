package org.example.aispringboot.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO;
import org.example.aispringboot.DTO.response.UvTrendItem;
import org.example.aispringboot.service.AnalyticsService;
import org.example.common.Result;
import org.example.common.annotation.RequireIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端数据看板。
 *
 * AnalyticsService 里 getOverview / getUVTrend 一直都有，但从来没有 controller 暴露过，
 * 所以 B 端首页一直报"接口不存在"——这不是拆微服务弄坏的，是项目原始就缺的一块。
 * 这里只做"把已有能力接出来"，不改任何统计逻辑。
 *
 * 前端消费路径：dashboard.vue → aiData.systemOverview / aiData.consultationStats，
 * 对应 AnalyticsOverviewResponseDTO 的字段，所以必须原样返回 DTO，不能包一层。
 */
@Slf4j
@RestController
@RequestMapping("/api/data-analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @RequireIdentity(adminOnly = true)
    @GetMapping("/overview")
    @SentinelResource(value="analytics-overview",
        blockHandler="overviewBlocked",fallback="overviewFallback")
    public Result<AnalyticsOverviewResponseDTO> getOverview() {
        log.debug("管理端看板总览请求");
        return Result.success(analyticsService.getOverview());
    }

    private static Result<AnalyticsOverviewResponseDTO> overviewBlocked(BlockException e){
        log.warn("看板被限流/熔断拦阻：{}",e.getClass().getSimpleName());
        return Result.success(AnalyticsOverviewResponseDTO.empty());
    }

    private static Result<AnalyticsOverviewResponseDTO> overviewFallback(Throwable t){
        log.warn("看板降级",t);
        return Result.success(AnalyticsOverviewResponseDTO.empty());
    }

    /** 前端暂时没用到，但服务层写好了，顺手接出来，省得以后再来改这个文件 */
    @RequireIdentity(adminOnly = true)
    @GetMapping("/uv-trend")
    public Result<List<UvTrendItem>> getUvTrend() {
        return Result.success(analyticsService.getUVTrend());
    }
}
