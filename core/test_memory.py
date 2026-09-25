import unittest

from langchain_core.messages import AIMessage

from memory import extract_memory_update
from prompts import build_chat_messages


class FakeMemoryModel:
    def __init__(self, response):
        self.response = response

    def invoke(self, _messages):
        return AIMessage(content=self.response)


class MemoryTests(unittest.TestCase):
    def test_extracts_and_normalizes_memory_json(self):
        model = FakeMemoryModel(
            '```json\n{"summary":"目标是 Java 后端，计划两周准备。",'
            '"facts":[{"key":"target_role","value":"Java 后端工程师","source":"user"},'
            '{"key":"","value":"ignored"}]}\n```'
        )
        result = extract_memory_update(
            query="我准备 Java 后端岗位，两周后面试。",
            answer="建议优先复习 Spring Boot。",
            model=model,
        )
        self.assertEqual(result["summary"], "目标是 Java 后端，计划两周准备。")
        self.assertEqual(result["facts"], [{"key": "target_role", "value": "Java 后端工程师", "source": "user"}])

    def test_invalid_memory_response_is_ignored(self):
        result = extract_memory_update(
            query="继续准备面试",
            answer="先从项目复盘开始。",
            model=FakeMemoryModel("不是 JSON"),
        )
        self.assertIsNone(result)

    def test_memory_is_included_in_chat_prompt(self):
        messages = build_chat_messages(
            query="下一步怎么安排？",
            memory={"summary": "用户准备 Java 后端岗位", "facts": []},
        )
        prompt = messages[-1].content
        self.assertIn("用户准备 Java 后端岗位", prompt)


if __name__ == "__main__":
    unittest.main()
