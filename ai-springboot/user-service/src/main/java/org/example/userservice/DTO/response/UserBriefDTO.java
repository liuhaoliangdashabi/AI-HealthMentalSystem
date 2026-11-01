package org.example.userservice.DTO.response;

/**
 * 给其他服务用的最小用户信息（会话列表要显示昵称、日记列表要显示作者）。
 * 只暴露三个字段，password / email / phone 一律不出去。
 */
public record UserBriefDTO(Long id, String username, String nickname, String displayName) {
}
