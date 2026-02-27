#!/usr/bin/env python3
import argparse
import html
import json
import re
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import requests


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def text_from_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, list):
        parts = [text_from_value(v) for v in value]
        return "\n".join(p for p in parts if p).strip()
    if isinstance(value, dict):
        for key in ("text", "content", "output_text", "reasoning_content", "reasoning", "arguments"):
            if key in value:
                text = text_from_value(value.get(key))
                if text:
                    return text
        if "function" in value:
            text = text_from_value(value.get("function"))
            if text:
                return text
    return ""


def parse_retry_seconds(message: str) -> float | None:
    if not message:
        return None
    m = re.search(r"Try again in ([0-9]*\.?[0-9]+)\s*seconds", message, flags=re.IGNORECASE)
    if not m:
        return None
    try:
        return float(m.group(1))
    except ValueError:
        return None


def strip_html_tags(value: str) -> str:
    without_tags = re.sub(r"<[^>]+>", " ", value)
    without_entities = html.unescape(without_tags)
    normalized = re.sub(r"\s+", " ", without_entities)
    return normalized.strip()


def fetch_chapter_segments(chapter_url: str, timeout_sec: int, max_segments: int, max_chars: int) -> tuple[list[str], str]:
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml",
    }
    response = requests.get(chapter_url, headers=headers, timeout=timeout_sec)
    response.raise_for_status()
    page_html = response.text or ""

    chunks = re.findall(r"<p[^>]*>(.*?)</p>", page_html, flags=re.IGNORECASE | re.DOTALL)
    candidates: list[str] = []
    for chunk in chunks:
        text = strip_html_tags(chunk)
        if len(text) < 30:
            continue
        if not re.search(r"[A-Za-z]", text):
            continue
        text = text[:max_chars].strip()
        if text:
            candidates.append(text)

    # Preserve order while removing duplicates.
    seen: set[str] = set()
    segments: list[str] = []
    for item in candidates:
        if item in seen:
            continue
        seen.add(item)
        segments.append(item)
        if len(segments) >= max_segments:
            break

    return segments, page_html


def build_classic_system_prompt() -> str:
    return (
        "### ROLE\n"
        "You are a professional literary translator for novels and light novels.\n"
        "Your output must read naturally in Russian while preserving tone, intent, and narrative voice.\n\n"
        "### GOALS\n"
        "1. Preserve meaning, plot details, and character voice.\n"
        "2. Prioritize fluent Russian prose over literal calques.\n"
        "3. Keep terminology consistent across segments.\n"
        "4. Keep honorifics and culture-specific terms only when they are important for context.\n\n"
        "### STYLE RULES\n"
        "- Prefer idiomatic Russian syntax and punctuation.\n"
        "- Avoid machine-like phrasing and over-literal word order.\n"
        "- Keep dialogue natural and character-appropriate.\n\n"
        "### OUTPUT FORMAT\n"
        "1. Return ONLY XML tags in the same shape as input: <s i='N'>...</s>.\n"
        "2. No preamble, no explanations, no markdown.\n"
        "3. Preserve the same indexes and segment count whenever possible."
    )


def build_airforce_user_prompt(source_lang: str, target_lang: str, tagged_input: str) -> str:
    return (
        f"TRANSLATE from {source_lang} to {target_lang}.\n"
        "Inject soul into the text. Make the reader believe this was written by a Russian author.\n\n"
        "Use popular genre terminology (Magic -> Магия, etc.). Make it sound like high-quality fiction.\n\n"
        "1. Keep the XML structure exactly as is (<s i='...'>...</s>).\n"
        "2. NO PREAMBLE. NO ANALYSIS TEXT. NO MARKDOWN HEADERS.\n"
        "3. Start your response IMMEDIATELY with the first XML tag.\n\n"
        "INPUT BLOCK:\n"
        f"{tagged_input}"
    )


