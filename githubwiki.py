#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2019-Present Datadog, Inc

import re
import subprocess
import shutil
import sys
import os
from argparse import ArgumentParser, Namespace
from tempfile import TemporaryDirectory
from typing import Tuple, TextIO
from xmlrpc.client import Boolean

import requests
from git import Repo

OWNER = "DataDog"
REPOSITORY = "dd-sdk-android"


def parse_arguments(args: list) -> Namespace:
    parser = ArgumentParser()

    parser.add_argument("-d", "--dry-run", required=False, dest="dry_run",
                        help="Don't push changes or create a PR.", action="store_true")

    return parser.parse_args(args)


def git_clone_repository(gh_token: str, temp_dir_path: str) -> Tuple[Repo, str]:
    print("Cloning repository " + REPOSITORY)
    url = "https://" + gh_token + ":x-oauth-basic@github.com/" + OWNER + "/" + REPOSITORY + ".wiki"
    repo = Repo.clone_from(url, temp_dir_path)
    base_name = repo.active_branch.name
    return repo, base_name


def copy_generated_wiki(module_name: str, repoPath: str, sidebar_file: TextIO):
    wiki_path = os.path.join(".", module_name, "build", "wiki")
    index_path = os.path.join(".", module_name, "build", "wiki", module_name + ".md")

    if os.path.exists(wiki_path):
        print("Copying wiki for " + module_name)
        shutil.copytree(wiki_path, repoPath, dirs_exist_ok=True)
        print("Copying sidebar content")
        with open(index_path, "r") as index_file:
            sidebar_file.write(index_file.read())

def git_commit_changes(repo: Repo):
    print("Committing changes, head at " + repo.head.commit.hexsha)

    repo.index.add(repo.untracked_files)
    repo.index.add("*.md")
    print("Changes added")

    repo.index.commit("Update wiki")
    print("Changes committed, head at " + repo.head.commit.hexsha)


def git_push_changes(repo: Repo):
    print("Pushing branch")
    origin = repo.remote(name="origin")
    repo.git.push("--set-upstream", origin, repo.head.ref)


def update_wiki(gh_token: str, dry_run: bool) -> int:
    temp_dir = TemporaryDirectory()
    temp_dir_path = temp_dir.name
    print("Using temp dir " + temp_dir_path)

    repo, base_name = git_clone_repository(gh_token, temp_dir_path)

    sidebar_path = os.path.join(temp_dir_path, "_Sidebar.md")
    sidebar_file = open(sidebar_path, 'w', encoding="utf-8")

    modules = sorted([f.name for f in os.scandir() if f.is_dir()])
    for module in modules:
        copy_generated_wiki(module, temp_dir_path, sidebar_file)
    sidebar_file.close()

    if (not repo.is_dirty()) and (not repo.untracked_files):
        print("Nothing to commit, all is in order")
        return 0

    if not dry_run:
        git_commit_changes(repo)
        git_push_changes(repo)


def run_main() -> int:
    cli_args = parse_arguments(sys.argv[1:])

    # This script expects to have a valid Github Token in a "gh_token" text file
    # The token needs the `repo` permissions, and for now is a PAT
    with open('gh_token', 'r') as f:
        gh_token = f.read().strip()

    return update_wiki(gh_token, cli_args.dry_run)


if __name__ == "__main__":
    sys.exit(run_main())
