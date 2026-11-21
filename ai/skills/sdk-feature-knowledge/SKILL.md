---
name: sdk-feature-knowledge
description: >-
  External knowledge for developing, debugging, and reviewing dd-sdk-android
  features: official platform docs the codebase does not own, plus example
  prompts pairing them with SDK tasks.
when_to_use: >-
  Developing, debugging, or reviewing code under features/<module> that has a
  reference file listed below.
---

# dd-sdk-android Feature Knowledge

For developing, debugging, and reviewing a feature module: external references the
codebase does not own (platform docs, guides) for the platform APIs the feature builds on,
with example prompts pairing them with SDK tasks. This is not a spec of the code: for how
the SDK works, read the code.

## Feature index

| Feature | Module | Reference |
|---|---|---|
| Profiling | `features/dd-sdk-android-profiling` | [references/profiling.md](references/profiling.md) |
| RUM, Logs, Trace, Session Replay, Flags, NDK, WebView, others | `features/*` | Not yet documented |

## Rules

- Add only external links (one line on why each matters) and SDK-oriented example prompts. Never restate code.
- Update a reference file when the external docs or links change, not when the code changes.
