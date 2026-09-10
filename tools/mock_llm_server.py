#!/usr/bin/env python3
"""OpenAI-compatible mock LLM endpoint used to verify Lingua end to end.

Why this exists: the app must be verified against a deterministic endpoint that
never costs money and can fail on demand. Start it on the host and point an API
profile at ``http://<host-ip>:8765/v1`` with the key ``test-key``.

Fault injection is driven by the requested model name:

===========================  ==========================================
model                        behaviour
===========================  ==========================================
mock-translate (default)     200, well-formed JSON translation
mock-plain                   200, plain text (no JSON) -> parser fallback
mock-prose-json              200, chatty prose wrapped around JSON
error-400                    400 bad request
error-401                    401 unauthorized
error-404                    404 not found
error-429                    429 rate limited
error-500                    500 server error
error-malformed              this model name is rejected with code below
error-timeout                hangs for 120s, triggers client timeout
===========================  ==========================================

Usage:
    python3 tools/mock_llm_server.py [--host 0.0.0.0] [--port 8765]
"""

from __future__ import annotations

import argparse
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_KEY = "test-key"
DEFAULT_MODEL = "mock-translate"

PHRASES = {
    "hello, world!": "你好，世界！",
    "the early bird catches the worm.": "早起的鸟儿有虫吃。",
    "今天天气很好，我们去公园散步吧。": "The weather is lovely today — let's take a walk in the park.",
}

MODEL_IDS = [
    DEFAULT_MODEL,
    "mock-plain",
    "mock-prose-json",
    "error-400",
    "error-401",
    "error-404",
    "error-429",
    "error-500",
    "error-malformed",
    "error-timeout",
]


def detect_language(text: str) -> tuple[str, str]:
    if re.search(r"[\u4e00-\u9fff]", text):
        return "Chinese (Simplified)", "zh"
    if re.search(r"[\u3040-\u30ff]", text):
        return "Japanese", "ja"
    if re.search(r"[\uac00-\ud7af]", text):
        return "Korean", "ko"
    return "English", "en"


def target_language(system_prompt: str) -> str:
    match = re.search(r"Translate the user's text into ([^(\\n]+)", system_prompt)
    return match.group(1).strip() if match else "Chinese (Simplified)"


def fake_translation(text: str, target: str) -> str:
    stripped = text.strip()
    known = PHRASES.get(stripped.lower())
    if known:
        return known
    return f"[{target}] {stripped}"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "LinguaMock/1.0"

    # ---- helpers -----------------------------------------------------
    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return {}

    def _send(self, code: int, payload: dict | str, content_type: str = "application/json") -> None:
        body = payload.encode("utf-8") if isinstance(payload, str) else json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, code: int, message: str) -> None:
        self._send(code, {"error": {"message": message, "type": "mock_error", "code": code}})

    def _authorised(self) -> bool:
        header = self.headers.get("Authorization") or ""
        return header.strip() == f"Bearer {API_KEY}"

    # ---- routes ------------------------------------------------------
    def do_GET(self) -> None:  # noqa: N802
        if self.path.rstrip("/").endswith("/models"):
            if not self._authorised():
                self.log_message("GET %s -> 401", self.path)
                self._error(401, "Invalid API key")
                return
            self.log_message("GET %s -> 200 (%d models)", self.path, len(MODEL_IDS))
            self._send(200, {"object": "list", "data": [{"id": m, "object": "model"} for m in MODEL_IDS]})
            return
        self._error(404, f"Unknown path {self.path}")

    def do_POST(self) -> None:  # noqa: N802
        if not self.path.rstrip("/").endswith("/chat/completions"):
            self._error(404, f"Unknown path {self.path}")
            return
        if not self._authorised():
            self.log_message("POST %s -> 401 (bad key)", self.path)
            self._error(401, "Invalid API key")
            return

        payload = self._read_json()
        model = payload.get("model") or DEFAULT_MODEL
        messages = payload.get("messages") or []
        system_prompt = next((m.get("content", "") for m in messages if m.get("role") == "system"), "")
        user_text = next((m.get("content", "") for m in reversed(messages) if m.get("role") == "user"), "")

        if model == "error-timeout":
            self.log_message("POST %s -> sleeping 120s (error-timeout)", self.path)
            time.sleep(120)
            return
        if model in {"error-400", "error-401", "error-404", "error-429", "error-500"}:
            code = int(model.split("-")[1])
            self.log_message("POST %s -> %d (injected)", self.path, code)
            self._error(code, f"Injected failure for model {model}")
            return
        if model == "error-malformed":
            self.log_message("POST %s -> 200 with malformed JSON body", self.path)
            self._send(200, "{ this is not valid json", "application/json")
            return

        source_name, source_code = detect_language(user_text)
        translation = fake_translation(user_text, target_language(system_prompt))

        if model == "mock-plain":
            content = translation
        elif model == "mock-prose-json":
            content = (
                "Sure! Here is the translation you asked for:\n```json\n"
                + json.dumps(
                    {"source_language": source_name, "source_language_code": source_code, "translation": translation},
                    ensure_ascii=False,
                )
                + "\n```\nLet me know if you need anything else."
            )
        else:
            content = json.dumps(
                {"source_language": source_name, "source_language_code": source_code, "translation": translation},
                ensure_ascii=False,
            )

        self.log_message("POST %s model=%s -> 200", self.path, model)
        self._send(
            200,
            {
                "id": "chatcmpl-mock",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": model,
                "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": content}, "finish_reason": "stop"}
                ],
                "usage": {"prompt_tokens": 42, "completion_tokens": 17, "total_tokens": 59},
            },
        )

    def log_message(self, fmt: str, *args) -> None:  # keep stdout tidy but informative
        print(f"[mock] {self.address_string()} {fmt % args}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="OpenAI-compatible mock endpoint for Lingua")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"[mock] listening on http://{args.host}:{args.port}/v1 (key: {API_KEY})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
