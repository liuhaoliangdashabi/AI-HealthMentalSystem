package org.example.userservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name="file-service",path="/internal/file")
public interface FileClient {

    /**
     * 换头像/换封面：把旧图彻底删掉（删记录 + 删 OSS）。
     * objectKey 里带斜杠（useravatar/2026/11/09/xxx.jpg），只能走 query 参数，
     * 放进路径会被当成路径分隔符拆开，直接 404。
     */
    @PostMapping("/by-key")
    int deleteByKey(@RequestParam("objectKey")String objectKey,
                    @RequestParam("userId")Long userId);

    /**
     * 注销用户：名下所有文件打上 is_temp=1，交给 FileCleanupTask 兜底清理。
     * 不直接删 OSS —— 这个方法在全局事务里，OSS 删了回滚不回来。
     */
    @DeleteMapping("/by-user")
    int markTempByUser(@RequestParam("userId")Long userId);
}
