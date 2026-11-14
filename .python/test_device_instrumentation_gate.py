import json
import unittest

from device_instrumentation_gate import compare, content_digest, run_as_problem, signer_problem, summarize

RUN = """INSTRUMENTATION_STATUS: class=x.SettingsNavigationTest
INSTRUMENTATION_STATUS: test=opensTheFiveEntries
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: test=opensTheFiveEntries
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: test=needsApi29
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: test=needsApi29
INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: WindowInspector needs API 29
INSTRUMENTATION_STATUS_CODE: -4
INSTRUMENTATION_RESULT: stream=
OK (2 tests)
INSTRUMENTATION_CODE: -1
"""


class SummarizeTest(unittest.TestCase):
    def test_a_pass_and_a_skip(self):
        passed, counts, line = summarize("SettingsNavigationTest", RUN)
        self.assertTrue(passed)
        self.assertEqual([1, 0, 1], counts)
        self.assertEqual("PASS SettingsNavigationTest: 1 passed, 0 failed, 1 skipped", line)

    def test_a_failure_names_the_test_and_the_start_of_its_stack(self):
        output = RUN.replace(
            "INSTRUMENTATION_STATUS: test=opensTheFiveEntries\nINSTRUMENTATION_STATUS_CODE: 0",
            "INSTRUMENTATION_STATUS: test=opensTheFiveEntries\n"
            "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: no entry\n"
            "INSTRUMENTATION_STATUS_CODE: -2")
        passed, counts, line = summarize("SettingsNavigationTest", output)
        self.assertFalse(passed)
        self.assertEqual([0, 1, 1], counts)
        self.assertTrue(line.endswith("first failure opensTheFiveEntries: java.lang.AssertionError: no entry"), line)

    def test_an_unfinished_run_fails(self):
        passed, _, line = summarize("SettingsNavigationTest", RUN.replace("INSTRUMENTATION_CODE: -1\n", ""))
        self.assertFalse(passed)
        self.assertIn("did not finish", line)

    def test_a_class_where_everything_was_skipped_fails(self):
        output = "INSTRUMENTATION_STATUS: test=a\nINSTRUMENTATION_STATUS_CODE: 1\n" \
                 "INSTRUMENTATION_STATUS: test=a\nINSTRUMENTATION_STATUS_CODE: -4\nINSTRUMENTATION_CODE: -1\n"
        passed, counts, line = summarize("EngineCacheCleanupDeviceTest", output)
        self.assertFalse(passed)
        self.assertEqual([0, 0, 1], counts)
        self.assertIn("no test passed", line)


class CompareTest(unittest.TestCase):
    def state(self, services, history="h1"):
        return {
            "services": "digest-" + services,
            "services (content)": content_digest(services),
            "conversation history": history,
            "prefs model_picker": "p1",
        }

    def test_a_rewrite_with_a_higher_revision_and_the_same_content_is_kept(self):
        before = json.dumps({"schemaVersion": 4, "revision": 7, "defaultProfileId": "a", "profiles": [{"profileId": "a"}]})
        after = json.dumps({"profiles": [{"profileId": "a"}], "revision": 9, "defaultProfileId": "a", "schemaVersion": 4})
        same, lines = compare(self.state(before), self.state(after), "before")
        self.assertTrue(same)
        self.assertIn("  services: rewritten, same content compared with before", lines)

    def test_a_service_left_behind_is_reported(self):
        before = json.dumps({"revision": 7, "profiles": [{"profileId": "a"}]})
        after = json.dumps({"revision": 9, "profiles": [{"profileId": "a"}, {"profileId": "test"}]})
        same, lines = compare(self.state(before), self.state(after), "before")
        self.assertFalse(same)
        self.assertIn("  services: CHANGED compared with before", lines)

    def test_other_items_must_be_byte_identical(self):
        document = json.dumps({"revision": 1, "profiles": []})
        same, lines = compare(self.state(document), self.state(document, history="h2"), "before")
        self.assertFalse(same)
        self.assertIn("  conversation history: CHANGED compared with before", lines)

    def test_unreadable_documents_have_no_content_digest(self):
        self.assertIsNone(content_digest(""))
        self.assertIsNone(content_digest("run-as: package not debuggable"))


class SignerTest(unittest.TestCase):
    def test_matching_release_signers_pass(self):
        self.assertIsNone(signer_problem({"app": ("31a6", False), "test": ("31a6", False)}))

    def test_the_android_debug_key_is_refused(self):
        self.assertIn("debug key", signer_problem({"app": ("2e64", True), "test": ("2e64", True)}))

    def test_different_signers_are_refused(self):
        self.assertIn("different signers", signer_problem({"app": ("31a6", False), "test": ("9f00", False)}))


class RunAsTest(unittest.TestCase):
    def test_a_debuggable_install_is_readable(self):
        self.assertIsNone(run_as_problem("uid=10234(u0_a234) gid=10234(u0_a234) groups=10234(u0_a234)\n"))

    def test_a_release_install_is_refused_instead_of_reading_as_gone(self):
        # run-as prints "package not debuggable" on stderr; stdout stays empty.
        self.assertIn("firstInstallTime", run_as_problem(""))
        self.assertIn("not debuggable", run_as_problem("run-as: package not debuggable: example"))


if __name__ == "__main__":
    unittest.main()
