#!/usr/bin/env python3
"""Discover public chat models without credentials, inference, or third-party packages.

Only explicit model rows/fields are authoritative; examples and replacement columns
are not. Source format changes retain the last valid provider catalog. Documentation
describes availability, not a guarantee that every account or protocol feature works.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timezone
from html.parser import HTMLParser
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile
import time
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


PROVIDERS = ("openai", "anthropic", "gemini", "deepseek", "openrouter")
MAX_LONG = (1 << 63) - 1
MAX_CATALOG_BYTES = 256 * 1024
MAX_SOURCE_BYTES = 8 * 1024 * 1024
MAX_DETAIL_PAGES = 96
ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}\Z", re.ASCII)
OPENAI_INDEX = "https://developers.openai.com/api/docs/models/all.md"
OPENAI_DEPRECATIONS = "https://developers.openai.com/api/docs/deprecations.md"
ANTHROPIC_INDEX = "https://platform.claude.com/docs/en/models/overview.md"
ANTHROPIC_STATUS = "https://platform.claude.com/docs/en/about-claude/model-deprecations.md"
GEMINI_INDEX = "https://ai.google.dev/gemini-api/docs/models.md.txt"
DEEPSEEK_INDEX = "https://api-docs.deepseek.com/quick_start/pricing/"
OPENROUTER_INDEX = "https://openrouter.ai/api/v1/models"
DEFAULT_POLICY = Path(__file__).resolve().parents[1] / ".github/model-catalog-policy.json"
Fetch = Callable[[str], str]


class CatalogError(ValueError):
    """Invalid catalog, source response, or refresh policy."""


class DiscoveredModels(list):
    """IDs plus audit evidence kept in the CI report, never in the app catalog."""

    def __init__(self, models, *, unavailable=None, upcoming=None):
        super().__init__(models)
        self.unavailable = unavailable or {}
        self.upcoming = upcoming or {}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise CatalogError(message)


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        require(key not in result, f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(raw: bytes | str) -> object:
    def invalid_constant(value: str) -> None:
        raise CatalogError(f"invalid JSON constant: {value}")

    return json.loads(raw, object_pairs_hook=unique_object, parse_constant=invalid_constant)


def exact_keys(value: object, keys: set[str], name: str) -> None:
    require(type(value) is dict and set(value) == keys, f"invalid {name} keys")


def valid_id(value: object) -> bool:
    return type(value) is str and ID.fullmatch(value) is not None


def model_list(value: object, name: str, minimum: int = 1) -> None:
    require(type(value) is list and minimum <= len(value) <= 128, f"invalid {name} count")
    require(all(valid_id(item) for item in value), f"invalid {name} model ID")
    require(len(set(value)) == len(value), f"duplicate {name} model ID")


def validate_catalog(raw: bytes) -> dict:
    require(len(raw) <= MAX_CATALOG_BYTES, "catalog exceeds 256 KiB")
    value = load_json(raw)
    exact_keys(value, {"schemaVersion", "revision", "updatedAtEpochMillis", "providers"}, "catalog")
    require(type(value["schemaVersion"]) is int and value["schemaVersion"] == 1, "unsupported schemaVersion")
    for key in ("revision", "updatedAtEpochMillis"):
        require(type(value[key]) is int and 0 < value[key] <= MAX_LONG, f"invalid {key}")
    exact_keys(value["providers"], set(PROVIDERS), "providers")
    for provider, entry in value["providers"].items():
        exact_keys(entry, {"defaultModelId", "models"}, provider)
        model_list(entry["models"], provider)
        require(valid_id(entry["defaultModelId"]) and entry["defaultModelId"] in entry["models"],
                f"invalid {provider} defaultModelId")
    return value


# Public entry point shared by the publisher; always decode bytes so duplicate
# object keys cannot disappear in an earlier permissive JSON parse.
decode_catalog = validate_catalog


def validate_policy(raw: bytes) -> dict:
    require(len(raw) <= MAX_CATALOG_BYTES, "policy too large")
    value = load_json(raw)
    exact_keys(value, {"schemaVersion", "minimumCountRatio", "openRouter", "providers"}, "policy")
    require(type(value["schemaVersion"]) is int and value["schemaVersion"] == 1, "invalid policy version")
    ratio = value["minimumCountRatio"]
    require(type(ratio) in (int, float) and 0 < ratio <= 1, "invalid minimumCountRatio")
    router = value["openRouter"]
    exact_keys(router, {"maxModelsPerVendor", "vendorNamespaces"}, "OpenRouter policy")
    require(type(router["maxModelsPerVendor"]) is int and 1 <= router["maxModelsPerVendor"] <= 5,
            "invalid OpenRouter per-vendor limit")
    vendors = router["vendorNamespaces"]
    require(type(vendors) is dict and 1 <= len(vendors) <= 25, "invalid OpenRouter vendor count")
    namespaces = []
    for vendor, prefixes in vendors.items():
        require(re.fullmatch(r"[a-z][a-z0-9-]*", vendor) is not None, "invalid OpenRouter vendor name")
        require(type(prefixes) is list and 1 <= len(prefixes) <= 4 and
                all(type(prefix) is str and re.fullmatch(r"[a-z][a-z0-9-]*", prefix) for prefix in prefixes),
                "invalid OpenRouter namespaces")
        namespaces.extend(prefixes)
    require(len(set(namespaces)) == len(namespaces), "duplicate OpenRouter namespace")
    exact_keys(value["providers"], set(PROVIDERS), "policy providers")
    for provider, entry in value["providers"].items():
        exact_keys(entry, {"maxModels", "pinnedModelIds", "fallbackDefaultModelIds"}, f"{provider} policy")
        require(type(entry["maxModels"]) is int and 1 <= entry["maxModels"] <= 128, "invalid maxModels")
        model_list(entry["pinnedModelIds"], "pinnedModelIds", minimum=0)
        model_list(entry["fallbackDefaultModelIds"], "fallbackDefaultModelIds")
        require(len(entry["pinnedModelIds"]) < entry["maxModels"], "pinned models leave no discovery capacity")
    require(len(vendors) * router["maxModelsPerVendor"] <= value["providers"]["openrouter"]["maxModels"],
            "OpenRouter total limit would displace configured vendors")
    for vendor, prefixes in vendors.items():
        count = sum(model.partition("/")[0] in prefixes
                    for model in value["providers"]["openrouter"]["pinnedModelIds"])
        require(count < router["maxModelsPerVendor"], f"{vendor} pins leave no default/discovery capacity")
    return value


def trusted_url(url: str) -> bool:
    parts = urlsplit(url)
    if parts.scheme != "https" or parts.username or parts.password or parts.port or parts.query or parts.fragment:
        return False
    if url in {OPENAI_INDEX, OPENAI_DEPRECATIONS, ANTHROPIC_INDEX, ANTHROPIC_STATUS,
               GEMINI_INDEX, DEEPSEEK_INDEX, OPENROUTER_INDEX}:
        return True
    if parts.netloc == "developers.openai.com":
        return re.fullmatch(r"/api/docs/models/[a-z0-9.-]+\.md", parts.path) is not None
    if parts.netloc == "ai.google.dev":
        return re.fullmatch(r"/gemini-api/docs/models/[a-z0-9.-]+\.md\.txt", parts.path) is not None
    return False


class TrustedRedirects(HTTPRedirectHandler):
    max_redirections = 3

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        require(trusted_url(newurl), "source redirected outside the trusted catalog paths")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch_text(url: str) -> str:
    require(trusted_url(url), "untrusted model source URL")
    for attempt in range(2):
        try:
            request = Request(url, headers={
                "User-Agent": "Three-Stone-AI-Model-Catalog/1.0",
                "Accept": "application/json, text/markdown, text/plain, text/html",
                "Accept-Encoding": "identity",
            })
            with build_opener(TrustedRedirects()).open(request, timeout=20) as response:
                require(response.status == 200, f"source HTTP {response.status}")
                raw = response.read(MAX_SOURCE_BYTES + 1)
                require(len(raw) <= MAX_SOURCE_BYTES, "model source too large")
                return raw.decode("utf-8", errors="strict")
        except HTTPError as error:
            if attempt == 0 and error.code in (429, 500, 502, 503, 504):
                continue
            raise CatalogError(f"source HTTP {error.code}: {url}") from error
        except (URLError, TimeoutError, OSError) as error:
            if attempt == 0:
                continue
            raise CatalogError(f"source unavailable ({type(error).__name__}): {url}") from error
    raise AssertionError("unreachable")


def markdown(text: str) -> str:
    require(bool(text.strip()) and not re.search(r"<!doctype\s+html|<html\b", text[:2000], re.I),
            "expected Markdown, received an empty page or HTML")
    return text


def cells(line: str) -> list[str]:
    return [part.strip().replace(r"\|", "|") for part in re.split(r"(?<!\\)\|", line.strip().strip("|"))]


def tables(text: str) -> list[tuple[list[str], list[list[str]]]]:
    result = []
    lines = text.splitlines()
    for index, line in enumerate(lines[:-1]):
        if not line.lstrip().startswith("|"):
            continue
        separator = cells(lines[index + 1])
        if not separator or not all(re.fullmatch(r":?-{3,}:?", cell) for cell in separator):
            continue
        header = cells(line)
        require(len(separator) == len(header), "malformed Markdown table header")
        rows = []
        for row in lines[index + 2:]:
            if not row.lstrip().startswith("|"):
                break
            values = cells(row)
            require(len(values) == len(header), "malformed Markdown table row")
            rows.append(values)
        result.append((header, rows))
    return result


def natural_key(value: str) -> tuple:
    return tuple((1, int(part)) if part.isdigit() else (0, part.lower())
                 for part in re.split(r"(\d+)", value))


EXCLUDED = re.compile(r"(?:^|[-_/])(?:audio|realtime|transcribe|transcription|tts|image|embedding|embed|moderation|"
                      r"research|live|translate|codex|cyber|rosalind|daybreak|oss|mythos|distill|guard|safeguard|rerank|reranker|content-safety)(?:[-_/]|$)", re.I)


def general_openai(model: str) -> bool:
    return bool(re.match(r"^(?:gpt-\d|o\d)", model)) and not EXCLUDED.search(model) and "chat" not in model


def retirement_date(value: str) -> date:
    value = re.sub(r"[\u2010-\u2015]", "-", value).replace("\u00a0", " ").strip()
    for pattern in ("%Y-%m-%d", "%b %d, %Y", "%B %d, %Y"):
        try:
            return datetime.strptime(value, pattern).date()
        except ValueError:
            pass
    raise CatalogError("unrecognized model retirement date")


def openai_retirements(text: str, today: date | None = None) -> tuple[set[str], dict[str, str]]:
    today = today or datetime.now(timezone.utc).date()
    retired, upcoming, seen = set(), {}, set()
    found = False
    for header, rows in tables(markdown(text)):
        if not header or header[0].lower() != "shutdown date":
            continue
        if len(header) < 2 or "model" not in header[1].lower():
            continue
        found = True
        for row in rows:
            # Only the model column: replacement IDs must not be deprecated too.
            models = re.findall(r"`([a-zA-Z0-9][a-zA-Z0-9._:/-]*)`", row[1])
            if not models:
                continue
            # An earliest possible date is not a confirmed shutdown date. Newer
            # announcements appear first, so an older superseded date cannot win.
            if re.search(r"earliest|not sooner|to be announced|\bTBA\b|\bTBD\b", row[0], re.I):
                continue
            models = [model for model in models if model not in seen]
            if not models:
                continue
            shutdown = retirement_date(row[0])
            seen.update(models)
            if shutdown <= today:
                retired.update(models)
            else:
                upcoming.update({model: shutdown.isoformat() for model in models})
    require(found and (retired or upcoming), "OpenAI deprecation tables missing")
    return retired, upcoming


def parse_openai_detail(text: str, expected_slug: str, deprecated: set[str]) -> str | None:
    text = markdown(text)
    match = re.search(r"^Model ID:\s*`([^`]+)`\s*$", text, re.M)
    require(match is not None and valid_id(match[1]), "OpenAI detail missing Model ID")
    model = match[1]
    require(model == expected_slug, "OpenAI detail Model ID differs from catalog link")
    snapshot = re.search(r"^- Default snapshot:\s*`([^`]+)`", text, re.M)
    require(snapshot is not None, f"OpenAI {model} missing default snapshot")
    if model in deprecated or snapshot[1] in deprecated:
        return None
    if re.search(r"\b(?:approved organizations|limited access|restricted access|trusted access)\b", text, re.I):
        return None
    output = re.search(r"^- Output modalities:\s*([^\n]+)", text, re.M)
    require(output is not None, f"OpenAI {model} missing output modalities")
    endpoint_rows = [rows for header, rows in tables(text) if header == ["Endpoint", "Route", "Support"]]
    require(len(endpoint_rows) == 1, f"OpenAI {model} missing endpoints table")
    chat = [row for row in endpoint_rows[0] if row[1].strip("`") == "v1/chat/completions"]
    require(len(chat) == 1 and chat[0][2] in {"Supported", "Not supported"}, f"OpenAI {model} missing chat support")
    features = re.search(r"^## Supported features\s*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    require(features is not None, f"OpenAI {model} missing features section")
    if output[1].strip().lower() != "text" or chat[0][2] != "Supported":
        return None
    return model if re.search(r"^\s*- streaming\s*$", features[1], re.M) else None


def discover_openai(fetch: Fetch) -> list[str]:
    index = markdown(fetch(OPENAI_INDEX))
    deprecated, upcoming = openai_retirements(fetch(OPENAI_DEPRECATIONS))
    slugs = set(re.findall(r"\]\((?:https://developers\.openai\.com)?/api/docs/models/([a-z0-9.-]+)\.md\)", index))
    require(slugs, "OpenAI catalog links missing")
    slugs = sorted(slug for slug in slugs if general_openai(slug))
    require(0 < len(slugs) <= MAX_DETAIL_PAGES, "unexpected OpenAI detail page count")

    def detail(slug: str) -> str | None:
        return parse_openai_detail(fetch(f"https://developers.openai.com/api/docs/models/{slug}.md"), slug, deprecated)

    with ThreadPoolExecutor(max_workers=4) as pool:
        return DiscoveredModels([model for model in pool.map(detail, slugs) if model is not None],
                                unavailable={model: "retired" for model in deprecated},
                                upcoming={model: day for model, day in upcoming.items() if general_openai(model)})


def discover_anthropic(fetch: Fetch) -> list[str]:
    overview = tables(markdown(fetch(ANTHROPIC_INDEX)))
    rows = [row for _, values in overview for row in values]
    ids = [row[1:] for row in rows if row[0] == "Claude API ID"]
    aliases = [row[1:] for row in rows if row[0] == "Claude API alias"]
    require(len(ids) == len(aliases) == 1 and len(ids[0]) == len(aliases[0]), "Anthropic ID/alias rows missing")
    mapping = {}
    for fixed, alias in zip(ids[0], aliases[0]):
        fixed, alias = fixed.strip("`"), alias.strip("`")
        require(valid_id(fixed) and valid_id(alias), "invalid Anthropic overview ID")
        mapping[fixed] = alias
    status_tables = [(header, values) for header, values in tables(markdown(fetch(ANTHROPIC_STATUS)))
                     if header[:2] == ["API model name", "Current state"]]
    require(len(status_tables) == 1 and status_tables[0][1], "Anthropic status table missing")
    active = []
    for row in status_tables[0][1]:
        model, state = row[0].strip("`"), row[1]
        require(valid_id(model) and state in {"Active", "Legacy", "Deprecated", "Retired"}, "invalid Anthropic lifecycle row")
        if state == "Active" and model.startswith("claude-") and not EXCLUDED.search(model):
            active.append(mapping.get(model, model))
    require(all(alias in active or EXCLUDED.search(alias) for alias in mapping.values()),
            "Anthropic overview contradicts lifecycle table")
    return active


GEMINI_CHAT = re.compile(r"gemini-\d+(?:\.\d+)*-(?:pro|flash(?:-lite)?)(?:-(?:preview|exp)(?:-\d+(?:-\d+)*)?)?\Z")


def parse_gemini_index(text: str) -> dict[str, str]:
    text = markdown(text)
    candidates = {}
    sections = re.split(r"(?m)^## ", text)
    for section in sections[1:]:
        heading = section.splitlines()[0]
        if not re.match(r"Gemini \d", heading):
            continue
        if re.search(r"limiting access|restricted access|only available to|users who have actively", section, re.I):
            continue
        for header, rows in tables(section):
            if "Endpoint" not in header or "Model" not in header:
                continue
            for row in rows:
                endpoint = row[header.index("Endpoint")].strip("` ")
                label = row[header.index("Model")]
                if not GEMINI_CHAT.fullmatch(endpoint) or re.search(r"shut down|deprecated|discontinued", label, re.I):
                    continue
                link = re.search(r"\]\(https://ai\.google\.dev/gemini-api/docs/models/([a-z0-9.-]+)\)", label)
                require(link is not None, "Gemini endpoint missing detail link")
                candidates[endpoint] = f"https://ai.google.dev/gemini-api/docs/models/{link[1]}.md.txt"
    require(0 < len(candidates) <= MAX_DETAIL_PAGES, "Gemini general model tables missing or excessive")
    return candidates


def parse_gemini_detail(text: str, expected: str) -> str | None:
    text = markdown(text)
    rows = [row for _, values in tables(text) for row in values]
    codes = [row for row in rows if row[0].strip("* ") == "Model code"]
    types = [row for row in rows if row[0].strip("* ") == "Supported data types"]
    require(len(codes) == len(types) == 1, f"Gemini {expected} missing model specification")
    require(expected in re.findall(r"`+\s*([a-z0-9.-]+)\s*`+", " ".join(codes[0][1:])), "Gemini detail ID mismatch")
    data_types = " ".join(types[0][1:])
    output = re.search(r"\*\*Output(?:s)?\*\*\s*:?\s*(.*)", data_types, re.I)
    require(output is not None, "Gemini output modality missing")
    if re.search(r"limiting access|restricted access|only available to|users who have actively", text, re.I):
        return None
    return expected if output[1].strip().strip("* ").lower() == "text" else None


def discover_gemini(fetch: Fetch) -> list[str]:
    candidates = parse_gemini_index(fetch(GEMINI_INDEX))

    def detail(item: tuple[str, str]) -> str | None:
        model, url = item
        return parse_gemini_detail(fetch(url), model)

    with ThreadPoolExecutor(max_workers=4) as pool:
        return [model for model in pool.map(detail, candidates.items()) if model is not None]


class HtmlTableRows(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.rows: list[list[str]] = []
        self.row: list[str] | None = None
        self.cell: list[str] | None = None
        self.sup_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        elif tag in {"td", "th"} and self.row is not None:
            self.cell = []
        elif tag == "sup":
            self.sup_depth += 1

    def handle_endtag(self, tag):
        if tag == "sup":
            self.sup_depth = max(0, self.sup_depth - 1)
        elif tag in {"td", "th"} and self.cell is not None and self.row is not None:
            self.row.append("".join(self.cell).strip())
            self.cell = None
        elif tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None

    def handle_data(self, data):
        if self.cell is not None and not self.sup_depth:
            self.cell.append(data)


def discover_deepseek(fetch: Fetch) -> list[str]:
    parser = HtmlTableRows()
    parser.feed(fetch(DEEPSEEK_INDEX))
    rows = [row for row in parser.rows if row and row[0].upper() == "MODEL"]
    require(len(rows) == 1 and len(rows[0]) > 1, "DeepSeek pricing MODEL row missing")
    result = rows[0][1:]
    require(all(valid_id(model) and model.startswith("deepseek-") for model in result), "invalid DeepSeek MODEL row")
    return [model for model in result if not EXCLUDED.search(model)]


def discover_openrouter(fetch: Fetch, router_policy: dict, today: date | None = None) -> list[str]:
    today = today or datetime.now(timezone.utc).date()
    value = load_json(fetch(OPENROUTER_INDEX))
    require(type(value) is dict and type(value.get("data")) is list and 0 < len(value["data"]) <= 10000,
            "invalid OpenRouter model list")
    candidates: dict[str, list[tuple[int, str]]] = {name: [] for name in router_policy["vendorNamespaces"]}
    namespaces = {prefix: vendor for vendor, prefixes in router_policy["vendorNamespaces"].items() for prefix in prefixes}
    seen = set()
    unavailable, upcoming = {}, {}
    for entry in value["data"]:
        require(type(entry) is dict and type(entry.get("id")) is str, "invalid OpenRouter model record")
        model = entry["id"]
        prefix, _, name = model.partition("/")
        if prefix not in namespaces:
            continue
        require(valid_id(model), "invalid selected OpenRouter ID")
        require(model not in seen, "duplicate OpenRouter model ID")
        seen.add(model)
        vendor = namespaces[prefix]
        if ":" in name or EXCLUDED.search(name):
            continue
        if prefix == "tencent" and re.match(r"hy-mt\d", name):
            continue  # Hunyuan's translation-only series, not general conversation.
        description = entry.get("description", "")
        require(type(description) is str, "invalid OpenRouter description")
        if re.search(r"\b(?:restricted access|limited access|only available to vetted)\b", description, re.I):
            continue
        if vendor == "openai" and not general_openai(name):
            continue
        if vendor == "google" and not GEMINI_CHAT.fullmatch(name):
            continue
        if vendor == "anthropic" and not name.startswith("claude-"):
            continue
        if vendor == "deepseek" and not name.startswith("deepseek-"):
            continue
        architecture = entry.get("architecture")
        require(type(architecture) is dict, "OpenRouter architecture missing")
        inputs, outputs = architecture.get("input_modalities"), architecture.get("output_modalities")
        require(type(inputs) is list and type(outputs) is list and
                all(type(item) is str for item in inputs + outputs), "invalid OpenRouter modalities")
        if "text" not in inputs or outputs != ["text"]:
            continue
        expiry = entry.get("expiration_date")
        require(expiry is None or (type(expiry) is str and re.fullmatch(r"\d{4}-\d{2}-\d{2}", expiry)),
                "invalid OpenRouter expiration_date")
        deprecated = entry.get("deprecated", False)
        require(type(deprecated) is bool, "invalid OpenRouter deprecated flag")
        if deprecated:
            unavailable[model] = "provider marks deprecated"
            continue
        if expiry is not None and retirement_date(expiry) <= today:
            unavailable[model] = "expired"
            continue
        if expiry is not None:
            upcoming[model] = expiry
        created = entry.get("created")
        require(type(created) is int and created > 0, "invalid OpenRouter created timestamp")
        candidates[vendor].append((created, model))
    require(any(candidates.values()), "OpenRouter returned no suitable models")
    # Within each configured vendor, newest first, with deterministic natural-ID ties.
    return DiscoveredModels(
        [model for models in candidates.values()
         for _, model in sorted(models, key=lambda item: (item[0], natural_key(item[1])), reverse=True)],
        unavailable=unavailable, upcoming=upcoming,
    )


def select_models(found: list[str], previous: dict, policy: dict, minimum_ratio: float,
                  provider: str, router_policy: dict | None = None) -> dict:
    require(isinstance(found, list) and found and len(found) <= 1000, "empty or excessive discovered list")
    require(all(valid_id(model) for model in found), "invalid discovered model ID")
    require(len(set(found)) == len(found), "duplicate discovered model ID")
    available = set(found)
    require(len(found) >= math.ceil(len(previous["models"]) * minimum_ratio), "model count decreased beyond safety threshold")
    default = next((model for model in [previous["defaultModelId"]] + policy["fallbackDefaultModelIds"]
                    if model in available), None)
    def ordering(model):
        return (model.startswith("gpt-") if provider == "openai" else True, natural_key(model))

    ordered = sorted(available, key=ordering, reverse=True)
    default = default or ordered[0]
    pinned = [model for model in [default] + policy["pinnedModelIds"] if model in available]
    selected = list(dict.fromkeys(pinned))
    if provider == "openrouter":
        require(router_policy is not None, "OpenRouter vendor policy missing")
        selected = []
        for vendor, prefixes in router_policy["vendorNamespaces"].items():
            group = [model for model in found if model.partition("/")[0] in prefixes]
            previous_group = [model for model in previous["models"] if model.partition("/")[0] in prefixes]
            if previous_group and not group:
                evidence = getattr(found, "unavailable", {})
                require(all(model in evidence for model in previous_group),
                        f"OpenRouter vendor disappeared without retirement evidence: {vendor}")
            group_pins = list(dict.fromkeys(model for model in pinned if model in group))
            require(len(group_pins) <= router_policy["maxModelsPerVendor"], "too many pinned OpenRouter models")
            preferred = group
            if vendor == "openai":
                variants = [model for model in group if model.endswith("-pro") and model[:-4] in group]
                preferred = [model for model in group if model not in variants] + variants
            group_selected = (group_pins + [model for model in preferred if model not in group_pins])[:router_policy["maxModelsPerVendor"]]
            # Preserve source recency order, while pins retain older desired entries.
            selected.extend(model for model in group if model in group_selected)
        require(selected and default in selected, "OpenRouter selection omitted default model")
        return {"defaultModelId": default, "models": selected}
    for model in ordered:
        if model not in selected and len(selected) < policy["maxModels"]:
            selected.append(model)
    return {"defaultModelId": default, "models": sorted(selected, key=ordering, reverse=True)}


def collectors(fetch: Fetch, policy: dict) -> dict[str, Callable[[], list[str]]]:
    result = {name: (lambda fn=fn: fn(fetch)) for name, fn in (
        ("openai", discover_openai), ("anthropic", discover_anthropic), ("gemini", discover_gemini),
        ("deepseek", discover_deepseek),
    )}
    result["openrouter"] = lambda: discover_openrouter(fetch, policy["openRouter"])
    return result


def refresh(previous_raw: bytes, policy: dict, sources: dict[str, Callable[[], list[str]]],
            now_ms: int | None = None) -> tuple[bytes, dict]:
    previous = validate_catalog(previous_raw)
    require(set(sources) == set(PROVIDERS), "incomplete collectors")
    updated = dict(previous["providers"])
    report = {"changed": False, "providers": {}, "errors": []}
    with ThreadPoolExecutor(max_workers=len(PROVIDERS)) as pool:
        futures = {provider: pool.submit(sources[provider]) for provider in PROVIDERS}
        for provider in PROVIDERS:
            old = previous["providers"][provider]
            try:
                found = futures[provider].result()
                new = select_models(found, old, policy["providers"][provider], policy["minimumCountRatio"], provider,
                                    policy["openRouter"])
                updated[provider] = new
                report["providers"][provider] = {
                    "status": "updated" if new != old else "unchanged", "count": len(new["models"]),
                }
                if getattr(found, "upcoming", None):
                    report["providers"][provider]["upcomingRetirements"] = found.upcoming
                if getattr(found, "unavailable", None):
                    report["providers"][provider]["excludedModels"] = found.unavailable
                if provider == "openrouter":
                    counts = {vendor: sum(model.partition("/")[0] in prefixes for model in new["models"])
                              for vendor, prefixes in policy["openRouter"]["vendorNamespaces"].items()}
                    report["providers"][provider]["vendorCounts"] = counts
                    report["providers"][provider]["missingVendors"] = [vendor for vendor, count in counts.items() if not count]
            except Exception as error:
                # A failed source is never evidence for removing previous entries.
                detail = f"{type(error).__name__}: {error}"
                report["providers"][provider] = {"status": "retained", "count": len(old["models"]), "error": detail}
                report["errors"].append({"provider": provider, "error": detail})
    if updated == previous["providers"]:
        return previous_raw, report
    require(previous["revision"] < MAX_LONG, "catalog revision exhausted")
    new = {"schemaVersion": 1, "revision": previous["revision"] + 1,
           "updatedAtEpochMillis": max(now_ms if now_ms is not None else int(time.time() * 1000),
                                       previous["updatedAtEpochMillis"] + 1), "providers": updated}
    raw = (json.dumps(new, ensure_ascii=True, indent=2) + "\n").encode("utf-8")
    validate_catalog(raw)
    report["changed"] = True
    return raw, report


def atomic_write(path: Path, raw: bytes) -> None:
    if path.exists() and path.read_bytes() == raw:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=f".{path.name}.", delete=False) as target:
            temporary = Path(target.name)
            target.write(raw)
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate-only", type=Path, metavar="PATH")
    parser.add_argument("--previous", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    args = parser.parse_args(argv)
    try:
        if args.validate_only is not None:
            require(args.previous is None and args.output is None and args.report is None,
                    "--validate-only cannot be combined with refresh paths")
            validate_catalog(args.validate_only.read_bytes())
            print("Model catalog is valid.")
            return 0
        require(all((args.previous, args.output, args.report)), "--previous, --output and --report are required")
        require(args.report.resolve() not in {args.previous.resolve(), args.output.resolve()},
                "report path must differ from catalog paths")
        policy = validate_policy(args.policy.read_bytes())
        raw, report = refresh(args.previous.read_bytes(), policy, collectors(fetch_text, policy))
        atomic_write(args.output, raw)
        atomic_write(args.report, (json.dumps(report, ensure_ascii=True, indent=2) + "\n").encode("utf-8"))
        print(json.dumps({"changed": report["changed"], "errors": len(report["errors"])}))
        return 0
    except (CatalogError, ValueError, OSError) as error:
        print(f"Model catalog refresh failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
