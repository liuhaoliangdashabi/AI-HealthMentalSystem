package org.example.aispringboot.DTO.command;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;

@Data
@TableName("user")
@Builder
public class UserRegisterCommandDTO {
    @NotBlank(message="用户名不能为空")
    @Size(min=3,max=50,message="用户名长度在3~50个字符之间")
    private String username;

    @NotBlank(message="邮箱不能为空")
    @Email(message="邮箱格式错误")
    @Size(max=100,message="邮箱长度不能超过100个字符")
    private String email;

    @Size(max=50,message="昵称长度最长不能超过50个字符")
    private String nickname;

    @Pattern(regexp="^1[3-9]\\d{9}$",message="手机号格式错误")
    private String phone;

    private LocalDate birthday;

    @NotBlank(message = "密码不能为空")
    @Size(min=6,max=50,message="密码长度必须在6到50个字符之间")
    @ToString.Exclude
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Size(min=6,max=50,message="确认密码长度必须在6到50个字符之间")
    @ToString.Exclude
    private String confirmPassword;

    private Integer gender;
    @Builder.Default
    private Integer userType=1;
}
