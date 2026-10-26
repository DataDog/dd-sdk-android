---
name: sdk-review
description: >-
  Code review for dd-sdk-android. Use when reviewing a feature branch or pull
  request for performance issues, binary compatibility, threading bugs,
  main-thread blocking, and other defects.
when_to_use: >-
  Reviewing an Android SDK branch or PR, checking concurrency and API
  compatibility, or requesting another review pass.
---

# sdk-review — dd-sdk-android Code Review

Run the steps below **in order**. Use `AskUserQuestion` for every decision that can be expressed as a closed choice — only fall back to free-form prompts when the user must type something (e.g. a branch name).

When `AskUserQuestion` or `Read` is unavailable, use the current agent's equivalent tools. If no user-input tool is available, ask in chat and wait for the answer before the dependent step.

Follow the repository's `AGENTS.md` for branch naming and review conventions.

All user-facing text (questions, findings, summary) must match the language the user used when invoking the skill. Default to **English** when unsure. Keep the SKILL itself and internal reasoning in English regardless.

## Step 0 — Resolve the working branch

```bash
git branch --show-current
```

### 0a. Accepted pattern

A valid feature branch matches:

```
^[a-zA-Z0-9._-]+/[a-zA-Z][a-zA-Z0-9]*-\d+/[^/]+$
```

(i.e. `<username>/<ticket>/<short-description>`, for example `username/rum-1234/short-description`, following `AGENTS.md`.)

### 0b. If on a protected branch (`main`, `master`, `develop`)

Exception — uncommitted changes: if `git status --porcelain` is not empty, review them in place without switching. Use `git diff HEAD` as the diff. `git diff HEAD` omits untracked files, so for each path from `git ls-files --others --exclude-standard` also add its full content as a new-file patch with `git diff --no-index /dev/null <path>` (exit code 1 is expected). Skip Step 1 and go to Step 2.

Otherwise, do NOT review the protected branch. Offer to switch:

1. Collect up to 3 candidate feature branches the user has recently worked on:
   ```bash
   git for-each-ref --sort=-committerdate --count=20 \
     --format='%(refname:short)|%(committerdate:iso8601)' refs/heads/ \
     | grep -Ev '^(main|master|develop)\|' \
     | head -3
   ```
   Prefer branches that match the accepted pattern; if fewer than 3 match, fall back to the most recently updated non-protected branches.

2. Ask via `AskUserQuestion`:
   > Question: `You are on '<protected-branch>'. Which branch would you like to review?`
   > Options (exactly four):
   > - `<candidate 1>` — last commit `<date>`
   > - `<candidate 2>` — last commit `<date>`
   > - `<candidate 3>` — last commit `<date>`
   > - `Enter another branch name` — prompt the user for free-form input

   If fewer than 3 candidates exist, keep the remaining options but mark missing ones as `— (none)` and still offer `Enter another branch name`.

3. If the user picks one of the candidates, run `git switch <branch>` and continue.

4. If the user picks `Enter another branch name`, ask for the name in a follow-up free-form prompt. If the user submits an empty response or cancels, **exit the skill silently**. Otherwise `git switch <branch>` (abort if the switch fails — surface the git error and stop).

### 0c. If on any other branch that does not match the pattern

Surface a single warning line:

> `Branch '<name>' does not match username/ticket/short-description — review will proceed anyway.`

Do not block. Proceed to Step 1.

## Step 1 — Determine the diff to review

1. Detect the base branch:
   - `git config --get "branch.$(git branch --show-current).merge"` → strip `refs/heads/` prefix.
   - Fallback order if unset: `develop` if it exists locally or on `origin`, else `main`, else `master`.
   - If the result is the current branch itself (it tracks its own remote branch), ask for the base via `AskUserQuestion` with options `develop` (Recommended) and `main`, plus "Other" for a custom branch.

