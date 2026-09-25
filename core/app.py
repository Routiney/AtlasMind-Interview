"""AtlasMind LangChain ChatModel 服务。"""

from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from agent import stream_agent_events
from model import ModelConfigurationError, create_chat_model
from memory import extract_memory_update
from planning_workflow import public_planning_result, run_planning_workflow
from query_guard import review_query


def sse(event: str, data: dict[str, Any]) -> bytes:
    """把一个业务事件编码成标准 SSE 文本。"""
    payload = json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n".encode("utf-8")


class AgentHandler(BaseHTTPRequestHandler):
    def write_event(self, payload: bytes) -> bool:
        """写入一个事件；客户端断开时停止后续生成。"""
        try:
            self.wfile.write(payload)
            self.wfile.flush()
            return True
        except (BrokenPipeError, ConnectionAbortedError, ConnectionResetError):
            print("[core] client disconnected; stop streaming")
            return False

    def end_headers(self) -> None:
        # 教学前端运行在 localhost:5173，允许它访问本地 Core 服务。
        self.send_header("Access-Control-Allow-Origin", "http://localhost:5173")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        super().end_headers()

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/agents":
            self.send_error(404, "unknown endpoint")
            return

        payload = json.dumps({"agents": ["PlexusAgent"]}, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self) -> None:  # noqa: N802 - 由 http.server 要求使用此名称
        if self.path == "/agents/PlexusAgent/plan":
            self._do_plan()
            return
        if self.path == "/agents/PlexusAgent/memory":
            self._do_memory()
            return
        if self.path != "/agents/PlexusAgent/execute":
            self.send_error(404, "unknown endpoint")
            return

        length = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(length) or b"{}")
        query = str(request.get("query", "")).strip()
        resume_profile = request.get("resume_profile")
        target_job = request.get("target_job")
        history = request.get("history") or []
        memory = request.get("memory") or {}
        session_id = request.get("session_id")
        conversation_id = request.get("conversation_id")
        deep_thinking = request.get("deep_thinking") is True
        stream = request.get("stream") is True

        if not query and not resume_profile:
            self.send_error(400, "query is required")
            return
        if not stream:
            self.send_error(400, "this demo requires stream=true")
            return

        query_review = review_query(query)
        if not query_review.allowed:
            self._write_review_block(
                query_review.code,
                query_review.message,
                session_id,
                conversation_id,
                deep_thinking,
            )
            return

        try:
            # 先创建模型做配置校验；真正的 Agent 在事件流开始时组装。
            model = create_chat_model()
        except ModelConfigurationError as exc:
            payload = json.dumps({"code": "MODEL_CONFIGURATION_ERROR", "message": str(exc)}, ensure_ascii=False).encode("utf-8")
            self.send_response(503)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        event_context = {"session_id": session_id, "conversation_id": conversation_id}
        thinking_text = "正在准备简历上下文" if resume_profile else "正在准备回答"
        if deep_thinking and not self.write_event(sse("thinking", {"text": thinking_text, **event_context})):
            return

        answer = ""
        try:
            for event_name, event_data in stream_agent_events(
                query=query,
                resume_profile=resume_profile,
                target_job=target_job,
                history=history,
                memory=memory,
                deep_thinking=deep_thinking,
                model=model,
            ):
                if event_name == "chunk":
                    chunk = str(event_data.get("content", ""))
                    if chunk:
                        answer += chunk
                if not self.write_event(sse(event_name, {**event_data, **event_context})):
                    return
        except Exception as exc:  # 模型错误需要转换为 SSE，避免前端一直等待。
            print(f"[core] model request failed: {exc}")
            self.write_event(sse("error", {"message": "模型请求失败，请检查模型配置或稍后重试。", **event_context}))
            self.write_event(b"event: done\ndata:\n\n")
            self.close_connection = True
            return

        if not self.write_event(sse("final", {"content": answer, **event_context})):
            return
        if not self.write_event(b"event: done\ndata:\n\n"):
            return

        self.close_connection = True

    def _do_plan(self) -> None:
        """执行独立的能力评估和学习计划工作流。"""
        length = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(length) or b"{}")
        resume_profile = request.get("resume_profile")
        if not isinstance(resume_profile, dict):
            self._write_json(400, {"code": "RESUME_REQUIRED", "message": "resume_profile 必须是结构化简历对象。"})
            return

        try:
            weeks = int(request.get("weeks", 4))
            hours_per_week = int(request.get("hours_per_week", 8))
        except (TypeError, ValueError):
            self._write_json(400, {"code": "INVALID_SCHEDULE", "message": "weeks 和 hours_per_week 必须是整数。"})
            return

        try:
            model = create_chat_model()
        except ModelConfigurationError as exc:
            self._write_json(503, {"code": "MODEL_CONFIGURATION_ERROR", "message": str(exc)})
            return

        try:
            result = run_planning_workflow(
                resume_profile=resume_profile,
                job_description=str(request.get("job_description", "") or request.get("target_job", "")),
                weeks=weeks,
                hours_per_week=hours_per_week,
                research_market=request.get("research_market") is True,
                model=model,
            )
        except ValueError as exc:
            self._write_json(400, {"code": "INVALID_SCHEDULE", "message": str(exc)})
            return
        except Exception as exc:
            print(f"[core] planning workflow failed: {exc}")
            self._write_json(502, {"code": "PLANNING_WORKFLOW_ERROR", "message": "能力评估或学习计划生成失败。"})
            return

        self._write_json(200, public_planning_result(result))

    def _do_memory(self) -> None:
        """异步任务调用的增量记忆整理接口，不参与用户回答流。"""
        length = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(length) or b"{}")
        memory_value = request.get("memory")
        context_value = request.get("context")
        if not isinstance(memory_value, dict) or not isinstance(context_value, list):
            self._write_json(400, {"code": "INVALID_MEMORY_CONTEXT", "message": "memory 和 context 格式无效。"})
            return
        memory = memory_value
        context = context_value
        try:
            model = create_chat_model()
            update = extract_memory_update(
                query="",
                answer="",
                context=json.dumps(context, ensure_ascii=False),
                memory=memory,
                model=model,
            )
        except ModelConfigurationError as exc:
            self._write_json(503, {"code": "MODEL_CONFIGURATION_ERROR", "message": str(exc)})
            return
        except Exception as exc:
            print(f"[core] memory workflow failed: {exc}")
            self._write_json(502, {"code": "MEMORY_WORKFLOW_ERROR", "message": "会话记忆整理失败。"})
            return
        self._write_json(200, update or {"summary": "", "facts": []})

    def _write_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _write_review_block(
        self,
        code: str,
        message: str,
        session_id: Any,
        conversation_id: Any,
        deep_thinking: bool,
    ) -> None:
        """以 SSE 错误事件返回审查结果，让前端沿用同一条错误处理链路。"""
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        context = {"session_id": session_id, "conversation_id": conversation_id}
        if deep_thinking:
            self.write_event(sse("thinking", {"text": "正在检查问题", **context}))
        self.write_event(sse("error", {"code": code, "message": message, **context}))
        self.write_event(b"event: done\ndata:\n\n")
        self.close_connection = True

    def log_message(self, format: str, *args: object) -> None:
        print(f"[core] {format % args}")


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8100), AgentHandler)
    print("AtlasMind Core listening on http://127.0.0.1:8100")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[core] stopped")
        server.server_close()
