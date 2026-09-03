---
name: coldstart-benchmark
description: Operating rules for the cold-start benchmark harness that ships in this repository at tools/coldstart-benchmark (verify_sdk_active.sh, coldstart_bench.sh, ab_stats.py, capture_trace.sh, verify_trace.py). Use when measuring the Datadog Android SDK's cold-start impact on an app, when app start got slower after adding the SDK, or when judging whether a cold-start benchmark can be trusted. You run these scripts and report what they output; a hand-rolled adb or logcat measurement is not a substitute for any of them, and their refusals are results rather than obstacles.
---

# Datadog Android SDK — Cold-start benchmark harness

## What this skill is

**The operating manual for one specific toolchain in this repository, not general advice
about benchmarking.** Everything below exists to get you to a number produced by
`ab_stats.py` from a CSV produced by `coldstart_bench.sh`. If you find yourself reasoning
about how to measure cold start, stop: that decision is already made and implemented, and
your job is to drive it correctly.

**Rules that bound the whole task:**

1. **Run the scripts. Do not reimplement them.** `am start -W`, `logcat`, `dumpsys` and
   `perfetto` invocations you compose yourself skip controls you cannot see (permission
   pre-grant, force-stop settle, cleared-buffer boundary, per-launch validity gates), and a
   result obtained that way is not comparable to one the harness produced. This is the
   failure that has actually happened, and it looked like a confident answer.
2. **Report only what the tools print.** Every millisecond figure you state must be
   traceable to a line of `ab_stats.py` output over a named CSV. Do not average CSV columns
   yourself, and do not quote `am start -W TotalTime` from a launch you drove by hand.
3. **An abort is an answer.** When a script refuses, the refusal names the condition that
   makes the measurement invalid. Fix that condition and re-run. Do not switch to a manual
   path, do not silence a gate, and do not use an `ALLOW_*` override unless its own
   documentation covers your situation.
4. **If the analyzer suppresses the primary interval, there is no number to report.**
   `NOT REPORTABLE` means the data cannot support an estimate. Say that, and say why.
5. **Ask for what only the app's owner knows** (build type, feature flags, SDK
   configuration, whether `reportFullyDrawn()` is called) instead of assuming it.
6. **No device, no result.** If you cannot reach an unlocked physical device over `adb`,
   report that you could not measure. Do not substitute an emulator, a reasoned estimate or
   numbers from another app.

## Why the controls exist

Cold-start A/B benchmarking looks trivial and is not. An uncontrolled protocol produces
numbers that are wrong by more than the effect being measured. On identical APKs (true
delta zero), a fixed-arm-order protocol with unhandled permission dialogs reported
**+12.7 ms, 95% CI [+3.0, +22.5], p = 0.011**: a statistically significant regression that
did not exist.

**Core principle:** never trust an A/B result until you have (a) proven the SDK actually
initializes in the treatment arm, and (b) run the same APK in both arms and confirmed the
result is null.

The scripts: [`tools/coldstart-benchmark/`](../../../tools/coldstart-benchmark), with
[`README.md`](../../../tools/coldstart-benchmark/README.md) as their reference for every
environment variable and exit code. The method in prose, with the measurements behind it:
[`docs/benchmarking_sdk_cold_start.md`](../../../docs/benchmarking_sdk_cold_start.md). Read
those for detail; do not re-derive their conclusions from first principles here.

The harness ships in the SDK repository, so it applies whether the app being measured is
ours or a customer's.

## When to Use

