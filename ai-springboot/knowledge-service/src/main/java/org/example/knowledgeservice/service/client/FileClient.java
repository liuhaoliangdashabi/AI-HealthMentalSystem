package org.example.knowledgeservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调 file-service 的内部接口。
 *
 * 必须是 interface —— @FeignClient 挂在类上不会生效，Spring 只会当成普通 Bean。
 * name 用来去 Nacos 查实例，path 是统一前缀（前导斜杠不能省）。
 */
@FeignClient(name = "file-service", path = "/internal/file")
public interface FileClient {

    /**
     * 删一张图：先删 sys_file_info 记录，成功了再删 OSS。
     *
     * file-service 内部不按 upload_user_id 过滤 —— 文章封面可能是管理员代传的，
     * 传作者 id 会匹配 0 行、静默不删。userId 只落在日志里便于追溯。
     */
    @PostMapping("/by-key")
    int deleteByKey(@RequestParam("objectKey") String objectKey,
                    @RequestParam("userId") Long userId);
}
