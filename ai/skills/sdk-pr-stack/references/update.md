# sdk-pr-stack update — Restack and Submit a Graphite PR Stack

Follow the shared rules in [../SKILL.md](../SKILL.md), then execute this workflow. Do not run the create workflow or rebuild the stack from scratch.

Conduct the entire session in the language the user used when invoking the skill.

The user has made changes to the current branch (or one of its ancestors in a Graphite stack) and needs those changes propagated through all descendant branches and pushed to remote so every PR in the stack reflects the latest state.

---

## Step 0 — Pre-flight checks

### 0.1 Git repository

```bash
git rev-parse --is-inside-work-tree 2>/dev/null
```

If this fails, abort: "This directory is not a git repository."

### 0.2 `gt` is installed and authenticated

```bash
command -v gt && gt log short 2>&1 | head -5
```

If `gt` is not found, abort: "Graphite CLI (`gt`) is not installed. Install it with `brew install withgraphite/tap/graphite` and authenticate before retrying."

If the output contains `Please authenticate` / `Missing auth token` / `401`, abort: "Graphite is not authenticated. Run `gt auth --token <token>` (get a token from https://app.graphite.com/settings/cli) and retry."

### 0.3 Current branch is not protected

```bash
git rev-parse --abbrev-ref HEAD
```

Save the result as `$BRANCH`. If it is `main`, `master`, or `develop`, abort: "You are on a protected branch (`$BRANCH`). Restack operates on feature branches only. Switch to a branch in your stack and retry."

### 0.4 Verify the branch is part of a Graphite stack

Run:

```bash
gt log short 2>&1
```

Parse the output to determine whether `$BRANCH` appears in a stack that has more than one branch (i.e., at least one ancestor above trunk or at least one descendant below `$BRANCH`).

A branch is considered **part of a stack** when either of these is true:
- It has one or more Graphite-tracked children (descendants).
- It has a Graphite-tracked parent that is not trunk (`main` / `master` / `develop`).

If `$BRANCH` appears alone with trunk as its only neighbour and has no children, it is a single-branch stack — not a multi-PR stack. In this case use `AskUserQuestion`:

- **It's a single-branch stack — restack anyway** — proceed; `gt restack` + `gt submit` still work and will push the branch to remote.
- **Abort — I may be on the wrong branch** — exit; the user can switch to the correct branch and re-invoke the skill.

---

## Step 1 — Show the current stack

Display the full stack to the user:

```bash
gt log short
```

Also show uncommitted changes on the current branch:

```bash
git status --porcelain
```

If there are uncommitted changes, warn the user:

> "There are uncommitted changes on `$BRANCH`. Restack works on committed history. Please commit or stash your changes before restacking."

Then use `AskUserQuestion`:

- **Stash changes and continue** — run `git stash push -u -m "restack-stash"` and proceed. Remember to suggest popping the stash at the end.
- **Abort** — exit the skill.

Then check whether any other branch of the stack is checked out in another worktree:

```bash
git worktree list --porcelain
```

Git cannot rebase a branch checked out in another worktree, so `gt restack` skips it and still exits successfully. If any stack branch from `gt log short` other than `$BRANCH` appears in a `branch refs/heads/<name>` line, list each such branch with its `worktree` path and abort:

> "These stack branches are checked out in other worktrees and would be skipped by `gt restack`: <list>. Switch those worktrees to another branch (or restack from them), then re-run `/sdk-pr-stack update`."

---

## Step 2 — Confirm restack

Use `AskUserQuestion`:

- **Restack and submit the full stack (Recommended)** — run `gt restack` then `gt submit --stack`.
- **Restack only (no submit)** — run `gt restack` but skip the submit step; the user will push manually.
- **Abort** — exit.

Save the chosen mode as `$MODE`.

---

## Step 3 — Restack

Run:

```bash
gt restack
```

`gt restack` checks the current stack and rebases branches whose parents are no longer in their history, including affected descendants. It modifies local branches only — nothing is pushed yet.

If `gt restack` exits non-zero, surface the full output verbatim and abort:

> "Restack failed — see the error above. Common causes: rebase conflicts (resolve them manually with `gt continue` after fixing conflicts), or a corrupted Graphite branch metadata (repair with `gt track`)."

If it succeeds but its output reports any branch as skipped, surface that output verbatim and abort before Step 4. Otherwise show the updated stack:

```bash
gt log short
```

---

## Step 4 — Submit the stack (skip if `$MODE` is restack-only)

Run:

```bash
gt submit --stack --no-interactive
```

- `--stack` pushes the current branch, its ancestors back to trunk, and its descendants.
- `--no-interactive` avoids editor prompts.

If `gt submit` exits non-zero, surface the error verbatim. Do NOT retry automatically — tell the user to resolve the issue (auth expiry, branch protection, network) and re-run `gt submit --stack --no-interactive` themselves.

If it succeeds, show the list of PR URLs printed by `gt submit`.

---

## Step 5 — Wrap up

Tell the user:

- The final stack state (`gt log short` output).
- PR URLs updated on remote (from Step 4 output), or a note that submit was skipped.
- If a stash was created in Step 1, remind them: "Your changes are stashed — run `git stash pop` to restore them when ready."

End the session.

---

## Key principles

- Never commit, amend, or touch file content — this skill only moves branch pointers.
- Never force-push directly; rely on `gt submit` which uses force-push-with-lease internally.
- Surface `gt` errors verbatim — do not attempt silent recovery or workarounds.
- A restack on a single-branch stack is valid; do not block it.