- App start got slower after adding the Datadog SDK
- Measuring what the SDK costs at startup, before or after adopting it
- Deciding whether a benchmark result (yours or someone else's) can be trusted
- Auditing whether a Perfetto trace can support the conclusion drawn from it

**Not for:** designing a different measurement, benchmarking something other than Android
cold start, or answering "how would one benchmark this?" in the abstract. If the task cannot
be served by running these scripts on a real device, say so rather than approximating them.

## The required procedure, in this order

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
`capture_trace.sh`'s settle *launches*, which derive from `EXPECTED_WARMUP`), then reads
`/proc/<pid>/task/*/comm`. Exit 0 = live,
1 = not, 2 = setup failure (no adb, no device, missing APK, or a thread list it could
not read — an unverifiable check exits 2, never 1, so "not live" always means the
script actually looked).

**Never substitute an ad-hoc `adb` probe for one of the scripts.** A hand-rolled `am start -W`
plus a `logcat` grep looks equivalent and is not: it skips the runtime-permission pre-grant, the
force-stop settle and the cleared-buffer boundary that every launch in the protocol gets. An app
whose initialization or logging is permission-gated then looks inert, and the improvised probe
reports an absence the protocol would never have produced. This has already happened, and it
reads as a confident result rather than as an error. To check whether a `datadog-*` thread or a
log signpost appears on a single launch, run `verify_sdk_active.sh`. If you need the CSV
columns too, run the shortest real A/A instead, with both expectations matching the APK you
pass twice (`EXPECT_A=1 EXPECT_B=1` for an SDK build, `0`/`0` for a baseline one; a mismatch
aborts on the liveness probe, which is the gate working):

```bash
# RUNS and BLOCKS are POSITIONAL (argv 3 and 4). Setting them in the environment is
# discarded, and you get the full 4x8 default run instead of a two-block probe.
EXPECT_A=1 EXPECT_B=1 LABEL_A=A1 LABEL_B=A2 WARMUP=0 \
  ./coldstart_bench.sh <apk> <apk> 1 2
```

Two blocks is below the three the analyzer needs, so treat that CSV as a probe, not a
measurement. **Absence observed any other way is not evidence of absence.**

If it reports not-live, check logcat for SDK errors and for host-app gating:

```bash
adb shell logcat -d | grep -iE 'datadog|DD_SDK'
```

`libdatadog-ndk.so` in `/proc/<pid>/maps` is corroborating only: the SDK loads it with a
plain `System.loadLibrary`, which does not reliably emit a trace slice. That line and the
Datadog logcat count print `unknown` when their source could not be read; neither is a
zero you can quote as absence.

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
PKG=<app.id> BENCHMARK_CSV=results_<ts>.csv BENCHMARK_ARM=<the arm's label> \
  ./capture_trace.sh treatment.apk treatment

# Or capture without CSV binding and assert the identities by hand:
PKG=<app.id> \
EXPECTED_APK_MD5=<baseline_md5 or treatment_md5 for this arm> \
EXPECTED_PERMISSION_STATE_ID=<permission_a or permission_b for this arm> \
EXPECTED_COMPILE_STATUS=<compile_status from the CSV header> \
EXPECTED_PERF_MODE=<perf_mode from the CSV header> \
EXPECTED_WARMUP=<warmup from the CSV header> \
EXPECTED_ANIMATIONS=<animations> \
EXPECTED_AIRPLANE=<airplane> \
EXPECTED_FP=<fp> \
EXPECTED_ANDROID_USER=<android_user> \
EXPECTED_LAUNCHER=<launcher> \
EXPECTED_SDK_LIVENESS=<expect_a or expect_b for this arm> \
  ./capture_trace.sh treatment.apk treatment 1
./.venv/bin/python verify_trace.py treatment.pftrace --package <app.id>
```

`BENCHMARK_CSV` and `BENCHMARK_ARM` read every expected identity from that run's own header, so
none of them is transcribed by hand. `BENCHMARK_ARM` is the label the CSV recorded, the same name
the rows and `ab_stats.py` use, so a typo is an error rather than the silent selection of the
other arm. A matching explicit `EXPECTED_*` may repeat the header value; a conflicting one is
rejected because the bound CSV is authoritative.

The binding refuses rather than guesses. A file with no `# device=` line is not a benchmark CSV;
more than one means it pools several runs and cannot identify one; an aborted run has no completed
A/B result to explain; exactly one `# RUN COMPLETE` marker is required; and every current-format
stamp must be present. It is read before every gate and changes only where an expected value comes
from, never what is compared.

Set `TRACE_ENDPOINT` to the A/B metric the trace is meant to explain. It defaults to
`total_ms` and validates a successful cold `am start -W` first frame. Use `ttfd`, or
`app_trace_ms` together with the same `APP_TRACE_REGEX` as the benchmark, to require that later
marker before Perfetto stops. App-owned regex matches are restricted to the installed package's
unique UID, so a foreign process cannot provide either the A/B value or the trace endpoint. The
capture is invalid if the selected endpoint is not reached. With `app_trace_ms`, also pass
`EXPECTED_APP_TRACE_ID=<app_trace_id>` so the capture proves its regex names the benchmarked event.
`EXPECTED_LAUNCHER` is required on both paths and compared against the component the
device resolves, so supply it for an unbound capture too; it must start with the application
id. Omit the positional SDK expectation when `BENCHMARK_CSV` is set: it is derived from the selected
arm's `expect_a` / `expect_b` stamp. Pass it, and a contradicting value aborts. Without
`BENCHMARK_CSV` it is required. Never set `WARMUP` for a capture; it is not an input and aborts.
All unbound expected inputs shown above are mandatory. `EXPECTED_APK_MD5` must equal the selected arm's
`baseline_md5` or `treatment_md5` before device mutation. `EXPECTED_PERMISSION_STATE_ID` must equal
that arm's `permission_a` or `permission_b` after permission setup. `EXPECTED_COMPILE_STATUS` must
equal the benchmark header's achieved `compile_status`, not merely use the same requested
`COMPILE_FILTER`. `EXPECTED_PERF_MODE` must equal its achieved `fixed` or `dynamic` performance
mode; `ALLOW_DYNAMIC_PERFORMANCE=1` is not an override for a mismatch. `EXPECTED_WARMUP` must equal
the header's `warmup` and is the only source of the settle count. `EXPECTED_ANIMATIONS` and
`EXPECTED_AIRPLANE` supply `ANIMATIONS` and `AIRPLANE` when those are unset and abort on an
explicit disagreement, for the two device controls a default of `0` used to let differ from the
A/B in silence. `EXPECTED_FP` must equal the header's `fp`: it is checked against
`ro.build.fingerprint` before anything is installed or uninstalled, because nothing else pinned
device identity and a capture from another model explains nothing about the run it is attached
to. `EXPECTED_ANDROID_USER` must equal the header's `android_user` and the active device user
before mutation. `EXPECTED_SDK_LIVENESS` must equal the selected arm's `expect_a` / `expect_b`
stamp, and the positional argument when one is given. A bound capture also requires the recorded
launcher component to equal the component resolved from the installed APK. Of the twelve expected identities, ten are
compared against an independent observable; `warmup` and the SDK expectation are not, so the
bound capture derives both from the CSV and says so; an unbound capture reports them as asserted.
Report that line as given rather than describing the capture as fully verified. Capture names are
non-destructive: an existing `.pftrace` is never overwritten.

**Do not edit the harness scripts while a run is in flight.** Bash re-reads a running script
by byte offset, so adding or removing lines in `coldstart_bench.sh` mid-run makes it resume parsing
mid-statement. Observed once: a three-line edit during a 6-minute run produced
`syntax error near unexpected token` after the last block, and the CSV — with all its rows
correctly collected — was stamped `# RUN ABORTED`, which `ab_stats.py` then refuses. Queue edits
until the run exits.

Use the venv interpreter: `verify_trace.py`'s shebang is the system `python3`, which will
not see a venv-installed `perfetto`.

## If a run aborts

Re-run the complete registered design from the beginning when you can. A completed CSV ends with
exactly one `# RUN COMPLETE`; an abort, killed process or host crash does not.

`ab_stats.py` will still analyze an interrupted run over the whole counterbalanced blocks it
collected, if there are at least four after flooring to an even count, and that result is
reportable. Report it only with the two things the tool prints beside it: the block shortfall
(analyzed of declared) and the recorded abort trailer. **Quote the MDE it prints, not the
design's** -- it is the power actually achieved. Never describe such a run as complete, and say
that re-running the full design is better.

Refused, not reportable: fewer than four whole blocks; a run whose declared matrix is whole but
which aborted anyway; an interrupted run passed alongside other CSVs. `--allow-aborted` inspects
any of them diagnostically and suppresses the primary interval.

## What a finished task looks like

Report these, and nothing that is not one of them:

- the `verify_sdk_active.sh` verdict for the treatment APK, and its exit code
- the A/A result, with the CSV filename it came from
- the A/B result as `ab_stats.py` printed it: the paired block mean, its 95% CI, the MDE at
  that block count, and the significance verdict, each quoted rather than recomputed
- every warning or `NOT REPORTABLE` line the analyzer emitted, and what it implies
- the bias direction of the controls that were on (see below), so the number is read as the
  lower bound it usually is
- if a trace was captured: the `verify_trace.py` verdict and exit code, plus what the trace
  can and cannot attribute

If any of those is missing because a script aborted or the device was unavailable, that
absence is the deliverable. A benchmarking task that ends in "the harness refused, here is
the condition it named" is complete and useful. One that ends in a number obtained some
other way is neither.

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
| require a readable, stable achieved dexopt status and stamp it separately from the requested filter | a successful compile command can leave different arms/cells at `verify` vs `speed-profile`, attributing compilation state to the SDK | — |
| require fixed-performance mode to be accepted, or `ALLOW_DYNAMIC_PERFORMANCE=1` and a `perf_mode` stamp | a rejected command silently leaves dynamic CPU behavior while benchmark and trace claim one scheduling scenario. Acceptance is the only evidence there is; Android cannot read the mode back | — |
| require background dexopt to be disabled | accumulated profile data can trigger compilation during a cell and change later launches | — |
| real launcher intent | `am start -n <component>` isn't an icon tap | wrong code path on apps that route the launcher through activity aliases |
| pre-registered warm-up count | post-hoc outlier dropping | turned a null into a "finding" in one report |
| device-state snapshot + restore trap | leaving a device with no lock screen, animations off, Wi-Fi flipped on and permissions granted | — |
| refuse to mutate a setting with no restorable snapshot | a key the device never set reads `null`, and guessing a default leaves a borrowed device changed. Write it once (`settings put`) and re-run. The two radio settings are the exception a device can genuinely lack: `ALLOW_UNVERIFIED_RADIOS=1` accepts an unreadable one and restores nothing for it | — |
| read back every animation scale after setting it | `settings put` reports success on a device that ignored it, so the header would name a rendering scenario that was never measured | — |
| reserve the CSV and log before touching the device | two runs started in the same second share the timestamped filenames and interleave or truncate each other's evidence | — |
| mandatory `aapt2` package preflight (all three device-touching scripts; both APKs in the A/B) | `PKG` naming a different app than the APKs (every block runs `adb uninstall $PKG`), or arms built from different app versions | — |
| launcher resolved *after* each install | resolving up front cannot work on a clean device, and silently reuses a component read off a leftover build when one is installed | — |

## Reading `ab_stats.py` output

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
  marker is absent in most apps, so that gate passes vacuously. Zero means every discovered PID
  came from a successful full `ps` listing and returned a readable thread list with no match. A
  failed listing cannot fall back to exact-name `pidof`, which omits private processes; a failed
  or empty read from any PID is likewise unknown and rejects both arms rather than proving SDK
  absence. One reader serves all three scripts, so
  the arm gate cannot pass on evidence a measured launch would have refused. A read that fails
  because one thread exited under the glob is retried against a still-running process, so churn
  costs a retry rather than the run.
- The liveness probe is itself a conditioning launch. Give it a post-settle logcat boundary,
  target/foreign-display scan and final-foreground check before its thread result can count.
- A launch whose logcat buffer holds no `ActivityTaskManager: Displayed <pkg>/` line is
  **rejected**, not accepted: that line anchors the mid-window foreground check, so without it
  the check did not run and the row carries no evidence of a clean window.
  `ALLOW_NO_DISPLAYED_MARKER=1` accepts the weaker guarantee and records `foreground=NA`, which
  `ab_stats.py` keeps descriptive but makes the selected comparison diagnostic-only: no primary
  CI, MDE or significance verdict is reportable.
- Establish the foreground logcat boundary **after** the force-stop settle. From that clear onward,
  any foreign `Displayed` event contaminates the guarded window, including a permission/system
  activity that draws before the app's first frame and hands back before the final snapshot.
- Apply that contract to every `capture_trace.sh` conditioning launch too: each gets its own
  verified boundary, target marker, foreign-display scan and final-foreground check before its
  SDK-liveness result can count. Abort rather than replacing a contaminated launch, because the
  requested `EXPECTED_WARMUP + 1` position is part of the trace protocol. Supply the benchmark
  header's `warmup` as required `EXPECTED_WARMUP`; it is the only source of that count, and
  setting `WARMUP` aborts before device access. Discard 1 uses the benchmark
  probe's 3-second pre-launch/8-second validation cadence; later discards use its warm-up cadence
  (5 seconds before launch, validation after 6, then the final 4-second wait). Matching launch
  count without matching elapsed conditioning time does not reproduce the same ramp. Perfetto
  starts *during* the last registered wait, rather than after it: the warm-up's final four seconds,
  or the last four seconds of the probe check when the header's `warmup` is zero. The final
  force-stop follows immediately, then the benchmark's five-second wait, log boundary and launch.
- The order-effect test **refuses to report** when arm and position are confounded. With a
  single block, arm A is always first, so any "order effect" *is* the treatment effect —
  previously this reported a genuine +30 ms regression as an ordering artifact. It is also
  **paired on blocks**, like the primary endpoint: one `2nd − 1st` delta per block, so it cannot
  manufacture an order effect out of cell-level shifts. ABBA makes the treatment effect cancel
  out of those deltas.
- **Concatenating CSVs that omit or disagree on mandatory device/protocol metadata is refused**
  (`--allow-mixed` to override). Two missing values are not evidence that the runs match.
  Namespacing block ids stops blocks merging; it does not make
  two experiments comparable. The achieved `compile_status` and `perf_mode` must agree when
  stamped files carry them, and warn when any file does not. A legacy missing stamp cannot hide a
  disagreement among the other stamped files; the
  requested `compile_filter` alone does not prove the same AOT/JIT state. A differing `warmup`
  counts as a differing protocol: every cell
  is a fresh install, so the warm-up count sets where in the post-install JIT/profile ramp the
  measured launches sit. `blocks` and `runs` may differ between valid files, since they only
  lengthen the tail, but once either field is present in one header both must be positive integers
  so that file's declared matrix can be checked. The
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
- **Every selected arm/block must contain each declared run ID exactly once.** A cell with two
  `run=1` rows and no `run=2` has the expected row count but ambiguous evidence; it is an
  incomplete matrix, not a reportable experiment. Unselected labels cannot satisfy this gate.
- **A selected-arm conditioning launch with `foreground=NA` suppresses primary inference.** Probe
  and warm-up rows do not enter the estimate, but they prepare permission, migration and
  JIT/profile state inherited by measured launches; unknown screen ownership there is not a pass.
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
- **The selected endpoint must be finite and non-negative on every otherwise eligible measured
  launch.** An `NA` can be a slow launch censored by the collection window, while a negative
  sentinel such as `-1` is not an elapsed time. Dropping either and reporting the faster survivors
  can manufacture an improvement. The analyzer refuses by default.
  `--allow-missing-endpoint` exposes diagnostics only and keeps the primary interval suppressed.
- **Every selected measured row must carry explicit `status`, `launch_state` and `foreground`
  evidence.** Legacy missing/empty fields are not filled with passing defaults. Their values remain
  available for descriptive diagnosis, but the CI, MDE and significance verdict are suppressed.
- **The primary endpoint is gated on complete order evidence and the counterbalancing of the
  blocks that actually contribute.** Every contributing block must record exactly one stable,
  complementary `{1}`/`{2}` position pair for the selected arms; missing, same or internally
  mixed positions suppress the CI, MDE and significance verdict. With valid evidence,
  `order` cancels out of the block deltas only when each
  arm ran first equally often. Any imbalance, including 3:1, suppresses the interval: every delta
  contains `effect + order`, and unequal counts leave a residual order term in the estimate.
- **The relative percentage uses the same block weighting as the absolute primary delta.** Its
  denominator is the equal-weight mean of contributing baseline cell means, not the mean of every
  baseline launch; pooled files may differ in `runs`, but a high-run-count file must not dominate
  only the denominator. A zero denominator makes the percentage undefined, not the absolute CI.

## Interpreting a result: where the cost landed in our measurements

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

## Which way the harness's controls bias the number

Every discretionary control removes variance, and most bias the measured SDK cost *downward*.
Full table in the
[guide](../../../docs/benchmarking_sdk_cold_start.md#which-controls-bias-the-result-and-in-which-direction).
The short version: forced AOT, discarded warm-ups, `am force-stop` (which does **not** evict
the page cache), TTID-only measurement and pre-granted permissions all shrink the number. The
metric is a **process-cold, page-cache-warm** start to first frame, and is closer to a lower
bound than a worst case. Quote it with that caveat attached.

## `capture_trace.sh` / `verify_trace.py` gotchas

`verify_trace.py` returns three outcomes, and the distinction matters:

| exit | meaning |
|---|---|
| 0 | SDK active, or correctly absent with `--expect-absent` |
| 1 | SDK **not** active, or the process/package is not in the trace. Sound as a negative *only* because the trace contains the cold start |
| 3 | trace unusable — no `bindApplication`, no force-stop boundary by either method, no final process generation, or a launch too close to the boundary to be told apart from the conditioning generation. The printed verdict names which |
| 4 | with `--require-foreground`: the app lost the foreground *during* the capture, **or** ownership could not be established from the trace at all. Ownership spans every process of the app (`<pkg>` and `<pkg>:<private>`) and is tracked as the set of resumed activities, so a splash handing over to the next activity is held, while an activity that pauses and returns as itself is lost. Global ActivityManager `launching:` slices catch a foreign permission/system activity followed by a different target activity, which lifecycle gaps alone cannot distinguish from a valid handoff; the check also fails closed if that global launch evidence is unavailable — `ALLOW_MISSING_LAUNCH_MARKER=1` degrades to the lifecycle-only check on a device that never emits the slice, and reports `held-lifecycle-only` so the capture is never called clean. Rejection spans the whole capture, including the tail after the measured endpoint — conservative rather than exact, since the endpoint's timestamp is host-observed and absent from the trace. The detail lines are timestamped relative to the app's first resume: read them before re-capturing. Applies to both the treatment arm and the `--expect-absent` baseline arm. The SDK may be active; the trace is just not demonstrably the scenario the benchmark measured |

Why: the thread oracle's *absence* only proves something when init ran inside the trace
window. With a bare `linux.process_stats` data source, thread names come only from scheduler
events, so an idle `datadog-*` thread is invisible and absence proves nothing.
`capture_trace.sh` sets `scan_all_processes_on_start: true` and `record_thread_names: true`
to fix this, and `verify_trace.py` reports whether the trace enumerates idle threads so a
negative can be distinguished from an inconclusive.

The oracle is scoped to the package processes started after the in-trace force-stop and
**requires** a `datadog-*` thread. The conditioning process is deliberately still alive when
Perfetto starts, so including every package `upid` would let its stale thread make a final launch
with no SDK pass. The boundary is the conditioning generation's last scheduler activity;
`sched/sched_process_free` is recorded but does not populate `process.end_ts` on the target
device, so the exact process-lifetime closure is used where it works and relied on nowhere.
Private processes started by the final launch remain in scope. The verifier requires the traced
launch to start at least a second after the boundary, against the five the protocol leaves, and
prints both the method and the margin: quote them, and do not treat a refused separation as an
SDK result. If the boundary or the final `bindApplication` cannot be established at all, the
trace is unusable and the verdict says which. Matching any
slice or path containing "datadog" would false-positive unconditionally on this repo's own sample
apps.

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

## Choosing `--metric`: the measurement-window trap

`am start -W TotalTime` ends at **first frame**. For React Native and Flutter apps much of
startup follows: in one measured case first frame landed at ~963 ms while framework bring-up
ran to ~1840 ms. `TotalTime` therefore *understates* any SDK cost landing after first frame.
Recommend the host app call `reportFullyDrawn()`; without it TTFD is unmeasurable by anyone.
The harness records `total_ms`, `displayed` (TTID from logcat) and `ttfd` separately.

`displayed` must be extracted anchored on the AOSP `ActivityTaskManager: Displayed <pkg>/…`
format: some vendors (e.g. Motorola) log their own `MotoDisplayed` line first, and an
unanchored `grep -m1` picks that one and silently yields `NA` on every row.

Look for a signpost the same way you look for the SDK: through a script. A launch you drive by
hand carries none of the permission and settle guarantees, so a marker missing from an
improvised probe can still be present in every launch of a real run.

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
  at least one radio setting must read back `1`. The override can accept an indeterminate read,
  but literal `wifi_on=0 mobile_data=0` is never overridable; external transports are outside this
  Wi-Fi/mobile-radio scenario. A `1` proves only the controlled setting, not association,
  validated internet, DNS or reachability of every app/SDK endpoint; the operator must keep those
  external conditions stable.
