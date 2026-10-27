package org.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.aispringboot.enumClass.ArticleStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("knowledge_article")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeArticle {

    //主键，数据库是 varchar(36) 存 UUID
    //ASSIGN_UUID：id 为空时自动生成；前端传了就用前端的
    @TableId(type = IdType.ASSIGN_UUID)
    @Size(max=36, message="ID不能超过36个字符")
    private String id;

    //Long 类型要用 @NotNull
    @NotNull(message = "分类不能为空")
    @TableField("category_id")
    @Positive(message = "分类ID必须是正整数")
    private Long categoryId;

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题不能超过200个字符")
    private String title;

    @Size(max = 1000, message = "文章摘要不能超过1000个字符")
    private String summary;

    @NotBlank(message = "文章内容不能为空")
    @Size(max = 5000, message = "文章内容不能超过5000个字符")
    private String content;

    @TableField("cover_image")
    @Size(max=500, message = "封面图片不能超过500个字符")
    private String coverImage;

    //逗号分隔的标签串，如 "焦虑,情绪管理"
    private String tags;

    @NotNull(message = "作者不能为空")
    @TableField("author_id")
    @Positive(message = "作者ID必须是正整数")
    private Long authorId;

    @TableField("read_count")
    @Min(value = 0, message = "阅读量不能是负数")
    private Integer readCount;

    //0:草稿 1:已发布 2:已下线
    @Min(value = 0, message = "状态只有 0/1/2")
    @Max(value = 2, message = "状态只有 0/1/2")
    private Integer status;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    //==================== 封装方法 ====================

    //是否草稿——前端据此显示"发布"按钮
    public boolean isDraft() {
        return ArticleStatus.DRAFT.getCode().equals(this.status);
    }

    //是否已发布——前端据此显示"下线"按钮
    public boolean isPublished() {
        return ArticleStatus.PUBLISHED.getCode().equals(this.status);
    }

    //是否已下线
    public boolean isOffline() {
        return ArticleStatus.OFFLINE.getCode().equals(this.status);
    }

    //状态的中文名
    public String getStatusDisplayName() {
        try {
            return ArticleStatus.fromCode(this.status).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    //把逗号分隔的 tags 拆成列表
    public List<String> getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String tag : tags.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    //列表页摘要：没写摘要就剥掉 HTML 标签，从正文里截一段
    public String getSummaryOrExcerpt(int maxLength) {
        if (summary != null && !summary.trim().isEmpty()) {
            return summary;
        }
        if (content == null || content.isEmpty()) {
            return "";
        }
        String plain = content.replaceAll("<[^>]+>", "").trim();
        return plain.length() <= maxLength ? plain : plain.substring(0, maxLength) + "...";
    }
}
