"""第 5 课单元 2：岗位描述分析工具。"""

import re
from typing import Annotated

from langchain.tools import tool
from langchain_core.tools import ToolException
from pydantic import Field


SKILL_ALIASES = {
    "Java": ("java",),
    "Python": ("python",),
    "Spring Boot": ("spring boot",),
    "Spring Cloud": ("spring cloud",),
    "PostgreSQL": ("postgresql", "postgres"),
    "MySQL": ("mysql",),
    "Redis": ("redis",),
    "Docker": ("docker",),
    "Kubernetes": ("kubernetes", "k8s"),
    "Kafka": ("kafka",),
    "FastAPI": ("fastapi",),
    "Django": ("django",),
    "SQL": ("sql",),
    "Linux": ("linux",),
    "Git": ("git",),
    "CI/CD": ("ci/cd", "continuous integration", "持续集成"),
    "Spark": ("spark",),
    "Selenium": ("selenium",),
    "微服务": ("微服务", "microservice"),
}
SENIORITY_SIGNALS = ("实习", "初级", "中级", "高级", "senior", "junior", "校招")
RESPONSIBILITY_SIGNALS = ("负责", "参与", "设计", "开发", "维护", "优化", "排查", "建设")


def skill_alias_present(text: str, alias: str) -> bool:
    """匹配技能别名，避免把 SQL 误识别为 PostgreSQL 的子串。"""
    lowered = text.lower()
    normalized_alias = alias.lower()
    if re.fullmatch(r"[a-z0-9][a-z0-9+#./ -]*", normalized_alias):
        pattern = rf"(?<![a-z0-9]){re.escape(normalized_alias)}(?![a-z0-9])"
        return bool(re.search(pattern, lowered))
    return normalized_alias in lowered


def extract_job_signals(description: str) -> dict:
    """提取岗位描述信号，供 LangChain Tool 和其他业务工具复用。"""
    text = description.strip()
    if not text:
        raise ToolException("岗位描述不能为空。")

    lowered = text.lower()
    detected_skills = [
        skill for skill, aliases in SKILL_ALIASES.items()
        if any(skill_alias_present(text, alias) for alias in aliases)
    ]
    seniority = [signal for signal in SENIORITY_SIGNALS if signal.lower() in lowered]
    responsibilities = [
        sentence.strip()
        for sentence in text.replace("。", "。\n").splitlines()
        if any(signal in sentence for signal in RESPONSIBILITY_SIGNALS)
    ][:10]

    return {
        "detected_skills": detected_skills,
        "seniority_signals": list(dict.fromkeys(seniority)),
        "responsibility_signals": responsibilities,
        "analysis_scope": "基于关键词和句子线索的初步提取，最终判断需要结合完整岗位描述和用户经历。",
    }


@tool(
    description=(
        "分析用户提供的完整岗位描述，提取技能、职责和级别线索，"
        "用于与简历经历进行匹配。该工具只做本地结构化提取，不代表已经验证岗位的实时信息。"
    )
)
def analyze_job_description(
    description: Annotated[str, Field(min_length=10, max_length=8000)],
) -> dict:
    """提取岗位描述中的技能、职责和级别线索，供 Agent 进行岗位匹配。"""
    return extract_job_signals(description)
