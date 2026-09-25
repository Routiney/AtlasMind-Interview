"""进入 Agent 前的轻量查询审查。"""

from __future__ import annotations

import re
from dataclasses import dataclass


MAX_QUERY_LENGTH = 4000
_CONTROL_CHARACTERS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_PROMPT_INJECTION_PATTERNS = (
    re.compile(r"忽略(?:之前|以上|所有|先前).{0,12}(?:指令|提示|规则)", re.IGNORECASE),
    re.compile(r"(?:泄露|显示|输出|打印).{0,12}(?:系统提示词|开发者消息|隐藏提示|思维链)", re.IGNORECASE),
    re.compile(r"(?:ignore|disregard).{0,30}(?:system|developer|previous).{0,20}(?:instruction|prompt|rule)", re.IGNORECASE),
)


@dataclass(frozen=True)
class QueryReview:
    allowed: bool
    code: str = ""
    message: str = ""


def review_query(query: str) -> QueryReview:
    """审查用户查询，不调用模型，也不把原文发送到外部服务。"""
    text = query.strip()
    if not text:
        return QueryReview(allowed=True)
    if len(text) > MAX_QUERY_LENGTH:
        return QueryReview(
            allowed=False,
            code="QUERY_TOO_LONG",
            message=f"问题内容过长，请控制在 {MAX_QUERY_LENGTH} 个字符以内。",
        )
    if _CONTROL_CHARACTERS.search(text):
        return QueryReview(
            allowed=False,
            code="QUERY_INVALID_CHARACTERS",
            message="问题包含不可处理的控制字符，请重新输入。",
        )
    if any(pattern.search(text) for pattern in _PROMPT_INJECTION_PATTERNS):
        return QueryReview(
            allowed=False,
            code="QUERY_REVIEW_BLOCKED",
            message="当前问题包含不适合交给求职助手处理的指令，请改写为具体的求职问题。",
        )
    return QueryReview(allowed=True)
