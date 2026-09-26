"""Exercise the catalog publisher against local Git remotes, without network access."""
import json
from pathlib import Path
import tempfile
import unittest

from publish_model_catalog import CATALOG_PATH, REF, SEED_PATH, git, prepare, publish


SEED = Path(__file__).resolve().parents[1] / SEED_PATH


class CatalogPublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="stone-publish-test-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.remote = self.directory / "remote.git"
        self.root = self.directory / "source"
        self.output = self.directory / "publication"
        self.root.mkdir()
        git(self.directory, "init", "--bare", str(self.remote))
        git(self.root, "init", "--initial-branch=master")
        git(self.root, "config", "user.name", "Catalog Test")
        git(self.root, "config", "user.email", "catalog@example.invalid")
        git(self.root, "config", "core.autocrlf", "false")
        bundled = self.root / SEED_PATH
        bundled.parent.mkdir(parents=True)
        bundled.write_bytes(SEED.read_bytes())
        git(self.root, "add", SEED_PATH)
        git(self.root, "commit", "-m", "test: seed")
        git(self.root, "remote", "add", "origin", str(self.remote))
        git(self.root, "push", "origin", "master")
        self.source_head = git(self.root, "rev-parse", "HEAD")

    def collect(self, *, changed=False):
        prepare(self.root, self.output)
        candidate = (self.output / "previous.json").read_bytes()
        if changed:
            value = json.loads(candidate)
            value["revision"] += 1
            value["updatedAtEpochMillis"] += 1000
            value["providers"]["openai"]["models"].append("gpt-test-next")
            candidate = (json.dumps(value, indent=2) + "\n").encode()
        (self.output / CATALOG_PATH).write_bytes(candidate)

    def test_bootstrap_is_orphan_and_never_changes_code_branch_or_index(self):
        self.collect()
        before_status = git(self.root, "status", "--porcelain")
        self.assertTrue(publish(self.root, self.output))
        self.assertEqual(self.source_head, git(self.root, "rev-parse", "HEAD"))
        self.assertEqual(before_status, git(self.root, "status", "--porcelain"))
        self.assertEqual(b"1\n", git(self.remote, "rev-list", "--count", REF))
        self.assertEqual(b"online-models.json\n", git(self.remote, "ls-tree", "--name-only", REF))
        self.assertEqual(SEED.read_bytes(), git(self.remote, "show", f"{REF}:{CATALOG_PATH}"))

    def test_unchanged_collection_does_not_create_a_daily_commit(self):
        self.collect()
        publish(self.root, self.output)
        before = git(self.remote, "rev-parse", REF)
        self.collect()
        self.assertFalse(publish(self.root, self.output))
        self.assertEqual(before, git(self.remote, "rev-parse", REF))

    def test_changed_catalog_fast_forwards_without_changing_source_commit_count(self):
        self.collect()
        publish(self.root, self.output)
        self.collect(changed=True)
        self.assertTrue(publish(self.root, self.output))
        self.assertEqual(b"2\n", git(self.remote, "rev-list", "--count", REF))
        self.assertEqual(b"1\n", git(self.root, "rev-list", "--count", "HEAD"))

    def test_concurrent_publication_rejects_stale_collection(self):
        self.collect()
        publish(self.root, self.output)
        self.collect(changed=True)
        competing = self.directory / "competing"
        prepare(self.root, competing)
        (competing / CATALOG_PATH).write_bytes((self.output / CATALOG_PATH).read_bytes())
        publish(self.root, competing)
        latest = git(self.remote, "rev-parse", REF)
        with self.assertRaisesRegex(ValueError, "changed since collection"):
            publish(self.root, self.output)
        self.assertEqual(latest, git(self.remote, "rev-parse", REF))

    def test_same_revision_cannot_replace_models(self):
        self.collect(changed=True)
        value = json.loads((self.output / CATALOG_PATH).read_bytes())
        value["revision"] -= 1
        (self.output / CATALOG_PATH).write_text(json.dumps(value), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "increment revision"):
            publish(self.root, self.output)

    def test_metadata_only_churn_is_rejected(self):
        self.collect()
        value = json.loads((self.output / CATALOG_PATH).read_bytes())
        value["updatedAtEpochMillis"] += 1000
        (self.output / CATALOG_PATH).write_text(json.dumps(value), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "preserve metadata"):
            publish(self.root, self.output)

    def test_newer_bundled_snapshot_advances_remote_even_without_source_changes(self):
        self.collect()
        publish(self.root, self.output)
        value = json.loads(SEED.read_bytes())
        value["revision"] += 2
        value["updatedAtEpochMillis"] += 1000
        value["providers"]["openai"]["models"].append("gpt-bundled-next")
        (self.root / SEED_PATH).write_text(json.dumps(value), encoding="utf-8")
        self.collect()
        self.assertTrue(publish(self.root, self.output))
        actual = json.loads(git(self.remote, "show", f"{REF}:{CATALOG_PATH}"))
        self.assertEqual(value, actual)

    def test_equal_revision_conflicting_bundle_fails_before_collection(self):
        self.collect()
        publish(self.root, self.output)
        value = json.loads(SEED.read_bytes())
        value["providers"]["openai"]["models"].append("gpt-conflicting-bundle")
        (self.root / SEED_PATH).write_text(json.dumps(value), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "conflict at the same revision"):
            self.collect()

    def test_published_other_files_are_preserved(self):
        self.collect()
        publish(self.root, self.output)
        head = git(self.remote, "rev-parse", REF).decode().strip()
        # Model a maintainer adding a small explanatory README on the data branch.
        git(self.root, "fetch", "origin", REF)
        index = self.directory / "extra-index"
        import os
        environment = dict(os.environ, GIT_INDEX_FILE=str(index))
        git(self.root, "read-tree", head, env=environment)
        blob = git(self.root, "hash-object", "-w", "--stdin", data=b"Catalog data only\n").decode().strip()
        git(self.root, "update-index", "--add", "--cacheinfo", f"100644,{blob},README.md", env=environment)
        tree = git(self.root, "write-tree", env=environment).decode().strip()
        commit = git(self.root, "commit-tree", tree, "-p", head, "-m", "docs: explain catalog").decode().strip()
        git(self.root, "push", "origin", f"{commit}:{REF}")
        self.collect(changed=True)
        publish(self.root, self.output)
        self.assertEqual(b"Catalog data only\n", git(self.remote, "show", f"{REF}:README.md"))


if __name__ == "__main__":
    unittest.main()