def to_tagged_input(segments: list[str]) -> str:
    lines = [f"<s i='{index}'>{text}</s>" for index, text in enumerate(segments)]
    return "\n".join(lines)


def run_preflight(base_url: str, api_key: str, models: list[str], timeout_sec: int, retries: int, min_spacing: float) -> tuple[bool, list[str]]:
    issues: list[str] = []
    warnings: list[str] = []

    parsed = urlparse(base_url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        issues.append(f"Invalid base URL: {base_url}")

    if not api_key or len(api_key.strip()) < 20:
        issues.append("API key is empty or too short")
    elif not api_key.startswith("sk-"):
        warnings.append("API key does not start with 'sk-'")

    if not models:
        issues.append("Models list is empty")
    else:
        empty_models = [m for m in models if not m or not m.strip()]
        if empty_models:
            issues.append("Models list contains empty value")

    if timeout_sec <= 0:
        issues.append("timeout-sec must be > 0")
    if retries <= 0:
        issues.append("retries must be > 0")
    if min_spacing < 0:
        issues.append("min-spacing must be >= 0")

    return len(issues) == 0, issues + warnings


@dataclass
class ProbeResult:
    success: bool
    model: str
    prompt: str
    elapsed_ms: int
    http_status: int | None = None
    finish_reason: str = ""
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    message_content_len: int = 0
    choice_text_len: int = 0
    output_text_len: int = 0
    reasoning_len: int = 0
    tool_calls_len: int = 0
    error: str = ""
    error_body: str = ""
    body_raw: str = ""


class AirforceDiagnoser:
    def __init__(self, base_url: str, api_key: str, timeout_sec: int, retries: int, min_spacing: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout_sec = timeout_sec
        self.retries = retries
        self.min_spacing = min_spacing
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            }
        )
        self._last_call_ts = 0.0

    def _respect_spacing(self) -> None:
        elapsed = time.time() - self._last_call_ts
        wait = self.min_spacing - elapsed
        if wait > 0:
            time.sleep(wait)

    def _request_json(self, method: str, path: str, payload: dict[str, Any] | None = None) -> tuple[int, str]:
        url = f"{self.base_url}{path}"
        last_exc: Exception | None = None
        for attempt in range(1, self.retries + 1):
            self._respect_spacing()
            started = time.time()
            try:
                if method == "GET":
                    resp = self.session.get(url, timeout=self.timeout_sec)
                else:
                    resp = self.session.post(url, data=json.dumps(payload or {}), timeout=self.timeout_sec)
                self._last_call_ts = time.time()

                body_text = resp.text or ""
                if resp.status_code == 429 and attempt < self.retries:
                    retry_s = None
                    try:
                        err = resp.json().get("error", {})
                        retry_s = parse_retry_seconds(str(err.get("message", "")))
                    except Exception:
                        retry_s = None
                    sleep_for = max(self.min_spacing, (retry_s or 1.0) + 0.3)
                    print(f"429 rate-limit on {path} attempt={attempt}, sleeping {sleep_for:.2f}s")
                    time.sleep(sleep_for)
                    continue

                return resp.status_code, body_text
            except Exception as exc:
                self._last_call_ts = time.time()
                last_exc = exc
                if attempt < self.retries:
                    time.sleep(max(self.min_spacing, 1.0) + attempt * 0.5)
                    continue
                raise RuntimeError(f"{method} {path} failed after {self.retries} attempts: {exc}") from exc
            finally:
                _ = started

        raise RuntimeError(f"{method} {path} failed: {last_exc}")

    def probe_models(self) -> dict[str, Any]:
        try:
            status, body = self._request_json("GET", "/v1/models")
            payload = json.loads(body) if body else {}
            ids: list[str] = []
            for item in payload.get("data", []) or []:
                model_id = item.get("id")
                if isinstance(model_id, str):
                    ids.append(model_id)
            ids = sorted(set(ids))
            return {
                "success": 200 <= status < 300,
                "status": status,
                "model_count": len(ids),
                "has_deepseek": "deepseek-v3.2" in ids,
                "has_deepseek_thinking": "deepseek-v3.2-thinking" in ids,
                "has_glm_fast": "glm-5-fast" in ids,
                "ids_sample": ids[:50],
                "body_raw": body,
            }
        except Exception as exc:
            return {
                "success": False,
                "error": str(exc),
            }

    def probe_chat(self, model: str, prompt_name: str, messages: list[dict[str, str]], max_tokens: int) -> ProbeResult:
        payload = {
            "model": model,
            "messages": messages,
            "temperature": 0.2,
            "top_p": 0.95,
            "max_tokens": max_tokens,
            "stream": False,
        }

        started = time.time()
        try:
            status, body_raw = self._request_json("POST", "/v1/chat/completions", payload=payload)
            elapsed_ms = int((time.time() - started) * 1000)

            if not body_raw:
                return ProbeResult(
                    success=False,
                    model=model,
                    prompt=prompt_name,
                    elapsed_ms=elapsed_ms,
                    http_status=status,
                    error="Empty HTTP body",
                    body_raw="",
                )

            try:
                body = json.loads(body_raw)
            except Exception as exc:
                return ProbeResult(
                    success=False,
                    model=model,
                    prompt=prompt_name,
                    elapsed_ms=elapsed_ms,
                    http_status=status,
                    error=f"Non-JSON response: {exc}",
                    body_raw=body_raw,
                )

            if not (200 <= status < 300):
                err = body.get("error", {}) if isinstance(body, dict) else {}
                return ProbeResult(
                    success=False,
                    model=model,
                    prompt=prompt_name,
                    elapsed_ms=elapsed_ms,
                    http_status=status,
                    error=str(err.get("message") or f"HTTP {status}"),
                    error_body=body_raw,
                    body_raw=body_raw,
                )

            choice = ((body.get("choices") or [None])[0]) if isinstance(body, dict) else None
            message = choice.get("message", {}) if isinstance(choice, dict) else {}

            message_content = text_from_value(message.get("content"))
            choice_text = text_from_value(choice.get("text") if isinstance(choice, dict) else None)
            output_text = text_from_value(choice.get("output_text") if isinstance(choice, dict) else None)
            reasoning = text_from_value(message.get("reasoning_content")) or text_from_value(message.get("reasoning"))
            tool_calls = text_from_value(message.get("tool_calls"))
            usage = body.get("usage", {}) if isinstance(body, dict) else {}

            return ProbeResult(
                success=True,
                model=model,
                prompt=prompt_name,
                elapsed_ms=elapsed_ms,
                http_status=status,
                finish_reason=str(choice.get("finish_reason", "") if isinstance(choice, dict) else ""),
                prompt_tokens=usage.get("prompt_tokens"),
                completion_tokens=usage.get("completion_tokens"),
                total_tokens=usage.get("total_tokens"),
                message_content_len=len(message_content),
                choice_text_len=len(choice_text),
                output_text_len=len(output_text),
                reasoning_len=len(reasoning),
                tool_calls_len=len(tool_calls),
                body_raw=body_raw,
            )
        except Exception as exc:
            elapsed_ms = int((time.time() - started) * 1000)
            return ProbeResult(
                success=False,
                model=model,
                prompt=prompt_name,
                elapsed_ms=elapsed_ms,
                error=str(exc),
            )


