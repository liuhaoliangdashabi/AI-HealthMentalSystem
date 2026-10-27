package org.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArticleCommandDTO {
    @NotBlank(message = "文章标题不能为空")
    @Size(max=200,message = "文章标题不能超过200个字符")
    private String title;

    @NotNull(message = "文章分类不能为空")
    private Long categoryId;

    @NotBlank(message = "文章内容不能为空")
    @Size(max=5000,message = "文章内容不能超过5000个字符")
    private String content;

    @Size(max=1000,message="文章摘要不能超过1000个字符")
    private String summary;

    @Size(max = 500,message="封面路径不能超过500个字符")
    private String coverImage;

    @Size(max=500,message="标签不能超过500个字符")
    private String tags;
}
