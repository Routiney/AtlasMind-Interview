"""AtlasMind 求职场景的结构化分析工具。"""

from __future__ import annotations

import re
from typing import Annotated, Any

from langchain.tools import tool
from langchain_core.tools import ToolException
from pydantic import Field

from job_tool import SKILL_ALIASES, extract_job_signals, skill_alias_present


RESUME_FIELD = Annotated[str, Field(max_length=6000)]
SHORT_FIELD = Annotated[str, Field(max_length=500)]


def _text(value: str | None) -> str:
    return (value or "").strip()


def _all_resume_text(
    *,
    summary: str,
    education: str,
    internship: str,
    projects: str,
    skills: str,
) -> dict[str, str]:
    return {
        "summary": _text(summary),
        "education": _text(education),
        "internship": _text(internship),
        "projects": _text(projects),
        "skills": _text(skills),
    }


def _find_skills(text: str) -> list[str]:
    return [
        skill
        for skill, aliases in SKILL_ALIASES.items()
        if any(skill_alias_present(text, alias) for alias in aliases)
    ]


def _unique(items: list[str]) -> list[str]:
    return list(dict.fromkeys(item for item in items if item))


def _resume_evidence(
    *,
    target_role: str,
    summary: str,
    education: str,
    internship: str,
    projects: str,
    skills: str,
) -> dict[str, Any]:
    fields = _all_resume_text(
        summary=summary,
        education=education,
        internship=internship,
        projects=projects,
        skills=skills,
    )
    detected_skills = _find_skills(" ".join(fields.values()))
    evidence = []
    for skill in detected_skills:
        aliases = SKILL_ALIASES[skill]
        declared = any(skill_alias_present(fields["skills"], alias) for alias in aliases)
        project_evidence = any(skill_alias_present(fields["projects"], alias) for alias in aliases)
        internship_evidence = any(skill_alias_present(fields["internship"], alias) for alias in aliases)
        evidence_fields = [
            field for field, present in (
                ("skills", declared),
                ("projects", project_evidence),
                ("internship", internship_evidence),
                ("summary", any(skill_alias_present(fields["summary"], alias) for alias in aliases)),
                ("education", any(skill_alias_present(fields["education"], alias) for alias in aliases)),
            ) if present
        ]
        evidence.append({
            "skill": skill,
            "evidence_fields": evidence_fields,
            "evidence_strength": (
                "experience_supported" if project_evidence or internship_evidence
                else "self_declared_only"
            ),
        })

    missing_fields = [
        label for key, label in (
            ("summary", "个人简介"),
            ("education", "教育经历"),
            ("internship", "实习经历"),
            ("projects", "项目经历"),
            ("skills", "专业技能"),
        ) if not fields[key]
    ]
    quality_signals = {
        "has_target_role": bool(_text(target_role)),
        "has_quantified_result": bool(re.search(r"\d+(?:%|万|秒|次|个|人)", fields["projects"] + " " + fields["internship"])),
        "has_project_or_internship": bool(fields["projects"] or fields["internship"]),
        "has_experience_supported_skill": any(
            item["evidence_strength"] == "experience_supported" for item in evidence
        ),
    }
    return {
        "target_role": _text(target_role),
        "detected_skills": detected_skills,
        "skill_evidence": evidence,
        "missing_fields": missing_fields,
        "quality_signals": quality_signals,
        "analysis_scope": "仅检测字段中的技能提及和数字线索；experience_supported 表示经历中提及，不证明熟练度、职责或真实性。数字线索也不一定代表成果。",
    }


@tool(
    description=(
        "分析结构化简历中的能力证据，区分技能栏自述与项目、实习经历中的实际证据，"
        "并指出缺失字段和简历质量信号。适合用户询问简历优势、短板或证据充分性时调用。"
    )
)
def analyze_resume_evidence(
    target_role: SHORT_FIELD,
    summary: RESUME_FIELD,
    education: RESUME_FIELD,
    internship: RESUME_FIELD,
    projects: RESUME_FIELD,
    skills: RESUME_FIELD,
) -> dict:
    """分析结构化简历的技能证据和字段完整性。"""
    return _resume_evidence(
        target_role=target_role,
        summary=summary,
        education=education,
        internship=internship,
        projects=projects,
        skills=skills,
    )


