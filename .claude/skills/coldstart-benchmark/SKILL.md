---
name: coldstart-benchmark
description: Use when measuring the Datadog Android SDK's cold-start impact on an app (your own or one you are supporting) when app start got slower after adding the SDK, or when checking whether a cold-start A/B benchmark can be trusted. Enforces the liveness check, A/A validation and statistical controls that uncontrolled benchmarks miss.
---

# Datadog Android SDK — Cold-start benchmarking

## Overview

Cold-start A/B benchmarking looks trivial and is not. An uncontrolled protocol produces
numbers that are wrong by more than the effect being measured. On identical APKs (true
delta zero), a fixed-arm-order protocol with unhandled permission dialogs reported
**+12.7 ms, 95% CI [+3.0, +22.5], p = 0.011**: a statistically significant regression that
did not exist.

**Core principle:** never trust an A/B result until you have (a) proven the SDK actually
initializes in the treatment arm, and (b) run the same APK in both arms and confirmed the
result is null.

Scripts: [`tools/coldstart-benchmark/`](../../../tools/coldstart-benchmark). The same
material in prose, with more background:
[`docs/benchmarking_sdk_cold_start.md`](../../../docs/benchmarking_sdk_cold_start.md).

This skill ships in the SDK repository, so it applies whether you are measuring your own
app or helping someone else measure theirs. Where a step needs information only the app's
owner has (build type, feature flags, SDK configuration), ask for it rather than guessing.

## When to Use

