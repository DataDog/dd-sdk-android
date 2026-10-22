# sdk-pr-stack create — Stacked PR creation via Graphite with optional auto-rebase

Follow the shared rules in [../SKILL.md](../SKILL.md), then execute this workflow.

All commit messages and PR titles produced by this skill MUST follow the sibling `sdk-commit` skill at [../../sdk-commit/SKILL.md](../../sdk-commit/SKILL.md). Before generating any commit message (in Steps 3.1, 3.4, and 4.2), invoke that skill via the `Skill` tool to load its current rules, then apply them to every subject/body you generate. Do this once at the start of a generation step and reuse the loaded rules for all commits in that step.

This skill turns the current feature branch into a series of stacked pull requests via the [Graphite CLI (`gt`)](https://graphite.com/docs/graphite-cli). It supports two modes:

1. **Direct** — group existing commits into logical PRs and reconstruct the gt stack (one gt branch per PR, each branch may contain multiple commits).
2. **Auto-rebase** — analyse the full diff with an LLM subagent, propose a clean commit split, group those commits into reviewable PRs, build a fresh gt stack, verify diff-equivalence, then submit.

**Key principle: one gt branch = one PR, but one PR can contain multiple commits.** Do not force a 1-commit-per-PR structure. Organise commits into PRs that make logical sense for a reviewer — target ~400 lines of changed code per PR, but never split artificially.

## PR content rules — MANDATORY

Each PR in the stack must contain only code that is logically related to the changes introduced on the branch. These rules apply when deciding how to split commits into PRs (Steps 2, 3.1, and 3.2) and must never be violated in the name of keeping PRs small:

- **Tests travel with their production code.** If a PR contains new or modified production code, its tests must be in the same PR — not split into a separate PR. Conversely, if a PR contains new or modified tests, the production code those tests cover must be in the same PR.
- **Detekt rule changes are bundled with the triggering production code.** Additions or modifications to detekt config files (e.g. `detekt_custom_unsafe_calls.yml`, `detekt_custom_safe_calls.yml`) must be in the same PR as the production code that requires the rule change — never in a standalone PR.

When the subagent in Step 3.1 proposes a split, validate the output against these rules before presenting it to the user. If any PR violates them, revise the split — either by merging the offending PRs or by moving the misplaced files — before showing it.

---

## Language rule

Conduct the entire session in the language the user used when invoking the skill (detect from the invocation message and any prior user turns). All `AskUserQuestion` labels, descriptions, and narrative updates must match that language.

Generated **commit messages and PR titles/descriptions** are always in English, regardless of session language.

This skill file itself is authored in English — do not translate it.

---

## Interactive questions — MANDATORY

Every clarifying question throughout every step MUST be asked via the `AskUserQuestion` tool. Never present choices as plain numbered text. Batch related questions when possible (up to 4 per call). The user can always pick "Other" to type a free-text response.

---

## Step 0 — Pre-flight gates

Run these checks in order. Abort on any failure with a clear message in the session language.

### 0.1 Git repository

```
git rev-parse --is-inside-work-tree
```

If this fails, abort: "This directory is not a git repository."

### 0.2 Protected branch refusal

```
git rev-parse --abbrev-ref HEAD
```

If the result is `main`, `master`, or `develop`, abort: "autostack is designed for feature branches. Please switch to a feature branch before running it."

### 0.3 Uncommitted changes

```
git status --porcelain
```

Ignore entries that are purely untracked build artefacts (`__pycache__/`, `node_modules/`, `dist/`, `build/`, `.gradle/`, `target/`) — they do not interfere with a rebase.

For any remaining non-empty output, show `git status` to the user and use `AskUserQuestion`:

- **Commit all changes** — load the `sdk-commit` skill, draft a commit message following its rules, and confirm it via a second `AskUserQuestion` (Approve / "Other" → edit). Validate an edited message against the same rules, then `git add -A && git commit -m "<msg>"`.
- **Stash** — `git stash push -u -m "autostack-<timestamp>"`; remember the stash id to suggest restoring it later.
- **Abort** — exit the skill.

### 0.4 Graphite CLI (`gt`) availability, auth, and repo init

Three sub-checks. All must pass before continuing.

**0.4.a — `gt` binary is installed**

```
command -v gt && gt --version
```

If missing, use `AskUserQuestion`:

- **Install via Homebrew (Recommended)** — confirm via `AskUserQuestion`, then run `brew install withgraphite/tap/graphite`. Re-run the check; if still missing, abort with the fallback link `https://graphite.com/docs/graphite-cli#install-the-graphite-cli`.
- **Install via npm** — run `npm install -g @withgraphite/graphite-cli@stable`. Re-run the check.
- **Abort** — exit.

**0.4.b — `gt` is authenticated**

`gt` stores its auth token in `~/.graphite_user_config` (or similar) and does not expose a dedicated `gt whoami` / `gt auth status` command. Use an indirect check:

```
gt log short 2>&1 | head -5
```

If the output contains `Please authenticate` / `Missing auth token` / `401` / similar, treat as not authenticated. A clean output (even if empty) means auth is fine.

If not authenticated, use `AskUserQuestion`:

- **Authenticate in a separate terminal (Recommended)** — tell the user:
  1. Open https://app.graphite.com/activate (or https://app.graphite.com/settings/cli).
  2. Create a new auth token.
  3. Run `gt auth --token <token>` in a separate terminal, outside this chat, and reply here once done. Never paste the token or the command into the chat: it would end up in the model context and the session transcript.

  If the user pastes a token anyway, do not echo or use it, and recommend revoking it. After the user confirms, re-run the auth check. If still failing, abort.
- **Abort** — exit the skill.

**0.4.c — Graphite is initialised for this repo**

```
gt log short 2>&1 | head -5
```

If the output contains `gt has not been initialized` / `Please run gt init` / similar, Graphite has no metadata for this repo yet. Use `AskUserQuestion`:

- **Initialise with detected trunk (Recommended)** — run `gt init --trunk "$BASE"` (where `$BASE` is resolved in 0.5 — so defer this check until after 0.5 if needed). If the command prompts interactively despite `--trunk`, tell the user to run `! gt init` and pick the trunk manually.
- **Abort** — exit.

If `gt init` fails for another reason, surface the error verbatim and abort.

### 0.5 Base-branch detection

Try these sources in order, stopping at the first success. A source that resolves to the current branch itself (a pushed feature branch tracking its own remote) does not count as a success:

1. Existing Graphite config: `gt trunk 2>/dev/null` — if it returns a branch name, use it.
2. `git rev-parse --abbrev-ref @{upstream}` — if it resolves, strip the `origin/` prefix first, then use it only if it is not the current branch.
3. `git config --get branch.<current>.remote` + `git config --get branch.<current>.merge` — strip `refs/heads/` first, then use it only if it is not the current branch.
4. Reflog inspection: `git reflog show <current>` — look for the branch this one was created from.
5. `develop` (the repository's default PR base) — use it if `git rev-parse --verify develop` succeeds.

If none of the above uniquely identifies a base branch, use `AskUserQuestion`:

- Offer each of `main`, `master`, `develop` that exists locally as an option.
- "Other" lets the user type a branch name.

**If the user-supplied branch does not exist** (`git rev-parse --verify <name>` fails): tell the user, and ask once more. Second failure → abort: "Base branch could not be resolved. Exiting."

Save the resolved base branch name as `$BASE`. If Graphite was not yet initialised (0.4.c), initialise now with `gt init --trunk "$BASE"`.

### 0.6 Merge-commit refusal

```
git log --merges $BASE..HEAD
```

If the output is non-empty, abort: "This branch contains merge commits; autostack supports linear history only. Please rebase/linearise manually before retrying."

### 0.7 Single-commit branch

Run `git log --oneline $BASE..HEAD | wc -l`. Note the commit count for later decisions — it does not block progress. A one-commit branch is still allowed to enter auto-rebase mode (the diff may be large and worth splitting).

---

## Step 1 — Review and confirm detected state

Show the user a summary of everything detected so far:

- Current branch, resolved base branch, Graphite trunk.
- Commit count on the branch.
- Total lines changed: `git diff --shortstat $BASE...HEAD`.
- Graphite / git status checks (clean tree, no merge commits, gt auth).

Then go through the following confirmation questions **in order** — do not batch them, as each answer may affect the next.

### 1.1 Confirm base branch

Use `AskUserQuestion`:

- **`<detected base branch>` — looks correct, proceed**
- **Other** — user types a different branch name; update `$BASE` and re-run `git diff --shortstat` with the new base before continuing.

### 1.2 File exclusions

Run:

```
git diff --name-only $BASE...HEAD
```

Display the **complete output** as a plain list in the conversation — do not summarise, truncate, or paraphrase it. Every file must be visible to the user.

Use `AskUserQuestion`:

- **No exclusions — include all files**
- **Exclude files** — "Other" → user types a space- or newline-separated list of paths or glob patterns (e.g. `package-lock.json`, `*.generated.ts`, `dist/`).

Save the resolved patterns as the shell array `EXCLUDED` (`EXCLUDED=()` if none). Build a separate pathspec array once:

```bash
EXCLUDE_PATHSPECS=()
for stack_excluded_pattern in "${EXCLUDED[@]}"; do
    EXCLUDE_PATHSPECS+=(":(exclude)$stack_excluded_pattern")
done
```

All subsequent diff and staging operations MUST skip these files:
- In `git diff` calls: append `-- . "${EXCLUDE_PATHSPECS[@]}"` to exclude them.
- In staging (Step 3.4): skip files matching the patterns in `EXCLUDED` when running `git add` or `git apply`.
- In the subagent prompt (Step 3.1): do not include excluded files in the diff or file stats passed to the subagent.
- If exclusions are non-empty, use auto-rebase mode. Direct mode cherry-picks whole commits and cannot filter their contents.

### 1.3 Mode selection

Show the updated diff stat with exclusions applied:

```
git diff --shortstat "$BASE"...HEAD -- . "${EXCLUDE_PATHSPECS[@]}"
```

Use `AskUserQuestion`:

- **Direct stacked PR** — group existing commits into logical PRs and rebuild the stack. Fastest; best when commit history is already clean.
- **Auto-rebase then stacked PR** — let the skill reorganise the diff into atomic commits grouped into reviewable PRs before building the stack.

Branch on the choice.

---

## Step 2 — Direct mode

Commits are grouped into PRs — do not create one PR per commit.

1. Capture the commit list oldest→newest:
   ```
   COMMITS=$(git log --reverse --format="%H %s" $BASE..HEAD)
   ```
2. Record source state:
   ```
   SRC_BRANCH=$(git rev-parse --abbrev-ref HEAD)
   ```
3. Show the commit list to the user and propose a PR grouping — group consecutive commits that belong to the same logical feature or concern into one PR. Apply the ~400 lines per PR guideline. Present the grouping via `AskUserQuestion` (Approve / Edit).
4. Proceed to **Step 4 — Build the gt stack** using the approved PR groups.

---

## Step 3 — Auto-rebase mode

### 3.1 Delegate diff analysis to a subagent

Large diffs would otherwise pollute the main context and degrade later decisions. Always delegate, regardless of diff size — consistency matters more than a few saved tokens.

**Before invoking the subagent, load the `sdk-commit` skill** (via the `Skill` tool) and read the full text of its SKILL.md. You MUST inline the commit skill rules verbatim into the subagent prompt (below the TASK section, in a `## sdk-commit convention` block) so the subagent formats every `title` and `pr_title` accordingly. Do not paraphrase — copy the rules as-is.

Gather inputs (excluding `$EXCLUDED` files if any):

```
git diff "$BASE"...HEAD -- . "${EXCLUDE_PATHSPECS[@]}"          # full patch
git diff --stat "$BASE"...HEAD -- . "${EXCLUDE_PATHSPECS[@]}"   # per-file stats
git log --oneline "$BASE"..HEAD                               # existing commit list
```

Invoke the `Agent` tool with `subagent_type: "general-purpose"`. Prompt template:

```
You are analysing a git diff to propose a clean commit organisation into stacked PRs.

Base branch: $BASE
Current branch: $CURRENT
Total files changed: <N>, lines: +<A> / -<D>
Existing commit subjects (for style reference):
<list>

Full diff (git diff $BASE...HEAD):
<DIFF>

TASK — organise the diff into stacked PRs. Each PR:
- represents a coherent, self-contained unit of work a reviewer can understand independently,
- may contain one or more commits,
- targets ~400 lines of changed code (additions + deletions). If a logical unit exceeds 400 lines,
  try to split it into sub-PRs. If a clean split is not possible without breaking compilation or
  logical cohesion, keep it as a single PR — never force artificial splits,
- compiles on its own (no dangling references from later PRs).

Within each PR, split changes into atomic commits where it makes sense:
- Each commit is a single coherent logical change (one feature, one refactor, one fix).
- Each feature commit MUST bundle: the production code change + its tests + any directly tied docs/fixtures/config.
- Do NOT separate tests from the production code they cover into different commits.
- A PR with a single commit is fine if the change is already atomic.

Return ONLY a JSON array of PRs, no prose:
[
  {
    "pr_title": "<imperative subject for the PR, conforming to sdk-commit convention>",
    "pr_slug": "<kebab-case slug derived from pr_title without ticket prefix, ≤40 chars, ASCII>",
    "commits": [
      {
        "title": "<imperative commit subject, conforming to sdk-commit convention>",
        "files": ["path/one", "path/two"],
        "hunks": [ {"file": "path/one", "hunk_header": "@@ -10,5 +10,8 @@"} ]
      }
    ]
  },
  ...
]

Rules:
- The union of all commits' "files"+"hunks" across all PRs must reproduce the source diff exactly.
- Order PRs so later PRs may depend on earlier ones (respect natural build order).
- Prefer file-granular splits when hunks inside a file are logically inseparable.
- NEVER add Co-authored-by trailers.

## sdk-commit convention
<INLINED sdk-commit SKILL RULES VERBATIM HERE>
```

Parse the returned JSON. If parsing fails, retry once with a stricter prompt. If still failing, abort.

### 3.2 Present the rebase idea

Render the parsed plan as a numbered list of PRs, each with its commits indented beneath. For every PR show a total lines count; for every commit show per-file stats:

```
PR 1: <pr_title>  (branch: <pr_slug>)  [total: +142 / -3]
  commit 1: <commit title>
     path/one                    | 42 ++++++++++
     path/two                    | 10 +-
  commit 2: <commit title>
     path/three                  |  5 +-

PR 2: <pr_title>  (branch: <pr_slug>)  [total: +519 / -0]
  commit 1: <commit title>
     path/four                   | 519 ++++++++++++++++
...
```

The `[total: +X / -Y]` on each PR is the sum across all its commits' files. Always show it — it lets the user judge PR size at a glance.

Then use `AskUserQuestion`:

- **Approve and execute** — proceed to 3.3.
- **Request changes** — "Other" → free-text feedback, feed back to subagent for a refined split, re-render, re-ask (loop until approved or aborted).
- **🛑 Dry run** — build the local gt branches and staging branch so you can inspect the result, but do NOT create any PRs on GitHub. After inspection, clean up all local gt branches and the staging branch before exiting.

### 3.3 Compile-check configuration

Detect build systems in the repo root (use `Glob`/`Read`):

| Manifest file         | Default command          |
|-----------------------|--------------------------|
| `package.json`        | `npm run build`          |
| `go.mod`              | `go build ./...`         |
| `Cargo.toml`          | `cargo build`            |
| `pom.xml`             | `mvn -q compile`         |
| `build.gradle(.kts)`  | `./gradlew assembleLibrariesDebug`        |
| `pyproject.toml`      | `python -m compileall .` |
| (none, only `.py`)    | `python -m compileall .` |

Use `AskUserQuestion`:

- **Use detected command** (label shows the command)
- **Edit command** — "Other" → user types their own
- **Skip compile check** — no per-branch verification (CI will still run on push)

Save as `$COMPILE_CMD` (possibly empty).

### 3.4 Build the auto-rebase plan into a plain-commit staging branch

Record source state:

```
SRC_HEAD=$(git rev-parse HEAD)
SRC_BRANCH=$(git rev-parse --abbrev-ref HEAD)
STAGING="${SRC_BRANCH}-autorebase"
MERGE_BASE=$(git merge-base "$BASE" "$SRC_HEAD")
```

If `$STAGING` already exists, ask via `AskUserQuestion` to delete it (`git branch -D $STAGING`) or pick a suffixed name.

Create a temporary branch from `$MERGE_BASE` and rebuild the linear history of all planned commits there (without Graphite metadata yet). The staging branch is flat — all commits in sequence regardless of PR grouping. PR boundaries are applied later in Step 4 when building gt branches.

Before switching branches, save the complete source diff with `git diff --binary "$MERGE_BASE" "$SRC_HEAD" -- . "${EXCLUDE_PATHSPECS[@]}"`. Store the patch in a temporary file. Capture the full merge-base-to-source diff, not only the last commit's patch. Do not diff from `$BASE` directly: if `$BASE` has moved ahead, that diff also reverts the new upstream commits. Step 4 cherry-picks the staging commits onto the current `$BASE`.

```
git checkout -b "$STAGING" "$MERGE_BASE"
```

Apply the saved patch on `$STAGING` with `git apply <patch-file>` before staging individual commits.

For each planned commit C across all PRs (oldest→newest, preserving PR order):

1. `git reset HEAD` — unstage everything.
2. Stage only the files/hunks belonging to commit C per the plan, skipping any file that matches `$EXCLUDED`:
   - Whole files listed in `files`: `git add <file>` (skip if file is in `$EXCLUDED`).
   - File-level hunks listed in `hunks`: extract the subset patch and `git apply --cached <patch>` (skip if file is in `$EXCLUDED`).
3. Move everything else aside so the compile-check sees a clean snapshot:
   ```
   git stash push --include-untracked --keep-index -m "autostack-leftover-<C>"
   ```
   Record the identifier of the stash created by this command. If no stash was created, record that explicitly; do not reuse an existing stash. Including untracked files is required because the source patch may introduce new files belonging to later commits.
4. `git commit -m "<title>"` — no Co-authored-by trailer.
5. Run `$COMPILE_CMD` if configured. On failure → rollback (3.6).
6. Restore only the stash recorded for this iteration, if one was created, before proceeding to the next commit. If restoration fails, stop and preserve that stash for recovery.

After the final commit, verify the working tree in Step 3.5 before comparing the branches.

### 3.5 Verify diff-equivalence

First run `git status --porcelain`. Require no staged or unstaged changes and no untracked source files. Ignore only the untracked build artifacts already excluded by Step 0.3. A branch-to-branch diff alone cannot detect leftover uncommitted changes.

```
git diff "$SRC_BRANCH" "$STAGING" -- . "${EXCLUDE_PATHSPECS[@]}"
```

Differences in explicitly excluded files are expected and must not trigger a retry.

The comparison of included files must be empty. If not:

- Feed the residual diff to the subagent with an addendum: "The previous split produced a branch whose diff vs. source is NOT empty. Residual diff follows. Revise the split so the sum matches exactly."
- Delete `$STAGING` (`git checkout $SRC_BRANCH && git branch -D $STAGING`) and redo 3.4 with the revised split.
- Re-check 3.5.

If the second attempt also produces a non-empty diff, show file-list + shortstat of the residual and use `AskUserQuestion`:

- **Accept as-is** — proceed despite the mismatch (user accepts responsibility).
- **Abort and roll back** — delete `$STAGING`, `git checkout $SRC_BRANCH`, exit.

### 3.6 Rollback helper

On an unrecoverable failure inside 3.4–3.5, preserve the staging branch and the exact stashes created in this invocation so the work remains recoverable. Show their identifiers and the error to the user.

Return to `$SRC_BRANCH` only after preserving any pending changes. Do not reset the source branch or drop stashes by a name pattern. Delete a temporary branch only after validating that it was created in this invocation and the user has chosen cleanup.

### 3.7 Post-rebase report

Show the user:

- Staging branch name (`$STAGING`).
- Commit list (`git log --oneline $MERGE_BASE..$STAGING`).
- Per-commit shortstat.
- Confirmation that the working tree is clean and the source-to-staging diff is empty for all included files.

Then `AskUserQuestion`:

- **Proceed to gt stack build** — Step 4 using the $STAGING commits as the plan.
- **Stop here** — exit; tell the user how to pick up later (`git checkout $STAGING`).

---

## Step 4 — Build the gt stack

At this point we have an ordered list of N PRs (each containing one or more commits) to turn into N stacked gt branches. The source is:

- **Direct mode**: approved PR groups from `$SRC_BRANCH` relative to `$BASE`.
- **Auto-rebase mode**: PR groups from the 3.1 plan, commits on `$STAGING` relative to `$BASE`.

Call the source branch `$SOURCE`. Each PR has a `pr_slug`, a `pr_title`, and an ordered list of commit hashes.

### 4.1 Confirm branch naming

One gt branch = one PR. Derive a slug for each **PR** (not each commit):

- Auto-rebase mode: use the `pr_slug` from the plan JSON.
- Direct mode: group commits into PRs first (see Step 2), then slugify the PR title.

Always ask the user for the prefix via `AskUserQuestion` before rendering the branch list, even if a Graphite default is configured. Branch names must follow the repository format from `AGENTS.md`: `<username>/<ticket>/<short-description>`. Offer these options (substituting the actual git username for `<username>`):

- **`<username>/<ticket>/` (Recommended)** — use the ticket from the source branch name (e.g. `tvaleev/rum-1234/`). If the source branch has no ticket, a follow-up `AskUserQuestion` asks for it (e.g. `RUM-1234`).
- **Other** — free-text input (user types the full prefix); it must still contain a ticket segment.

If a Graphite default prefix is configured (`gt config`), mark it as "(current default)" next to the matching option, but still ask — do not silently use it.

Show the planned (prefix+slug) list. Use `AskUserQuestion`:

- **Approve** — proceed to 4.2.
- **Edit username or slugs** — "Other" → user edits; re-render; re-ask. Validate the final names against the repository branch format `<username>/<ticket>/<short-description>`.

### 4.2 Reconstruct the stack with gt

**Before this step**, load the `sdk-commit` skill via the `Skill` tool (if not already loaded) and read its full rules. Every commit message created in this step MUST be generated using those rules: extract the ticket ID from the current branch name, write an imperative header, and add a brief body when the change warrants it. Do NOT write bare titles or copy PR descriptions into commit bodies.

```
git checkout "$BASE"
git pull --ff-only          # best-effort; skip on failure with a warning
```

For each **PR** in the plan (oldest→newest):

1. Create the gt branch for this PR:
   ```
   gt create "<prefix><pr_slug>"
   ```
   (This creates an empty branch stacked on the current branch.)

2. For each commit belonging to this PR (in order):
   a. Cherry-pick the commit onto the current branch WITHOUT committing:
      ```
      git cherry-pick --no-commit "$H"
      ```
   b. Stage everything:
      ```
      git add -A
      ```
   c. Generate the commit message using the `sdk-commit` skill rules:
      - Read `git diff --cached --stat` to understand what is staged.
      - Extract the ticket ID from `$SRC_BRANCH` (or `$STAGING` in auto-rebase mode) per the commit skill header rule.
      - The planned commit title is a starting point; rewrite it into a conforming imperative header with the ticket prefix.
      - Add a brief body if the change is non-trivial; keep it to 1–3 lines describing *what* changed — do NOT paste the PR description here.
      - Show the drafted message to the user via `AskUserQuestion` (Approve / Edit).
   d. Commit with the approved message:
      ```
      git commit -m "<approved commit message>"
      ```

3. The PR title is applied after submission in Step 4.4 via `gh pr edit --title`, using the approved `pr_title`.

After the loop, verify the stack:

```
gt log short
```

Must show `$BASE` → PR-branch-1 → PR-branch-2 → … → PR-branch-N, each with its own commits visible via `gt log`.

### 4.2.3 Generate stack summary markdown

Before asking the user what to do next, generate a markdown summary file of the full stack plan.

**Determine the output path:**

1. Use the repo root as the base (resolved via `git rev-parse --show-toplevel`).
2. Default to `<repo-root>/plans/<branch-slug>-pr-stack.md` — the file stays with the project.
3. Propose the path via `AskUserQuestion`:
   - **`plans/<branch-slug>-pr-stack.md` in the repo root (Recommended)**
   - **Other** — user types a custom absolute or relative path.

Create the `plans/` directory if it does not exist (`mkdir -p`).

**Write the file** using the `Write` tool with this structure:

````markdown
# PR Stack: <current branch> → <BASE>

Generated: <date>

## Stack overview

```
<BASE>
  └── PR 1: <pr_title>  (<prefix><pr_slug>)  [+X / -Y, N commits]
        └── PR 2: <pr_title>  (<prefix><pr_slug>)  [+X / -Y, N commits]
              └── PR 3: ...
```

---

## PR 1 — <pr_title>

**Branch:** `<prefix><pr_slug>` → `<BASE or previous PR branch>`
**Lines:** +X / -Y across N files, M commits

### What and why

<one paragraph describing what this PR changes and why, inferred from the diff and commit titles>

### How

<brief description of the implementation approach — omit if the PR is small/obvious>

### Review checklist
- [ ] Logic is correct and handles edge cases
- [ ] Tests cover the new behaviour
- [ ] No unintended side effects on other features
- [ ] Commit messages follow convention

### Files changed

| File | +lines | -lines |
|------|--------|--------|
| path/one | 42 | 3 |
| path/two | 10 | 0 |

<details>
<summary>Diff — path/one</summary>

```diff
<actual diff hunk for this file>
```

</details>

---

## PR 2 — <pr_title>

... (same structure repeated for each PR)
````

Tell the user the file path once written: "Summary written to `<path>`."

### 4.2.5 Pre-submit checkpoint

Before creating any PRs, show the user the full local stack (`gt log short` output).

Then display this warning prominently in the session language:

> ⚠️ **AFTER THIS POINT, PULL REQUESTS WILL BE CREATED ON GITHUB. THIS CANNOT BE UNDONE AUTOMATICALLY.**

Then use `AskUserQuestion`:

- **🛑 Dry run — inspect locally, no PRs** — do NOT run `gt submit`. The local gt branches are already built and can be inspected with `gt log short`, `gt log`, etc. When the user is done inspecting, ask via `AskUserQuestion` whether to **keep the local branches** (exit as-is) or **clean up** (delete all gt branches created in this session and the staging branch if any, then check out the original source branch).
- **Submit stack as draft PRs (Recommended)** — proceed to 4.3 with `--draft`.
- **Submit stack as open PRs** — proceed to 4.3 with `--publish`.

### 4.3 Generate PR descriptions (separate from commit messages)

**PR descriptions are MANDATORY. They can never be omitted, skipped, or left empty — not even for small or "obvious" PRs.**

PR descriptions are GitHub metadata — they must NOT be embedded in git commit messages. Generate one PR description per PR branch now, before submission, so they can be applied via `gh pr edit` after `gt submit`.

Steps:

1. Detect a PR template in the repo (look in order: `.github/PULL_REQUEST_TEMPLATE.md`, `.github/pull_request_template.md`, `docs/PULL_REQUEST_TEMPLATE.md`, `PULL_REQUEST_TEMPLATE.md`, any file under `.github/PULL_REQUEST_TEMPLATE/`).
2. For each PR branch in the stack:
   - Collect its diff stat: `git diff <parent-branch>...<pr-branch> --stat`
   - Draft a PR description in English:
     - **If a template exists**: fill it out section by section. Do NOT delete, reorder, or skip any heading — if a section genuinely does not apply write `N/A` (or the repo's equivalent). Following the template structure is not optional.
     - **If no template**: write a 2–4 sentence summary covering, in order: what changed (user-visible / behavioural effect), why (motivation: ticket, bug, performance, follow-up), and any relevant notes (caveats, migration steps, test coverage, deliberate omissions).
   - If the branch name encodes a ticket (`RUM-xxxx`), add `Ref: RUM-xxxx` on its own line at the end so GitHub/Jira auto-links it.
   - Show the drafted description to the user via `AskUserQuestion` (Approve / Edit / Regenerate) before storing it. The user may edit the wording but **may not approve an empty description** — if the user attempts to leave it blank, prompt again.
3. Store the approved `pr_title` and description per branch in a map `{ "<branch-name>": { "title": "<pr_title>", "body": "<pr-description>" } }` for use in Step 4.4.

Do NOT amend any commits. Commit messages remain what was written in Step 4.2.

### 4.4 Submit the stack

Ensure the current branch is the top of the stack (`gt top`), then run with the flag chosen in 4.2.5:

```
gt submit --stack --no-interactive --draft    # default
# or
gt submit --stack --no-interactive --publish  # if user chose open PRs
```

Flag notes:
- `--stack` submits the current branch, its ancestors back to trunk, and its descendants.
- `--no-interactive` avoids editor prompts (PR title defaults to the first commit's subject; it is replaced with the approved `pr_title` below).
- `--draft` creates draft PRs; reviewers can still see them, but they are marked as not ready for review.

If `gt submit` fails (auth issue, branch protection, network), surface the error verbatim and stop. Do NOT attempt workarounds — tell the user to resolve manually (e.g. re-run `gt auth` with a fresh token, check branch protection rules, etc.).

For each PR branch, apply the PR title and description stored in Step 4.3 by branch name (`gt submit` prints Graphite links, which `gh` does not accept):

```
gh pr edit <branch-name> --title "<pr_title>" --body-file "<approved-description-file>"
```

Write each approved description to a temporary UTF-8 file using the file-editing tool, preserving newlines. Run these `gh pr edit` calls after all PRs are created. If any call fails, surface the error and tell the user the description for that PR so they can paste it manually.

Present a summary list of GitHub PR URLs (`gh pr view <branch-name> --json url -q .url`) once all descriptions have been applied. Spot-check one PR with `gh pr view <branch-name> --json body -q .body` to confirm the description was set correctly.

---

## Step 5 — Wrap up

Tell the user:

- The list of PR URLs created.
- Names of the gt branches in the stack (`gt log short` output).
- If a stash was created in Step 0.3, remind them of the stash id and how to pop it.
- If auto-rebase was used: the source branch is unchanged and can be used as a rollback reference.
- Then include the **"How to use Graphite from here"** primer below (translated to the session language).

### How to use Graphite from here — developer primer

Give this to the user verbatim (translated into the session language). Keep it short — a handful of bullet points per section.

**Daily workflow:**
- `gt log` — see the full stack as a tree.
- `gt log short` — compact one-line-per-branch view.
- `gt up` / `gt down` — move up/down the stack by one branch.
- `gt checkout` — interactive branch picker within the stack.
- `gt top` / `gt bottom` — jump to the top or bottom of the current stack.

**Making changes:**
- Edit a branch in the middle of the stack → `gt modify -a` (stages all, amends the branch's commit, and automatically restacks every descendant). No manual rebase needed.
- Start a new feature on top of the current branch → stage changes → `gt create <name> -m "<msg>" -a`.
- Interactive rebase on the current branch's commits → `gt branch edit`.

**Syncing with trunk:**
- `gt sync` — pulls latest trunk, rebases all your open stacks on top, prompts to delete merged/stale branches. Run this at the start of your day.
- `gt restack` — re-runs the rebase logic if something drifted (e.g. after a manual `git rebase`).

**Submitting / updating PRs:**
- `gt submit` — submits the current branch downward to trunk (creates/updates PRs for the current branch and all ancestors).
- `gt submit --stack` — submits the current branch AND all its descendants (the whole stack going up).
- `gt submit --draft` — same but creates PRs as drafts.
- After amending any branch, just re-run `gt submit --stack` — Graphite force-pushes with lease and updates every affected PR.

**Merging:**
- Merge PRs bottom-up (closest to trunk first). Graphite's web UI at https://app.graphite.com has a one-click "merge when ready" queue that does this automatically.
- After a merge, `gt sync` cleans up the merged branch locally and rebases the rest of the stack onto the new trunk tip.

**Getting unstuck:**
- `gt track` — repair corrupted Graphite metadata on an existing branch (select its parent interactively).
- `gt branch fold` — merge a branch into its parent (useful when a branch turns out to be too small to stand alone).
- `gt branch delete <name>` — local-only deletion; PR on GitHub is NOT touched.
- Full docs: https://graphite.com/docs/graphite-cli

End the session.

---

## Key principles

- Every user question goes through `AskUserQuestion` — no plain numbered text prompts.
- Session language matches invocation; commit messages + PR metadata always English.
- LLM diff analysis is always delegated to a subagent — keeps the main context clean.
- Never touch `main` / `master` / `develop`.
- Never rewrite history on the source branch — staging work always happens on `<branch>-autorebase`.
- Verify diff-equivalence before submitting; one retry max, then ask the user.
- Fail loudly and clearly — surface gt/git errors verbatim. Never attempt silent recovery.
- The gt stack is built as one branch per PR; each PR in the stack targets the previous PR's branch as its base (Graphite wires this automatically via `gt submit`).
- NEVER add Co-authored-by trailers to any commit.
- **PR descriptions are mandatory** — every PR must have a non-empty description. `gt submit --no-interactive` does not populate PR bodies from commit messages; always apply descriptions via `gh pr edit` immediately after submission (Step 4.4). Never consider a PR "done" until its body has been set and spot-checked.
