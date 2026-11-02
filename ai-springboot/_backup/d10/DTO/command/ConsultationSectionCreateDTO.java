package org.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;

@Data
public class ConsultationSectionCreateDTO {
    @Size(max=50,message="会话标题最多50个字符")
    private String sessionTitle;
    @Size(max=500,message="初始消息做多500个字符")
    @NotBlank(message="初始消息不能为空")
    private String initialMessage;
}
