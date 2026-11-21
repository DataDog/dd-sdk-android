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
    for path in sync.PRECOMPUTED['files']:
        write(path, path)
    write('precomputed-assignments/cases/a.json', json.dumps({'name': 'a', 'evaluations': [], 'response': {'n': 5.0}}))
    write('response-signing/v1/corpus.json', '{"cases": []}')
    write('response-signing/v1/nested/case.json', '{}')
    write('response-signing/v1/generate.sh', '#!/bin/sh\n')
    (root / 'response-signing/v1/generate.sh').chmod(0o755)
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
        self.tree = root / 'tree'
        self.tree_spec = {'name': 'tree', 'format': 'tree', 'path': 'response-signing/v1/'}
        sync.write_snapshot(self.tree, sync.snapshot(root / 'upstream', 'HEAD', self.tree_spec))

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

    def test_tree_source_copies_every_file_under_its_path(self):
        self.assertEqual(sorted(sync.read_snapshot(self.tree)),
                         ['SOURCE.json', 'corpus.json', 'generate.sh', 'nested/case.json'])
        sync.verify(self.tree, self.tree_spec)

    def test_tree_source_rejects_symbolic_links(self):
        upstream = Path(self.temporary.name) / 'upstream'
        (upstream / 'response-signing/v1/link.json').symlink_to('corpus.json')
        subprocess.run(['git', '-C', str(upstream), 'add', '-A'], check=True)
        subprocess.run(['git', '-C', str(upstream), '-c', 'user.name=t', '-c', 'user.email=t@t', 'commit', '-qm', 'l'],
                       check=True)
        with self.assertRaises(ValueError):
            sync.snapshot(upstream, 'HEAD', self.tree_spec)

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



class UpdateTest(unittest.TestCase):
    """Runs the script in a temporary consumer repository against a local upstream checkout."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        root = Path(self.temporary.name)
        self.upstream = root / 'upstream'
        _upstream(self.upstream)
        self.consumer = root / 'consumer'
        (self.consumer / 'ci').mkdir(parents=True)
        script = Path(_SPEC.origin).read_text()
        (self.consumer / 'ci' / 'sync.py').write_text(script)
        self.config = self.consumer / 'ci' / 'feature-flags-precomputed-fixtures.json'
        self.config.write_text(json.dumps({'sources': [
            dict(sync.PRECOMPUTED, destination='fixtures', commit=self._head()),
            {'name': 'signing', 'format': 'tree', 'path': 'response-signing/v1/', 'destination': 'signing',
             'commit': self._head()},
        ]}))
        subprocess.run(['git', 'init', '-q', str(self.consumer)], check=True)

    def tearDown(self):
        self.temporary.cleanup()

    def _head(self):
        return subprocess.run(['git', '-C', str(self.upstream), 'rev-parse', 'HEAD'],
                              check=True, capture_output=True, text=True).stdout.strip()

    def _commit(self, path, text):
        (self.upstream / path).parent.mkdir(parents=True, exist_ok=True)
        (self.upstream / path).write_text(text)
        subprocess.run(['git', '-C', str(self.upstream), 'add', '-A'], check=True)
        subprocess.run(['git', '-C', str(self.upstream), '-c', 'user.name=t', '-c', 'user.email=t@t',
                        'commit', '-qm', path], check=True)
        return self._head()

    def _run(self, *args):
        return subprocess.run(['python3', str(self.consumer / 'ci' / 'sync.py'), *args], capture_output=True, text=True)

    def _update(self, ref, source=0):
        self._run('--update', '--ref', ref, '--source', str(self.upstream)).check_returncode()
        return json.loads(self.config.read_text())['sources'][source]['commit']

    def test_update_keeps_the_pin_when_no_imported_file_changed(self):
        pinned = self._head()
        unrelated = self._commit('unrelated/notes.md', 'notes')
        self.assertNotEqual(unrelated, pinned)
        self.assertEqual(self._update(unrelated), pinned)
        # A fresh checkout has no local snapshot to compare.
        subprocess.run(['rm', '-rf', str(self.consumer / 'fixtures')], check=True)
        self.assertEqual(self._update(unrelated), pinned)
        changed = self._commit('precomputed-assignments/cases/b.json',
                               json.dumps({'name': 'b', 'evaluations': [], 'response': {}}))
        self.assertEqual(self._update(changed), changed)

    def test_update_moves_each_pin_independently(self):
        pinned = self._head()
        changed = self._commit('response-signing/v1/corpus.json', '{"cases": [1]}')
        self.assertEqual(self._update(changed, source=1), changed)
        self.assertEqual(json.loads(self.config.read_text())['sources'][0]['commit'], pinned)
        self.assertEqual((self.consumer / 'signing' / 'nested' / 'case.json').read_text(), '{}')
        self.assertEqual(json.loads((self.consumer / 'signing' / 'SOURCE.json').read_text())['commit'], changed)

    def test_verify_checks_each_source(self):
        self._update(self._head())
        self.assertEqual(self._run('--verify').returncode, 0)
        (self.consumer / 'signing' / 'corpus.json').write_text('changed')
        result = self._run('--verify')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('checksums', result.stderr)

    def test_rejects_invalid_manifest(self):
        for sources in ([], [{'name': 'x', 'format': 'zip', 'path': 'x/', 'destination': 'x', 'commit': '0' * 40}],
                        [dict(sync.PRECOMPUTED, destination='same', commit='0' * 40),
                         {'name': 'other', 'format': 'tree', 'path': 'x/', 'destination': 'same', 'commit': '0' * 40}]):
            self.config.write_text(json.dumps({'sources': sources}))
            self.assertNotEqual(self._run('--verify').returncode, 0)

if __name__ == '__main__':
    unittest.main()
