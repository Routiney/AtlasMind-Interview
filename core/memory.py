"""Conversation memory extraction for the chat agent.

Memory is deliberately separate from the visible answer. It is a small, durable
summary of user-provided context and preferences, not a transcript or hidden
chain of thought.
"""

from __future__ import annotations

import json
import re
from collections.abc import Mapping
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage


MEMORY_SYSTEM_PROMPT = """你是 AtlasMind 的会话记忆整理器。

请从用户问题和助手回答中提取下一轮对话仍然有用的长期上下文，只保留：
- 用户明确表达的目标岗位、职业方向、学习目标和时间约束
- 用户明确表达的偏好、限制、正在准备的事项和待解决问题
- 对后续求职或面试建议有帮助的稳定事实

不要保存密码、API key、联系方式、身份证件、完整简历原文或任何与任务无关的敏感信息。
不要保存助手的隐藏推理、工具调用过程或无法由对话确认的推断。
如果没有值得长期保留的新信息，保留已有信息并更新 summary。

只返回合法 JSON，不要 Markdown，不要解释：
{"summary":"不超过 1200 字的中文摘要","facts":[{"key":"稳定字段名","value":"事实内容","source":"user|conversation"}]}
"""


def _text(value: Any) -> str:
    if isinstance(value, str):
        return value
    content = getattr(value, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            item if isinstance(item, str) else str(item.get("text", ""))
            for item in content
            if isinstance(item, (str, Mapping))
        )
    return ""


def _parse_json(text: str) -> dict[str, Any] | None:
    candidate = text.strip()
    fenced = re.search(r"```(?:json)?\s*(\{.*\})\s*```", candidate, re.DOTALL | re.IGNORECASE)
    if fenced:
        candidate = fenced.group(1)
    try:
        payload = json.loads(candidate)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    summary = payload.get("summary")
    facts = payload.get("facts")
    if not isinstance(summary, str) or not isinstance(facts, list):
        return None
    normalized_facts = [
        {
            "key": str(item.get("key", "")).strip(),
            "value": str(item.get("value", "")).strip(),
            "source": str(item.get("source", "conversation")).strip(),
        }
        for item in facts
        if isinstance(item, Mapping)
        and str(item.get("key", "")).strip()
        and str(item.get("value", "")).strip()
    ]
    return {"summary": summary.strip()[:4000], "facts": normalized_facts[:30]}


def extract_memory_update(
    *,
    query: str,
    answer: str,
    memory: Mapping[str, Any] | None = None,
    context: str | None = None,
    model: Any,
) -> dict[str, Any] | None:
    """Extract a compact memory update; failures never fail the chat answer."""
    existing = json.dumps(memory or {}, ensure_ascii=False)
    new_context = context or f"用户问题：\n{query.strip()[:4000]}\n\n助手回答：\n{answer.strip()[:6000]}"
    prompt = f"""已有会话记忆：
<memory>
{existing}
</memory>

新增对话上下文：
<conversation_context>
{new_context[:10000]}
</conversation_context>

请合并已有记忆并输出下一轮可用的记忆 JSON。"""
    try:
        response = model.invoke([
            SystemMessage(content=MEMORY_SYSTEM_PROMPT),
            HumanMessage(content=prompt),
        ])
        return _parse_json(_text(response))
    except Exception as exc:  # noqa: BLE001 - memory must never break chat
        print(f"[core] memory update skipped: {exc}")
        return None
