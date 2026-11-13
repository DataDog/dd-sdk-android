---
name: sdk-feature-knowledge
description: >-
  Per-feature architecture knowledge for dd-sdk-android. Use before changing,
  debugging, or reviewing a feature module to load its data flow, threading,
  core integration, invariants, and tests.
when_to_use: >-
  Working on code under features/<module> that has a reference file listed below.
---

# dd-sdk-android Feature Knowledge

Load only the reference file for the feature you touch. Each file describes the
code on the current branch; verify against the source when in doubt.

## Feature index

| Feature | Module | Reference |
|---|---|---|
| Profiling | `features/dd-sdk-android-profiling` | [references/profiling.md](references/profiling.md) |
| RUM, Logs, Trace, Session Replay, Flags, NDK, WebView, others | `features/*` | Not yet documented |

## Reference file template

Each reference file uses these sections, in this order:

1. Purpose and public surface
2. Architecture and data flow
3. Threading
4. Core integration (feature context, events, messages)
5. Generated models
6. Invariants and gotchas
7. Tests
8. Cross-platform parity
9. Do not

## Keep it current

When a change alters a documented behavior, class, flow, or constant, update the
feature's reference file in the same PR. Remove statements you can no longer
verify instead of leaving them stale.
