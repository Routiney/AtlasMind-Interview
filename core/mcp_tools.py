"""把 MCP Server 工具适配成 LangChain Tool。"""

from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path
from typing import Annotated, Any

from langchain.tools import tool
from langchain_core.tools import ToolException
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from pydantic import Field

from career_tools import CAREER_TOOLS


MCP_TOOL_NAME = "search_public_web"
MCP_SERVER_PATH = Path(__file__).with_name("search_mcp_server.py")
CAREER_MCP_SERVER_PATH = Path(__file__).with_name("career_mcp_server.py")


def _server_parameters(server_path: Path = MCP_SERVER_PATH) -> StdioServerParameters:
    environment = os.environ.copy()
    environment["PYTHONUNBUFFERED"] = "1"
    return StdioServerParameters(
        command=sys.executable,
        args=[str(server_path)],
        env=environment,
        cwd=str(server_path.parent),
    )


def _result_payload(result: Any) -> dict:
    if getattr(result, "is_error", False) or getattr(result, "isError", False):
        raise ToolException("MCP 工具返回错误，不能据此生成分析结论。")

    structured = getattr(result, "structured_content", None)
    if isinstance(structured, dict) and structured:
        return structured

    for item in getattr(result, "content", []):
        text = getattr(item, "text", None)
        if not isinstance(text, str):
            continue
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict):
            return payload
    raise ToolException("MCP 工具返回了无法解析的结果。")


async def _call_mcp_tool(server_path: Path, tool_name: str, arguments: dict[str, Any]) -> dict:
    async with asyncio.timeout(45):
        async with stdio_client(_server_parameters(server_path)) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                tool_result = await session.call_tool(tool_name, arguments)
                return _result_payload(tool_result)


def call_mcp_tool(server_path: Path, tool_name: str, arguments: dict[str, Any]) -> dict:
    """同步调用任意 MCP 工具。"""
    try:
        return asyncio.run(_call_mcp_tool(server_path, tool_name, arguments))
    except ToolException:
        raise
    except Exception as exc:
        raise ToolException(f"MCP 工具 {tool_name} 不可用，请检查 MCP Server。") from exc


def call_mcp_search(query: str) -> dict:
    """同步 Agent Tool 对 MCP 异步 Client 的适配入口。"""
    return call_mcp_tool(MCP_SERVER_PATH, MCP_TOOL_NAME, {"query": query})


def list_mcp_tool_names() -> list[str]:
    """通过 MCP tools/list 检查 Server 暴露的工具。"""
    async def list_tools() -> list[str]:
        async with stdio_client(_server_parameters()) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                result = await session.list_tools()
                return [item.name for item in result.tools]

    return asyncio.run(list_tools())


def list_career_mcp_tool_names() -> list[str]:
    """通过 MCP tools/list 检查求职分析 Server 暴露的工具。"""
    async def list_tools() -> list[str]:
        async with stdio_client(_server_parameters(CAREER_MCP_SERVER_PATH)) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                result = await session.list_tools()
                return [item.name for item in result.tools]

    return asyncio.run(list_tools())


@tool(
    description=(
        "通过 MCP Server 搜索公开网页中的最新岗位要求、技术文档或行业信息。"
        "只接收简短公开关键词，不得传入姓名、联系方式或整份私人简历。"
    )
)
def mcp_search_tool(query: Annotated[str, Field(min_length=1, max_length=500)]) -> dict:
    """调用标准 MCP search_public_web 工具。"""
    return call_mcp_search(query.strip())


mcp_search_tool.handle_tool_error = True
mcp_search_tool.handle_validation_error = "搜索参数无效：query 必须是 1 到 500 字符的字符串。"


@tool(description="通过 MCP Server 分析结构化简历中的技能自述、项目证据和实习证据。")
def mcp_analyze_resume_evidence(
    target_role: Annotated[str, Field(max_length=500)],
    summary: Annotated[str, Field(max_length=6000)],
    education: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    projects: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
) -> dict:
    return call_mcp_tool(CAREER_MCP_SERVER_PATH, "analyze_resume_evidence", locals())


@tool(description="通过 MCP Server 匹配结构化简历和岗位描述，返回技能缺口与解释性分数。")
def mcp_match_resume_to_job(
    target_role: Annotated[str, Field(max_length=500)],
    summary: Annotated[str, Field(max_length=6000)],
    education: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    projects: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
    job_description: Annotated[str, Field(min_length=10, max_length=8000)],
) -> dict:
    return call_mcp_tool(CAREER_MCP_SERVER_PATH, "match_resume_to_job", locals())


@tool(description="通过 MCP Server 基于简历证据推荐岗位方向和技能缺口。")
def mcp_recommend_job_directions(
    target_role: Annotated[str, Field(max_length=500)],
    summary: Annotated[str, Field(max_length=6000)],
    education: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    projects: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
) -> dict:
    return call_mcp_tool(CAREER_MCP_SERVER_PATH, "recommend_job_directions", locals())


@tool(description="通过 MCP Server 根据岗位目标和技能缺口生成学习计划。")
def mcp_generate_learning_plan(
    target_role: Annotated[str, Field(max_length=500)],
    current_skills: Annotated[str, Field(max_length=2000)],
    skill_gaps: Annotated[str, Field(max_length=2000)],
    weeks: Annotated[int, Field(ge=1, le=24)] = 4,
    hours_per_week: Annotated[int, Field(ge=1, le=40)] = 8,
) -> dict:
    return call_mcp_tool(CAREER_MCP_SERVER_PATH, "generate_learning_plan", locals())


@tool(description="通过 MCP Server 根据项目、实习和技能生成结构化面试题。")
def mcp_generate_interview_questions(
    target_role: Annotated[str, Field(max_length=500)],
    projects: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
    seniority: Annotated[str, Field(max_length=500)] = "",
) -> dict:
    return call_mcp_tool(CAREER_MCP_SERVER_PATH, "generate_interview_questions", locals())


CAREER_MCP_TOOLS = [
    mcp_analyze_resume_evidence,
    mcp_match_resume_to_job,
    mcp_recommend_job_directions,
    mcp_generate_learning_plan,
    mcp_generate_interview_questions,
]

for career_tool in CAREER_MCP_TOOLS:
    local_tool = next(item for item in CAREER_TOOLS if career_tool.name == f"mcp_{item.name}")
    career_tool.description = local_tool.description
    career_tool.handle_tool_error = True
    career_tool.handle_validation_error = "工具参数不符合 Schema，请检查字段长度、必填字段和数值范围。"
