import hashlib
import pathlib
import tempfile
import unittest

from verify_android_apk_artifact import verify


SHA = "a" * 40


class ArtifactVerificationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = pathlib.Path(self.tmp.name)
        self.apk = self.path / "app-debug.apk"
        self.apk.write_bytes(b"sample-apk")
        self.digest = hashlib.sha256(b"sample-apk").hexdigest()
        (self.path / "SHA256SUMS.txt").write_text(
            f"{self.digest}  app-debug.apk\n", encoding="ascii"
        )
        (self.path / "SOURCE_COMMIT.txt").write_text(SHA + "\n", encoding="ascii")

    def test_valid_artifact(self):
        self.assertEqual(verify(self.path, SHA), self.digest)

    def test_wrong_commit(self):
        with self.assertRaises(ValueError):
            verify(self.path, "b" * 40)

    def test_invalid_commit_format(self):
        with self.assertRaises(ValueError):
            verify(self.path, "main")

    def test_corrupted_apk(self):
        self.apk.write_bytes(b"corrupted")
        with self.assertRaises(ValueError):
            verify(self.path, SHA)

    def test_missing_checksum(self):
        (self.path / "SHA256SUMS.txt").unlink()
        with self.assertRaises(ValueError):
            verify(self.path, SHA)

    def test_multiple_apks(self):
        (self.path / "second.apk").write_bytes(b"other")
        with self.assertRaises(ValueError):
            verify(self.path, SHA)

    def test_extra_file(self):
        (self.path / "unexpected.txt").write_text("unexpected")
        with self.assertRaises(ValueError):
            verify(self.path, SHA)

    def test_checksum_filename_mismatch(self):
        (self.path / "SHA256SUMS.txt").write_text(
            f"{self.digest}  other.apk\n", encoding="ascii"
        )
        with self.assertRaises(ValueError):
            verify(self.path, SHA)

    def test_symlink_rejected(self):
        (self.path / "alias").symlink_to(self.apk)
        with self.assertRaises(ValueError):
            verify(self.path, SHA)


if __name__ == "__main__":
    unittest.main()
