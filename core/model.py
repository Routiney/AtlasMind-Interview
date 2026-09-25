"""AtlasMind ChatModel 配置和流式调用。"""

from __future__ import annotations

import os
from collections.abc import Iterable
from pathlib import Path
from typing import Any

from dotenv import load_dotenv


class ModelConfigurationError(RuntimeError):
    """模型依赖或配置不可用。"""


def create_chat_model() -> Any:
    # Core is normally started from the repository root or the core directory.
    # Resolve the file from this module so both launch locations load the same config.
    load_dotenv(Path(__file__).with_name(".env"), override=False)
    try:
        from langchain_openai import ChatOpenAI
    except ImportError as exc:
        raise ModelConfigurationError(
            "缺少 langchain-openai，请先执行 python -m pip install -r requirements.txt"
        ) from exc

    provider = os.getenv("ATLASMIND_PROVIDER", "deepseek").strip().lower()
    if provider == "deepseek":
        api_key = os.getenv("DEEPSEEK_API_KEY")
        if not api_key:
            raise ModelConfigurationError("未配置 DEEPSEEK_API_KEY，无法调用 DeepSeek ChatModel")
        model = os.getenv("DEEPSEEK_MODEL", "deepseek-flash")
        base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    elif provider == "openai":
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise ModelConfigurationError("未配置 OPENAI_API_KEY，无法调用 OpenAI ChatModel")
        model = os.getenv("ATLASMIND_MODEL", "gpt-4.1-mini")
        base_url = os.getenv("OPENAI_BASE_URL")
    else:
        raise ModelConfigurationError(
            f"不支持的 ATLASMIND_PROVIDER: {provider}，可选值为 deepseek 或 openai"
        )

    try:
        temperature = float(os.getenv("ATLASMIND_TEMPERATURE", "0.2"))
        timeout = float(os.getenv("ATLASMIND_MODEL_TIMEOUT", "60"))
        if not 0 <= temperature <= 2 or not 0 < timeout < float("inf"):
            raise ValueError
    except ValueError as exc:
        raise ModelConfigurationError("温度须在 0 到 2 之间，超时须为正数") from exc

    options: dict[str, Any] = {
        "model": model,
        "api_key": api_key,
        "use_responses_api": False,
        "temperature": temperature,
        "timeout": timeout,
        "max_retries": 2,
    }
    if base_url:
        options["base_url"] = base_url

    if provider == "deepseek":
        thinking = os.getenv("DEEPSEEK_THINKING", "disabled").strip().lower()
        if thinking not in {"enabled", "disabled"}:
            raise ModelConfigurationError("DEEPSEEK_THINKING 须为 enabled 或 disabled")
        options["extra_body"] = {"thinking": {"type": thinking}}
        options["stream_usage"] = False
        if thinking == "enabled":
            options.pop("temperature")
            options["reasoning_effort"] = os.getenv("DEEPSEEK_REASONING_EFFORT", "high")

    return ChatOpenAI(**options)


def chunk_text(chunk: Any) -> str:
    """兼容 ChatModel 文本块的字符串和内容块格式。"""
    text = getattr(chunk, "text", None)
    if isinstance(text, str) and text:
        return text

    content = getattr(chunk, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, Iterable) and not isinstance(content, (bytes, str)):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
        return "".join(parts)
    return ""
