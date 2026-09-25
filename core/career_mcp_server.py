"""AtlasMind 求职分析 MCP Server。"""

from typing import Annotated

from mcp.server.mcpserver import MCPServer
from pydantic import Field

from career_tools import (
    analyze_resume_evidence,
    generate_interview_questions,
    generate_learning_plan,
    match_resume_to_job,
    recommend_job_directions,
)


mcp = MCPServer(
    "atlasmind-career",
    version="0.1.0",
    description="AtlasMind 的结构化简历、岗位匹配和面试准备能力。",
)


@mcp.tool(
    name="analyze_resume_evidence",
    description=analyze_resume_evidence.description,
)
async def analyze_resume_evidence_mcp(
    target_role: Annotated[str, Field(max_length=500)],
    summary: Annotated[str, Field(max_length=6000)],
    education: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    projects: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
) -> dict:
    return analyze_resume_evidence.invoke(locals())


@mcp.tool(
    name="match_resume_to_job",
    description=match_resume_to_job.description,
)
async def match_resume_to_job_mcp(
    target_role: Annotated[str, Field(max_length=500)],
    summary: Annotated[str, Field(max_length=6000)],
    education: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    projects: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
    job_description: Annotated[str, Field(min_length=10, max_length=8000)],
) -> dict:
    return match_resume_to_job.invoke(locals())


@mcp.tool(
    name="recommend_job_directions",
    description=recommend_job_directions.description,
)
async def recommend_job_directions_mcp(
    target_role: Annotated[str, Field(max_length=500)],
    summary: Annotated[str, Field(max_length=6000)],
    education: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    projects: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
) -> dict:
    return recommend_job_directions.invoke(locals())


@mcp.tool(
    name="generate_learning_plan",
    description=generate_learning_plan.description,
)
async def generate_learning_plan_mcp(
    target_role: Annotated[str, Field(max_length=500)],
    current_skills: Annotated[str, Field(max_length=2000)],
    skill_gaps: Annotated[str, Field(max_length=2000)],
    weeks: Annotated[int, Field(ge=1, le=24)] = 4,
    hours_per_week: Annotated[int, Field(ge=1, le=40)] = 8,
) -> dict:
    return generate_learning_plan.invoke(locals())


@mcp.tool(
    name="generate_interview_questions",
    description=generate_interview_questions.description,
)
async def generate_interview_questions_mcp(
    target_role: Annotated[str, Field(max_length=500)],
    projects: Annotated[str, Field(max_length=6000)],
    internship: Annotated[str, Field(max_length=6000)],
    skills: Annotated[str, Field(max_length=6000)],
    seniority: Annotated[str, Field(max_length=500)] = "",
) -> dict:
    return generate_interview_questions.invoke(locals())


if __name__ == "__main__":
    mcp.run("stdio")
