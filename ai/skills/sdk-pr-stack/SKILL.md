---
name: sdk-pr-stack
description: >-
  Create or update Graphite PR stacks for dd-sdk-android. Use when splitting a
  feature branch into stacked PRs, restacking branches after committed changes,
  or submitting updates to an existing stack. Accepts create or update.
when_to_use: >-
  Creating stacked PRs, splitting commits into reviewable PRs, running autostack,
  propagating changes through a Graphite stack, or submitting a restacked stack.
argument-hint: "create | update"
---

# sdk-pr-stack — Graphite PR stacks

## Choose the action

Read the action from the invocation arguments (`$ARGUMENTS` in Claude Code) or an
unambiguous user request. The supported actions are:

| Action | Invocation | Workflow |
| --- | --- | --- |
| `create` | `/sdk-pr-stack create` | [references/create.md](references/create.md): split the current feature branch into stacked PRs, using existing commits or an approved auto-rebase plan. |
| `update` | `/sdk-pr-stack update` | [references/update.md](references/update.md): restack the existing Graphite stack and submit its updated PRs. |

Load the selected workflow in full and follow only that workflow.

- If the action is missing or the request is ambiguous, ask the user to choose
  `create` or `update` before starting either workflow.
- If an explicit action is unsupported or both actions are supplied, show
  `/sdk-pr-stack create` and `/sdk-pr-stack update` as valid usage and stop.
- Do not choose `create` merely because `update` cannot find an existing stack.

## Shared rules

- Follow the repository's `AGENTS.md` for ticket IDs, branch names, commit
  headers, and PR templates. The default PR base in this repository is `develop`.
- In `create` mode, load the sibling [sdk-commit](../sdk-commit/SKILL.md) before
  generating commit messages or PR titles. Pass those rules to any analysis subagent.
- In `update` mode, preserve file contents and commit messages. Do not commit,
  amend, split commits, or run the create workflow.
- Preserve unrelated working-tree changes and the source branch. Stage only the
  approved files, even where a copied workflow shows `git add -A`.
- Keep approval choices from the selected workflow: create supports a local dry
  run, and update supports restack-only. Honor choices already made in this session.
- When file exclusions are requested in `create` mode, use auto-rebase mode;
  whole-commit cherry-picks in direct mode would reintroduce excluded files.
  Build exclusion pathspecs as a shell array of `:(exclude)<pattern>` entries
  and pass it as `"${EXCLUDE_PATHSPECS[@]}"`.

## Agent compatibility

The workflow references retain the source agent's tool names. Use the equivalent
tools available in the current agent:

- `AskUserQuestion`: the host's user-input tool; if unavailable, ask in chat
  and wait for the answer before any dependent action.
- `Skill`: invoke the named skill, or read its linked `SKILL.md` in full.
- `Read`, `Glob`, `Write`: use filesystem tools and the host's file-editing
  tool. Preserve multiline PR descriptions in files and pass them with `--body-file`.
- `Agent`: use the host's subagent tool for the analysis step. If unavailable,
  perform the analysis in the main agent and preserve the same review checkpoints.
