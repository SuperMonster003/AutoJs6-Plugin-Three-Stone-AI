import unittest

from device_opencode_go_acceptance import verdicts


class VerdictsTest(unittest.TestCase):
    def test_finished_tests_get_their_verdict_and_stack(self):
        output = "\n".join([
            "INSTRUMENTATION_STATUS: test=openAiCompatibleEndpointNeedsTheSessionHeader",
            "INSTRUMENTATION_STATUS_CODE: 1",
            "INSTRUMENTATION_STATUS: test=openAiCompatibleEndpointNeedsTheSessionHeader",
            "INSTRUMENTATION_STATUS_CODE: 0",
            "INSTRUMENTATION_STATUS: test=googleEndpointNeedsTheSessionHeader",
            "INSTRUMENTATION_STATUS_CODE: 1",
            "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: google: HTTP status expected:<401> but was:<400>",
            "INSTRUMENTATION_STATUS: test=googleEndpointNeedsTheSessionHeader",
            "INSTRUMENTATION_STATUS_CODE: -2",
            "INSTRUMENTATION_CODE: -1",
        ])
        found = verdicts(output)
        self.assertEqual(
            [("openAiCompatibleEndpointNeedsTheSessionHeader", "PASS", None),
             ("googleEndpointNeedsTheSessionHeader", "FAIL",
              "java.lang.AssertionError: google: HTTP status expected:<401> but was:<400>")],
            found,
        )

    def test_skipped_tests_and_empty_output(self):
        output = "\n".join([
            "INSTRUMENTATION_STATUS: test=anthropicEndpointNeedsTheSessionHeader",
            "INSTRUMENTATION_STATUS_CODE: 1",
            "INSTRUMENTATION_STATUS: test=anthropicEndpointNeedsTheSessionHeader",
            "INSTRUMENTATION_STATUS_CODE: -3",
        ])
        self.assertEqual([("anthropicEndpointNeedsTheSessionHeader", "SKIPPED", None)], verdicts(output))
        self.assertEqual([], verdicts(""))


if __name__ == "__main__":
    unittest.main()
