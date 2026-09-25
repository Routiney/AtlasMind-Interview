"""第 5 课单元 2：原始简历文本解析工具。"""

import re
from typing import Annotated

from langchain.tools import tool
from langchain_core.tools import ToolException
from pydantic import Field


FIELD_ALIASES = {
    "target_role": ("目标岗位", "求职意向", "目标职位"),
    "summary": ("个人简介", "个人总结", "简介"),
    "education": ("教育经历", "教育背景", "学历经历"),
    "internship": ("实习经历", "工作经历", "工作经验"),
    "projects": ("项目经历", "项目经验"),
    "skills": ("专业技能", "技能清单", "技术栈", "技能"),
}


def _extract_sections(text: str) -> dict[str, str]:
    headings = [alias for aliases in FIELD_ALIASES.values() for alias in aliases]
    heading_pattern = "|".join(re.escape(heading) for heading in headings)
    matches = list(re.finditer(rf"(?m)^\s*({heading_pattern})\s*[:：]?\s*(.*?)\s*$", text))
    sections: dict[str, str] = {}
    for index, match in enumerate(matches):
        heading = match.group(1)
        inline_content = match.group(2).strip()
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        following_content = text[start:end].strip()
        content = "\n".join(part for part in (inline_content, following_content) if part)
        field = next(key for key, aliases in FIELD_ALIASES.items() if heading in aliases)
        sections[field] = content
    return sections


@tool(
    description=(
        "解析用户粘贴或上传的原始简历文本，按目标岗位、简介、教育经历、"
        "实习经历、项目经历和专业技能等字段提取结构化档案。"
        "仅在收到原始简历文本时调用；已有结构化简历档案无需调用。"
    )
)
def parse_resume(
    resume_text: Annotated[str, Field(min_length=30, max_length=12000)],
) -> dict:
    """将带有常见字段标题的原始简历文本提取为结构化档案。"""
    text = resume_text.strip()
    if not text:
        raise ToolException("简历文本不能为空。")

    sections = _extract_sections(text)
    if not sections:
        raise ToolException("没有识别出简历字段标题，请提供目标岗位、项目经历或专业技能等字段。")

    return {
        "name": "",
        "target_role": sections.get("target_role", ""),
        "summary": sections.get("summary", ""),
        "education": sections.get("education", ""),
        "internship": sections.get("internship", ""),
        "projects": sections.get("projects", ""),
        "skills": sections.get("skills", ""),
        "parse_scope": "基于字段标题的初步提取，内容真实性和字段完整性需要继续校验。",
    }
