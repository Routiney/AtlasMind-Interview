import unittest
import threading
import time
from unittest.mock import patch

from planning_workflow import (
    CapabilityAssessment,
    LearningPlan,
    PlanningReport,
    ResearchPlan,
    ResearchTask,
    TaskSummary,
    public_planning_result,
    run_planning_workflow,
)


PROFILE = {
    "target_role": "Java 后端工程师",
    "summary": "熟悉后端开发",
    "education": "计算机科学",
    "internship": "参与 Java 接口开发",
    "projects": "订单系统：使用 Java、Spring Boot、PostgreSQL 完成接口开发",
    "skills": "Java、Spring Boot、PostgreSQL、Docker",
}


class FakeStructuredRunnable:
    def __init__(self, schema):
        self.schema = schema

    def invoke(self, messages):
        if self.schema is ResearchPlan:
            return {
                "objective": "评估 Java 后端方向并制定准备路线",
                "tasks": [
                    {"task_id": "resume-evidence", "title": "简历证据盘点", "intent": "区分技能自述和经历证据", "task_type": "resume_evidence"},
                    {"task_id": "role-fit", "title": "岗位匹配", "intent": "识别匹配点和缺口", "task_type": "role_fit"},
                    {"task_id": "learning-priority", "title": "学习优先级", "intent": "排序学习缺口", "task_type": "learning_priority"},
                ],
            }
        if self.schema is TaskSummary:
            return {
                "task_id": "task",
                "title": "任务摘要",
                "findings": ["有 Java 后端相关证据。"],
                "evidence": ["项目经历"],
                "gaps": ["缺少量化结果"],
                "implications": ["需要补充项目指标"],
                "confidence": "medium",
            }
        if self.schema is PlanningReport:
            return {
                "assessment": {
                    "overall_assessment": "具备 Java 后端初步证据，项目成果量化不足。",
                    "strengths": [{
                        "area": "后端开发",
                        "conclusion": "有 Java 和 Spring Boot 项目证据。",
                        "evidence": ["项目经历"],
                        "confidence": "high",
                    }],
                    "gaps": [{
                        "area": "成果表达",
                        "conclusion": "缺少量化结果。",
                        "evidence": ["当前项目字段"],
                        "confidence": "medium",
                    }],
                    "role_recommendations": [{
                        "role": "Java 后端工程师",
                        "reason": "技能和项目方向一致。",
                        "strengths": ["Java"],
                        "gaps": ["Redis"],
                    }],
                    "evidence_limits": ["无法从当前资料确认独立负责范围。"],
                    "next_actions": ["补充项目结果指标。"],
                },
                "learning_plan": {
                    "target_role": "Java 后端工程师",
                    "duration_weeks": 2,
                    "hours_per_week": 6,
                    "rationale": "优先补齐岗位缺口并强化项目表达。",
                    "phases": [{
                        "phase": "项目证据强化",
                        "objective": "补充项目结果和技术取舍",
                        "tasks": ["整理项目指标"],
                        "deliverables": ["项目复盘稿"],
                        "estimated_hours": 6,
                    }],
                    "interview_focus": ["项目深挖"],
                    "adjustment_rules": ["每周复盘一次"],
                },
                "interview_focus": ["项目深挖"],
                "evidence_limits": ["当前资料无法确认工作边界。"],
            }


class FakeStructuredModel:
    def with_structured_output(self, schema, method):
        self.last_schema = schema
        self.last_method = method
        return FakeStructuredRunnable(schema)


class PlanningWorkflowTests(unittest.TestCase):
    def test_langgraph_runs_specialists_in_parallel(self):
        active = 0
        peak = 0
        lock = threading.Lock()
        original_execute_task = __import__("planning_workflow")._execute_task

        def delayed_execute_task(task, *, context, research_market):
            nonlocal active, peak
            with lock:
                active += 1
                peak = max(peak, active)
            time.sleep(0.03)
            try:
                return original_execute_task(task, context=context, research_market=research_market)
            finally:
                with lock:
                    active -= 1

        with patch("planning_workflow._execute_task", side_effect=delayed_execute_task):
            run_planning_workflow(resume_profile=PROFILE, model=FakeStructuredModel())

        self.assertGreaterEqual(peak, 2)

    def test_workflow_runs_planner_summarizers_and_report_writer(self):
        model = FakeStructuredModel()
        result = run_planning_workflow(
            resume_profile=PROFILE,
            job_description="负责 Java 后端开发，要求 Spring Boot 和 Redis。",
            weeks=2,
            hours_per_week=6,
            model=model,
        )

        self.assertIsInstance(result.assessment, CapabilityAssessment)
        self.assertIsInstance(result.learning_plan, LearningPlan)
        self.assertIn("resume_evidence", result.evidence_context)
        self.assertIn("job_match", result.evidence_context)
        self.assertEqual(len(result.research_plan.tasks), 3)
        self.assertEqual(len(result.task_summaries), 3)
        self.assertEqual(
            [summary.task_id for summary in result.task_summaries],
            [task.task_id for task in result.research_plan.tasks],
        )
        self.assertEqual(
            [summary.agent_name for summary in result.task_summaries],
            ["Resume Evidence Auditor", "Role Fit Analyst", "Learning Prioritization Agent"],
        )
        self.assertEqual(model.last_method, "function_calling")

        public_result = public_planning_result(result)
        self.assertNotIn("evidence_context", public_result)
        self.assertNotIn("task_type", public_result["research_plan"]["tasks"][0])
        self.assertNotIn("query", public_result["research_plan"]["tasks"][0])
        self.assertNotIn("experience_supported", str(public_result))

    def test_public_result_removes_nested_internal_fields(self):
        result = run_planning_workflow(
            resume_profile=PROFILE,
            model=FakeStructuredModel(),
        )
        result.assessment.next_actions.append("字段 resume_evidence 不应展示")
        payload = public_planning_result(result)
        serialized = str(payload)
        self.assertNotIn("evidence_context", serialized)
        self.assertNotIn("resume_evidence", serialized)
        self.assertNotIn("self_declared_only", serialized)
        self.assertIn("简历证据", serialized)

    def test_workflow_rejects_invalid_schedule(self):
        with self.assertRaises(ValueError):
            run_planning_workflow(resume_profile=PROFILE, weeks=0, model=FakeStructuredModel())


if __name__ == "__main__":
    unittest.main()
