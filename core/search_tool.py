"""第 5 课单元 2：可由模型选择调用的网页搜索工具。"""

import os
from typing import Annotated

import httpx
from langchain.tools import tool
from langchain_core.tools import ToolException
from pydantic import Field


def perform_public_search(query: str) -> dict:
    """执行公开网页搜索；LangChain Tool 和 MCP Server 共用这段业务逻辑。"""
    query = query.strip()
    if not query:
        raise ToolException("搜索关键词不能为空。")
    api_key = os.getenv("TAVILY_API_KEY")
    if not api_key:
        raise ToolException("搜索不可用：请配置 TAVILY_API_KEY。不能据此声称已经搜索。")
    try:
        response = httpx.post(
            "https://api.tavily.com/search",
            headers={"Authorization": f"Bearer {api_key}"},
            json={"query": query, "search_depth": "basic", "max_results": 3},
            timeout=20,
        )
        response.raise_for_status()
        payload = response.json()
        results = payload["results"]
        if not isinstance(results, list):
            raise ValueError("Invalid search results")
        return {"query": query, "results": [
            {"title": item["title"], "url": item["url"], "content": item["content"][:2000]}
            for item in results[:3]
        ]}
    except httpx.HTTPStatusError as exc:
        raise ToolException(f"搜索服务返回 HTTP {exc.response.status_code}，请稍后重试。") from exc
    except httpx.RequestError as exc:
        raise ToolException("搜索连接失败或超时，请稍后重试。") from exc
    except (ValueError, KeyError, TypeError) as exc:
        raise ToolException("搜索服务返回的数据格式异常。") from exc


@tool(
    description=(
        "搜索公开网页中的最新岗位要求、技术文档或行业信息。"
        "只接收简短公开关键词，不要传入姓名、联系方式或整份私人简历；"
        "普通简历分析不需要调用此工具。"
    )
)
def search_tool(query: Annotated[str, Field(min_length=1, max_length=500)]) -> dict:
    """搜索公开网页中的岗位要求、技术文档或最新信息，返回标题、URL 和摘要。"""
    return perform_public_search(query)


# 让错误以 ToolMessage 回到模型，而不是中断整个 Agent。
search_tool.handle_tool_error = True
search_tool.handle_validation_error = "搜索参数无效：query 必须是 1 到 500 字符的字符串。"
