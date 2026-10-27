package org.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_category")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCategory {

    //状态常量
    public static final int STATUS_DISABLED = 0;//禁用
    public static final int STATUS_ENABLED  = 1;//启用

    @TableId(type = IdType.AUTO)
    private Long id;

    //父分类ID，0 表示顶级分类
    @TableField("parent_id")
    @Min(value = 0,message = "父分类Id不能是负数")
    private Long parentId;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 100, message = "分类名不能超过100个字符")
    @TableField("category_name")
    private String categoryName;

    //分类代码（数据库有唯一索引），现有数据都是 NULL
    @TableField("category_code")
    @Size(max=50, message = "分类代码不能超过50个字符")
    private String categoryCode;

    @Size(max = 100, message = "分类描述不能超过100个字符")
    private String description;

    @TableField("sort_order")
    @Min(value = 0,message = "排序值不能是负数")
    private Integer sortOrder;

    //0:禁用 1:启用
    @Min(value = 0,message = "状态只有 0/1")
    @Max(value = 1,message = "状态只有 0/1")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    //==================== 封装方法 ====================

    //是否启用——查分类树时只返回启用的
    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }

    //是否顶级分类
    public boolean isRoot() {
        return parentId == null || parentId == 0L;
    }

    //状态的中文名
    public String getStatusDisplayName() {
        if (status == null) {
            return "未知";
        }
        return status == STATUS_ENABLED ? "启用" : "禁用";
    }
}
