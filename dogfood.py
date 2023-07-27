#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2019-Present Datadog, Inc

import re
import subprocess
import sys
import os
from argparse import ArgumentParser, Namespace
from tempfile import TemporaryDirectory
from typing import Tuple
from xmlrpc.client import Boolean

import requests
from git import Repo

TARGET_APP = "app"
TARGET_DEMO = "demo"
TARGET_BRIDGE = "bridge"
TARGET_GRADLE_PLUGIN = "gradle-plugin"

REPOSITORIES = {
    TARGET_APP: "datadog-android",
    TARGET_DEMO: "shopist-android",
    TARGET_BRIDGE: "dd-bridge-android",
    TARGET_GRADLE_PLUGIN: "dd-sdk-android-gradle-plugin",
    # Flutter is not needed because it pulls updates instead of being pushed them with the dogfood script.
}

FILE_PATH = {
    TARGET_APP: os.path.join("gradle", "libs.versions.toml"),
    TARGET_DEMO: os.path.join("gradle", "libs.versions.toml"),
    TARGET_BRIDGE: os.path.join("gradle", "libs.versions.toml"),
    TARGET_GRADLE_PLUGIN: os.path.join("gradle", "libs.versions.toml"),
}

PREFIX = {
    TARGET_APP: "datadog",
    TARGET_DEMO: "datadogSdk",
    TARGET_BRIDGE: "datadogSdk",
    TARGET_GRADLE_PLUGIN: "datadogSdk",
}


def parse_arguments(args: list) -> Namespace:
    parser = ArgumentParser()

    parser.add_argument("-v", "--version", required=True, help="the version of the SDK")
    parser.add_argument("-t", "--target", required=True,
                        choices=[TARGET_APP, TARGET_DEMO, TARGET_BRIDGE, TARGET_GRADLE_PLUGIN],
                        help="the target repository")
    parser.add_argument("-d", "--dry-run", required=False, dest="dry_run",
                        help="Don't push changes or create a PR.", action="store_true")

    return parser.parse_args(args)


def github_create_pr(repository: str, branch_name: str, base_name: str, version: str, previous_version: str, gh_token: str) -> int:
    headers = {
        'authorization': "Bearer " + gh_token,
        'Accept': 'application/vnd.github.v3+json',
    }
    body = "This PR has been created automatically by the CI"
    if previous_version:
        diff = "Updating Datadog SDK from version {previous_version} to version {version}: [diff](https://github.com/DataDog/dd-sdk-android/compare/{previous_version}...{version})".format(previous_version=previous_version, version=version)
        body = "\\n".join([body, diff])
    data = '{"body": "' + body + '", ' \
           '"title": "Update Datadog SDK to version ' + version + '", ' \
           '"base":"' + base_name + '", "head":"' + branch_name + '"}'

    url = "https://api.github.com/repos/DataDog/" + repository + "/pulls"
    response = requests.post(url=url, headers=headers, data=data)
    if response.status_code == 201:
        print("Pull Request created successfully")
        return 0
    else:
        print("pull request failed " + str(response.status_code) + '\n' + response.text)
        return 1

def generate_target_code(target: str, temp_dir_path: str, version: str):
    print("Generating code with version " + version)
    file_path = FILE_PATH[target]
    target_file = os.path.join(temp_dir_path, file_path)
    prefix = PREFIX[target]
    regex = prefix + " = \"[0-9a-z\\.-]+\""

    previous_version = None

    with open(target_file, 'r') as target:
        content = target.read()
        previous_version_search = re.search(prefix + " = \"(.*)\"", content, flags=re.M)
        if previous_version_search:
            previous_version = previous_version_search.group(1)
        updated_content = re.sub(regex, prefix + " = \"" + version + "\"", content, flags=re.M)

    with open(target_file, 'w') as target:
        target.write(updated_content)

    return previous_version


def git_clone_repository(repo_name: str, gh_token: str, temp_dir_path: str) -> Tuple[Repo, str]:
    print("Cloning repository " + repo_name)
    url = "https://" + gh_token + ":x-oauth-basic@github.com/DataDog/" + repo_name
    repo = Repo.clone_from(url, temp_dir_path)
    base_name = repo.active_branch.name
    return repo, base_name


def git_push_changes(repo: Repo, version: str):
    print("Committing changes")
    repo.git.add(update=True)
    repo.index.commit("Update Datadog SDK to " + version)

    print("Pushing branch")
    origin = repo.remote(name="origin")
    repo.git.push("--set-upstream", "--force", origin, repo.head.ref)


def update_dependant(version: str, target: str, gh_token: str, dry_run: bool) -> int:
    branch_name = "update_sdk_" + version
    temp_dir = TemporaryDirectory()
    temp_dir_path = temp_dir.name
    repo_name = REPOSITORIES[target]

    repo, base_name = git_clone_repository(repo_name, gh_token, temp_dir_path)

    print("Creating branch " + branch_name)
    repo.git.checkout('HEAD', b=branch_name)

    previous_version = generate_target_code(target, temp_dir_path, version)

    if not repo.is_dirty():
        print("Nothing to commit, all is in order-")
        return 0

    if not dry_run:
        git_push_changes(repo, version)

        return github_create_pr(repo_name, branch_name, base_name, version, previous_version, gh_token)
    
    return 0

def run_main() -> int:
    cli_args = parse_arguments(sys.argv[1:])

    # This script expects to have a valid Github Token in a "gh_token" text file
    # The token needs the `repo` permissions, and for now is a PAT
    with open('gh_token', 'r') as f:
        gh_token = f.read().strip()

    return update_dependant(cli_args.version, cli_args.target, gh_token, cli_args.dry_run)


if __name__ == "__main__":
    sys.exit(run_main())
