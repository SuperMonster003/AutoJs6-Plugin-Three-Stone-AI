import contextlib
import io
import unittest

from device_attachments_flow import report

PASSED = """INSTRUMENTATION_STATUS: class=x.ChatAttachmentRealDeviceFlowTest
INSTRUMENTATION_STATUS: test=listsTheOnlineTargets
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: test=listsTheOnlineTargets
INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: Not this case: pdf
INSTRUMENTATION_STATUS_CODE: -4
INSTRUMENTATION_STATUS: test=oneRequestToARealService
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: test=oneRequestToARealService
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=
OK (2 tests)
INSTRUMENTATION_CODE: -1
"""


class ReportTest(unittest.TestCase):
    def run_report(self, output):
        printed = io.StringIO()
        with contextlib.redirect_stdout(printed):
            result = report("pdf model", output)
        return result, printed.getvalue()

    def test_a_passing_request_counts_and_the_skipped_case_is_not_printed(self):
        passed, printed = self.run_report(PASSED)
        self.assertTrue(passed)
        self.assertEqual("PASS pdf model (oneRequestToARealService)\n", printed)

    def test_a_failure_is_printed_with_the_start_of_its_stack(self):
        output = PASSED.replace(
            "INSTRUMENTATION_STATUS: test=oneRequestToARealService\nINSTRUMENTATION_STATUS_CODE: 0",
            "INSTRUMENTATION_STATUS: test=oneRequestToARealService\n"
            "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: pdf: the reply did not complete\n"
            "INSTRUMENTATION_STATUS_CODE: -2",
        )
        passed, printed = self.run_report(output)
        self.assertFalse(passed)
        self.assertIn("FAIL pdf model (oneRequestToARealService): java.lang.AssertionError", printed)

    def test_a_run_where_every_test_was_skipped_fails(self):
        output = PASSED.replace(
            "INSTRUMENTATION_STATUS: test=oneRequestToARealService\nINSTRUMENTATION_STATUS_CODE: 0",
            "INSTRUMENTATION_STATUS: test=oneRequestToARealService\nINSTRUMENTATION_STATUS_CODE: -4",
        )
        passed, printed = self.run_report(output)
        self.assertFalse(passed)
        self.assertIn("no test ran", printed)

    def test_an_unfinished_instrumentation_fails(self):
        passed, printed = self.run_report("INSTRUMENTATION_STATUS: test=oneRequestToARealService\n"
                                          "INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_FAILED: crashed")
        self.assertFalse(passed)
        self.assertIn("did not finish", printed)


if __name__ == "__main__":
    unittest.main()