- App start got slower after adding the Datadog SDK
- Measuring what the SDK costs at startup, before or after adopting it
- Deciding whether a benchmark result (yours or someone else's) can be trusted
- Auditing whether a Perfetto trace can support the conclusion drawn from it

## Do these in order

### 0. Reject the run before it starts if either of these is wrong

**Release-configured builds.** Debug builds are not optimized, shrunk or obfuscated and
carry tooling users never run; their startup behavior says nothing about production.
Require `isMinifyEnabled = true`, `isDebuggable = false`, release signing, production R8
rules, matching `versionCode`/`versionName` on both arms. A number from a debug build is
not a measurement of the SDK. Establish the build type before analyzing anything else.

**Physical device.** Emulator numbers are worthless for this question: a laptop core is
many times faster than a mid-range phone core, on an SSD, with no big.LITTLE scheduling and
no thermal throttling, which distorts exactly the phases in play. No correction factor
recovers the real answer. The harness stamps `emulator=1` and warns, but the run should not
happen at all.

### 1. Prove the SDK initializes at all

Most likely single cause of a nonsensical benchmark. A build that *contains* the SDK is not
a build that *initializes* it: feature flags, remote config, experiment buckets, consent
gating and deferred init all leave it compiled in but inert. This is not hypothetical: an
"SDK enabled" build whose initialization was gated off by a host-app feature flag invalidated
every measurement made against it, on both sides, before anyone noticed.

**The oracle.** `CoreFeature.initialize()` calls `setupExecutors()` then immediately submits
the NTP-sync task to `persistenceExecutorService`
(`dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt:265-266`).
That executor uses `DatadogThreadFactory`, which names threads
`datadog-<context>-thread-<n>`, truncated by Linux to 15 chars (`datadog-storage`). The name
is built at runtime from a string template, so **R8 cannot rename it**. A completed
`Datadog.initialize()` therefore always leaves at least one `datadog-*` thread.

```bash
tools/coldstart-benchmark/verify_sdk_active.sh <apk> <your.app.id>
```

This installs, md5-attests the install against the local file, launches via the real launcher
intent, settles (`SETTLE`, default 20s of wall clock, unchanged and distinct from
`capture_trace.sh`'s settle *launches*, which derive from `WARMUP`), then reads
`/proc/<pid>/task/*/comm`. Exit 0 = live,
1 = not, 2 = setup failure (no adb, no device, missing APK).

If it reports not-live, check logcat for SDK errors and for host-app gating:

```bash
adb shell logcat -d | grep -iE 'datadog|DD_SDK'
```

`libdatadog-ndk.so` in `/proc/<pid>/maps` is corroborating only: the SDK loads it with a
plain `System.loadLibrary`, which does not reliably emit a trace slice.

### 2. Validate the protocol with an A/A run

Same APK in both arms. True delta is zero, so whatever comes out is the protocol's error.

```bash
cd tools/coldstart-benchmark
PKG=<app.id> EXPECT_B=0 LABEL_A=A1 LABEL_B=A2 \
  ./coldstart_bench.sh baseline.apk baseline.apk
./ab_stats.py results_<timestamp>.csv --baseline A1 --treatment A2
```

`EXPECT_B=0` because both arms are the baseline APK; to A/A the treatment APK, use
`EXPECT_A=1 EXPECT_B=1`.

Pass: the paired block CI straddles zero, the order effect is not significant, and the MDE is
small enough to detect the effect the A/B is looking for. **Do NOT require per-block deltas to
share a sign**: the true delta is zero, so they should straddle it. Unanimity across 8 blocks
happens <1% of the time and indicates directional bias, not cleanliness.
**If A/A fails, no A/B number from that setup means anything.**

### 3. Run the A/B

```bash
PKG=<app.id> ./coldstart_bench.sh baseline.apk treatment.apk
./ab_stats.py results_<timestamp>.csv
```

Defaults are 4 runs × 8 blocks (32 measured launches per arm, ~1 hour). `coldstart_bench.sh`
aborts if the treatment arm has no `datadog-*` threads, if the baseline arm unexpectedly has
some, or if any measured launch is not `LaunchState=COLD` / `Status=ok` with the app in the
foreground afterwards.

Give Session Replay its own arm, but do not assume the answer. It is the heaviest feature
while a session is recording; that is not the same as being heavy at **startup**. Measured on
a React Native app (8×4, 32 launches/arm, mid-range device), enabling it added +8.9 ms TTID
(CI [−3.2, +21.0]) and +10.0 ms TTFD (CI [−20.7, +40.6]), neither separable from zero,
against a core-SDK cost of +24.7 / +78.7 ms. If disabling Session Replay does not move the
number, the cost is in the core SDK.

### 4. Attribute with a trace (optional)

```bash
PKG=<app.id> ./capture_trace.sh treatment.apk treatment 1
./.venv/bin/python verify_trace.py treatment.pftrace --package <app.id>
```

Set `TRACE_ENDPOINT` to the A/B metric the trace is meant to explain. It defaults to
`total_ms` and validates a successful cold `am start -W` first frame. Use `ttfd`, or
`app_trace_ms` together with the same `APP_TRACE_REGEX` as the benchmark, to require that later
marker before Perfetto stops. App-owned regex matches are restricted to the installed package's
unique UID, so a foreign process cannot provide either the A/B value or the trace endpoint. The
capture is invalid if the selected endpoint is not reached.
Capture names are non-destructive: an existing `.pftrace` is never overwritten.

**Do not edit the harness scripts while a run is in flight.** Bash re-reads a running script
by byte offset, so adding or removing lines in `coldstart_bench.sh` mid-run makes it resume parsing
mid-statement. Observed once: a three-line edit during a 6-minute run produced
`syntax error near unexpected token` after the last block, and the CSV — with all its rows
correctly collected — was stamped `# RUN ABORTED`, which `ab_stats.py` then refuses. Queue edits
until the run exits.

Use the venv interpreter: `verify_trace.py`'s shebang is the system `python3`, which will
not see a venv-installed `perfetto`.

## Controls the scripts enforce, and why

| control | failure it prevents | measured cost of omitting |
|---|---|---|
| paired per-block analysis | pooling launches ignores between-cell variance | 2×15 unpaired false-positives 23% of the time at a 4 ms between-block shift; 8×4 paired holds at 5%. Reproduce: `./fp_simulation.py` |
| ABBA arm order | fixed order turns session drift into a fake treatment effect | manufactured a significant +12.7 ms on identical APKs |
| pre-grant runtime permissions | prompt reappears each launch and instances accumulate | 23 stacked dialogs; ~23 ms, and most of the 18.9 → 11.7 ms pooled-sd drop |
| abort on a permission that cannot be granted | a partially granted app is a different scenario from the permissions-already-decided one being measured. Retried across passes first, since a grant can depend on another permission. `ALLOW_PARTIAL_PERMISSIONS=1` accepts the weaker guarantee and names what stayed denied | — |
| stamp each arm's effective permission outcome | a role/exemption change silently mixes permission-dependent startup paths across cells or pooled runs even when no dialog appears | — |
| one snapshotted Android user for package, permission and launch commands | personal/work-profile state gets mixed, so a no-op grant can be recorded as owned and revoked later | — |
| foreground assertion, every launch | dialog/crash/ANR on top of the app | silently corrupted two full A/A baselines before it existed; contamination accumulates, so once-per-arm cannot see it |
| numeric `TotalTime` plus `LaunchState`/`Status` assertion | accepting a truncated or malformed `am start -W` result as a valid conditioning/measured launch | — |
| md5 attestation of the install | measuring a build you didn't intend | invalidated an entire trace pair |
| verified uninstall postcondition before `install -r`, with refusal when another Android user owns the package | a protected package preserves data, caches and profile state while APK md5 still passes; global `adb uninstall` would delete another profile's data | — |
| `compile -m speed-profile -f` + discard warm-ups | no AOT profile makes early launches slow and erratic | first-block means differed by ~17 ms |
| real launcher intent | `am start -n <component>` isn't an icon tap | wrong code path on apps that route the launcher through activity aliases |
| pre-registered warm-up count | post-hoc outlier dropping | turned a null into a "finding" in one report |
| device-state snapshot + restore trap | leaving a device with no lock screen, animations off, Wi-Fi flipped on and permissions granted | — |
| refuse to mutate a setting with no restorable snapshot | a key the device never set reads `null`, and guessing a default leaves a borrowed device changed. Write it once (`settings put`) and re-run. The two radio settings are the exception a device can genuinely lack: `ALLOW_UNVERIFIED_RADIOS=1` accepts an unreadable one and restores nothing for it | — |
| read back every animation scale after setting it | `settings put` reports success on a device that ignored it, so the header would name a rendering scenario that was never measured | — |
| reserve the CSV and log before touching the device | two runs started in the same second share the timestamped filenames and interleave or truncate each other's evidence | — |
| mandatory `aapt2` package preflight (all three device-touching scripts; both APKs in the A/B) | `PKG` naming a different app than the APKs (every block runs `adb uninstall $PKG`), or arms built from different app versions | — |
| launcher resolved *after* each install | resolving up front cannot work on a clean device, and silently reuses a component read off a leftover build when one is installed | — |

## Reading the statistics

`ab_stats.py` prints all of this. The rules:

- **The paired block-level delta is the primary endpoint.** Launches inside one arm×block
  cell share an install, an AOT compilation and a thermal state, so an unpaired test over
  pooled launches estimates the SE from within-cell scatter only and is anti-conservative.
  ABBA removes the ordering *bias*; it does nothing about this variance underestimate. The
  Welch numbers are printed as `[diagnostic]` for contrast, not for reporting.
- **Blocks buy power, runs per block buy less of it.** The CI narrows with √(blocks). Below
  3 complete blocks `ab_stats.py` refuses to print an interval at all; at 2 blocks
  `t_crit(df=1) = 12.7`, which cannot support any significance claim.
- **Report mean and median.** If they disagree materially the distribution is skewed and
  neither stands alone. One dataset: mean +8 ms, median +40 ms, a fivefold swing on
  statistic choice.
- **Never quote a bare average.** A 4-run-per-arm dataset had a 95% CI of
  **[−106, +122] ms**: it could not distinguish 0 from 120 ms.
- **Check MDE before believing a null.** "No significant impact" from an under-powered run
  means "the run couldn't have detected it either way". The script computes required even block
  counts from the run's own between-block sd; don't transplant a required-n between apps or
  devices. If all block deltas are identical, zero sample variance is not proof of zero population
  variance: the primary CI and MDE are non-estimable and must not be quoted.
- **Per-block deltas straddling zero is normal**, not a warning, whenever the effect is
  comparable to the between-block sd. Judge spread by the CI and MDE, never by sign counting.
- Every measured launch is rechecked for **SDK liveness on the process just measured**, across
  all of the package's processes, and a contradiction with the arm aborts the run. The per-cell
  probe cannot cover it: initialization that is first-launch-only, consent-gated or
  remote-config-gated passes the probe and then never happens again, and the app's own log
  marker is absent in most apps, so that gate passes vacuously.
- The liveness probe is itself a conditioning launch. Give it a post-settle logcat boundary,
  target/foreign-display scan and final-foreground check before its thread result can count.
- A launch whose logcat buffer holds no `ActivityTaskManager: Displayed <pkg>/` line is
  **rejected**, not accepted: that line anchors the mid-window foreground check, so without it
  the check did not run and the row carries no evidence of a clean window.
  `ALLOW_NO_DISPLAYED_MARKER=1` accepts the weaker guarantee and records `foreground=NA`, which
  `ab_stats.py` warns about whatever metric is analyzed.
- Establish the foreground logcat boundary **after** the force-stop settle. From that clear onward,
  any foreign `Displayed` event contaminates the guarded window, including a permission/system
  activity that draws before the app's first frame and hands back before the final snapshot.
- Apply that contract to every `capture_trace.sh` conditioning launch too: each gets its own
  verified boundary, target marker, foreign-display scan and final-foreground check before its
  SDK-liveness result can count. Abort rather than replacing a contaminated launch, because the
  requested `WARMUP + 1` position is part of the trace protocol.
- The order-effect test **refuses to report** when arm and position are confounded. With a
  single block, arm A is always first, so any "order effect" *is* the treatment effect —
  previously this reported a genuine +30 ms regression as an ordering artifact. It is also
  **paired on blocks**, like the primary endpoint: one `2nd − 1st` delta per block, so it cannot
  manufacture an order effect out of cell-level shifts. ABBA makes the treatment effect cancel
  out of those deltas.
- **Concatenating CSVs that omit or disagree on mandatory device/protocol metadata is refused**
  (`--allow-mixed` to override). Two missing values are not evidence that the runs match.
  Namespacing block ids stops blocks merging; it does not make
  two experiments comparable. A differing `warmup` counts as a differing protocol: every cell
  is a fresh install, so the warm-up count sets where in the post-install JIT/profile ramp the
  measured launches sit. `blocks` and `runs` may differ, since they only lengthen the tail. The
  normal protocol keeps one physical device; it does not stamp the adb serial or distinguish two
  same-model devices on the same system build.
- **Each arm's effective permission outcome is part of pooled identity.** The benchmark stamps a
  hash of its canonical granted/denied sets and aborts if that state changes across fresh installs.
  The analyzer refuses a mismatch because apps can silently branch on grants without showing a
  dialog; legacy missing stamps produce a warning, not invented equality.
- **Duplicate aliases and byte-identical copies of the same CSV are refused.** Passing
  `results.csv` and `./results.csv`, a symlink/hard link, or an archived copy is not another run;
  counting it twice would narrow the interval without adding evidence. This catches accidental
  duplicate input, not deliberate tampering with operator-owned files or code.
- **Pooling CSVs whose `baseline_md5` / `treatment_md5` disagree is refused** as well. Two runs
  from successive APK pairs on the same device agree on every other metadata field, so without
  the digests they pool silently.
- **The label -> arm mapping is part of the run's identity** (`label_a` / `label_b`). Swap
  `LABEL_A` and `LABEL_B` between two runs of the same apk pair and both digests still agree
  while the second file's deltas enter the pool with the sign reversed — a real regression
  cancels to "no effect". Rename them instead and that file contributes no rows at all while
  the output still lists it as pooled, so a file holding neither requested label is refused too.
- **`--metric app_trace_ms` additionally requires a matching `app_trace_id`**, the md5 of
  `APP_TRACE_REGEX`. That metric's window is whatever the app's own log line measures, so two
  files captured with different patterns can hold native-init duration and total launch
  duration under one column name. Every other metric is defined by the harness.
- **The selected endpoint must exist on every otherwise eligible measured launch.** An `NA`
  can be a slow launch censored by the collection window, so dropping it and reporting the
  faster survivors can manufacture an improvement. The analyzer refuses by default.
  `--allow-missing-endpoint` exposes diagnostics only and keeps the primary interval suppressed.
- **Every selected measured row must carry explicit `status`, `launch_state` and `foreground`
  evidence.** Legacy missing/empty fields are not filled with passing defaults. Their values remain
  available for descriptive diagnosis, but the CI, MDE and significance verdict are suppressed.
- **The primary endpoint is gated on complete order evidence and the counterbalancing of the
  blocks that actually contribute.** Every contributing block must record which arm ran first;
  otherwise the CI, MDE and significance verdict are suppressed. With complete evidence,
  `order` cancels out of the block deltas only when each
  arm ran first equally often. If they all ran the same arm first the interval is **suppressed** —
  every delta is then `effect + order` with no way to separate them. Otherwise the residual
  fraction is printed: with an odd number of contributing blocks it cannot be better than `1/k`.
- **The relative percentage uses the same block weighting as the absolute primary delta.** Its
  denominator is the equal-weight mean of contributing baseline cell means, not the mean of every
  baseline launch; pooled files may differ in `runs`, but a high-run-count file must not dominate
  only the denominator. A zero denominator makes the percentage undefined, not the absolute CI.

## Where the SDK's startup cost comes from

These are the real contributors. State them plainly in any writeup: they are all
discoverable from a trace or from StrictMode, so a result that omits them reads as
incomplete rather than favorable.

- **dex/APK growth** → extra class loading and verification, independent of init
- **`DdRumContentProvider` runs before `Application.onCreate`** whether or not
  `Datadog.initialize()` is called (registered in
  `dd-sdk-android-internal/src/main/AndroidManifest.xml`), so an "SDK disabled" build is not
  a zero-cost build
- **deliberate main-thread disk I/O at init**: `CoreFeature.kt:275`,
  `NdkCrashReportsFeature.kt:55` and `:111`, plus Session Replay's requirement checkers.
  `StrictModeExt.kt` exists specifically to suppress StrictMode for these, so "no additional
  I/O" is not an accurate description — StrictMode or `sched_blocked_reason` will show it.
- **executor/thread creation at init**, which competes for cores on low-end devices
- **per-frame callbacks** when vitals or long-task tracking are on
- **Session Replay**: heaviest feature during a recording session, but its measured
  *startup* increment on one React Native app was ~9 ms and inside the noise. Measure it in
  its own arm rather than assuming either way

On React Native, check for **double initialization** — native in `Application.onCreate` and
again from JS. The core guards re-init, but `DdSdkImplementation.initialize` has no early
return: it rebuilds its configuration, and `enableJankStatsTracking` plus the
`Choreographer.FrameCallback` in `FrameRateProvider` are **not** guarded.

## Bias direction of the controls

Every discretionary control removes variance, and most bias the measured SDK cost *downward*.
Full table in the
[guide](../../../docs/benchmarking_sdk_cold_start.md#which-controls-bias-the-result-and-in-which-direction).
The short version: forced AOT, discarded warm-ups, `am force-stop` (which does **not** evict
the page cache), TTID-only measurement and pre-granted permissions all shrink the number. The
metric is a **process-cold, page-cache-warm** start to first frame, and is closer to a lower
bound than a worst case. Quote it with that caveat attached.

## Trace gotchas

`verify_trace.py` returns three outcomes, and the distinction matters:

| exit | meaning |
|---|---|
| 0 | SDK active, or correctly absent with `--expect-absent` |
| 1 | SDK **not** active, or the process/package is not in the trace. Sound as a negative *only* because the trace contains the cold start |
| 3 | trace unusable — no `bindApplication`, so no launch in it at all |
| 4 | with `--require-foreground`: the app lost the foreground *during* the capture, **or** ownership could not be established from the trace at all. Ownership spans every process of the app (`<pkg>` and `<pkg>:<private>`) and is tracked as the set of resumed activities, so a splash handing over to the next activity is held, while an activity that pauses and returns as itself is lost. Global ActivityManager `launching:` slices catch a foreign permission/system activity followed by a different target activity, which lifecycle gaps alone cannot distinguish from a valid handoff; the check also fails closed if that global launch evidence is unavailable — `ALLOW_MISSING_LAUNCH_MARKER=1` degrades to the lifecycle-only check on a device that never emits the slice, and reports `held-lifecycle-only` so the capture is never called clean. Rejection spans the whole capture, including the tail after the measured endpoint — conservative rather than exact, since the endpoint's timestamp is host-observed and absent from the trace. The detail lines are timestamped relative to the app's first resume: read them before re-capturing. Applies to both the treatment arm and the `--expect-absent` baseline arm. The SDK may be active; the trace is just not demonstrably the scenario the benchmark measured |

Why: the thread oracle's *absence* only proves something when init ran inside the trace
window. With a bare `linux.process_stats` data source, thread names come only from scheduler
events, so an idle `datadog-*` thread is invisible and absence proves nothing.
`capture_trace.sh` sets `scan_all_processes_on_start: true` and `record_thread_names: true`
to fix this, and `verify_trace.py` reports whether the trace enumerates idle threads so a
negative can be distinguished from an inconclusive.

The oracle is scoped to the app's own `upid` and **requires** a `datadog-*` thread. Matching
any slice or path containing "datadog" would false-positive unconditionally on this repo's own
sample apps.

Also: `am force-stop` before tracing or there is no cold start; keep app content consistent
(one real pair differed by ~90 ExoPlayer/MediaCodec threads and +24.8% CPU); and never
cross-reference a JIT trace (`Compiling baseline` slices present) with AOT-compiled benchmark
numbers.

**Timestamp work before calling it a startup cost.** The single biggest block of SDK CPU we
have measured (~149 ms of `com.datadog.*` JIT, ~390 ms with Session Replay, across ten traces)
turned out to be **post-launch** background CPU: zero compilations began before
`reportFullyDrawn()`, the first started 406–500 ms after it, in all ten traces. It costs nothing
on TTID or TTFD. Locate work relative to the measurement endpoint before attributing it, and
report the n: a single trace per arm had put the Session Replay figure at 443 ms.

`capture_trace.sh` captures **device-wide** process, thread and window data from every running
app. Check what a trace contains before attaching it to a ticket or sending it to anyone.

## Measurement-window trap

`am start -W TotalTime` ends at **first frame**. For React Native and Flutter apps much of
startup follows: in one measured case first frame landed at ~963 ms while framework bring-up
ran to ~1840 ms. `TotalTime` therefore *understates* any SDK cost landing after first frame.
Recommend the host app call `reportFullyDrawn()`; without it TTFD is unmeasurable by anyone.
The harness records `total_ms`, `displayed` (TTID from logcat) and `ttfd` separately.

`displayed` must be extracted anchored on the AOSP `ActivityTaskManager: Displayed <pkg>/…`
format: some vendors (e.g. Motorola) log their own `MotoDisplayed` line first, and an
unanchored `grep -m1` picks that one and silently yields `NA` on every row.

## Device caveats

- Never compare emulator to device, or across device models. Emulators inflate exactly the
  phases in question (native library loading, dex verification, disk I/O, thread contention)
  and have no thermal throttling.
- `dumpsys thermalservice` returns **stubbed values on some devices**: five consecutive
  byte-identical snapshots have been observed. A flat reading is not evidence of no drift.
- Perfetto callstack sampling and heap profiling need `<profileable android:shell="true"/>`
  or a debuggable build; on a `user` build, neither flag means an empty profile. App and
  framework atrace slices, including `bindApplication`, are captured regardless. Without a
  profileable build you get phase-level attribution, not method-level.
- `AIRPLANE=1` is a misnomer: it uses `svc wifi/data disable`, not airplane mode, and
  `svc data disable` needs root on most retail devices. The readback is strict in both
  directions. Under `AIRPLANE=1` both `wifi_on` and `mobile_data` must be exactly `0`;
  `null` or any unreadable value counts as not-provably-off and aborts, overridable with
  `ALLOW_UNVERIFIED_RADIOS=1`, and a literal `1` is never overridable. Under `AIRPLANE=0`
  at least one radio setting must read back `1`, with the same override for an unrepresented
  transport. That proves only the controlled setting, not association, validated internet,
  DNS or reachability of every app/SDK endpoint; the operator must keep those external
  conditions stable.
