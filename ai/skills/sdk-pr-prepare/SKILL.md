---
name: sdk-pr-prepare
description: >-
  Pre-PR preparation flow for the dd-sdk-android project — verifies branch naming, runs local CI checks (compile, tests, linter) and regenerates checked-in generated files so CI passes on the first push. Accepts optional combinable arguments: lint, test, compile, api, dep, local. Use when the user asks to "prepare PR", "get ready for PR", "run pre-PR checks", or invokes /sdk-pr-prepare before opening/pushing a pull request.
---

# sdk-pr-prepare — dd-sdk-android PR Preparation

Run the steps below **in order**. Stop on the first failing step, surface the error, and do not proceed until the user resolves it.

## Arguments

Arguments are **combinable** — the user may pass several at once (e.g. `/sdk-pr-prepare lint test`). Each argument selects which checks to run; they map to the steps below:

- `compile` — run Step 2 only.
- `test` — run formatting (Step 2) first, then Step 3's test part only, with error fixing and restart on failure.
- `lint` — run formatting (Step 2) first, then Step 3's lint part only, then apiSurface and transitiveDependencies validation.
- `api` — run Step 4's apiSurface part only (regeneration + validation).
- `dep` — run Step 4's transitive dependencies part only (regeneration).
- `local` — local-only mode. **Skip Step 7 (push) and Step 8 (create draft PR)** regardless of which checks run. Use when the user invokes `/sdk-pr-prepare local` or otherwise passes `local` in the arguments. In this mode, Step 9 reports that push/PR were intentionally skipped. `local` is a modifier, not a check selector: ignore it when deciding which checks to run. If no check arguments remain, run the full default flow.

**No arguments** — run the full default flow (Steps 2–4): first format and compile (Step 2), then **in parallel** one lint subagent and one test subagent (Step 3, with error fixing and restart as needed), then **sequentially** apiSurface regeneration followed by transitiveDependencies regeneration (Step 4), and validation after the commit (Step 6).

When specific check arguments are given, run only the corresponding parts (plus Step 0/1 branch checks). The commit step (Step 6) still runs if any of the executed steps modified tracked files.

## Step 0 — Protected / release branch guard

```bash
git branch --show-current
```

If the current branch matches any of:

- `main`
- `master`
- `develop`
- A release branch: anything matching the regex `^release/\d+\.\d+\.\d+$` (e.g. `release/2.1.0`, `release/10.4.12`),

do NOT silently continue. Use `AskUserQuestion` to confirm:

> Question: "You are on a protected branch (`<branch>`). prepare-pr will push changes and open a PR from this branch — that's usually not what you want. Continue anyway?"
> Options:
> - **Abort (Recommended)** — stop the skill; switch to a feature branch manually and re-run.
> - **Continue on `<branch>`** — proceed anyway (user takes responsibility for any push consequences).

- If the user picks **Abort**, exit the skill with a short message.
- If the user picks **Continue**, proceed to Step 1. Remember the override so later steps (push, PR creation) do not ask again.

Do not short-circuit this check; always surface the question when on one of the listed branches.

## Step 1 — Verify branch name

Expected format from `AGENTS.md`: `<username>/<ticket>/<short-description>` (e.g. `tvaleev/rum-1234/prepare-stack`).

```bash
git branch --show-current
```

If the current branch does not match the pattern, surface a warning before proceeding (this is a soft check — only Step 0 hard-aborts).

## Step 2 — Format and compile (shared artifacts)

Format sources first, then run clean + compile once to produce all build artifacts. Both commands are sequential, so the parallel Step 3 builds never see sources being rewritten. **Skip if only `api` or `dep` arguments were given** (they do not need compiled artifacts). When only `test` or `lint` was given, run just the format command.

```bash
./gradlew ktlintFormatAll
./gradlew clean assembleLibrariesDebug assembleDebugUnitTest :instrumented:integration:assembleDebugAndroidTest
```

## Step 3 — Run tests and linter

Run only the parts selected by the arguments (both, one of them, or neither). When both tests and lint are needed (default flow), run them as **two separate subagents running in parallel**, without `clean`. They reuse the artifacts produced by Step 2.

**Test subagent:**
```bash
./gradlew unitTestAll :build-logic:test
```

