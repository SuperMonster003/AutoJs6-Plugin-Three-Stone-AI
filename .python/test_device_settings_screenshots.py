import unittest

from device_settings_screenshots import check_migration

INSTALL = 1_000
DIGEST = "a" * 64
PROFILES = '{"schema":1,"defaultProfileId":"p1","profiles":[]}'
CATALOG = '{"schema":1,"selectedModelId":"m1","entries":[]}'


def state(target, profiles=PROFILES, catalog=CATALOG, mtime=900, digest=DIGEST):
    return {
        "documents": {"profiles": (mtime, digest), "catalog": (mtime, digest)},
        "target": target,
        "profiles_text": profiles,
        "catalog_text": catalog,
    }


class CheckMigrationTest(unittest.TestCase):
    def test_online_default_service_is_kept(self):
        passed, lines = check_migration(INSTALL, state(""), state("v1\nprofile:p1.model." + "0" * 24))
        self.assertTrue(passed, lines)
        self.assertIn("alternative", "\n".join(lines))

    def test_local_default_is_kept_when_no_service_is_default(self):
        profiles = '{"schema":1,"defaultProfileId":null,"profiles":[]}'
        after = state("v1\nlocal:m1", profiles=profiles)
        passed, lines = check_migration(INSTALL, state(""), after)
        self.assertTrue(passed, lines)

    def test_document_written_after_the_install_fails(self):
        passed, lines = check_migration(INSTALL, state(""), state("v1\nprofile:p1", mtime=1_200))
        self.assertFalse(passed)
        self.assertIn("WRITTEN after the install", "\n".join(lines))

    def test_other_service_or_missing_file_fails(self):
        self.assertFalse(check_migration(INSTALL, state(""), state("v1\nprofile:p2"))[0])
        self.assertFalse(check_migration(INSTALL, state(""), state(""))[0])

    def test_empty_target_needs_no_previous_default(self):
        none = '{"defaultProfileId":null}'
        self.assertTrue(check_migration(INSTALL, state(""), state("v1\n", profiles=none, catalog="{}"))[0])
        self.assertFalse(check_migration(INSTALL, state(""), state("v1\n"))[0])

    def test_absent_documents_are_reported_without_failing(self):
        after = state("v1\nlocal:m1", profiles="", digest=None)
        passed, lines = check_migration(INSTALL, state("", digest=None), after)
        self.assertTrue(passed, lines)
        self.assertIn("profiles: absent", "\n".join(lines))


if __name__ == "__main__":
    unittest.main()
