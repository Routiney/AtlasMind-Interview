from __future__ import annotations

import json
import urllib.request


def read_sse() -> None:
    body = json.dumps(
        {
            "query": "什么是 SSE",
            "session_id": "demo-1",
            "stream": True,
        },
        ensure_ascii=False,
    ).encode("utf-8")

    request = urllib.request.Request(
        "http://127.0.0.1:8100/agents/PlexusAgent/execute",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    with urllib.request.urlopen(request) as response:
        event_type = "message"
        data_lines: list[str] = []
        answer = ""
        thinking = ""
        is_streaming = True

        for raw_line in response:
            line = raw_line.decode("utf-8").rstrip("\r\n")

            if line == "":
                raw_data = "".join(data_lines)
                if event_type == "done":
                    is_streaming = False
                    print(f"收到事件: done，最终回答：{answer}")
                    break
                if raw_data:
                    data = json.loads(raw_data)
                    if event_type == "thinking":
                        thinking = str(data.get("text", ""))
                        print(f"思考状态：{thinking}")
                    elif event_type == "chunk":
                        answer += str(data.get("content", ""))
                        print(f"回答增量：{answer}")
                    elif event_type == "final":
                        answer = str(data.get("content", answer))
                        print(f"最终内容：{answer}")

                event_type = "message"
                data_lines = []
                continue

            if line.startswith("event:"):
                event_type = line[6:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].strip())

        print(f"streaming={is_streaming}, thinking={thinking!r}, answer={answer!r}")


if __name__ == "__main__":
    read_sse()
