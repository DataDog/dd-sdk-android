#!/usr/bin/env python3
# Unless explicitly stated otherwise, all files in this repository are licensed under the
# Apache License Version 2.0. This product includes software developed at Datadog
# (https://www.datadoghq.com/). Copyright 2025-Present Datadog, Inc.
"""Tests for sync-feature-flags-precomputed-fixtures.py. Run: python3 ci/test_sync_feature_flags_precomputed_fixtures.py"""
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

_SPEC = importlib.util.spec_from_file_location(
    'sync_fixtures', Path(__file__).resolve().parent / 'sync-feature-flags-precomputed-fixtures.py')
sync = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(sync)


def _upstream(root):
    def write(path, text):
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    for path in sync.FILES:
        write(path, path)
    write('precomputed-assignments/cases/a.json', json.dumps({'name': 'a', 'evaluations': [], 'response': {'n': 5.0}}))
    subprocess.run(['git', 'init', '-q', str(root)], check=True)
    subprocess.run(['git', '-C', str(root), 'add', '-A'], check=True)
    subprocess.run(['git', '-C', str(root), '-c', 'user.name=t', '-c', 'user.email=t@t', 'commit', '-qm', 'c'],
                   check=True)


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        root = Path(self.temporary.name)
        _upstream(root / 'upstream')
        self.destination = root / 'precomputed'
        sync.write_snapshot(self.destination, sync.snapshot(root / 'upstream', 'HEAD'))

    def tearDown(self):
        self.temporary.cleanup()

    def test_verify_ignores_finder_metadata(self):
        (self.destination / '.DS_Store').write_bytes(b'\0')
        (self.destination / 'cases' / '.DS_Store').write_bytes(b'\0')
        self.assertNotIn('.DS_Store', sync.read_snapshot(self.destination))
        sync.verify(self.destination)

    def test_verify_rejects_stray_file(self):
        (self.destination / 'cases' / 'stray.json').write_text('{}')
        with self.assertRaises(ValueError):
            sync.verify(self.destination)

    def test_response_preserves_numeric_tokens(self):
        self.assertEqual((self.destination / 'responses/cases/a.json').read_text(), '{"n": 5.0}\n')


class FetchTest(unittest.TestCase):
    def test_retries_with_timeout_then_succeeds(self):
        calls = []

        def run(*args, **kwargs):
            calls.append(kwargs)
            if len(calls) == 1:
                raise subprocess.TimeoutExpired(args[0], kwargs['timeout'])
            if len(calls) == 2:
                raise subprocess.CalledProcessError(128, args[0])

        with mock.patch.object(sync.subprocess, 'run', side_effect=run), mock.patch.object(sync.time, 'sleep'):
            sync.fetch(Path('source'), 'main')
        self.assertEqual(len(calls), 3)
        self.assertTrue(all(call['timeout'] == sync.FETCH_TIMEOUT_SECONDS for call in calls))

    def test_fails_after_last_attempt(self):
        error = subprocess.CalledProcessError(128, ['git'])
        with mock.patch.object(sync.subprocess, 'run', side_effect=error) as run, \
                mock.patch.object(sync.time, 'sleep'):
            with self.assertRaises(subprocess.CalledProcessError):
                sync.fetch(Path('source'), 'main')
        self.assertEqual(run.call_count, sync.FETCH_ATTEMPTS)


if __name__ == '__main__':
    unittest.main()
