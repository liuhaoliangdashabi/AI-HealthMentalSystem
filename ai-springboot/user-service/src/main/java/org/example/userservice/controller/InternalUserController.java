package org.example.userservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.userservice.DTO.response.UserBriefDTO;
import org.example.userservice.entity.User;
import org.example.userservice.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务间接口，只能内网调。
 *
 * 网关故意不配 /internal/** 路由，所以外部从 9000 进不来；
 * 本服务也不对外暴露这个 context path，直连 8050 才能访问。
 * 以后要更严就加一个共享密钥头校验，现阶段内网足够。
 */
@Slf4j
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    @Autowired
    private UserMapper userMapper;

    /**
     * 批量取用户简要信息。传空列表直接返回空 Map，不查库。
     * 返回 Map 而不是 List，调用方拿到就能按 id 取，不用再自己转一次。
     */
    @PostMapping("/brief")
    public Map<Long, UserBriefDTO> brief(@RequestBody List<Long> ids) {
        Map<Long, UserBriefDTO> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().in(User::getId, ids));
        for (User u : users) {
            String nickname = u.getNickname();
            if (nickname == null || nickname.isBlank()) {
                nickname = u.getUsername();
            }
            result.put(u.getId(), new UserBriefDTO(u.getId(), u.getUsername(), nickname, u.getDisplayName()));
        }
        log.debug("internal brief 入参 {} 个，命中 {} 个", ids.size(), result.size());
        return result;
    }

    //B 端看板要的两个数：总用户+最近几天每日新增。
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "30") int days) {
        Map<String, Object> result = new HashMap<>();
        result.put("total", userMapper.selectCount(null));
        result.put("dailyNew", dailyNew(days));
        return result;
    }

    /**
     * 每日新增用户数，给 B 端首页统计用（原 AnalyticsService 里 in-memory 数表的那段）。
     * days 指最近几天，返回 "yyyy-MM-dd" -> 数量。
     */
    @GetMapping("/daily-new")
    public Map<String, Long> dailyNew(@RequestParam(defaultValue = "7") int days) {
        Map<String, Long> result = new HashMap<>();
        if (days <= 0) {
            return result;
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getCreatedAt)
                .ge(User::getCreatedAt, java.time.LocalDateTime.now().minusDays(days)));
        for (User u : users) {
            if (u.getCreatedAt() == null) {
                continue;
            }
            result.merge(u.getCreatedAt().toLocalDate().toString(), 1L, Long::sum);
        }
        return result;
    }
}
