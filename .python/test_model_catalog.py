"""Offline regression checks for source changes, data loss, and catalog validation."""

import copy
from datetime import date
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import update_model_catalog as catalog


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = Path(__file__).parent / "fixtures/model-catalog"


def fixture(name):
    return (FIXTURES / name).read_text(encoding="utf-8")


def seed():
    return {"schemaVersion": 1, "revision": 7, "updatedAtEpochMillis": 100,
            "providers": {provider: {"defaultModelId": f"{provider}-old", "models": [f"{provider}-old"]}
                          for provider in catalog.PROVIDERS} | {
                "openrouter": {"defaultModelId": "openai/gpt-5.6-sol", "models": ["openai/gpt-5.6-sol"]}}}


def encode(value):
    return json.dumps(value, indent=4).encode("utf-8") + b"\r\n"


def policy():
    return catalog.validate_policy(catalog.DEFAULT_POLICY.read_bytes())


def unchanged_sources(value):
    return {provider: lambda provider=provider: list(value["providers"][provider]["models"])
            for provider in catalog.PROVIDERS}


class CatalogValidationTest(unittest.TestCase):
    def test_seed_and_repository_asset_satisfy_same_contract(self):
        self.assertEqual(catalog.decode_catalog(encode(seed())), seed())
        catalog.decode_catalog((ROOT / "app/src/main/assets/online-models.json").read_bytes())

    def test_rejects_wrong_types_unknown_keys_and_unsafe_ids(self):
        mutations = [
            lambda d: d.update(schemaVersion=True),
            lambda d: d.update(revision=True),
            lambda d: d.update(revision=0),
            lambda d: d.update(revision=catalog.MAX_LONG + 1),
            lambda d: d.update(updatedAtEpochMillis=1.5),
            lambda d: d.update(extra="no"),
            lambda d: d["providers"].update(custom={}),
            lambda d: d["providers"].pop("gemini"),
            lambda d: d["providers"]["openai"].update(models=[]),
            lambda d: d["providers"]["openai"].update(models=["same", "same"]),
            lambda d: d["providers"]["openai"].update(models=["模型"]),
            lambda d: d["providers"]["openai"].update(models=["a" * 257]),
            lambda d: d["providers"]["openai"].update(models=["bad\nmodel"]),
            lambda d: d["providers"]["openai"].update(models=["/bad"]),
            lambda d: d["providers"]["openai"].update(models=[f"m{i}" for i in range(129)]),
            lambda d: d["providers"]["openai"].update(defaultModelId="absent"),
            lambda d: d["providers"]["openai"].update(unknown=True),
        ]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                document = seed()
                mutation(document)
                with self.assertRaises(catalog.CatalogError):
                    catalog.decode_catalog(encode(document))

    def test_duplicate_json_keys_nonfinite_numbers_and_size_are_rejected(self):
        raw = encode(seed())
        for bad in (raw.replace(b'"revision": 7', b'"revision": 7, "revision": 8'),
                    raw.replace(b'"revision": 7', b'"revision": NaN'),
                    raw.replace(b'"models": [', b'"models": [], "models": [', 1),
                    b" " * (catalog.MAX_CATALOG_BYTES + 1)):
            with self.subTest(raw=bad[:60]), self.assertRaises((ValueError, catalog.CatalogError)):
                catalog.decode_catalog(bad)

    def test_validate_only_is_read_only_and_never_fetches(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            raw = encode(seed())
            path.write_bytes(raw)
            with patch.object(catalog, "fetch_text", side_effect=AssertionError("network forbidden")):
                self.assertEqual(catalog.main(["--validate-only", str(path)]), 0)
            self.assertEqual(path.read_bytes(), raw)

    def test_cli_rejects_invalid_seed_before_any_output(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source, output, report = (directory / name for name in ("input", "output", "report"))
            source.write_text("{}", encoding="utf-8")
            process = subprocess.run([sys.executable, str(Path(catalog.__file__)), "--previous", str(source),
                                      "--output", str(output), "--report", str(report)], capture_output=True)
            self.assertEqual(process.returncode, 1)
            self.assertFalse(output.exists())
            self.assertFalse(report.exists())


class SourceParserTest(unittest.TestCase):
    def test_openai_discovers_new_models_but_not_examples_media_or_responses_only(self):
        detail = fixture("openai-detail.md")
        pages = {
            catalog.OPENAI_INDEX: fixture("openai-index.md"),
            catalog.OPENAI_DEPRECATIONS: fixture("openai-deprecations.md"),
            "https://developers.openai.com/api/docs/models/gpt-10.md": detail,
            "https://developers.openai.com/api/docs/models/gpt-9-pro.md": detail.replace("gpt-10", "gpt-9-pro")
                .replace("`v1/chat/completions` | Supported", "`v1/chat/completions` | Not supported"),
            "https://developers.openai.com/api/docs/models/o4-mini.md": detail.replace("gpt-10", "o4-mini"),
        }
        self.assertEqual(catalog.discover_openai(pages.__getitem__), ["gpt-10"])
        self.assertNotIn("gpt-10", catalog.openai_retirements(fixture("openai-deprecations.md"))[0])

    def test_openai_requires_exact_endpoint_evidence_not_substring(self):
        detail = fixture("openai-detail.md")
        self.assertIsNone(catalog.parse_openai_detail(detail.replace("| Supported |", "| Not supported |"), "gpt-10", set()))
        self.assertIsNone(catalog.parse_openai_detail(detail.replace("- streaming", "- streaming_audio"), "gpt-10", set()))
        with self.assertRaises(catalog.CatalogError):
            catalog.parse_openai_detail(detail.replace("## Endpoints", "## Removed").replace("| Route |", "| Path |"), "gpt-10", set())
        with self.assertRaises(catalog.CatalogError):
            catalog.parse_openai_detail(detail, "gpt-11", set())

    def test_anthropic_maps_current_aliases_and_excludes_retired_and_gated(self):
        pages = {catalog.ANTHROPIC_INDEX: fixture("anthropic-overview.md"),
                 catalog.ANTHROPIC_STATUS: fixture("anthropic-status.md")}
        self.assertEqual(catalog.discover_anthropic(pages.__getitem__),
                         ["claude-opus-6-2", "claude-opus-5-5", "claude-haiku-4-5"])
        pages[catalog.ANTHROPIC_STATUS] = pages[catalog.ANTHROPIC_STATUS].replace("claude-opus-6-2 | Active", "claude-opus-6-2 | Retired")
        with self.assertRaises(catalog.CatalogError):
            catalog.discover_anthropic(pages.__getitem__)

    def test_gemini_endpoint_column_and_restricted_sections_control_discovery(self):
        pages = {catalog.GEMINI_INDEX: fixture("gemini-index.md"),
                 "https://ai.google.dev/gemini-api/docs/models/gemini-4-flash.md.txt": fixture("gemini-detail.md")}
        self.assertEqual(catalog.discover_gemini(pages.__getitem__), ["gemini-4-flash"])
        self.assertIsNone(catalog.parse_gemini_detail(fixture("gemini-detail.md").replace("**Output** Text", "**Output** Audio"), "gemini-4-flash"))

    def test_deepseek_ignores_superscript_versions_and_retired_alias_footnotes(self):
        self.assertEqual(catalog.discover_deepseek(lambda _: fixture("deepseek-pricing.html")),
                         ["deepseek-flash", "deepseek-v5-pro"])
        with self.assertRaises(catalog.CatalogError):
            catalog.discover_deepseek(lambda _: fixture("deepseek-pricing.html").replace(">MODEL<", ">MODEL IDENTIFIER<"))

    def test_openrouter_uses_architecture_and_provider_expiration(self):
        def entry(model, outputs=None, expiration=None):
            return {"id": model, "created": 100, "architecture": {"input_modalities": ["text"], "output_modalities": outputs or ["text"]},
                    "expiration_date": expiration}

        accepted = ["openai/gpt-10", "anthropic/claude-opus-6", "google/gemini-4-flash", "deepseek/deepseek-v5-pro"]
        rows = [entry(model) for model in accepted] + [
            entry("other/model"), entry("openai/gpt-10:batch"), entry("openai/gpt-10-image"),
            entry("google/gemini-2-flash", expiration="2020-01-01"),
            entry("anthropic/claude-mythos-9"), entry("openai/gpt-9", outputs=["audio"]),
            entry("nvidia/nemotron-3.5-content-safety"), entry("tencent/hy-mt2-30b-a3b"),
        ]
        rows.append(entry("~openai/gpt-experimental"))
        self.assertEqual(catalog.discover_openrouter(lambda _: json.dumps({"data": rows}), policy()["openRouter"]), accepted)
        rows.append(copy.deepcopy(rows[0]))
        with self.assertRaises(catalog.CatalogError):
            catalog.discover_openrouter(lambda _: json.dumps({"data": rows}), policy()["openRouter"])

    def test_changed_markup_and_html_soft_404_do_not_become_empty_successes(self):
        for text in ("<!doctype html><html>Moved</html>", "", "# Something else"):
            with self.subTest(text=text):
                with self.assertRaises(catalog.CatalogError):
                    catalog.parse_gemini_index(text)
                with self.assertRaises(catalog.CatalogError):
                    catalog.openai_retirements(text)

    def test_future_announced_shutdown_does_not_remove_a_serving_model(self):
        retired, upcoming = catalog.openai_retirements(
            fixture("openai-deprecations.md").replace("October 23, 2020", "October 23, 2099"), date(2026, 9, 26))
        self.assertEqual(retired, set())
        self.assertEqual(upcoming["o4-mini"], "2099-10-23")
        self.assertEqual(catalog.parse_openai_detail(fixture("openai-detail.md").replace("gpt-10", "o4-mini"),
                                                   "o4-mini", retired), "o4-mini")
        entry = {"id": "baidu/ernie-future", "created": 100, "expiration_date": "2099-01-01",
                 "architecture": {"input_modalities": ["text"], "output_modalities": ["text"]}}
        models = catalog.discover_openrouter(lambda _: json.dumps({"data": [entry]}), policy()["openRouter"], date(2026, 9, 26))
        self.assertEqual(models, ["baidu/ernie-future"])
        self.assertEqual(models.upcoming, {"baidu/ernie-future": "2099-01-01"})

    def test_retirement_postponements_and_earliest_dates_are_not_actual_shutdowns(self):
        document = """| Shutdown date | Model snapshot | Recommended replacement |
| --- | --- | --- |
| 2099-01-01 | `gpt-old` | `gpt-new` |
| 2025\u201103\u201126 | `gpt-retired` | `gpt-new` |
| 2020-01-01 | `gpt-old` | `gpt-new` |
| at earliest 2020-01-01 | `gpt-tentative` | `gpt-new` |
"""
        retired, upcoming = catalog.openai_retirements(document, date(2026, 9, 26))
        self.assertEqual(retired, {"gpt-retired"})
        self.assertEqual(upcoming, {"gpt-old": "2099-01-01"})

    def test_trust_boundary_rejects_other_hosts_credentials_queries_and_paths(self):
        self.assertTrue(catalog.trusted_url(catalog.OPENAI_INDEX))
        self.assertTrue(catalog.trusted_url("https://developers.openai.com/api/docs/models/gpt-99.md"))
        for url in ("http://developers.openai.com/api/docs/models/gpt-99.md", "https://evil.example/models",
                    "https://developers.openai.com@evil.example/api/docs/models/gpt-99.md",
                    "https://developers.openai.com/api/docs/models/gpt-99.md?token=anything",
                    "https://developers.openai.com/api/docs/models/../other.md",
                    "https://developers.openai.com/api/docs/models/gpt-99.md#anchor"):
            self.assertFalse(catalog.trusted_url(url), url)


class RefreshBehaviorTest(unittest.TestCase):
    def test_no_change_preserves_original_bytes_revision_and_timestamp(self):
        value = seed()
        raw = encode(value)
        output, report = catalog.refresh(raw, policy(), unchanged_sources(value), now_ms=9999)
        self.assertEqual(output, raw)
        self.assertFalse(report["changed"])
        self.assertEqual(report["errors"], [])

    def test_partial_failure_keeps_old_provider_while_new_provider_advances_once(self):
        value = seed()
        sources = unchanged_sources(value)
        sources["openai"] = lambda: ["gpt-10", "gpt-9", "openai-old"]
        sources["anthropic"] = lambda: (_ for _ in ()).throw(TimeoutError("fixture unavailable"))
        output, report = catalog.refresh(encode(value), policy(), sources, now_ms=999)
        updated = catalog.decode_catalog(output)
        self.assertEqual(updated["revision"], 8)
        self.assertEqual(updated["updatedAtEpochMillis"], 999)
        self.assertEqual(updated["providers"]["openai"]["defaultModelId"], "openai-old")
        self.assertEqual(updated["providers"]["anthropic"], value["providers"]["anthropic"])
        self.assertEqual(report["providers"]["anthropic"]["status"], "retained")
        output2, report2 = catalog.refresh(output, policy(), sources, now_ms=1000)
        self.assertEqual(output2, output)
        self.assertFalse(report2["changed"])

    def test_all_failed_or_empty_sources_never_overwrite_last_good_catalog(self):
        value = seed()
        sources = {provider: lambda: [] for provider in catalog.PROVIDERS}
        output, report = catalog.refresh(encode(value), policy(), sources)
        self.assertEqual(output, encode(value))
        self.assertFalse(report["changed"])
        self.assertEqual(len(report["errors"]), 5)

    def test_suspicious_mass_removal_retains_old_data(self):
        value = seed()
        value["providers"]["openai"]["models"] += [f"gpt-{number}" for number in range(10)]
        sources = unchanged_sources(value)
        sources["openai"] = lambda: ["gpt-9"]
        output, report = catalog.refresh(encode(value), policy(), sources)
        self.assertEqual(output, encode(value))
        self.assertEqual(report["providers"]["openai"]["status"], "retained")

    def test_known_default_survives_sorting_and_bounded_automatic_new_models(self):
        previous = {"defaultModelId": "gpt-5.6-sol", "models": ["gpt-5.6-sol"]}
        rules = {"maxModels": 4, "pinnedModelIds": ["gpt-4.1"], "fallbackDefaultModelIds": ["gpt-5.6-sol"]}
        found = ["gpt-5.6-sol", "gpt-9", "gpt-10", "gpt-4.1", "gpt-8", "gpt-7"]
        result = catalog.select_models(found, previous, rules, 0.5, "openai")
        self.assertEqual(result["defaultModelId"], "gpt-5.6-sol")
        self.assertEqual(result["models"], ["gpt-10", "gpt-9", "gpt-5.6-sol", "gpt-4.1"])
        self.assertEqual(result, catalog.select_models(list(reversed(found)), previous, rules, 0.5, "openai"))

    def test_retired_default_uses_known_safe_fallback_before_newest(self):
        previous = {"defaultModelId": "old", "models": ["old"]}
        rules = {"maxModels": 4, "pinnedModelIds": [], "fallbackDefaultModelIds": ["gpt-5.6-sol"]}
        result = catalog.select_models(["gpt-10", "gpt-5.6-sol"], previous, rules, 0.5, "openai")
        self.assertEqual(result["defaultModelId"], "gpt-5.6-sol")

    def test_openrouter_each_vendor_has_capacity_and_meta_namespaces_share_limit(self):
        rules = policy()
        found = [f"openai/gpt-{number}" for number in range(20, 1, -1)]
        found += [f"meta/muse-{number}" for number in range(7, 1, -1)]
        found += [f"meta-llama/llama-{number}" for number in range(5, 1, -1)]
        found += ["ibm-granite/granite-5", "baidu/ernie-9", "openai/gpt-5.6-sol"]
        previous = {"defaultModelId": "openai/gpt-5.6-sol", "models": ["openai/gpt-5.6-sol"]}
        selected = catalog.select_models(found, previous, rules["providers"]["openrouter"], 0.5,
                                         "openrouter", rules["openRouter"])
        self.assertEqual(sum(item.startswith("openai/") for item in selected["models"]), 5)
        self.assertEqual(sum(item.startswith(("meta/", "meta-llama/")) for item in selected["models"]), 5)
        self.assertIn("ibm-granite/granite-5", selected["models"])
        self.assertIn("baidu/ernie-9", selected["models"])
        self.assertIn("openai/gpt-5.6-sol", selected["models"])

    def test_openrouter_losing_one_small_vendor_cannot_hide_in_large_total(self):
        value = seed()
        value["providers"]["openrouter"]["models"].append("baidu/ernie-existing")
        sources = unchanged_sources(value)
        models = [f"openai/gpt-{number}" for number in range(6, 20)] + ["openai/gpt-5.6-sol"]
        sources["openrouter"] = lambda: catalog.DiscoveredModels(models)
        output, report = catalog.refresh(encode(value), policy(), sources)
        self.assertEqual(output, encode(value))
        self.assertIn("baidu", report["providers"]["openrouter"]["error"])
        # Explicit expiration is evidence to remove it; disappearance alone is not.
        sources["openrouter"] = lambda: catalog.DiscoveredModels(models, unavailable={"baidu/ernie-existing": "expired"})
        output, report = catalog.refresh(encode(value), policy(), sources)
        self.assertTrue(report["changed"])
        self.assertNotIn("baidu/ernie-existing", catalog.decode_catalog(output)["providers"]["openrouter"]["models"])

    def test_clock_rollback_does_not_reverse_revision_time(self):
        value = seed()
        sources = unchanged_sources(value)
        sources["deepseek"] = lambda: ["deepseek-new"]
        output, _ = catalog.refresh(encode(value), policy(), sources, now_ms=1)
        self.assertEqual(catalog.decode_catalog(output)["updatedAtEpochMillis"], 101)

    def test_atomic_write_skips_identical_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            catalog.atomic_write(path, b"original")
            before = path.stat().st_mtime_ns
            catalog.atomic_write(path, b"original")
            self.assertEqual(path.stat().st_mtime_ns, before)
            catalog.atomic_write(path, b"replacement")
            self.assertEqual(path.read_bytes(), b"replacement")
            self.assertEqual([file.name for file in path.parent.iterdir()], ["catalog.json"])


if __name__ == "__main__":
    unittest.main()
