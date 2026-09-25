"""AtlasMind 的 LangChain Agent 组装和流式事件适配。"""

from __future__ import annotations

from collections.abc import Iterator, Mapping, Sequence
import os
from typing import Any

from langchain.agents import create_agent
from langchain_core.messages import AIMessage, AIMessageChunk

from career_tools import CAREER_TOOLS
from job_tool import analyze_job_description
from mcp_tools import CAREER_MCP_TOOLS, mcp_search_tool
from model import chunk_text, create_chat_model
from prompts import build_chat_messages
from resume_tool import parse_resume
from search_tool import search_tool


TOOLS = [parse_resume, analyze_job_description]
TOOL_LABELS = {
    "parse_resume": "解析简历",
    "analyze_job_description": "分析岗位描述",
    "search_tool": "搜索公开信息",
    "mcp_search_tool": "搜索公开信息",
    "mcp_analyze_resume_evidence": "分析简历证据",
    "mcp_match_resume_to_job": "匹配简历和岗位",
    "mcp_recommend_job_directions": "推荐岗位方向",
    "mcp_generate_learning_plan": "生成学习计划",
    "mcp_generate_interview_questions": "生成面试问题",
}
TOOL_LABELS.update({name.removeprefix("mcp_"): label for name, label in list(TOOL_LABELS.items()) if name.startswith("mcp_")})


def _search_tool():
    """默认使用 MCP 搜索；设置 local 可回退到进程内 LangChain Tool。"""
    mode = os.getenv("ATLASMIND_SEARCH_TOOL_MODE", "mcp").strip().lower()
    if mode == "local":
        return search_tool
    return mcp_search_tool


def _career_tools():
    """默认使用 MCP 求职分析工具；设置 local 可回退到进程内 Tool。"""
    mode = os.getenv("ATLASMIND_CAREER_TOOL_MODE", "mcp").strip().lower()
    return CAREER_TOOLS if mode == "local" else CAREER_MCP_TOOLS


def create_interview_agent(model: Any = None) -> Any:
    """创建求职 Agent；模型未传入时使用项目统一的 ChatModel 配置。"""
    return create_agent(
        model=model or create_chat_model(),
        tools=[*TOOLS, *_career_tools(), _search_tool()],
        # 系统消息已经由 build_chat_messages 组装，避免重复注入。
        name="atlasmind_interview_agent",
    )


def _tool_names(message: AIMessage | AIMessageChunk) -> list[str]:
    names: list[str] = []
    for call in [*getattr(message, "tool_calls", []), *getattr(message, "tool_call_chunks", [])]:
        name = call.get("name") if isinstance(call, Mapping) else None
        if name and name not in names:
            names.append(name)
    return names


def stream_agent_events(
    *,
    query: str,
    resume_profile: Any = None,
    target_job: Any = None,
    history: Sequence[Mapping[str, Any]] = (),
    memory: Mapping[str, Any] | None = None,
    deep_thinking: bool = False,
    model: Any = None,
) -> Iterator[tuple[str, dict[str, Any]]]:
    """执行 Agent；仅在开启深度思考时输出可展示的过程状态。"""
    messages = build_chat_messages(
        query=query,
        resume_profile=resume_profile,
        target_job=target_job,
        history=history,
        memory=memory,
    )
    agent = create_interview_agent(model)
    emitted_tools: set[str] = set()

    for message, _metadata in agent.stream(
        {"messages": messages},
        config={"recursion_limit": 12},
        stream_mode="messages",
    ):
        if isinstance(message, (AIMessage, AIMessageChunk)):
            names = _tool_names(message)
            new_names = [name for name in names if name not in emitted_tools]
            for name in new_names:
                emitted_tools.add(name)
                if deep_thinking:
                    label = TOOL_LABELS.get(name, name)
                    yield "thinking", {"text": f"正在{label}"}

            text = chunk_text(message)
            if text:
                yield "chunk", {"content": text}
            continue

        if getattr(message, "type", "") == "tool":
            if deep_thinking:
                name = getattr(message, "name", "") or "工具"
                label = TOOL_LABELS.get(name, name)
                yield "thinking", {"text": f"{label}已返回结果，正在继续分析"}
