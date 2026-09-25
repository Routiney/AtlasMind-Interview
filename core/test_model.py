import json
import os
import threading
import unittest
from http.server import ThreadingHTTPServer
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import httpx
from langchain_openai import ChatOpenAI

from app import AgentHandler
from model import ModelConfigurationError, chunk_text, create_chat_model


class ModelTests(unittest.TestCase):
    def test_deepseek_stream_request(self):
        for thinking in ("disabled", "enabled"):
            with self.subTest(thinking=thinking):
                captured = []

                def respond(request):
                    captured.append(request)
                    chunks = [
                        {"reasoning_content": "not answer text"},
                        {"content": "Hello"},
                        {"content": " world"},
                    ]
                    body = "".join(
                        "data: " + json.dumps({
                            "id": "test", "object": "chat.completion.chunk",
                            "created": 0, "model": "deepseek-flash",
                            "choices": [{"index": 0, "delta": delta, "finish_reason": None}],
                        }) + "\n\n"
                        for delta in chunks
                    ) + "data: [DONE]\n\n"
                    return httpx.Response(200, text=body, headers={"Content-Type": "text/event-stream"})

                env = {"DEEPSEEK_API_KEY": "test-deepseek", "DEEPSEEK_THINKING": thinking}
                with patch.dict(os.environ, env, clear=True), httpx.Client(transport=httpx.MockTransport(respond)) as client:
                    with patch("langchain_openai.ChatOpenAI", side_effect=lambda **kw: ChatOpenAI(http_client=client, **kw)):
                        model = create_chat_model()
                    answer = "".join(chunk_text(chunk) for chunk in model.stream([("human", "Hi")]))
                self.assertEqual(answer, "Hello world")
                request = captured[0]
                self.assertEqual(str(request.url), "https://api.deepseek.com/chat/completions")
                self.assertEqual(request.headers["Authorization"], "Bearer test-deepseek")
                payload = json.loads(request.content)
                self.assertTrue(payload["stream"])
                self.assertEqual(payload["model"], "deepseek-flash")
                self.assertEqual(payload["thinking"], {"type": thinking})
                if thinking == "enabled":
                    self.assertEqual(payload["reasoning_effort"], "high")
                    self.assertNotIn("temperature", payload)

    def test_provider_configuration(self):
        with patch("model.load_dotenv", return_value=False), patch.dict(os.environ, {"ATLASMIND_PROVIDER": "openai", "OPENAI_API_KEY": "test-openai"}, clear=True):
            model = create_chat_model()
            self.assertEqual(model.model_name, "gpt-4.1-mini")
            self.assertIsNone(model.extra_body)
        for env in ({}, {"ATLASMIND_PROVIDER": "unknown"}, {"DEEPSEEK_API_KEY": "test", "ATLASMIND_MODEL_TIMEOUT": "bad"}):
            with self.subTest(env=env), patch("model.load_dotenv", return_value=False), patch.dict(os.environ, env, clear=True):
                with self.assertRaises(ModelConfigurationError):
                    create_chat_model()

    def test_missing_key_returns_json_503(self):
        with patch("model.load_dotenv", return_value=False), patch.dict(os.environ, {}, clear=True), ThreadingHTTPServer(("127.0.0.1", 0), AgentHandler) as server:
            worker = threading.Thread(target=server.handle_request)
            worker.start()
            try:
                request = Request(
                    f"http://127.0.0.1:{server.server_port}/agents/PlexusAgent/execute",
                    data=json.dumps({"query": "Hi", "stream": True}).encode(),
                    headers={"Content-Type": "application/json"},
                )
                with self.assertRaises(HTTPError) as caught:
                    urlopen(request, timeout=10)
                with caught.exception as response:
                    self.assertEqual(response.code, 503)
                    payload = json.loads(response.read())
                    self.assertEqual(payload["code"], "MODEL_CONFIGURATION_ERROR")
                    self.assertIn("DEEPSEEK_API_KEY", payload["message"])
            finally:
                worker.join(timeout=10)

    def test_plan_endpoint_requires_structured_resume(self):
        with ThreadingHTTPServer(("127.0.0.1", 0), AgentHandler) as server:
            worker = threading.Thread(target=server.handle_request)
            worker.start()
            try:
                request = Request(
                    f"http://127.0.0.1:{server.server_port}/agents/PlexusAgent/plan",
                    data=json.dumps({"job_description": "Java 后端工程师"}).encode(),
                    headers={"Content-Type": "application/json"},
                )
                with self.assertRaises(HTTPError) as caught:
                    urlopen(request, timeout=10)
                with caught.exception as response:
                    self.assertEqual(response.code, 400)
                    payload = json.loads(response.read())
                    self.assertEqual(payload["code"], "RESUME_REQUIRED")
            finally:
                worker.join(timeout=10)


if __name__ == "__main__":
    unittest.main()