**Lint subagent (run concurrently with the test subagent when both are requested):**
```bash
./gradlew detekt checkTestPyramidCoverage lintCheckAll checkDependencyLicensesAll --continue
```

Analyze each result independently. On `FAILED`, inspect the error output, fix the issues, then **repeat from Step 2** (re-compile, then re-run Step 3 in parallel).

- If **all requested parts pass** → proceed to Step 4.

**When the `lint` argument is given**, run the lint part as a single sequence (not in parallel with tests):

1. The lint subagent command above
2. Generated files validation (the validate command from Step 6)

On any failure, fix the issues and re-run the failed part. When `lint` is combined with `test`, the test subagent still runs in parallel with this sequence.

**Never suppress linter issues** with `@Suppress("UnsafeThirdPartyFunctionCall")`:

- If the method can throw — add its signature to `detekt_custom_unsafe_calls.yml` with the thrown `Exception` class. Follow the existing file format.
- If the method never throws — add its signature to `detekt_custom_safe_calls_third_party.yml` (Kotlin stdlib / Java collections) or `detekt_custom_safe_calls_android.yml` (Android APIs). Do NOT use `detekt_custom_safe_calls.yml` — it does not exist.

Common issues to fix directly:
- `max-line-length` — lines over 120 chars
- `TooManyFunctions` — split the class
- `NestedBlockDepth` — extract helpers
- `ReturnCount` — restructure control flow

**Do not proceed until all requested checks in this step pass.**

## Step 4 — Regenerate apiSurface and transitive dependencies

Run these **sequentially, apiSurface first**. They cannot run in parallel: the transitive
dependencies step runs `./gradlew clean`, which deletes the compiled classes and generated sources
that `generateApiSurface` / `apiDump` read.

Only start these after Step 3 passes (with all fixes and retries if needed).

**When `api` and/or `dep` arguments are given**, run only the corresponding part(s) — still in the
order below, never concurrently. When `lint` was given, its validation steps already covered these;
skip regeneration unless it failed.

**apiSurface and metadata subagent:**
```bash
find ./ -name "apiSurface" -not -path '*/build/*' -exec rm {} \;
./gradlew clean
./gradlew --no-build-cache --rerun-tasks --stacktrace generateApiSurface
./gradlew --no-build-cache apiDump
./gradlew generateCompilerMetadata generateAarMetadataInfo
```

The last line regenerates `api/compiler-meta.txt` and `api/aar-metadata.txt`.

`clean`, `--no-build-cache` and `--rerun-tasks` are all required, and none of them is redundant:

- JSON models are generated into `build/generated/generate*ModelsFromJson`, and the generator never
  deletes outputs, so models of removed schemas survive until `clean` wipes `build/`.
- `generateJsonSchema` tasks are `@CacheableTask`, and their cache key does not discriminate the
  current schema set. With the build cache on, the task resolves `FROM-CACHE` and restores a stale
  model set — including classes with no schema on disk.
- `generateApiSurface` scans the generated sources directory, so those phantom classes land in the
  committed `apiSurface` / `<module>.api`. CI generates from a cold cache, gets fewer classes, and
  `checkApiSurfaceChanges` fails with `-data class …` lines for classes that only ever existed
  locally.

Sanity-check the generated model set against the schemas before committing, e.g. for RUM:
```bash
ls features/dd-sdk-android-rum/build/generated/generateRumModelsFromJson/com/datadog/android/rum/model/
ls features/dd-sdk-android-rum/src/main/json/rum/
```
Every generated model must map to a non-ignored schema file. A model with no schema means the
`clean` or the cache flag did not take effect — do not commit that `apiSurface`.

**Transitive dependencies (only after the apiSurface step above has finished and been committed or staged):**
```bash
find ./ -name "transitiveDependencies" -not -path '*/build/*' -exec rm {} \;
./gradlew clean
./gradlew --rerun-tasks generateTransitiveDependenciesList
```

On `FAILED`, surface the error and stop.

## Step 6 — Commit generated changes

The executed steps may have modified tracked files (`apiSurface`, `*.api`, `compiler-meta.txt`, `aar-metadata.txt`, `transitiveDependencies`, potentially linter-driven edits from Step 3). Check:

```bash
git status --porcelain
```

If the working tree is clean, skip to the validation at the end of this step.

Otherwise:

