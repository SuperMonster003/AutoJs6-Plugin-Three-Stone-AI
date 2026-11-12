"""Tests for check_settings_titles.py: the repository passes and the rules catch repeats."""
from pathlib import Path
import tempfile
import unittest

import check_settings_titles as checker

HOME = '''
class HomeActivity : ConfiguredActivity() {
    fun build() {
        setContentView(settingsPage(R.string.home_title, ::buildContent))
        content.addView(sectionHeader(R.string.appearance))
        content.addView(settingRow(
            title = getString(R.string.language),
        ).view)
    }
    internal companion object {
        val SUBPAGES = listOf(Subpage("chat", R.string.chat_title, R.string.chat_summary))
    }
}
'''
CHAT = '''
class ChatSettingsActivity : ConfiguredActivity() {
    fun build() {
        setContentView(settingsPage(R.string.chat_title, ::buildContent))
        content.addView(sectionHeader(R.string.display))
        content.addView(switchRow(getString(R.string.font_size), checked = true) { }.view)
        confirmedChoiceDialog(title = getString(R.string.font_size), labels = labels)
    }
}
'''
STRINGS = {
    'home_title': 'Settings', 'appearance': 'Appearance', 'language': 'Language',
    'chat_title': 'Conversation', 'chat_summary': 'Display', 'display': 'Display', 'font_size': 'Font size',
}


class SettingsTitlesTest(unittest.TestCase):
    def test_repository_titles_are_unique(self):
        self.assertEqual([], checker.check())

    def test_entry_row_and_dialog_title_may_repeat(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.repository(Path(directory), HOME, CHAT, STRINGS)
            self.assertEqual([], checker.check(root))

    def test_row_repeated_on_two_pages_fails(self):
        chat = CHAT.replace('R.string.font_size), checked', 'R.string.language), checked')
        with tempfile.TemporaryDirectory() as directory:
            errors = checker.check(self.repository(Path(directory), HOME, chat, STRINGS))
        self.assertEqual(len(checker.LOCALES), len(errors))
        self.assertIn('"language" appears 2 times', errors[0])

    def test_group_header_repeating_its_page_title_fails(self):
        strings = dict(STRINGS, display='Conversation')
        with tempfile.TemporaryDirectory() as directory:
            errors = checker.check(self.repository(Path(directory), HOME, CHAT, strings))
        self.assertTrue(errors)
        self.assertIn('section display in ChatSettingsActivity', errors[0])

    def test_translation_collision_is_reported_for_that_locale_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.repository(Path(directory), HOME, CHAT, STRINGS)
            self.write_strings(root / checker.RES / 'values-ja', {'display': 'Language'})
            errors = checker.check(root)
        self.assertEqual(1, len(errors))
        self.assertTrue(errors[0].startswith('ja: "language" appears 2 times'))

    def test_subclass_without_title_adds_its_rows_to_the_base_page(self):
        base = CHAT.replace('class ChatSettingsActivity : ConfiguredActivity()',
                            'abstract class ChatSettingsActivity : ConfiguredActivity()')
        local = '''
class LocalChatActivity : ChatSettingsActivity() {
    fun buildLocal() {
        content.addView(settingRow(title = getString(R.string.font_size)).view)
    }
}
'''
        with tempfile.TemporaryDirectory() as directory:
            root = self.repository(Path(directory), HOME, base + local, STRINGS)
            errors = checker.check(root)
            pages = {name: items for name, _, items in checker.pages(root)}
        self.assertNotIn('LocalChatActivity', pages)
        self.assertIn(('row', 'font_size'), pages['ChatSettingsActivity'])
        self.assertEqual(len(checker.LOCALES), len(errors))
        self.assertIn('"font size" appears 2 times', errors[0])

    def repository(self, root, home, chat, strings):
        source = root / checker.SOURCE
        source.mkdir(parents=True)
        (source / 'HomeActivity.kt').write_text(home, encoding='utf-8')
        (source / 'ChatSettingsActivity.kt').write_text(chat, encoding='utf-8')
        self.write_strings(root / checker.RES / 'values', strings)
        return root

    @staticmethod
    def write_strings(directory, strings):
        directory.mkdir(parents=True, exist_ok=True)
        body = ''.join(f'    <string name="{name}">{text}</string>\n' for name, text in strings.items())
        (directory / 'strings.xml').write_text(f'<resources>\n{body}</resources>\n', encoding='utf-8')


if __name__ == '__main__':
    unittest.main()
