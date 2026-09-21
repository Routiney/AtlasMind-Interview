"""AtlasMind 第一步：最小 SSE Agent 服务。

这是教学用的假 Agent，不调用真实模型，只演示请求和事件流。
"""

from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from time import sleep
from typing import Any


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
        if self.path != "/agents/PlexusAgent/execute":
            self.send_error(404, "unknown endpoint")
            return

        length = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(length) or b"{}")
        query = str(request.get("query", "")).strip()
        session_id = request.get("session_id")
        conversation_id = request.get("conversation_id")
        stream = request.get("stream") is True

        if not query:
            self.send_error(400, "query is required")
            return
        if not stream:
            self.send_error(400, "this demo requires stream=true")
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        event_context = {"session_id": session_id, "conversation_id": conversation_id}
        if not self.write_event(sse("thinking", {"text": "正在准备回答", **event_context})):
            return
        sleep(0.2)

        chunks = [f"你问的是：{query}。", "这是第一个流式回答。"]
        answer = ""
        for chunk in chunks:
            answer += chunk
            if not self.write_event(sse("chunk", {"content": chunk, **event_context})):
                return
            sleep(0.2)

        if not self.write_event(sse("final", {"content": answer, **event_context})):
            return
        if not self.write_event(b"event: done\ndata:\n\n"):
            return
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
