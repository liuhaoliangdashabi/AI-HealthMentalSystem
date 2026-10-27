package org.example.aispringboot.util;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.config.OssConfig;
import org.example.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class OSSUtil {
    //后缀 → Content-Type（自己维护，跟 ALLOWED_EXT 一套）
    private static final Map<String, String> CONTENT_TYPE_MAP = Map.of(
            ".jpg",  "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png",  "image/png",
            ".gif",  "image/gif",
            ".webp", "image/webp"
    );

    //允许的图片后缀
    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    //单文件大小上限 10MB（跟前端 beforeUpload 里的限制一致）
    private static final long MAX_SIZE = 10 * 1024 * 1024L;

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final OSSClient ossClient;
    private final OssConfig ossConfig;

    public OSSUtil(OSSClient ossClient, OssConfig ossConfig) {
        this.ossClient = ossClient;
        this.ossConfig = ossConfig;
    }

    /**
     * 上传文件
     *
     * @param file   前端传来的文件
     * @param prefix 业务前缀，如 article / avatar
     * @return objectKey（相对路径），存进数据库的就是它
     */
    public String upload(MultipartFile file, String prefix) {
        //① 校验（前端也校验了，但前端能绕过，后端必须再来一遍）
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传的文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件不能超过 10MB");
        }
        String suffix = extractSuffix(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(suffix)) {
            throw new BusinessException("只支持 jpg / jpeg / png / gif / webp 格式");
        }

        //② 生成 objectKey：article/2026/10/04/xxxxxxxx.jpg
        String objectKey = buildObjectKey(suffix, prefix);

        //③ 上传
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossConfig.getBucket())
                    .key(objectKey)
                    .contentType(CONTENT_TYPE_MAP.getOrDefault(suffix, "application/octet-stream"))   //不设的话浏览器会下载而不是预览
                    .body(BinaryData.fromStream(in))
                    .build();

            ossClient.putObject(request);
            log.info("文件上传成功：objectKey={}", objectKey);
            return objectKey;

        } catch (IOException e) {
            log.error("读取上传文件失败：{}", file.getOriginalFilename(), e);
            throw new BusinessException("文件上传失败");
        } catch (Exception e) {
            log.error("上传 OSS 失败：objectKey={}", objectKey, e);
            throw new BusinessException("文件上传失败");
        }
    }

    /**
     * objectKey 转完整可访问的 URL
     *
     * @param objectKey 数据库里存的相对路径
     * @return 完整 URL，传空返回 null
     */
    public String getUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        //历史数据里可能直接存了完整 URL —— 原样返回，别重复拼
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        return ossConfig.buildBaseUrl() + "/" + stripLeadingSlash(objectKey);
    }

    /** 删除文件，换封面时清理旧的用。删失败不影响主流程，只记警告不抛异常。 */
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return;   //完整 URL 的历史数据，不动
        }
        try {
            ossClient.deleteObject(DeleteObjectRequest.newBuilder()
                    .bucket(ossConfig.getBucket())
                    .key(stripLeadingSlash(objectKey))
                    .build());
            log.info("OSS 文件已删除：objectKey={}", objectKey);
        } catch (Exception e) {
            log.warn("删除 OSS 文件失败：objectKey={}", objectKey, e);
        }
    }

    //取后缀，统一转小写。没有后缀返回空串
    private String extractSuffix(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    //article/2026/10/04/9f3c8a1e....jpg
    private String buildObjectKey(String suffix, String prefix) {
        String dir = (prefix == null || prefix.isBlank()) ? "common" : prefix;
        return dir + "/" + LocalDate.now().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private String stripLeadingSlash(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
