import unittest

from device_models_flow import catalog_state, preference_values, profile_ids


class StateReadersTest(unittest.TestCase):
    def test_profile_ids_are_sorted_and_missing_documents_are_none(self):
        text = '{"schema":5,"profiles":[{"profileId":"b","displayName":"B"},{"profileId":"a"}]}'
        self.assertEqual(["a", "b"], profile_ids(text))
        self.assertIsNone(profile_ids(""))
        self.assertIsNone(profile_ids("run-as: package not debuggable"))

    def test_catalog_state_keeps_entry_ids_and_the_selected_model(self):
        text = '{"schema":2,"revision":7,"selectedModelId":"m2","entries":[{"modelId":"m2"},{"modelId":"m1"}]}'
        self.assertEqual((["m1", "m2"], "m2"), catalog_state(text))
        self.assertEqual(([], None), catalog_state('{"schema":2,"revision":1,"selectedModelId":null,"entries":[]}'))
        self.assertIsNone(catalog_state("cat: No such file or directory"))

    def test_preference_values_ignore_order_and_treat_a_missing_file_as_empty(self):
        first = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
                 '<set name="favorite_target_ids"><string>b</string><string>a</string></set>'
                 '<long name="sample-count.x" value="3" /><string name="values">https://a</string></map>')
        second = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"
                  '<string name="values">https://a</string><long name="sample-count.x" value="3" />'
                  '<set name="favorite_target_ids"><string>a</string><string>b</string></set></map>')
        self.assertEqual(preference_values(first), preference_values(second))
        self.assertEqual(3, len(preference_values(first)))
        self.assertEqual({}, preference_values("cat: shared_prefs/x.xml: No such file or directory"))
        self.assertEqual({}, preference_values("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map />"))
        self.assertNotEqual(preference_values(first), preference_values(first.replace('value="3"', 'value="4"')))


if __name__ == "__main__":
    unittest.main()
