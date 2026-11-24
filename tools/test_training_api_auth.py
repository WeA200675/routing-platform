"""Isolated API write-auth and CORS security tests.

Run: python3 -m unittest -v tools.test_training_api_auth
"""
import os
import unittest
from unittest.mock import patch

from backend.training_api import Handler


class TrainingApiAuthTest(unittest.TestCase):
    def handler(self, headers=None, client="127.0.0.1"):
        handler = object.__new__(Handler)
        handler.headers = headers or {}
        handler.client_address = (client, 12345)
        handler.responses = []
        handler._send = lambda status, payload: handler.responses.append((status, payload))
        return handler

    def test_postgres_without_token_fails_closed(self):
        with patch.dict(os.environ, {"TRAINING_JOB_BACKEND": "postgres"}, clear=True):
            handler = self.handler()
            self.assertFalse(handler._authorize_write())
            self.assertEqual(handler.responses[0][0], 503)

    def test_legacy_loopback_only_without_token(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertTrue(self.handler()._authorize_write())
            handler = self.handler(client="198.51.100.1")
            self.assertFalse(handler._authorize_write())
            self.assertEqual(handler.responses[0][0], 403)

    def test_bearer_token_is_required_when_configured(self):
        with patch.dict(os.environ, {"TRAINING_API_WRITE_TOKEN": "test-secret"}, clear=True):
            self.assertTrue(self.handler({"Authorization": "Bearer test-secret"})._authorize_write())
            for authorization in ("", "Bearer wrong", "Basic test-secret"):
                with self.subTest(authorization=authorization):
                    handler = self.handler({"Authorization": authorization})
                    self.assertFalse(handler._authorize_write())
                    self.assertEqual(handler.responses[0][0], 401)

    def test_cors_is_exact_allowlist(self):
        with patch.dict(os.environ, {"TRAINING_CORS_ORIGINS": "https://dashboard.example"}, clear=True):
            self.assertEqual(self.handler({"Origin": "https://dashboard.example"})._allowed_origin(),
                             "https://dashboard.example")
            self.assertIsNone(self.handler({"Origin": "https://evil.example"})._allowed_origin())
            self.assertIsNone(self.handler({"Origin": "https://dashboard.example.evil"})._allowed_origin())


if __name__ == "__main__":
    unittest.main()
