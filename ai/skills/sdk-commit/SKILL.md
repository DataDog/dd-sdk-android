---
name: sdk-commit
description: >-
  Commit-message conventions for dd-sdk-android. Use when drafting or reviewing
  commit messages and PR titles, making a requested commit, or generating messages
  for other skills.
when_to_use: >-
  Writing commit subjects and bodies, formatting a requested Git commit,
  or preparing commit messages and PR titles.
---

# sdk-commit — Commit Message Format

## Structure

```
<header>
<blank line>
<body>
```

## Rules

### 1. Header (first line)

- Write a short, imperative summary in English.
- Follow the repository format: `<JIRA-KEY>-<number>: <short description>`.
- Extract the ticket from the current branch, including branches following `<username>/<ticket>/<short-description>`; normalize the ticket to uppercase.
  - Example branch: `jdoe/rum-1234/add-logging`
  - Example header: `RUM-1234: Add structured logging to ingest pipeline`
- Keep the header at most 72 characters. Put longer explanations in the body.
- If the ticket cannot be determined from the branch or session context, ask the user before committing. Never invent a ticket or omit it.

### 2. Body (second line onward)

- Leave one blank line between the header and the body.
- Use the body for a short description of what was changed.
- Any longer explanation MUST go in the body, never in the header.
- If another skill produces a commit message, place its output in the body — always below the header line.

### 3. Attribution

- NEVER add `Co-authored-by: Claude` or any similar co-authorship/attribution trailer.

### 4. File endings

Every text file touched by a commit must end with exactly one newline character, with no blank lines after the last line of content. Before committing:
- For `.kt`/`.kts` files, run `./gradlew ktlintFormatAll` instead of checking them manually.
- Check other text files manually:
  - If a file has no trailing newline — add one.
  - If a file ends with blank lines — remove them, leaving a single trailing newline.
- Never touch binary files (images, archives, keystores, etc.).

### 5. Commit body vs. PR description — keep them separate

The commit body and the GitHub PR description serve different audiences and must NOT be conflated.

- **Commit body** (what goes in git history): a brief 1–3 line description of *what* changed and *why*, written for a future reader of `git log`. Keep it technical and concise — it is not a PR description.
- **PR description** (what goes on GitHub): a separate artifact. Do NOT embed PR descriptions in commit bodies.

**When to add a body to the commit:**

Add a body when the change is non-trivial and a one-line header does not fully capture the intent. A body is NOT needed for tiny, obviously self-explanatory changes.

If the branch name encodes a ticket (`RUM-xxxx`), add `Ref: RUM-xxxx` on its own line in the body so GitHub/Jira auto-links it. The header prefix alone is not enough for some integrations.

Commit bodies are always in English, regardless of session language.
