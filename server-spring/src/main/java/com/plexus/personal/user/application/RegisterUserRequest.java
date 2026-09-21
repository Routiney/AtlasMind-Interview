package com.plexus.personal.user.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 100, message = "用户名长度必须为 3 到 100 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为 8 到 72 个字符")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 100, message = "显示名称不能超过 100 个字符")
        String displayName
) {
}