def _match_resume_data(
    *,
    target_role: str,
    summary: str,
    education: str,
    internship: str,
    projects: str,
    skills: str,
    job_description: str,
) -> dict[str, Any]:
    description = _text(job_description)
    if len(description) < 10:
        raise ToolException("岗位描述至少需要 10 个字符。")
    job = extract_job_signals(description)
    fields = _all_resume_text(
        summary=summary,
        education=education,
        internship=internship,
        projects=projects,
        skills=skills,
    )
    resume_text = " ".join(fields.values())
    matched = [
        skill for skill in job["detected_skills"]
        if any(skill_alias_present(resume_text, alias) for alias in SKILL_ALIASES[skill])
    ]
    evidenced = [
        skill for skill in matched
        if any(
            skill_alias_present(fields["projects"] + " " + fields["internship"], alias)
            for alias in SKILL_ALIASES[skill]
        )
    ]
    self_declared_only = [skill for skill in matched if skill not in evidenced]
    missing = [skill for skill in job["detected_skills"] if skill not in matched]
    required_count = len(job["detected_skills"])
    score = round(len(matched) / required_count * 100) if required_count else None
    return {
        "target_role": _text(target_role),
        "detected_job_skills": job["detected_skills"],
        "matched_skills": matched,
        "experience_supported_skills": evidenced,
        "self_declared_only_skills": self_declared_only,
        "skill_gaps": missing,
        "match_score": score,
        "match_level": (
            "无法判断" if score is None else
            "高匹配" if score >= 75 else
            "中等匹配" if score >= 45 else "低匹配"
        ),
        "seniority_signals": job["seniority_signals"],
        "responsibility_signals": job["responsibility_signals"],
        "analysis_scope": "匹配分数只基于识别到的技能关键词，必须结合职责、级别和经历深度进行人工或模型复核。",
    }


@tool(
    description=(
        "把结构化简历与完整岗位描述进行初步匹配，返回匹配技能、经验支持程度、"
        "技能缺口、岗位级别线索和解释性分数。适合生成岗位匹配分析时调用。"
    )
)
def match_resume_to_job(
    target_role: SHORT_FIELD,
    summary: RESUME_FIELD,
    education: RESUME_FIELD,
    internship: RESUME_FIELD,
    projects: RESUME_FIELD,
    skills: RESUME_FIELD,
    job_description: Annotated[str, Field(min_length=10, max_length=8000)],
) -> dict:
    """计算简历与岗位描述的技能和证据匹配结果。"""
    return _match_resume_data(
        target_role=target_role,
        summary=summary,
        education=education,
        internship=internship,
        projects=projects,
        skills=skills,
        job_description=job_description,
    )


ROLE_PROFILES = {
    "Java 后端工程师": ("Java", "Spring Boot", "SQL", "Redis", "Docker"),
    "Python 后端工程师": ("Python", "FastAPI", "SQL", "Docker", "Git"),
    "数据工程师": ("Python", "SQL", "Kafka", "Spark", "Docker"),
    "云原生/DevOps 工程师": ("Linux", "Docker", "Kubernetes", "CI/CD", "Git"),
    "测试开发工程师": ("Java", "Python", "Selenium", "SQL", "Git"),
}


@tool(
    description=(
        "基于结构化简历中的目标岗位、项目、实习和技能证据推荐多个岗位方向，"
        "返回每个方向的匹配技能、证据字段、缺口和推荐理由。没有实时岗位数据时也可以调用。"
    )
)
def recommend_job_directions(
    target_role: SHORT_FIELD,
    summary: RESUME_FIELD,
    education: RESUME_FIELD,
    internship: RESUME_FIELD,
    projects: RESUME_FIELD,
    skills: RESUME_FIELD,
) -> dict:
    """基于简历证据推荐初步岗位方向。"""
    fields = _all_resume_text(
        summary=summary,
        education=education,
        internship=internship,
        projects=projects,
        skills=skills,
    )
    resume_text = " ".join(fields.values())
    recommendations = []
    for role, required in ROLE_PROFILES.items():
        matched = [
            skill for skill in required
            if any(skill_alias_present(resume_text, alias) for alias in SKILL_ALIASES[skill])
        ]
        supported = [
            skill for skill in matched
            if any(
                skill_alias_present(fields["projects"] + " " + fields["internship"], alias)
                for alias in SKILL_ALIASES[skill]
            )
        ]
        if not matched:
            continue
        gaps = [skill for skill in required if skill not in matched]
        score = round(len(matched) / len(required) * 100)
        recommendations.append({
            "role": role,
            "match_score": score,
            "matched_skills": matched,
            "experience_supported_skills": supported,
            "skill_gaps": gaps,
            "recommendation_reason": (
                "项目或实习中提及相关技术，仍需核验职责和使用深度。" if supported else
                "有部分技能匹配，但目前主要是技能栏自述或证据不足。"
            ),
        })
    recommendations.sort(key=lambda item: item["match_score"], reverse=True)
    return {
        "declared_target_role": _text(target_role),
        "recommendations": recommendations,
        "status": "preliminary" if recommendations else "insufficient_evidence",
        "supported_directions": list(ROLE_PROFILES),
        "analysis_scope": "基于固定岗位能力画像和简历文本关键词的初步推荐，不代表具体公司的录用判断。",
    }