2. Compute the merge base and the changed files:
   ```bash
   BASE="<detected base>"
   git fetch origin "$BASE" --quiet 2>/dev/null || true
   MB=$(git merge-base HEAD "origin/$BASE" 2>/dev/null || git merge-base HEAD "$BASE")
   git diff --name-only "$MB"..HEAD
   ```

3. If the diff is empty, tell the user `No changes to review against <base>.` and exit.

4. For the review itself, read the full diff via:
   ```bash
   git diff "$MB"..HEAD
   ```
   And, when a finding needs surrounding context, `Read` the affected file at HEAD rather than re-running `git show`.

## Step 2 — Analyze the diff

Build a working list of **candidate findings**. Aim for **at least 5 candidates** as a guideline — if you have fewer, re-read the diff once more and look harder (missing null/thread annotations, allocations in loops, etc.). This is not a hard limit in either direction: report only real findings, even if there are fewer than 5 or none, and do not cap the list if there are more.

In parallel, run your own built-in code review (e.g. the `code-review` skill in Claude Code, or `/review` in Codex) on the same diff. Merge its findings into the candidate list, dropping duplicates.

Check every changed file against the following categories. Findings may belong to multiple categories; record the most specific one.

### 2.1 UI / hot-path allocations and work

Android UI code runs `onDraw`, `onMeasure`, `onLayout`, `dispatchDraw`, `onTouchEvent`, `onBindViewHolder`, `RecyclerView.Adapter` callbacks, `Choreographer` frame callbacks, and custom `Drawable.draw` at very high frequency. In those sites, flag:

- Object allocations (`new Paint()`, `Rect()`, `Path()`, `Matrix()`, lambdas capturing state, `String.format`, boxed primitives, `ArrayList()`, regex compilation).
- Calls that internally allocate (e.g. `measureText`, `split`, `toLowerCase` without a `Locale`, `Collections.unmodifiableList`, Kotlin `listOf(...)` inside a hot loop).
- Reflection, `Class.forName`, JSON parsing, or I/O on draw/layout paths.
- Heavy computation not memoized across frames.

### 2.2 Binary backwards compatibility (library constraint)

This SDK is published as a library. **Breaking binary compatibility forces downstream apps into dependency hell.** Flag any change that is not ABI-compatible:

- Removing or renaming a `public` / `protected` class, method, field, property, or top-level function that is part of the public API.
- Changing a method signature: return type, parameter types/order, throws clause, `suspend` modifier.
- Changing visibility (`public` → `internal`/`private`), `open` → `final`, `abstract` flag, or `companion`/`static` modifiers.
- Reordering enum constants, changing `@JvmField`/`@JvmStatic`/`@JvmOverloads` annotations, changing default parameter values on methods annotated `@JvmOverloads`.
- Changing a class from `class` to `data class` (or vice versa) — alters `componentN`/`copy`.
- Kotlin-specific: changing `val` to `var` or vice versa on a public property, changing nullability (`T` ↔ `T?`), changing generic variance (`in`/`out`).

Models generated from JSON schemas (see Generated Models in `AGENTS.md`) are an exception: adding a new property changes their constructor signature, and this is expected — do not flag it.

Classes inside `internal` packages (commonly `…/internal/…`) or annotated `@InternalApi` / `@RestrictTo(LIBRARY_GROUP)` are **not** public API — do not flag them.

### 2.3 Multithreading correctness

Flag potential deadlocks, races, and visibility bugs:

- Nested `synchronized`/`ReentrantLock` acquisitions where lock order depends on runtime state.
- Shared mutable state (`var` field, `MutableList`, `HashMap`) read/written from more than one thread without `@Volatile`, `AtomicReference`, or a lock.
- Check-then-act patterns (`if (cache == null) cache = …`) that are not idempotent under concurrent entry.
- `Handler`/`Executor`/coroutine `launch` callbacks that capture `this` and race with teardown (`onDestroy`, `stop()`).
- Callbacks fired while holding a lock that may re-enter the same lock (lock re-entrance via listener graph).
- Coroutine code that assumes a specific dispatcher without `withContext`, or switches context while holding a non-reentrant lock.
- New `Thread`s or executors where an existing one could be reused (e.g. `contextExecutorService` or `persistenceExecutorService` in `CoreFeature`, via `submitSafe`/`executeSafe` from `ConcurrencyExt.kt`).

