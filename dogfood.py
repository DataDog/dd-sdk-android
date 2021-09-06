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

import requests
from git import Repo

TARGET_APP = "app"
TARGET_DEMO = "demo"
TARGET_BRIDGE = "bridge"

REPOSITORIES = {TARGET_APP: "datadog-android", TARGET_DEMO: "shopist-android", TARGET_BRIDGE: "dd-bridge-android"}

FILE_PATH = {
    TARGET_APP: os.path.join("dd-build", "dependencies", "src", "main", "java", "com", "datadog", "build", "dependency", "android", "Datadog.kt"),
    TARGET_DEMO: os.path.join("buildSrc", "src", "main", "kotlin", "com", "datadog", "gradle", "Dependencies.kt"),
    TARGET_BRIDGE: os.path.join("buildSrc", "src", "main", "kotlin", "com", "datadog", "gradle", "Dependencies.kt")
}

PREFIX = {
    TARGET_APP: "val version",
    TARGET_DEMO: "val Datadog",
    TARGET_BRIDGE: "val DatadogSdkVersion"
}


def parse_arguments(args: list) -> Namespace:
    parser = ArgumentParser()

    parser.add_argument("-v", "--version", required=True, help="the version of the SDK")
    parser.add_argument("-t", "--target", required=True,
                        choices=[TARGET_APP, TARGET_DEMO, TARGET_BRIDGE],
                        help="the target repository")

    return parser.parse_args(args)


def github_create_pr(repository: str, branch_name: str, base_name: str, version: str, gh_token: str) -> int:
    headers = {
        'authorization': "Bearer " + gh_token,
        'Accept': 'application/vnd.github.v3+json',
    }
    data = '{"body": "This PR has been created automatically by the CI", ' \
           '"title": "Update to version ' + version + '", ' \
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

    print("regex = ")
    with open(target_file, 'r') as target:
        content = target.read()
        updated_content = re.sub(regex, prefix + " = \"" + version + "\"", content, flags=re.M)
        # print(updated_content)

    with open(target_file, 'w') as target:
        target.write(updated_content)


def git_clone_repository(repo_name: str, gh_token: str, temp_dir_path: str) -> Tuple[Repo, str]:
    print("Cloning repository " + repo_name)
    url = "https://" + gh_token + ":x-oauth-basic@github.com/DataDog/" + repo_name
    repo = Repo.clone_from(url, temp_dir_path)
    base_name = repo.active_branch.name
    return repo, base_name


def git_push_changes(repo: Repo, version: str):
    print("Committing changes")
    repo.git.add(update=True)
    repo.index.commit("Update DD SDK to " + version)

    print("Pushing branch")
    origin = repo.remote(name="origin")
    repo.git.push("--set-upstream", "--force", origin, repo.head.ref)


def update_dependant(version: str, target: str, gh_token: str) -> int:
    branch_name = "update_sdk_" + version
    temp_dir = TemporaryDirectory()
    temp_dir_path = temp_dir.name
    repo_name = REPOSITORIES[target]

    repo, base_name = git_clone_repository(repo_name, gh_token, temp_dir_path)

    print("Creating branch " + branch_name)
    repo.git.checkout('HEAD', b=branch_name)

    generate_target_code(target, temp_dir_path, version)

    if not repo.is_dirty():
        print("Nothing to commit, all is in order-")
        return 0

    git_push_changes(repo, version)

    return github_create_pr(repo_name, branch_name, base_name, version, gh_token)


def run_main() -> int:
    cli_args = parse_arguments(sys.argv[1:])

    # This script expects to have a valid Github Token in a "gh_token" text file
    # The token needs the `repo` permissions, and for now is a PAT
    with open('gh_token', 'r') as f:
        gh_token = f.read().strip()

    return update_dependant(cli_args.version, cli_args.target, gh_token)


if __name__ == "__main__":
    sys.exit(run_main())
