---
name: sdk-feature-knowledge
description: >-
  External knowledge for dd-sdk-android features: official docs the codebase does
  not own, plus example prompts. Use before working on a listed feature.
when_to_use: >-
  Working on code under features/<module> that has a reference file listed below.
---

# dd-sdk-android Feature Knowledge

External references the codebase does not own (platform docs, guides), with example
prompts that combine them with a task. This is not a spec of the code: for how the SDK
works, read the code.

## Feature index

| Feature | Module | Reference |
|---|---|---|
| Profiling | `features/dd-sdk-android-profiling` | [references/profiling.md](references/profiling.md) |
| RUM, Logs, Trace, Session Replay, Flags, NDK, WebView, others | `features/*` | Not yet documented |

## Rules

- Add only external links (one line on why each matters) and example prompts. Never restate code.
- Update a reference file when the external docs or links change, not when the code changes.
