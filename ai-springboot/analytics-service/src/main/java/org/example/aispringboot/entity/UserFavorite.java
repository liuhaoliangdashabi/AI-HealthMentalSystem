package org.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
 * 用户收藏表
 * 注意：数据库有 UNIQUE(user_id, article_id)——同一个用户对同一篇文章只能收藏一次
 * 所以"收藏"要先查再插（或者捕获唯一键冲突），不能无脑 insert
 */
@Data
@TableName("user_favorite")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    //Long 类型要用 @NotNull，不能用 @NotBlank
    @NotNull(message = "用户不能为空")
    @Positive(message = "用户ID不合法")
    @TableField("user_id")
    private Long userId;

    //文章ID是 varchar(36) 的 UUID，所以是 String；长度上限就是列宽
    @NotBlank(message = "文章不能为空")
    @Size(max = 36, message = "文章ID不合法")
    @TableField("article_id")
    private String articleId;

    //这张表只有 created_at，没有 updated_at——收藏记录只会被删，不会被改
    @TableField("created_at")
    private LocalDateTime createdAt;
}
