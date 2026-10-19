package org.example.aispringboot.DTO.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 个人资料修改入参。
 *
 * 刻意「不含」这些字段，不是漏了：
 * - username：唯一键 + 登录凭据，改名等于换身份，要走独立的重名校验流程
 * - password：走独立的改密接口（要验旧密码），不能跟资料混在一起
 * - userType / status：越权字段，用户自己填了就能提权 / 解封
 */
@Data
public class UserProfileUpdateCommandDTO {

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    //空串是合法的，表示"把手机号清空"；null 表示"这次不改"
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Min(value = 0, message = "性别取值只能是 0未知 / 1男 / 2女")
    @Max(value = 2, message = "性别取值只能是 0未知 / 1男 / 2女")
    private Integer gender;

    @Past(message = "生日不能是未来日期")
    private LocalDate birthday;
}
