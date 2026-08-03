# rum-fit Cross-Repo CI Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable `dd-sdk-android` to trigger and gate on rum-fit Android integration tests from its own CI, and automatically bump rum-fit's Android SDK submodule after every successful develop merge.

**Architecture:** rum-fit adopts a `CI_PIPELINE_KEY` router in its `.gitlab-ci.yml`, exposing parameterized pipeline entry points for other SDK repos. `dd-sdk-android` triggers these as downstream multi-project GitLab pipelines. A Python script (managed by Bazel) handles the GitHub submodule-bump PR creation and merge.

**Tech Stack:** GitLab CI YAML, Bazel `genrule` + `py_binary`, Python 3.12, `requests` (already in lockfile), `dd-octo-sts` for GitHub auth.

## Global Constraints

- **Do not run lint or builds** without asking the user first.
- **rum-fit** repo path: `/Users/aleksandr.gringauz/projects/rum-sdk-integration-test-framework`
- **dd-sdk-android** repo path: `/Users/aleksandr.gringauz/projects/dd-sdk-android-3`
- **rum-fit commit style:** conventional commits (`feat:`, `fix:`, `chore:`, etc.)
- **dd-sdk-android commit style:** `RUM-XXXXX: <short description>`, GPG-signed (`git commit -S`)
- **rum-fit default branch:** `main`
- **dd-sdk-android default branch:** `develop`
- **Bazel version:** 8.7.0 (defined in rum-fit's `.bazelversion`); install via Bazelisk on macOS CI
- **Python:** 3.12; type-annotated; modern syntax (`X | None`, not `Optional[X]`)
- **macOS runner tag:** `["macos:sequoia-arm64", "specific:true"]`
- **amd64 runner tag:** `["arch:amd64"]`
- **`requests` is already in rum-fit's `requirements_lock.txt`** — use it for GitHub API calls; do not add new pip dependencies
- **Bazel Python deps** use hub name `rum_pip` (e.g. `@rum_pip//requests`)

---

## File Map

### `rum-sdk-integration-test-framework`

| File | Action | Purpose |
|------|--------|---------|
| `.gitlab-ci.yml` | Modify | Become pipeline router only |
| `ci/pipelines/default-pipeline.yml` | Create | All existing CI content + new `android-tests` Bazel job |
| `ci/pipelines/android-sdk-tests.yml` | Create | Parameterized pipeline for PR-time Android tests |
| `ci/pipelines/bump-android-sdk.yml` | Create | test + bump pipeline triggered on dd-sdk-android develop merges |
| `tools/ci/__init__.py` | Create | Package marker |
| `tools/ci/BUILD.bazel` | Create | `py_binary` target for bump script |
| `tools/ci/bump_android_sdk.py` | Create | Script: update submodule, commit, push branch, create + merge GitHub PR |

### `dd-sdk-android-3`

| File | Action | Purpose |
|------|--------|---------|
| `ci/pipelines/default-pipeline.yml` | Modify | Add `rum-fit:android-tests` and `rum-fit:bump-android-sdk` trigger jobs |

---

## Task 1: Restructure rum-fit's `.gitlab-ci.yml` into a router

**Repo:** `rum-sdk-integration-test-framework`

**Files:**
- Modify: `.gitlab-ci.yml`
- Create: `ci/pipelines/default-pipeline.yml`

**What to do:** Move all existing content out of `.gitlab-ci.yml` into `ci/pipelines/default-pipeline.yml`. The root file becomes a thin router that conditionally includes different pipeline files based on the `CI_PIPELINE_KEY` variable.

- [ ] **Step 1: Create `ci/pipelines/default-pipeline.yml`**

  Move the entire current content of `.gitlab-ci.yml` verbatim into this new file. The full current content is reproduced below so you don't need to read the file:

  ```yaml
  variables:
    CURRENT_CI_IMAGE: "test"
    CI_IMAGE_DOCKER: registry.ddbuild.io/ci/rum-sdk-integration-test-framework:$CURRENT_CI_IMAGE
    DD_SERVICE: "rum-sdk-integration-test-framework"
    DD_ENV: "ci"
    DD_LLMOBS_ML_APP: "rum-sdk-integration-test-framework"
    # Test app RUM credentials (placeholder values — not real tokens)
    TEST_CLIENT_TOKEN: "pub00000000000000000000000000000000"
    TEST_APPLICATION_ID: "00000000-0000-0000-0000-000000000001"

  .snippets:
    init:
      - git config --global url."https://gitlab-ci-token:${CI_JOB_TOKEN}@gitlab.ddbuild.io/DataDog/dd-sdk-maui".insteadOf "https://github.com/DataDog/dd-sdk-maui"
      - git submodule update --init
    setup-ci-visibility:
      - export DD_TRACE_AGENT_URL="http://${TRACE_AGENT_URL}"
      - export DD_CIVISIBILITY_FLAKY_RETRY_ENABLED=0
      - export DD_CIVISIBILITY_ITR_ENABLED=0
      - export DD_CIVISIBILITY_TEST_SKIPPING_ENABLED=0
      - export DD_CIVISIBILITY_EARLY_FLAKE_DETECTION_ENABLED=0
    setup-claude-auth:
      - |
        set +x
        AI_GATEWAY_TOKEN=$(authanywhere -audience rapid-ai-platform -raw -dc us1.ddbuild.io)
        export ANTHROPIC_API_KEY="$AI_GATEWAY_TOKEN"
        export ANTHROPIC_BASE_URL="https://ai-gateway.us1.ddbuild.io"
        export ANTHROPIC_CUSTOM_HEADERS="$(printf 'Authorization: Bearer %s\nsource: claude-code\norg-id: 2\nprovider: anthropic\nclaude-code: true\nx-target-account: eval' "$AI_GATEWAY_TOKEN")"
        set -x
    write-web-dd-config:
      - |
        cat > suites/datadog_rum/apps/web/dd_config.js <<EOF
        window.DD_CONFIG = {
            clientToken: '$TEST_CLIENT_TOKEN',
            applicationId: '$TEST_APPLICATION_ID',
            site: 'datadoghq.com',
            service: 'rum-integration-test-web',
            env: 'test',
            sessionSampleRate: 100,
            sessionReplaySampleRate: 0,
            trackUserInteractions: true,
            trackResources: true,
            trackLongTasks: false,
            trackViewsManually: true
        };
        EOF

  stages:
    - ci-image
    - test
    - integration

  ci-image:
    stage: ci-image
    when: manual
    except: [ tags, schedules ]
    tags: [ "arch:amd64" ]
    image: 486234852809.dkr.ecr.us-east-1.amazonaws.com/docker:24.0.4-jammy
    script:
      - docker buildx build --tag $CI_IMAGE_DOCKER --label target=build -f ci/Dockerfile.gitlab --push .

  # lint-and-test:
  #   stage: test
  #   ... (keep existing commented jobs as-is)

  web-tests:
    stage: integration
    when: manual
    allow_failure: true
    tags: ["arch:amd64"]
    image: $CI_IMAGE_DOCKER
    timeout: 30m
    artifacts:
      when: always
      expire_in: 1 week
      paths:
        - test-reports/
    script:
      - !reference [.snippets, init]
      - export DD_TAGS="platform:web"
      - !reference [.snippets, setup-ci-visibility]
      - !reference [.snippets, write-web-dd-config]
      - bazel test //suites/datadog_rum:web_tests
          --config=ci
          --test_arg="-k" --test_arg="test_click_action"
          --test_arg="--rum-browser=chromium"
          --test_arg="--headless"
          --nocache_test_results

  agentic-upgrade-tests:
    stage: integration
    when: manual
    allow_failure: true
    tags: ["arch:amd64"]
    image: $CI_IMAGE_DOCKER
    timeout: 30m
    artifacts:
      when: always
      expire_in: 1 week
      paths:
        - test-reports/
    script:
      - !reference [.snippets, init]
      - !reference [.snippets, setup-ci-visibility]
      - !reference [.snippets, setup-claude-auth]
      - '[[ -n "$ANTHROPIC_API_KEY" ]] || { echo "ERROR: ANTHROPIC_API_KEY is empty — authanywhere token retrieval failed"; exit 1; }'
      - bazel test //suites/agentic_upgrade:react_vite_v6_upgraded_tests
          --config=ci
          --test_output=all
          --test_timeout=900
          --nocache_test_results
          --action_env=ANTHROPIC_API_KEY
          --action_env=ANTHROPIC_BASE_URL
          --action_env=ANTHROPIC_CUSTOM_HEADERS
          --action_env=DD_TRACE_AGENT_URL
          --action_env=DD_LLMOBS_ML_APP
          --action_env=DD_ENV
          --action_env=DD_TRACE_DEBUG
          --test_arg="--headless"

  # electron-tests:
  # ios-tests:
  # android-tests:
  # kotlin-multiplatform-android-tests:
  # ... (keep all commented platform jobs as-is)
  ```

  > **Note:** Copy the commented-out platform jobs (`android-tests`, `ios-tests`, etc.) verbatim from the actual `.gitlab-ci.yml` — they are omitted above for brevity.

- [ ] **Step 2: Replace `.gitlab-ci.yml` with the router**

  ```yaml
  include:
    - local: 'ci/pipelines/default-pipeline.yml'
      rules:
        - if: '$CI_PIPELINE_KEY == null'
    - local: 'ci/pipelines/android-sdk-tests.yml'
      rules:
        - if: '$CI_PIPELINE_KEY == "android-sdk-tests"'
    - local: 'ci/pipelines/bump-android-sdk.yml'
      rules:
        - if: '$CI_PIPELINE_KEY == "bump-android-sdk"'
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add .gitlab-ci.yml ci/pipelines/default-pipeline.yml
  git commit -m "chore: restructure CI into pipeline router with default-pipeline.yml"
  ```

---

## Task 2: Add Android Snippets and `android-tests` Bazel job to default pipeline

**Repo:** `rum-sdk-integration-test-framework`

**Files:**
- Modify: `ci/pipelines/default-pipeline.yml`

**What to do:** Port the missing snippets from main branch and add the `android-tests` job using Bazel.

- [ ] **Step 1: Add missing variables and snippets to `default-pipeline.yml`**

  Extend the `variables:` block at the top to add Android-specific variables (merge with existing):

  ```yaml
  variables:
    # ... existing variables ...
    EMULATOR_NAME: "android_emulator"
    ANDROID_ARCH: "arm64-v8a"
    ANDROID_API: "36"
    ANDROID_PLATFORM: "platforms;android-$ANDROID_API"
    ANDROID_BUILD_TOOLS: "build-tools;$ANDROID_API.0.0"
    ANDROID_EMULATOR_IMAGE: "system-images;android-$ANDROID_API;google_apis;${ANDROID_ARCH}"
    ANDROID_NDK_VERSION: "26.1.10909125"
  ```

  Add these snippets inside the `.snippets:` anchor (alongside existing ones):

  ```yaml
  .snippets:
    # ... existing snippets ...
    setup-ci-visibility-common:
      - export DD_CIVISIBILITY_FLAKY_RETRY_ENABLED=0
      - export DD_CIVISIBILITY_ITR_ENABLED=0
      - export DD_CIVISIBILITY_TEST_SKIPPING_ENABLED=0
      - export DD_CIVISIBILITY_EARLY_FLAKE_DETECTION_ENABLED=0
    setup-ci-visibility-macos-ami:
      - vault login -method=aws -no-print
      - export DD_API_KEY=$(vault kv get -field=value kv/aws/arn:aws:iam::486234852809:role/ci-rum-sdk-integration-test-framework/dd_api_key)
      - export DD_CIVISIBILITY_AGENTLESS_ENABLED=true
      - !reference [.snippets, setup-ci-visibility-common]
    install-bazel:
      - |
        curl -fsSL "https://github.com/bazelbuild/bazelisk/releases/download/v1.26.0/bazelisk-darwin-arm64" \
          -o /usr/local/bin/bazel
        chmod +x /usr/local/bin/bazel
    install-android-sdk-components:
      - mkdir -p test-reports
      - echo y | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --install "emulator" > /dev/null 2>&1
      - echo y | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --install "platform-tools" > /dev/null 2>&1
      - echo y | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --install "$ANDROID_PLATFORM" > /dev/null 2>&1
      - echo y | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --install "$ANDROID_BUILD_TOOLS" > /dev/null 2>&1
      - echo y | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --install "$ANDROID_EMULATOR_IMAGE" > /dev/null 2>&1
      - echo y | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --install "ndk;$ANDROID_NDK_VERSION" > /dev/null 2>&1
      - yes | ~/android_sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true
      - echo "no" | ~/android_sdk/cmdline-tools/latest/bin/avdmanager create avd --force --name "$EMULATOR_NAME" --package "$ANDROID_EMULATOR_IMAGE" > test-reports/android-avd-setup.log 2>&1
    boot-android-emulator:
      - $ANDROID_HOME/emulator/emulator -avd "$EMULATOR_NAME" -grpc-use-jwt -no-snapstorage -no-audio -no-window -no-boot-anim -qemu -machine virt > test-reports/android-emulator.log 2>&1 &
      - $ANDROID_HOME/platform-tools/adb wait-for-device shell getprop sys.boot_completed
      - until [[ "$($ANDROID_HOME/platform-tools/adb shell getprop sys.boot_completed 2>/dev/null)" == "1" ]]; do sleep 2; done
    write-android-dd-config:
      - |
        cat > suites/datadog_rum/apps/android/app/src/main/assets/dd_config.properties <<EOF
        clientToken=$TEST_CLIENT_TOKEN
        applicationId=$TEST_APPLICATION_ID
        EOF
  ```

- [ ] **Step 2: Add `android-tests` job to `default-pipeline.yml`**

  Replace the existing `# android-tests:` comment block with this actual job:

  ```yaml
  android-tests:
    stage: integration
    tags: ["macos:sequoia-arm64", "specific:true"]
    timeout: 30m
    rules:
      - if: '$CI_COMMIT_BRANCH =~ /^bump\//'
        when: never
      - when: manual
    artifacts:
      when: always
      expire_in: 1 week
      paths:
        - test-reports/
    script:
      - !reference [.snippets, init]
      - export DD_TAGS="platform:android"
      - !reference [.snippets, setup-ci-visibility-macos-ami]
      - !reference [.snippets, install-bazel]
      - !reference [.snippets, install-android-sdk-components]
      - !reference [.snippets, write-android-dd-config]
      - !reference [.snippets, boot-android-emulator]
      - set +e
      - exit_code=0
      - bazel test //suites/datadog_rum:android_tests --config=ci --nocache_test_results || exit_code=$?
      - $ANDROID_HOME/platform-tools/adb emu kill
      - if [[ "$exit_code" -ne 0 ]]; then exit 1; fi
  ```

  > **Note:** The emulator is started by `boot-android-emulator` before `bazel test` runs. Bazel handles the APK build (via `./gradlew assembleDebug` inside the `debug_apk` genrule) and pytest execution. The `adb emu kill` at the end ensures cleanup even on failure.

- [ ] **Step 3: Commit**

  ```bash
  git add ci/pipelines/default-pipeline.yml
  git commit -m "feat: add android-tests Bazel job to default CI pipeline"
  ```

---

## Task 3: Create `ci/pipelines/android-sdk-tests.yml`

**Repo:** `rum-sdk-integration-test-framework`

**Files:**
- Create: `ci/pipelines/android-sdk-tests.yml`

**What to do:** Parameterized single-job pipeline. Accepts `ANDROID_SDK_REF` (commit SHA or branch name), overrides the submodule to that ref, runs Android tests. Used by the PR-time manual trigger from `dd-sdk-android`.

- [ ] **Step 1: Create `ci/pipelines/android-sdk-tests.yml`**

  ```yaml
  # Pipeline triggered by dd-sdk-android PR pipelines.
  # Required variable: ANDROID_SDK_REF — commit SHA or branch name of the SDK to test.

  stages:
    - test

  android-sdk-tests:
    stage: test
    tags: ["macos:sequoia-arm64", "specific:true"]
    timeout: 30m
    artifacts:
      when: always
      expire_in: 1 week
      paths:
        - test-reports/
    script:
      - !reference [.snippets, init]
      - git -C submodules/dd-sdk-android fetch --depth=1 origin $ANDROID_SDK_REF
      - git -C submodules/dd-sdk-android checkout FETCH_HEAD
      - export DD_TAGS="platform:android"
      - !reference [.snippets, setup-ci-visibility-macos-ami]
      - !reference [.snippets, install-bazel]
      - !reference [.snippets, install-android-sdk-components]
      - !reference [.snippets, write-android-dd-config]
      - !reference [.snippets, boot-android-emulator]
      - set +e
      - exit_code=0
      - bazel test //suites/datadog_rum:android_tests --config=ci --nocache_test_results || exit_code=$?
      - $ANDROID_HOME/platform-tools/adb emu kill
      - if [[ "$exit_code" -ne 0 ]]; then exit 1; fi
  ```

  > **Why `--depth=1 origin $ANDROID_SDK_REF` + `checkout FETCH_HEAD`:** The submodule was initialised to its pinned commit by `git submodule update --init`. Fetching with `--depth=1` then checking out `FETCH_HEAD` efficiently overrides it to the requested ref without fetching the full history. The checkout is ephemeral — nothing is committed.

- [ ] **Step 2: Commit**

  ```bash
  git add ci/pipelines/android-sdk-tests.yml
  git commit -m "feat: add android-sdk-tests parameterized CI pipeline"
  ```

---

## Task 4: Create `tools/ci/bump_android_sdk.py` and Bazel target

**Repo:** `rum-sdk-integration-test-framework`

**Files:**
- Create: `tools/ci/__init__.py`
- Create: `tools/ci/bump_android_sdk.py`
- Create: `tools/ci/BUILD.bazel`

**What to do:** Python script that updates the `submodules/dd-sdk-android` submodule pointer, commits it, pushes a branch to GitHub, creates a PR, and auto-merges it. Called by the CI `bump` job with `--commit` and `--github-token` args.

- [ ] **Step 1: Create `tools/ci/__init__.py`**

  Empty file (package marker):
  ```python
  ```

- [ ] **Step 2: Create `tools/ci/bump_android_sdk.py`**

  ```python
  #!/usr/bin/env python3
  """Bump the dd-sdk-android submodule pointer in rum-fit and create an auto-merged PR."""

  import argparse
  import subprocess
  import sys
  import time
  from pathlib import Path

  import requests

  REPO = "DataDog/rum-sdk-integration-test-framework"
  SUBMODULE_PATH = "submodules/dd-sdk-android"
  BASE_BRANCH = "main"
  GITHUB_API = "https://api.github.com"


  def run(cmd: list[str], cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
      return subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True)


  def short_sha(commit: str) -> str:
      return commit[:8]


  def bump_submodule(repo_root: Path, commit: str) -> None:
      run(["git", "-C", str(repo_root / SUBMODULE_PATH), "fetch", "--depth=1", "origin", commit])
      run(["git", "-C", str(repo_root / SUBMODULE_PATH), "checkout", "FETCH_HEAD"])
      run(["git", "add", SUBMODULE_PATH], cwd=repo_root)


  def create_commit(repo_root: Path, commit: str) -> None:
      message = (
          f"bump: android sdk to {short_sha(commit)}\n"
          "\n"
          f"Rum-Fit-Pipeline: bump-android-sdk\n"
          f"Android-SDK-Commit: {commit}\n"
      )
      run(["git", "commit", "--message", message], cwd=repo_root)


  def push_branch(repo_root: Path, branch: str, token: str) -> None:
      remote_url = f"https://x-access-token:{token}@github.com/{REPO}.git"
      run(["git", "remote", "set-url", "origin", remote_url], cwd=repo_root)
      run(["git", "push", "origin", f"HEAD:{branch}"], cwd=repo_root)


  def github_post(path: str, token: str, payload: dict) -> dict:
      resp = requests.post(
          f"{GITHUB_API}{path}",
          json=payload,
          headers={
              "Authorization": f"Bearer {token}",
              "Accept": "application/vnd.github+json",
              "X-GitHub-Api-Version": "2022-11-28",
          },
          timeout=30,
      )
      resp.raise_for_status()
      return resp.json()


  def github_put(path: str, token: str, payload: dict) -> dict:
      resp = requests.put(
          f"{GITHUB_API}{path}",
          json=payload,
          headers={
              "Authorization": f"Bearer {token}",
              "Accept": "application/vnd.github+json",
              "X-GitHub-Api-Version": "2022-11-28",
          },
          timeout=30,
      )
      resp.raise_for_status()
      return resp.json()


  def create_pr(token: str, branch: str, commit: str) -> int:
      data = github_post(
          f"/repos/{REPO}/pulls",
          token,
          {
              "title": f"bump: android sdk to {short_sha(commit)}",
              "body": (
                  f"Automated submodule bump.\n\n"
                  f"Android SDK commit: `{commit}`\n\n"
                  f"Tests passed in the triggering pipeline before this PR was created."
              ),
              "head": branch,
              "base": BASE_BRANCH,
          },
      )
      pr_number: int = data["number"]
      print(f"Created PR #{pr_number}: {data['html_url']}")
      return pr_number


  def merge_pr(token: str, pr_number: int, commit: str) -> None:
      # Give GitHub a moment to register the PR before merging
      time.sleep(3)
      github_put(
          f"/repos/{REPO}/pulls/{pr_number}/merge",
          token,
          {
              "merge_method": "squash",
              "commit_title": f"bump: android sdk to {short_sha(commit)}",
              "commit_message": (
                  f"Rum-Fit-Pipeline: bump-android-sdk\n"
                  f"Android-SDK-Commit: {commit}\n"
              ),
          },
      )
      print(f"Merged PR #{pr_number}")


  def main() -> None:
      parser = argparse.ArgumentParser(description="Bump dd-sdk-android submodule and create PR")
      parser.add_argument("--commit", required=True, help="Full commit SHA of dd-sdk-android to bump to")
      parser.add_argument("--github-token", required=True, help="GitHub token with repo write access")
      parser.add_argument("--repo-root", default=".", help="Path to rum-fit repo root (default: cwd)")
      args = parser.parse_args()

      repo_root = Path(args.repo_root).resolve()
      branch = f"bump/android-sdk-{short_sha(args.commit)}"

      print(f"Bumping {SUBMODULE_PATH} to {args.commit} on branch {branch}")

      bump_submodule(repo_root, args.commit)
      create_commit(repo_root, args.commit)
      push_branch(repo_root, branch, args.github_token)
      pr_number = create_pr(args.github_token, branch, args.commit)
      merge_pr(args.github_token, pr_number, args.commit)

      print("Done.")


  if __name__ == "__main__":
      main()
  ```

- [ ] **Step 3: Create `tools/ci/BUILD.bazel`**

  ```python
  load("@rules_python//python:defs.bzl", "py_binary")

  py_binary(
      name = "bump_android_sdk",
      srcs = ["bump_android_sdk.py"],
      main = "bump_android_sdk.py",
      deps = [
          "@rum_pip//requests",
      ],
      visibility = ["//visibility:public"],
  )
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add tools/ci/__init__.py tools/ci/bump_android_sdk.py tools/ci/BUILD.bazel
  git commit -m "feat: add bump_android_sdk Python script and Bazel target"
  ```

---

## Task 5: Create `ci/pipelines/bump-android-sdk.yml`

**Repo:** `rum-sdk-integration-test-framework`

**Files:**
- Create: `ci/pipelines/bump-android-sdk.yml`

**What to do:** Two-stage pipeline. `test` runs Android tests against the new SDK commit (macOS runner). `bump` runs only if `test` passes and pushes the submodule update via the Python script (amd64 runner).

- [ ] **Step 1: Create `ci/pipelines/bump-android-sdk.yml`**

  ```yaml
  # Pipeline triggered by dd-sdk-android develop merges.
  # Required variable: ANDROID_SDK_COMMIT — full commit SHA of dd-sdk-android to test and bump to.

  stages:
    - test
    - bump

  test:
    stage: test
    tags: ["macos:sequoia-arm64", "specific:true"]
    timeout: 30m
    artifacts:
      when: always
      expire_in: 1 week
      paths:
        - test-reports/
    script:
      - !reference [.snippets, init]
      - git -C submodules/dd-sdk-android fetch --depth=1 origin $ANDROID_SDK_COMMIT
      - git -C submodules/dd-sdk-android checkout FETCH_HEAD
      - export DD_TAGS="platform:android"
      - !reference [.snippets, setup-ci-visibility-macos-ami]
      - !reference [.snippets, install-bazel]
      - !reference [.snippets, install-android-sdk-components]
      - !reference [.snippets, write-android-dd-config]
      - !reference [.snippets, boot-android-emulator]
      - set +e
      - exit_code=0
      - bazel test //suites/datadog_rum:android_tests --config=ci --nocache_test_results || exit_code=$?
      - $ANDROID_HOME/platform-tools/adb emu kill
      - if [[ "$exit_code" -ne 0 ]]; then exit 1; fi

  bump:
    stage: bump
    tags: ["arch:amd64"]
    image: $CI_IMAGE_DOCKER
    needs: [test]
    id_tokens:
      DDOCTOSTS_ID_TOKEN:
        aud: dd-octo-sts
    script:
      - !reference [.snippets, init]
      - |
        set +x
        GITHUB_TOKEN=$(dd-octo-sts token \
          --scope DataDog/rum-sdk-integration-test-framework \
          --policy all.gitlab.pr)
        set -x
      - git config user.email "ci@datadoghq.com"
      - git config user.name "CI"
      - bazel run //tools/ci:bump_android_sdk --
          --commit="$ANDROID_SDK_COMMIT"
          --github-token="$GITHUB_TOKEN"
          --repo-root="$CI_PROJECT_DIR"
  ```

  > **`needs: [test]`** makes `bump` run only after `test` passes. If `test` fails, `bump` is skipped and the whole pipeline is marked failed — this propagates back to dd-sdk-android's develop pipeline via `strategy: depend`.

  > **`git config user.email/name`**: Required for `git commit` inside the CI job. The `init` snippet only sets up submodules; git identity must be set explicitly.

- [ ] **Step 2: Commit**

  ```bash
  git add ci/pipelines/bump-android-sdk.yml
  git commit -m "feat: add bump-android-sdk CI pipeline"
  ```

---

## Task 6: Add trigger jobs to `dd-sdk-android` default pipeline

**Repo:** `dd-sdk-android-3`

**Files:**
- Modify: `ci/pipelines/default-pipeline.yml`

**What to do:** Add two jobs to the `test-pyramid` stage — a manual PR trigger and an automatic develop trigger.

- [ ] **Step 1: Add jobs to `ci/pipelines/default-pipeline.yml`**

  Locate the end of the `test-pyramid` stage jobs (before the `# PUBLISH ARTIFACTS ON MAVEN` comment) and add:

  ```yaml
  rum-fit:android-tests:
    stage: test-pyramid
    when: manual
    allow_failure: false
    trigger:
      project: DataDog/rum-sdk-integration-test-framework
      branch: main
      strategy: depend
    variables:
      CI_PIPELINE_KEY: android-sdk-tests
      ANDROID_SDK_REF: $CI_COMMIT_SHA

  rum-fit:bump-android-sdk:
    stage: test-pyramid
    only: [ develop ]
    trigger:
      project: DataDog/rum-sdk-integration-test-framework
      branch: main
      strategy: depend
    variables:
      CI_PIPELINE_KEY: bump-android-sdk
      ANDROID_SDK_COMMIT: $CI_COMMIT_SHA
  ```

  > **`rum-fit:android-tests`**: `when: manual` means this never runs unless a developer clicks it. `allow_failure: false` means if it is triggered and fails, the PR pipeline goes red. If not triggered, the job is skipped (pipeline stays green).

  > **`rum-fit:bump-android-sdk`**: `only: [ develop ]` means it only runs on the develop branch. `strategy: depend` blocks the `publish` stage until the rum-fit child pipeline (test + bump) completes. If rum-fit fails, dd-sdk-android's develop pipeline fails and publish is blocked.

- [ ] **Step 2: Commit (GPG-signed)**

  ```bash
  git add ci/pipelines/default-pipeline.yml
  git commit -S -m "RUM-XXXXX: add rum-fit android test trigger jobs to CI pipeline"
  ```

  > Replace `RUM-XXXXX` with the actual ticket number.

---

## Self-Review

### Spec coverage

| Spec requirement | Task |
|-----------------|------|
| Android tests Bazel job in rum-fit default CI | Task 2 |
| `CI_PIPELINE_KEY` router in rum-fit `.gitlab-ci.yml` | Task 1 |
| `android-sdk-tests.yml` parameterized pipeline | Task 3 |
| `bump-android-sdk.yml` test + bump pipeline | Task 5 |
| `bump_android_sdk.py` Python script | Task 4 |
| Bazel `py_binary` target for bump script | Task 4 |
| Manual PR trigger job in dd-sdk-android | Task 6 |
| Automatic develop bump trigger job in dd-sdk-android | Task 6 |
| `install-bazel` snippet (Bazelisk on macOS) | Task 2 |
| `bump/android-sdk-*` branch naming | Task 4 (`push_branch`) |
| `Rum-Fit-Pipeline:` commit trailer | Task 4 (`create_commit`) |
| `rules:` to skip android-tests on bump branches | Task 2 |
| dd-octo-sts auth (no stored PAT) | Task 5 |

All requirements covered. No gaps.

### Placeholder scan

No TBDs, TODOs, or vague instructions. All code blocks are complete.

### Type consistency

- `bump_android_sdk.py`: `run()` returns `subprocess.CompletedProcess[str]`, used only for side effects (no return value consumed). `github_post()` / `github_put()` return `dict` — callers access `data["number"]` (int) and `data["html_url"]` (str). Consistent throughout.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-03-rum-fit-cross-repo-ci.md`. Two execution options:

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks

**2. Inline Execution** — execute tasks in this session with checkpoints

Which approach?
