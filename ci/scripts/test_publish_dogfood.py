# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Offline checks: no Gradle upload task or real adms process is executed."""

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import publish_dogfood


class PublishDogfoodTest(unittest.TestCase):
    def test_s3_failure_is_fatal_and_skips_depot(self):
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 17)) as run:
            self.assertEqual(publish_dogfood.main(), 17)
        self.assertEqual(run.call_count, 1)
        self.assertEqual(run.call_args.args[0], [
            "./gradlew", "publishReleasePublicationToInternalRepository",
            "--stacktrace", "--no-daemon", "-Pdd-skip-signing",
        ])

    def test_depot_staging_failure_only_warns_after_s3(self):
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 0)), patch.object(
            publish_dogfood, "publish_to_depot", side_effect=OSError("staging failed")
        ), patch.object(publish_dogfood, "warn") as warn:
            self.assertEqual(publish_dogfood.main(), 0)
        warn.assert_called_once()

    def test_complete_publications_and_continue_after_upload_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            staging = Path(directory) / "maven"
            staging.mkdir()
            stale = staging / "stale.pom"
            stale.write_text("stale")
            selected = []
            expected = []

            def run(command, **kwargs):
                if command[0] == "./gradlew":
                    self.assertFalse(stale.exists())
                    self.assertEqual(command[1], "publishReleasePublicationToDogfoodStagingRepository")
                    for artifact in ("first", "second"):
                        version_dir = staging / "com/datadoghq" / artifact / "1.0-dogfood-abc"
                        version_dir.mkdir(parents=True)
                        names = [f"{artifact}-1.0-dogfood-abc{suffix}" for suffix in (
                            ".pom", ".aar", ".module", "-sources.jar", "-javadoc.jar", "-extra.aar",
                            ".pom.sha512", ".aar.sha256", ".module.sha1", "-sources.jar.md5",
                        )]
                        for name in names:
                            (version_dir / name).write_text(name)
                        (version_dir.parent / "maven-metadata.xml").write_text("not supported")
                        expected.append({str(version_dir / name) for name in names})
                else:
                    self.assertEqual(command[:5], ["adms", "first-party", "java", "upload", "--pom"])
                    selected.append(set(command[5:]))
                    if len(selected) == 1:
                        raise subprocess.CalledProcessError(1, command)
                return subprocess.CompletedProcess(command, 0)

            with patch.object(publish_dogfood, "STAGING_DIRECTORY", staging), patch.object(
                subprocess, "run", side_effect=run
            ), patch.object(publish_dogfood, "warn") as warn:
                publish_dogfood.publish_to_depot()
            self.assertEqual(selected, expected)
            warn.assert_called_once()

    def test_staging_task_failure_only_warns(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(
            publish_dogfood, "STAGING_DIRECTORY", Path(directory) / "maven"
        ), patch.object(subprocess, "run", side_effect=[
            subprocess.CompletedProcess([], 0), subprocess.CalledProcessError(1, ["./gradlew"])
        ]), patch.object(publish_dogfood, "warn") as warn:
            self.assertEqual(publish_dogfood.main(), 0)
        warn.assert_called_once()

    def test_missing_adms_only_warns(self):
        with tempfile.TemporaryDirectory() as directory:
            staging = Path(directory)
            (staging / "artifact.pom").write_text("pom")
            with patch.object(publish_dogfood, "STAGING_DIRECTORY", staging), patch.object(
                publish_dogfood.shutil, "rmtree"
            ), patch.object(subprocess, "run", side_effect=[
                subprocess.CompletedProcess([], 0), FileNotFoundError("adms")
            ]), patch.object(publish_dogfood, "warn") as warn:
                publish_dogfood.publish_to_depot()
            warn.assert_called_once()

    def test_empty_staging_only_warns(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(
            publish_dogfood, "STAGING_DIRECTORY", Path(directory) / "maven"
        ), patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 0)), patch.object(
            publish_dogfood, "warn"
        ) as warn:
            self.assertEqual(publish_dogfood.main(), 0)
        warn.assert_called_once_with("Gradle staged no publications")


if __name__ == "__main__":
    unittest.main()
