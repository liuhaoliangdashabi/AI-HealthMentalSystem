package org.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("sys_file_info")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysFileInfo {

    //状态常量
    public static final int STATUS_DELETED = 0;//已删除
    public static final int STATUS_NORMAL  = 1;//正常

    @TableId(type = IdType.AUTO)
    private Long id;

    //用户上传时的原始文件名，如 "未命名.png"
    @TableField("original_name")
    @Size(max=255,message = "文件名不能超过255个字符")
    private String originalName;

    //服务器上的相对路径，如 "/files/bussiness/article/1756963989972.png"
    @TableField("file_path")
    @Size(max=500,message = "文件路径不能超过500个字符")
    private String filePath;

    //文件大小（字节），数据库是 bigint 所以用 Long
    @TableField("file_size")
    @Min(value = 0,message = "文件大小不能是负数")
    private Long fileSize;

    //IMG / PDF / TXT / DOC / XLS ...
    @TableField("file_type")
    @Size(max=20,message = "文件类型不能超过20个字符")
    private String fileType;

    //业务类型，前端硬编码传 "ARTICLE" / "USER_AVATAR"
    @TableField("business_type")
    @Size(max=50,message = "业务类型不能超过50个字符")
    private String businessType;

    //业务对象ID：文章传 UUID，头像传用户ID
    //历史数据里有一条是字符串 "null"，是前端传 null 被字符串化的脏数据
    @TableField("business_id")
    private String businessId;

    //业务字段名，前端硬编码传 "cover" / "avatar"，不是随机生成的
    @TableField("business_field")
    @Size(max=50,message = "业务字段名不能超过50个字符")
    private String businessField;

    //上传用户ID
    @TableField("upload_user_id")
    private Long uploadUserId;

    //是否临时文件 0:否 1:是
    @TableField("is_temp")
    @Min(value = 0,message = "isTemp只有0/1")
    @Max(value = 1,message = "isTemp只有0/1")
    private Integer isTemp;

    //0:删除 1:正常
    @Min(value = 0,message = "status只有0/1")
    @Max(value = 1,message = "status只有0/1")
    private Integer status;

    //时间
    @TableField("create_time")
    private LocalDateTime createTime;

    //过期时间，只对临时文件有效
    @TableField("expire_time")
    private LocalDateTime expireTime;

    //==================== 封装方法 ====================

    //是否图片
    public boolean isImage() {
        return "IMG".equals(fileType);
    }

    //是否临时文件
    public boolean isTempFile() {
        return isTemp != null && isTemp == 1;
    }

    //是否已被删除（软删除）
    public boolean isDeleted() {
        return status != null && status == STATUS_DELETED;
    }

    //是否已过期——清理定时任务用。永久文件的 expireTime 是 null，不算过期
    public boolean isExpired() {
        return isTempFile()
                && expireTime != null
                && expireTime.isBefore(LocalDateTime.now());
    }

    //人类可读的大小，如 "1.73 MB"
    public String getReadableSize() {
        if (fileSize == null || fileSize <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double size = fileSize;
        int unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return unitIndex == 0
                ? fileSize + " B"
                : String.format("%.2f %s", size, units[unitIndex]);
    }
}
