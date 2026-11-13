import unittest

from device_engine_caches import catalog_file_names, classify, parse_listing

KEPT = "model-" + "a" * 64 + ".litertlm"
GONE = "model-" + "b" * 64 + ".litertlm"
NOW = 1_800_000_000


class ClassifyTest(unittest.TestCase):
    def test_entries_are_classified_like_the_plugin_policy(self):
        old = NOW - 3600
        entries = [
            (100, old, "regular file", f"{KEPT}_1763101680_2213000000.xnnpack_cache"),
            (200, old, "regular file", f"{GONE}_1763101680_1597931520.xnnpack_cache"),
            (300, old, "regular file", f"{GONE}_1763101680_1597931520_mldrift_weight_cache.bin"),
            (400, NOW - 60, "regular file", f"{GONE}_1763101999_1597931520_mldrift_program_cache.bin"),
            (500, old, "regular file", "gemma.litertlm_1_2.xnnpack_cache"),
            (600, old, "directory", f"{GONE}_1_2.xnnpack_cache"),
            (700, old, "regular file", f"{GONE}_1_2.vision_encoder.mtp_drafter"),
        ]

        result = classify(entries, {KEPT}, NOW)

        self.assertEqual(
            ["keep", "orphan", "orphan", "recent", "unrecognized", "not a regular file", "orphan"],
            [entry["status"] for entry in result],
        )
        self.assertEqual(
            ["CPU cache", "CPU cache", "GPU weight cache", "GPU program cache", "-", "CPU cache", "MTP drafter"],
            [entry["kind"] for entry in result],
        )
        self.assertEqual(2213000000, result[0]["embeddedModelSize"])
        self.assertEqual(1597931520, result[1]["embeddedModelSize"])
        self.assertIsNone(result[4]["embeddedModelSize"])

    def test_an_unreadable_catalog_keeps_every_cache(self):
        result = classify([(1, NOW - 3600, "regular file", f"{GONE}_1_2.xnnpack_cache")], None, NOW)
        self.assertEqual("keep", result[0]["status"])

    def test_the_catalog_gives_the_file_names_or_none(self):
        text = '{"schema":2,"revision":3,"selectedModelId":null,"entries":[{"modelId":"m","fileName":"%s"}]}' % KEPT
        self.assertEqual({KEPT}, catalog_file_names(text))
        self.assertEqual(set(), catalog_file_names('{"entries":[]}'))
        self.assertIsNone(catalog_file_names(""))
        self.assertIsNone(catalog_file_names('{"schema":2}'))

    def test_the_listing_skips_malformed_lines(self):
        text = f"12|1763101680|regular file|{KEPT}_1_2.xnnpack_cache\nstat: denied\n|x|y|z\n"
        self.assertEqual([(12, 1763101680, "regular file", f"{KEPT}_1_2.xnnpack_cache")], parse_listing(text))


if __name__ == "__main__":
    unittest.main()