1. **Load the sibling [sdk-commit](../sdk-commit/SKILL.md) skill** via the `Skill` tool or read its SKILL.md. All commit messages MUST follow its rules.
2. Stage only files touched by the prepare-pr flow (typically `**/api/*`, `**/transitiveDependencies`, detekt config files, and any source edits you made to fix linter issues). Do not blind `git add -A` — other unrelated changes may still be in the tree.
3. Draft a commit message per the `sdk-commit` skill. The subject is imperative, English, ≤72 chars, and follows `<TICKET-ID>: <description>` (e.g. `RUM-1234: Regenerate apiSurface and fix detekt warnings`). If the ticket cannot be determined, ask the user before committing. The body lists what was regenerated / fixed and why.
4. Show the drafted message to the user via `AskUserQuestion` (Approve / Edit / Abort commit) before running `git commit`.
5. Commit with HEREDOC so formatting survives:
   ```bash
   git commit -m "$(cat <<'EOF'
   <subject>

   <body>
   EOF
   )"
   ```

Then validate the generated files against the commit (also when there was nothing to commit):
```bash
./gradlew --stacktrace checkGeneratedFiles
```

`checkGeneratedFiles` covers all checked-in generated files (API surfaces, compiler and AAR metadata, transitive dependencies, licenses). Its checks run `git diff HEAD -- <file>`, so they only pass once the regenerated files are committed:
`+` lines are in the freshly generated file and missing from the commit, `-` lines are in the
commit and missing from the generated file. On failure, regenerate (Step 4), commit, and re-run.

## Step 7 — Push the branch

**Skip this step entirely if `local` was passed in the arguments.**

```bash
git push -u origin "$(git branch --show-current)"
```

If the push fails due to non-fast-forward (someone force-pushed upstream), surface the error and stop — do not force-push automatically.

## Step 8 — Open a draft PR

**Skip this step entirely if `local` was passed in the arguments.**

1. Detect the base branch: use `git config --get branch.<current>.merge` (strip `refs/heads/`) or fall back to `develop` if it exists, otherwise `main`. If the result is the current branch itself (the usual case after `git push -u` in Step 7), ask for the base via `AskUserQuestion` with options `develop` (Recommended) and `main`, plus "Other" for a custom branch.
2. **Load the sibling [sdk-commit](../sdk-commit/SKILL.md) skill** if it was not loaded in Step 6; otherwise reuse its rules for keeping PR descriptions separate from commit bodies.
3. Detect a PR template in the repo (in order):
   - `.github/PULL_REQUEST_TEMPLATE.md`
   - `.github/pull_request_template.md`
   - `docs/PULL_REQUEST_TEMPLATE.md`
   - `PULL_REQUEST_TEMPLATE.md` at repo root
   - Any file under `.github/PULL_REQUEST_TEMPLATE/`
4. Draft the PR body — **mandatory, never empty**:
   - If a template exists: fill every section; do NOT delete or reorder any heading; write `N/A` for inapplicable sections. Following the template structure is not optional.
   - Otherwise: 2–4 sentences in English covering **what changed**, **why**, and **notes** (tests, migration, ticket reference). Append `Ref: <TICKET-ID>` on its own line when derivable from the branch name.
   - An empty or placeholder-only body is never acceptable — the user may edit wording but **may not approve a blank description**.
5. Derive the PR title from the last commit on the branch (or from the ticket ID + short description). English, ≤72 chars. Same rules as `sdk-commit`'s header section.
6. Show title + body to the user via `AskUserQuestion` (Approve / Edit / Abort) before creating the PR.
7. Create the PR as a **draft**:
   ```bash
   gh pr create --draft --base "<base>" --head "$(git branch --show-current)" \
     --title "<title>" \
     --body "$(cat <<'EOF'
   <body>
   EOF
   )"
   ```
8. Capture the PR URL from the output and show it to the user.

If `gh pr create` fails (auth, protected base, duplicate PR), surface the error verbatim and stop.

## Step 9 — Report

Print a concise summary:

- Generated files (apiSurface / transitiveDependencies)
- Fixed issues (tests / linter) with counts
- Commit hash created in Step 6 (if any)
- Draft PR URL from Step 8 — or, in `local` mode, an explicit note that push and PR creation were skipped and that the user can run `/sdk-pr-prepare` (without `local`) to publish
- Any remaining warnings the user should look at before marking the PR ready
