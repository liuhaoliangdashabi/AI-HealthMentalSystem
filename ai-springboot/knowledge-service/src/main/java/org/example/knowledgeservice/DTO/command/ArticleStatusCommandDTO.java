package org.example.knowledgeservice.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArticleStatusCommandDTO {
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只有 0/1/2")
    @Max(value = 2, message = "状态只有 0/1/2")
    private Integer status;
}
