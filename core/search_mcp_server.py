"""AtlasMind 的公开搜索 MCP Server，使用标准 stdio transport。"""

from typing import Annotated

from mcp.server.mcpserver import MCPServer
from pydantic import Field

from search_tool import perform_public_search


mcp = MCPServer(
    "atlasmind-search",
    version="0.1.0",
    description="AtlasMind 的公开网页搜索能力，只处理公开关键词。",
)


@mcp.tool(
    name="search_public_web",
    description=(
        "搜索公开网页中的最新岗位要求、技术文档或行业信息。"
        "只接收简短公开关键词，不得传入姓名、联系方式或整份私人简历。"
    ),
)
async def search_public_web(
    query: Annotated[str, Field(min_length=1, max_length=500)],
) -> dict:
    """通过 MCP 暴露公开网页搜索。"""
    return perform_public_search(query)


if __name__ == "__main__":
    mcp.run("stdio")
