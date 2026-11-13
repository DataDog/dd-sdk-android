#!/usr/bin/env python3
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""Publish dogfood builds to S3, then best-effort to Depot from a local Maven repository."""

import shutil
import subprocess
from pathlib import Path


STAGING_DIRECTORY = Path("build/dogfood-maven")
GRADLE_OPTIONS = ["--stacktrace", "--no-daemon", "-Pdd-skip-signing"]


def warn(message):
    print(f"WARNING: Depot dogfood publication: {message}", flush=True)


def publish_to_depot():
    # Never mix publications from a previous run with the current dogfood version.
    if STAGING_DIRECTORY.exists():
        shutil.rmtree(STAGING_DIRECTORY)
    subprocess.run(
        ["./gradlew", "publishReleasePublicationToDogfoodStagingRepository", *GRADLE_OPTIONS],
        check=True,
    )
    poms = sorted(STAGING_DIRECTORY.rglob("*.pom"))
    if not poms:
        raise RuntimeError("Gradle staged no publications")
    for pom in poms:
        # Gradle puts the complete publication in its Maven version directory.
        # Artifact-level maven-metadata.xml lives above it and is unsupported by adms.
        files = sorted(path for path in pom.parent.iterdir() if path.is_file() and path != pom)
        try:
            subprocess.run(
                ["adms", "first-party", "java", "upload", "--pom", str(pom), *map(str, files)],
                check=True,
            )
        except (OSError, subprocess.CalledProcessError) as error:
            warn(f"{pom}: {error}")


def main():
    result = subprocess.run(
        ["./gradlew", "publishReleasePublicationToInternalRepository", *GRADLE_OPTIONS],
        check=False,
    )
    if result.returncode:
        return result.returncode
    try:
        publish_to_depot()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        warn(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