### 2.4 Redundant or wrong synchronization

Flag when concurrency primitives are stacked without purpose:

- `@Volatile` on a field that is **only** read/written inside `synchronized` blocks covering the same monitor (the `synchronized` already provides happens-before; the `@Volatile` is dead weight).
- `AtomicInteger`/`AtomicReference` wrapped in an outer lock that guards every access.
- `AtomicXXX` (or other thread-safe holder) declared as `var` instead of `val`: reassigning the reference bypasses the atomic and is itself an unsynchronized write.
- `Collections.synchronizedList(...)` inside an already-guarded critical section.
- `synchronized` on a non-final reference (`synchronized(this.lock)` where `lock` can be reassigned).
- Double-checked locking without `@Volatile` on the guarded field (the opposite mistake — flag this too).
- `ConcurrentHashMap` used read-only inside a mutex that already serializes all access.

### 2.5 Main-thread blocking / heavy work

The SDK must never stall the host app's UI thread. Flag:

- Disk/network I/O on the main thread: `File.readText`, `FileInputStream`, `SharedPreferences.commit` (not `apply`), `SQLiteDatabase` queries, `HttpURLConnection`, OkHttp synchronous `execute`, `Thread.sleep`.
- `runBlocking { ... }` anywhere reachable from a public API that a host might call from the UI thread.
- `Future.get`, `CountDownLatch.await`, `Object.wait`, `Lock.lock` with known long-held critical sections.
- Heavy CPU work (JSON parse of large payloads, crypto, image decoding, reflection-heavy class scanning) without a documented background dispatcher.
- Newly added methods on classes historically called from the main thread (check the class's KDoc / existing callers).

### 2.6 Other defects (open-ended)

Also flag anything else you would catch in a normal review: null-deref risks, incorrect `equals`/`hashCode`, resource leaks (`Cursor`, `InputStream`, `BufferedReader` not closed / no `use {}`), swallowed exceptions, logging of PII, incorrect `when` exhaustiveness, misuse of `apply`/`also`, off-by-one, missing `@Throws`, wrong unit-test assertions, etc.

Also check that the project's agent docs (`AGENTS.md`, `ai/skills/*`) are still accurate after the change — especially when Gradle tasks, build scripts, or module layout change. Flag any skill or doc that references a renamed, removed, or changed task, command, file, or path.

## Step 3 — Present all findings

Do **not** ask the user about each finding individually. Instead, present the full list of findings in one pass:

1. Print a **detailed description of every finding** to the terminal, in order. Each finding gets its own numbered section with:
   - **Title** — short, category-tagged, e.g. `[UI hot path] Paint allocated in onDraw`.
   - **Location** — `path/to/File.kt:line` (use the current HEAD line, not the diff index).
   - **Category** — one of `UI hot path`, `Binary compatibility`, `Concurrency`, `Redundant sync`, `Main-thread blocking`, `Other`.
   - **Explanation** — 3–6 sentences. Explain *what* the problem is, *why* it matters (concrete user-visible impact: dropped frames, crash on update, ANR, data corruption, etc.), and *how* a reader can verify it. Quote the offending snippet.
   - **Suggested fix** — one concrete suggestion.

2. After all detailed descriptions, print a **short summary table** at the end with sequential numbers, one row per finding:

   | # | Title | Location | Category |
   |---|-------|----------|----------|
   | 1 | Paint allocated in onDraw | `path/to/File.kt:42` | UI hot path |
   | 2 | … | … | … |

The sequential numbers (1, 2, 3, …) are stable references — the user can cite a number when discussing a finding or asking for another round.
