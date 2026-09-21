package com.plexus.personal.resume.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResumeProfileRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 100, message = "姓名不能超过 100 个字符")
        String name,

        @NotBlank(message = "目标岗位不能为空")
        @Size(max = 100, message = "目标岗位不能超过 100 个字符")
        String targetRole,

        @Size(max = 2000, message = "个人简介不能超过 2000 个字符")
        String summary,

        @Size(max = 4000, message = "教育经历不能超过 4000 个字符")
        String education,

        @Size(max = 4000, message = "实习经历不能超过 4000 个字符")
        String internship,

        @Size(max = 6000, message = "项目经历不能超过 6000 个字符")
        String projects,

        @Size(max = 2000, message = "专业技能不能超过 2000 个字符")
        String skills
) {
}
