package org.example.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginResponseDTO {
    private String token;
    private String roleType;
    private UserDetailResponseDTO userInfo;

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDetailResponseDTO{
        private Long id;
        private String username;
        private String email;
        private String nickname;
        private String avatar;
        private String phone;
        private Integer gender;
        private String genderDisplayName;
        private LocalDate birthday;
        private Integer userType;
        private String userTypeDisplayName;
        private Integer status;
        private String statusDisplayName;
        private String displayName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
