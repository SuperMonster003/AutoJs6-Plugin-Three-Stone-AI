#!/usr/bin/env python3
"""Bounded local OpenAI-compatible SSE server for P4 device smoke tests."""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


class ProviderState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.previous_messages: dict[str, bytes] = {}
        self.request_count = 0

    def usage(self, kind: str, messages: bytes) -> tuple[int, int, int, int]:
        with self.lock:
            previous = self.previous_messages.get(kind, b"")
            common = 0
            for left, right in zip(previous, messages):
                if left != right:
                    break
                common += 1
            self.previous_messages[kind] = messages
            self.request_count += 1
            prompt_tokens = max(16, (len(messages) + 3) // 4)
            cached_tokens = min(prompt_tokens, common // 4)
            cache_write_tokens = max(0, prompt_tokens - cached_tokens)
            return self.request_count, prompt_tokens, cached_tokens, cache_write_tokens


class SmokeServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address: tuple[str, int], expected_key: str) -> None:
        super().__init__(address, SmokeHandler)
        self.expected_key = expected_key
        self.state = ProviderState()


class SmokeHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "ThreeStoneP4Smoke/1"

    @property
    def smoke_server(self) -> SmokeServer:
        return self.server  # type: ignore[return-value]

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if not self.path.rstrip("/").endswith("chat/completions"):
            self.send_error(404)
            return
        expected = f"Bearer {self.smoke_server.expected_key}"
        received = self.headers.get("Authorization", "")
        if not hmac.compare_digest(received, expected):
            self.send_error(401)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 2 * 1024 * 1024:
                raise ValueError("request length is outside the smoke limit")
            document = json.loads(self.rfile.read(length))
            messages = document.get("messages")
            if not isinstance(messages, list):
                raise ValueError("messages must be an array")
        except (ValueError, json.JSONDecodeError) as error:
            self.send_error(400, str(error))
            return

        canonical_messages = json.dumps(
            messages,
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode("utf-8")
        kind = "structured" if document.get("response_format") else "chat"
        request_number, prompt_tokens, cached_tokens, cache_write_tokens = (
            self.smoke_server.state.usage(kind, canonical_messages)
        )
        answer = self._answer(kind, request_number)
        completion_tokens = max(1, (len(answer.encode("utf-8")) + 3) // 4)
        events = [
            {"choices": [{"delta": {"content": answer}}]},
            {
                "choices": [],
                "usage": {
                    "prompt_tokens": prompt_tokens,
                    "completion_tokens": completion_tokens,
                    "total_tokens": prompt_tokens + completion_tokens,
                    "prompt_tokens_details": {
                        "cached_tokens": cached_tokens,
                        "cache_write_tokens": cache_write_tokens,
                    },
                },
            },
        ]
        body = "".join(
            f"data: {json.dumps(event, ensure_ascii=False, separators=(',', ':'))}\n\n"
            for event in events
        ) + "data: [DONE]\n\n"
        encoded = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)
        self.wfile.flush()
        digest = hashlib.sha256(canonical_messages).hexdigest()[:12]
        hit_rate = cached_tokens / prompt_tokens if prompt_tokens else 0.0
        print(
            "request="
            f"{request_number} kind={kind} input={prompt_tokens} cached={cached_tokens} "
            f"write={cache_write_tokens} hit_rate={hit_rate:.4f} messages_sha256={digest}",
            flush=True,
        )

    def log_message(self, format_string: str, *args: Any) -> None:
        print(f"http={format_string % args}", flush=True)

    @staticmethod
    def _answer(kind: str, request_number: int) -> str:
        if kind == "structured":
            # A deliberately invalid checkpoint keeps the smoke server independent of schema
            # revisions and exercises the launcher's non-blocking summary failure path.
            return "{}"
        return f"Mock answer {request_number}: context pipeline acknowledged."


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18443)
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--expected-key", default="p4-smoke-key")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not 1 <= args.port <= 65535:
        raise SystemExit("port must be between 1 and 65535")
    server = SmokeServer((args.bind, args.port), args.expected_key)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.cert, args.key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    print(f"listening=https://{args.bind}:{args.port}", flush=True)
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