def main() -> None:
    parser = argparse.ArgumentParser(description="Airforce API diagnostics")
    parser.add_argument("--api-key", required=True, help="Airforce API key")
    parser.add_argument("--base-url", default="https://api.airforce")
    parser.add_argument("--models", nargs="+", default=["deepseek-v3.2", "deepseek-v3.2-thinking", "glm-5-fast"])
    parser.add_argument("--timeout-sec", type=int, default=180)
    parser.add_argument("--retries", type=int, default=4)
    parser.add_argument("--min-spacing", type=float, default=1.2)
    parser.add_argument("--skip-models-probe", action="store_true", help="Do not call /v1/models to save rate-limit budget")
    parser.add_argument("--chapter-url", default="", help="Optional chapter URL to build XML translation prompt from real chapter text")
    parser.add_argument("--chapter-max-segments", type=int, default=24)
    parser.add_argument("--chapter-max-chars", type=int, default=1200)
    parser.add_argument("--source-lang", default="English")
    parser.add_argument("--target-lang", default="Russian")
    parser.add_argument("--max-tokens", type=int, default=4096)
    args = parser.parse_args()

    report_dir = Path("build/reports/airforce-diagnostics")
    report_dir.mkdir(parents=True, exist_ok=True)

    print("=== Airforce Diagnostics ===")
    print(f"BaseUrl: {args.base_url}")
    print(f"Models: {', '.join(args.models)}")
    print(f"ReportDir: {report_dir.resolve()}")

    print("\n=== Preflight (no API calls) ===")
    ok, preflight_notes = run_preflight(
        base_url=args.base_url,
        api_key=args.api_key,
        models=args.models,
        timeout_sec=args.timeout_sec,
        retries=args.retries,
        min_spacing=args.min_spacing,
    )
    if not ok:
        print("FAILED preflight:")
        for note in preflight_notes:
            print(f"- {note}")
        raise SystemExit(2)
    print("OK preflight")
    for note in preflight_notes:
        print(f"- {note}")

    diagnoser = AirforceDiagnoser(
        base_url=args.base_url,
        api_key=args.api_key,
        timeout_sec=args.timeout_sec,
        retries=args.retries,
        min_spacing=args.min_spacing,
    )

    if args.skip_models_probe:
        print("\n=== Step 1 - /v1/models probe ===")
        print("SKIPPED (--skip-models-probe)")
        models_probe = {"success": None, "skipped": True}
    else:
        print("\n=== Step 1 - /v1/models probe ===")
        models_probe = diagnoser.probe_models()
        if models_probe.get("success"):
            print(
                f"OK status={models_probe.get('status')} model_count={models_probe.get('model_count')} "
                f"deepseek={models_probe.get('has_deepseek')} "
                f"deepseek-thinking={models_probe.get('has_deepseek_thinking')} "
                f"glm-5-fast={models_probe.get('has_glm_fast')}"
            )
        else:
            print(f"FAILED /v1/models: {models_probe.get('error')}")

    chapter_segments: list[str] = []
    chapter_fetch_info: dict[str, Any] = {}

    if args.chapter_url.strip():
        print("\n=== Chapter fetch ===")
        try:
            chapter_segments, raw_html = fetch_chapter_segments(
                chapter_url=args.chapter_url.strip(),
                timeout_sec=args.timeout_sec,
                max_segments=args.chapter_max_segments,
                max_chars=args.chapter_max_chars,
            )
            chapter_fetch_info = {
                "url": args.chapter_url.strip(),
                "segments_count": len(chapter_segments),
                "html_size": len(raw_html),
                "sample_segments": chapter_segments[:3],
            }
            print(
                f"OK chapter fetched, segments={len(chapter_segments)}, html_size={len(raw_html)}, "
                f"sample_len={[len(s) for s in chapter_segments[:3]]}"
            )
            if not chapter_segments:
                print("FAILED: chapter parsed but no text segments extracted")
                raise SystemExit(3)
        except Exception as exc:
            print(f"FAILED chapter fetch: {exc}")
            raise SystemExit(3)

    if chapter_segments:
        tagged_input = to_tagged_input(chapter_segments)
        prompt_plain = None
        prompt_xml = [
            {"role": "system", "content": build_classic_system_prompt()},
            {
                "role": "user",
                "content": build_airforce_user_prompt(
                    source_lang=args.source_lang,
                    target_lang=args.target_lang,
                    tagged_input=tagged_input,
                ),
            },
        ]
    else:
        prompt_plain = [
            {"role": "user", "content": "Reply with exact text: OK"},
        ]
        prompt_xml = [
            {"role": "system", "content": build_classic_system_prompt()},
            {
                "role": "user",
                "content": build_airforce_user_prompt(
                    source_lang="English",
                    target_lang="Russian",
                    tagged_input="<s i='0'>Hello world.</s>\n<s i='1'>How are you?</s>",
                ),
            },
        ]

    results: list[ProbeResult] = []
    for model in args.models:
        if prompt_plain is not None:
            print(f"\n=== Step 2 - model={model} prompt=plain ===")
            r1 = diagnoser.probe_chat(model=model, prompt_name="plain", messages=prompt_plain, max_tokens=args.max_tokens)
            results.append(r1)
            print(
                f"{'OK' if r1.success else 'FAILED'} status={r1.http_status} elapsed_ms={r1.elapsed_ms} "
                f"finish_reason={r1.finish_reason} prompt_tokens={r1.prompt_tokens} "
                f"completion_tokens={r1.completion_tokens} total_tokens={r1.total_tokens} "
                f"lens=message:{r1.message_content_len},text:{r1.choice_text_len},output_text:{r1.output_text_len},"
                f"reasoning:{r1.reasoning_len},tool_calls:{r1.tool_calls_len}"
            )
            if not r1.success and r1.error:
                print(f"error={r1.error}")

        print(f"\n=== Step 3 - model={model} prompt=xml_translate ===")
        xml_prompt_name = "xml_translate_chapter" if chapter_segments else "xml_translate"
        r2 = diagnoser.probe_chat(
            model=model,
            prompt_name=xml_prompt_name,
            messages=prompt_xml,
            max_tokens=args.max_tokens,
        )
        results.append(r2)
        print(
            f"{'OK' if r2.success else 'FAILED'} status={r2.http_status} elapsed_ms={r2.elapsed_ms} "
            f"finish_reason={r2.finish_reason} prompt_tokens={r2.prompt_tokens} "
            f"completion_tokens={r2.completion_tokens} total_tokens={r2.total_tokens} "
            f"lens=message:{r2.message_content_len},text:{r2.choice_text_len},output_text:{r2.output_text_len},"
            f"reasoning:{r2.reasoning_len},tool_calls:{r2.tool_calls_len}"
        )
        if not r2.success and r2.error:
            print(f"error={r2.error}")

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    json_path = report_dir / f"diagnostics-{timestamp}.json"
    txt_path = report_dir / f"diagnostics-{timestamp}.txt"

    payload = {
        "generated_at": now_iso(),
        "base_url": args.base_url,
        "models": args.models,
        "models_probe": models_probe,
        "chapter_fetch": chapter_fetch_info,
        "results": [asdict(r) for r in results],
    }
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "Airforce Diagnostics Summary",
        f"generated_at={now_iso()}",
        f"base_url={args.base_url}",
        f"models={','.join(args.models)}",
        "",
    ]
    for r in results:
        if r.success:
            lines.append(
                f"OK model={r.model} prompt={r.prompt} status={r.http_status} elapsed_ms={r.elapsed_ms} "
                f"finish_reason={r.finish_reason} usage={r.prompt_tokens}/{r.completion_tokens}/{r.total_tokens} "
                f"lens=message:{r.message_content_len},text:{r.choice_text_len},output_text:{r.output_text_len},"
                f"reasoning:{r.reasoning_len},tool_calls:{r.tool_calls_len}"
            )
        else:
            lines.append(
                f"FAILED model={r.model} prompt={r.prompt} status={r.http_status} elapsed_ms={r.elapsed_ms} error={r.error}"
            )
    txt_path.write_text("\n".join(lines), encoding="utf-8")

    print("\n=== Done ===")
    print(f"JSON report: {json_path.resolve()}")
    print(f"Text summary: {txt_path.resolve()}")


if __name__ == "__main__":
    main()
