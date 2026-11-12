"""Checks that every title in the settings tree appears only once (roadmap 1.6.0 P5.1, D80).

Collects, per settings page, the page title, the group headers and the static row titles, resolves
them in every shipped locale and reports any text that is shown in more than one place. The only
allowed repeat is one entry row whose title is the title of the page it opens.

Usage: python .python/check_settings_titles.py [--root DIR]
"""
from pathlib import Path
import argparse
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('app/src/main/java/io/github/supermonster003/autojs6/plugin/threestoneai')
RES = Path('app/src/main/res')
# Pages built with the scaffold that are not part of the settings tree.
NOT_SETTINGS = {'ConversationHistoryActivity', 'RuntimeAccessActivity'}
# Resolution chains the way Android picks resources for each shipped locale.
LOCALES = {
    'en': ['values'], 'ar': ['values-ar', 'values'], 'es': ['values-es', 'values'],
    'fr': ['values-fr', 'values'], 'ja': ['values-ja', 'values'], 'ko': ['values-ko', 'values'],
    'ru': ['values-ru', 'values'], 'zh-CN': ['values-zh-rCN', 'values-zh', 'values'],
    'zh-HK': ['values-zh-rHK', 'values-zh', 'values'], 'zh-TW': ['values-zh-rTW', 'values-zh', 'values'],
}
CLASS = re.compile(r'^(?:internal |abstract |open |private )*class (\w+)', re.M)
PAGE = re.compile(r'(?:settingsPage|buildScaffold)\(\s*R\.string\.(\w+)')
SECTION = re.compile(r'sectionHeader\(\s*R\.string\.(\w+)')
ROW = re.compile(r'(?:settingRow|switchRow)\(\s*(?:title\s*=\s*)?getString\(\s*R\.string\.(\w+)\s*\)')
ENTRY = re.compile(r'Subpage\(\s*"[^"]+",\s*R\.string\.(\w+)')
STRING = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)


def pages(root):
    """Yields (page name, page title resource, [(kind, resource)]) for each settings page class."""
    for file in sorted((root / SOURCE).rglob('*.kt')):
        text = file.read_text(encoding='utf-8')
        starts = [(match.start(), match.group(1)) for match in CLASS.finditer(text)]
        for index, (start, name) in enumerate(starts):
            end = starts[index + 1][0] if index + 1 < len(starts) else len(text)
            body = text[start:end]
            title = PAGE.search(body)
            if name in NOT_SETTINGS or title is None:
                continue
            items = [('section', match.group(1)) for match in SECTION.finditer(body)]
            items += [('row', match.group(1)) for match in ROW.finditer(body)]
            items += [('row', match.group(1)) for match in ENTRY.finditer(body)]
            yield name, title.group(1), items


def strings(root, directory):
    values = {}
    for file in sorted((root / RES / directory).glob('strings*.xml')):
        for name, value in STRING.findall(file.read_text(encoding='utf-8')):
            values[name] = value
    return values


def normalize(text):
    text = re.sub(r'\\(.)', r'\1', text).replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
    return ' '.join(text.split()).casefold()


def check(root=ROOT):
    errors = []
    found = list(pages(root))
    if not found:
        return ['No settings pages found']
    tables = {directory: strings(root, directory) for chain in LOCALES.values() for directory in chain}
    for locale, chain in LOCALES.items():
        def resolve(resource):
            for directory in chain:
                if resource in tables[directory]:
                    return tables[directory][resource]
            errors.append(f'{locale}: missing string {resource}')
            return resource
        places = {}
        for name, title, items in found:
            places.setdefault(normalize(resolve(title)), []).append(('page', name, title))
            for kind, resource in items:
                places.setdefault(normalize(resolve(resource)), []).append((kind, name, resource))
        for text, where in sorted(places.items()):
            if len(where) < 2 or allowed_entry(where):
                continue
            listed = ', '.join(f'{kind} {resource} in {name}' for kind, name, resource in where)
            errors.append(f'{locale}: "{text}" appears {len(where)} times: {listed}')
    return errors


def allowed_entry(where):
    """One page plus one row on another page that opens it."""
    page = [place for place in where if place[0] == 'page']
    rows = [place for place in where if place[0] == 'row']
    return len(where) == 2 and len(page) == 1 and len(rows) == 1 and rows[0][1] != page[0][1]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    errors = check(args.root)
    for error in errors:
        print('FAIL ' + error)
    pages_found = len(list(pages(args.root)))
    print(f'{"FAIL" if errors else "PASS"} settings titles: {pages_found} pages, {len(LOCALES)} locales, {len(errors)} problems')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
