package org.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class UserLoginCommandDTO {
    @NotBlank(message="用户名或邮箱不能为空")
    @Size(max=100,message="用户名或邮箱长度不能超过100个字符")
    private String username;
    @NotBlank(message="密码不能为空")
    @Size(max=100,message="密码长度在6到50个字符之间")
    @ToString.Exclude
    private String password;
}
