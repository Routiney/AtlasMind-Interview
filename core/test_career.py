import unittest

from career_tools import (
    analyze_resume_evidence,
    generate_interview_questions,
    generate_learning_plan,
    match_resume_to_job,
    recommend_job_directions,
)
from mcp_tools import list_career_mcp_tool_names


RESUME = {
    "target_role": "Java 后端工程师",
    "summary": "熟悉后端开发",
    "education": "计算机科学",
    "internship": "参与 Java 接口开发",
    "projects": "订单系统：使用 Java、Spring Boot、PostgreSQL 完成接口开发",
    "skills": "Java、Spring Boot、PostgreSQL、Docker",
}


class CareerToolTests(unittest.TestCase):
    def test_resume_evidence_distinguishes_experience(self):
        result = analyze_resume_evidence.invoke(RESUME)
        java = next(item for item in result["skill_evidence"] if item["skill"] == "Java")
        self.assertEqual(java["evidence_strength"], "experience_supported")
        self.assertFalse(result["quality_signals"]["has_quantified_result"])

    def test_match_returns_gaps_without_substring_false_positive(self):
        result = match_resume_to_job.invoke({
            **RESUME,
            "job_description": "负责 Java 后端开发，要求 Spring Boot、MySQL、Redis 和 Docker。",
        })
        self.assertEqual(result["detected_job_skills"], ["Java", "Spring Boot", "MySQL", "Redis", "Docker"])
        self.assertEqual(result["skill_gaps"], ["MySQL", "Redis"])
        self.assertEqual(result["match_score"], 60)

    def test_recommendation_plan_and_questions_are_structured(self):
        recommendations = recommend_job_directions.invoke(RESUME)["recommendations"]
        self.assertEqual(recommendations[0]["role"], "Java 后端工程师")

        plan = generate_learning_plan.invoke({
            "target_role": "Java 后端工程师",
            "current_skills": "Java、Spring Boot",
            "skill_gaps": "Redis\nDocker",
            "weeks": 2,
            "hours_per_week": 6,
        })
        self.assertEqual([item["focus"] for item in plan["plan"]], ["Redis", "Docker"])

        questions = generate_interview_questions.invoke({
            "target_role": RESUME["target_role"],
            "projects": RESUME["projects"],
            "internship": RESUME["internship"],
            "skills": RESUME["skills"],
        })
        self.assertTrue(any(item["category"] == "项目深挖" for item in questions["questions"]))
        self.assertTrue(any("Spring Boot" in item["question"] for item in questions["questions"]))

    def test_career_mcp_server_exposes_all_tools(self):
        self.assertEqual(
            set(list_career_mcp_tool_names()),
            {
                "analyze_resume_evidence",
                "match_resume_to_job",
                "recommend_job_directions",
                "generate_learning_plan",
                "generate_interview_questions",
            },
        )


if __name__ == "__main__":
    unittest.main()
