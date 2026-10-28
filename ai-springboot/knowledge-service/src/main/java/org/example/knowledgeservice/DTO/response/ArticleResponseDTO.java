package org.example.knowledgeservice.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDTO {
    private String id;
    private String title;
    private String content;
    //库里存的是 OSS 的相对路径（objectKey），新增/修改时原样回传，别拿它当图片地址用
    private String coverImage;

    //coverImage 换算出来的完整访问 URL，只用来显示。前端读这个
    private String coverImageUrl;

    private Long categoryId;
    private Integer status;
    private Integer readCount;
    private String summary;
    private String tags;

    //tags 拆好的数组，前端直接 v-for 用，不用自己 split
    private List<String> tagList;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;

    private String categoryName;
    private String authorName;
}
