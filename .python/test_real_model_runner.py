import unittest

from run_real_model_test import require_test_pass


class RealModelRunnerResultTest(unittest.TestCase):
    def test_completed_inference_is_accepted(self):
        require_test_pass("INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n")

    def test_adb_success_is_not_test_success(self):
        for output in (
            "INSTRUMENTATION_CODE: -1\n",
            "FAILURES!!!\nTests run: 1, Failures: 1\n",
            "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n",
            "INSTRUMENTATION_STATUS_CODE: -3\nOK (1 test)\n",
            "INSTRUMENTATION_STATUS_CODE: -4\nOK (1 test)\n",
            "OK (0 tests)\n",
        ):
            with self.subTest(output=output), self.assertRaises(RuntimeError):
                require_test_pass(output)


if __name__ == "__main__":
    unittest.main()