@tool(
    description=(
        "根据目标岗位、当前技能和技能缺口生成阶段化学习计划，包含每周重点、练习任务和产出物。"
        "适合用户需要学习路线或面试准备计划时调用。"
    )
)
def generate_learning_plan(
    target_role: SHORT_FIELD,
    current_skills: Annotated[str, Field(max_length=2000)],
    skill_gaps: Annotated[str, Field(max_length=2000)],
    weeks: Annotated[int, Field(ge=1, le=24)] = 4,
    hours_per_week: Annotated[int, Field(ge=1, le=40)] = 8,
) -> dict:
    """生成可执行的学习和面试准备计划。"""
    role = _text(target_role) or "目标岗位"
    topics = [item.strip() for item in re.split(r"[,，、;；\n]+", skill_gaps) if item.strip()]
    gaps = _unique([
        next((skill for skill, aliases in SKILL_ALIASES.items() if item.lower() in aliases), item)
        for item in topics
    ]) or ["核心技能巩固", "项目成果表达", "面试模拟"]
    scheduled = gaps[:weeks]
    minutes = hours_per_week * 60
    study_minutes = minutes * 3 // 10
    practice_minutes = minutes * 5 // 10
    review_minutes = minutes - study_minutes - practice_minutes
    plan = []
    for index in range(1, weeks + 1):
        focus = scheduled[(index - 1) % len(scheduled)]
        round_number = (index - 1) // len(scheduled)
        stage = ("基础梳理", "实践验证", "复盘深化")[min(round_number, 2)]
        plan.append({
            "week": index,
            "focus": focus,
            "stage": stage,
            "time_budget_minutes": {
                "study": study_minutes,
                "practice": practice_minutes,
                "review": review_minutes,
            },
            "tasks": [
                f"用 {study_minutes} 分钟围绕{focus}完成{stage}，记录概念、使用场景和未解决问题",
                f"用 {practice_minutes} 分钟实现或改进一个{focus}练习，保存可复现步骤和验证结果",
                f"用 {review_minutes} 分钟复述技术取舍，核对错误并记录下一轮改进项",
            ],
            "deliverable": f"一份{focus}学习笔记和一个可展示的练习产出",
            "acceptance_criteria": ["能解释方案和适用边界", "能复现练习结果", "能说明一个失败案例和改进方法"],
        })
    return {
        "target_role": role,
        "current_skills": _text(current_skills),
        "skill_gaps": gaps,
        "weeks": weeks,
        "hours_per_week": hours_per_week,
        "total_hours": weeks * hours_per_week,
        "deferred_topics": gaps[weeks:],
        "plan": plan,
        "analysis_scope": "计划是基于输入缺口生成的初稿，需要结合用户可用时间和实际岗位要求调整。",
    }


@tool(
    description=(
        "根据目标岗位、项目经历、实习经历和专业技能生成分组面试题，"
        "覆盖项目深挖、技术基础、场景题和行为题，并优先围绕用户真实经历提问。"
    )
)
def generate_interview_questions(
    target_role: SHORT_FIELD,
    projects: RESUME_FIELD,
    internship: RESUME_FIELD,
    skills: RESUME_FIELD,
    seniority: SHORT_FIELD = "",
) -> dict:
    """生成结构化面试问题清单。"""
    role = _text(target_role) or "目标岗位"
    detected_skills = _find_skills(_text(skills) + " " + _text(projects) + " " + _text(internship))[:5]
    project_anchor = next((line.strip() for line in _text(projects).splitlines() if line.strip()), "你愿意分享的项目")[:200]
    questions = [
        {"category": "项目深挖", "question": f"请介绍{project_anchor}的背景、你的具体职责和最终结果。"},
        {"category": "项目深挖", "question": "项目中最难解决的技术问题是什么？你如何定位和验证解决方案？"},
        {"category": "项目深挖", "question": "如果重新做一次这个项目，你会优先改进哪一部分？为什么？"},
        {"category": "岗位场景", "question": f"针对{role}，当系统出现性能或稳定性问题时，你会如何排查？"},
        {"category": "行为面试", "question": "请举例说明一次你推动协作、解决分歧或承担额外责任的经历。"},
    ]
    questions.extend(
        {"category": "技术基础", "question": f"请结合你的经历解释 {skill} 的核心概念、使用场景和常见问题。"}
        for skill in detected_skills
    )
    if _text(internship):
        questions.append({"category": "实习深挖", "question": "在这段实习中，哪些工作由你独立完成，哪些由团队完成？请说明交付和反馈。"})
    return {
        "target_role": role,
        "seniority": _text(seniority),
        "questions": questions[:12],
        "follow_up_dimensions": ["职责边界", "技术取舍", "故障排查", "结果指标", "复盘改进"],
        "analysis_scope": "基于规则模板的提问初稿；seniority 仅记录用户目标，不代表已经校准题目难度。无项目时的通用问题不是对用户经历的认定。",
    }


CAREER_TOOLS = [
    analyze_resume_evidence,
    match_resume_to_job,
    recommend_job_directions,
    generate_learning_plan,
    generate_interview_questions,
]

for career_tool in CAREER_TOOLS:
    career_tool.handle_tool_error = True
    career_tool.handle_validation_error = "工具参数不符合 Schema，请检查字段长度、必填字段和数值范围。"
