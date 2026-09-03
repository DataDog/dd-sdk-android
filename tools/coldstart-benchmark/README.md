# Cold-start benchmark harness

Measures how much cold-start time the Datadog Android SDK adds to a host application, with
the controls needed to make the answer trustworthy.

**Full guide, including how to interpret the output:**
[`docs/benchmarking_sdk_cold_start.md`](../../docs/benchmarking_sdk_cold_start.md). This
file is the operator's reference for the scripts themselves.

> [!WARNING]
> **All three device-touching scripts (`verify_sdk_active.sh`, `coldstart_bench.sh` and
> `capture_trace.sh`) uninstall and reinstall the app under test**, which deletes all its
> data. That includes the step-zero verifier, so running it against an app you care about
> destroys its state. All three also pre-grant the app's runtime permissions.
> `coldstart_bench.sh` and `capture_trace.sh` also change device settings (animation scales,
> screen timeout, stay-awake, Wi-Fi, fixed-performance mode and the background dexopt job).
> Both abort before collection if Android rejects the request to disable background dexopt, or
> rejects fixed-performance mode without `ALLOW_DYNAMIC_PERFORMANCE=1`; otherwise CPU behavior or
> accumulated-profile compilation can change the scenario during the experiment.
> Both refuse to mutate the device unless every restorable setting has a concrete numeric
> snapshot; a key the device has never set reports `null`, and writing it once makes the state
> restorable. The two radio settings are the documented exception, since a device can genuinely
> not have one. `ALLOW_UNVERIFIED_RADIOS=1` accepts that, and restores nothing for it. They restore those settings on exit, including on Ctrl-C, and make a best-effort reversal
> of the two controls Android cannot read back. All three workflows revoke only permissions they
> changed from denied to granted, never permissions already granted at install and never
> device-wide. Use a test device.

## Why the extra machinery

A cold-start A/B without these controls produces numbers wrong by more than the effect
measured. Running the **same APK in both arms** (true delta zero) on a mid-range device:

| protocol | reported "difference" | 95% CI | p |
|---|---|---|---|
| fixed arm order, permission dialogs unhandled | +12.7 ms | [+3.0, +22.5] | **0.011** |
| counterbalanced, dialogs unhandled | +5.0 ms | [−4.4, +14.4] | 0.30 |
| counterbalanced + permissions pre-granted | −0.8 ms | [−6.9, +5.2] | 0.79 |

Most of these controls fix a failure mode we hit and measured; the rest are cheap
insurance against one that would be invisible after the fact.

## Two prerequisites that decide whether your numbers mean anything

**Build both APKs release-configured.** `isMinifyEnabled = true`, `isDebuggable = false`,
release signing, production R8 rules, no debug-only tooling (LeakCanary, Chucker, Stetho,
Flipper). A debug build's startup behavior has little to do with what your users experience.

**Run on a physical device.** Emulator results are effectively worthless here: a laptop core
is many times faster than a mid-range phone core, backed by an SSD rather than eMMC, with no
big.LITTLE scheduling and no thermal throttling. Those differences land squarely on native
library loading, dex verification, disk I/O and thread contention. No correction factor
recovers the real answer. Pick a device that resembles your actual user distribution, not the
newest phone on the team's desk.

## Requirements

- `adb` on `PATH`, or `ANDROID_HOME` / `ANDROID_SDK_ROOT` set, or `ADB=/path/to/adb`
- `aapt2` (Android SDK build-tools), or `AAPT2=/path/to/aapt2`. **Required**, not optional:
  it is what proves the APKs declare the `PKG` that `adb uninstall` is about to wipe
- exactly one authorized device attached (`adb devices` shows `device`, not `unauthorized`).
  With several attached, set `ANDROID_SERIAL=<serial>`
- the device **unlocked and on the home screen**. A locked device resumes the activity but
  never draws, so `am start -W` reports no `TotalTime` and `LaunchState=UNKNOWN`;
  `coldstart_bench.sh` refuses to start rather than collect unusable rows. `adb` cannot
  dismiss a PIN/pattern/password lock
- **Python ≥ 3.8** for `ab_stats.py` and `fp_simulation.py`. No third-party packages needed
- the `perfetto` package, **only** for the trace scripts:
  ```bash
  python3 -m venv .venv && ./.venv/bin/pip install perfetto
  ```
  `capture_trace.sh` finds this venv on its own. When running the verifier by hand, pass the
  venv interpreter explicitly. `verify_trace.py`'s shebang is `#!/usr/bin/env python3`, i.e.
  your system Python, which will not see the venv:
  ```bash
  ./.venv/bin/python verify_trace.py treatment.pftrace --package "$PKG"
  ```
- two **release-configured** APKs of the same app version (matching
  `versionCode`/`versionName`), differing only by the Datadog SDK

## Files

| file | role |
|---|---|
| `verify_sdk_active.sh` | **run this first**: proves the SDK actually initializes on-device. Uninstalls/reinstalls, so it deletes app data. Threads are read from **every** process of the package, `<pkg>` and `<pkg>:<private>` alike, so an app that initializes Datadog in a private process is not reported as SDK-less. Exit `0` = live, `1` = not live, `2` = the check could not run, which includes an unavailable full process listing or a thread list it could not read: an unverifiable liveness check never borrows the `1` that means the SDK is absent |
| `coldstart_bench.sh` | the A/B (or A/A) benchmark. Its liveness probe, warm-ups and measured launches all require a numeric `TotalTime` and apply the foreground gate before they can condition or enter the experiment. Each arm's effective runtime-permission outcome is stamped and must remain stable across fresh installs. The achieved dexopt status is likewise read after every compile, required to stay stable across all cells and stamped separately from the requested filter |
| `ab_stats.py` | statistics: paired per-block delta (primary), Welch and permutation tests (diagnostic), per-block drift, block-paired order effect, MDE / required even block counts. `--metric total_ms\|displayed\|ttfd\|app_trace_ms` selects the measurement window. Refuses duplicate aliases or byte-identical copies of an input file and pooled CSVs that omit or disagree on mandatory device/protocol metadata, including Android user, achieved `compile_status` and `warmup` (each cell is a fresh install, so the warm-up count fixes where in the post-install JIT/profile ramp the measured launches sit), CSVs whose arms were built from different APKs, CSVs that map their labels, SDK-liveness expectations or effective permission outcomes to different arms, and incompatible `APP_TRACE_REGEX` identities. The normal protocol keeps one physical device; the analyzer does not stamp the adb serial or distinguish two same-model devices on the same build. `--allow-mixed` overrides the metadata refusal for an operator who intends to pool anyway and will caveat the result. Soft-checks the two runtime-control outcomes, `compile_status` (the AOT/JIT state the device actually reached, which a matching requested `compile_filter` does not prove) and `perf_mode`: a disagreement among the stamped files is refused even when another legacy file omits the field, while absence warns because older CSVs must stay analyzable. Refuses aborted or truncated runs (including partially present, malformed or non-positive `blocks` / `runs` metadata, and a cell that has the right row count but does not contain every declared run ID exactly once), `measure_rejected` rows even when a hard kill prevented the abort trailer, legacy `phase=measure` rows whose recorded launch checks are invalid, and a selected endpoint that is missing, non-finite or negative on any otherwise eligible measured launch: excluding any of these can select away slow outcomes and manufacture an improvement. Rows that omit `status`, `launch_state` or `foreground`, or explicitly carry `foreground=NA`, remain descriptive but suppress the primary interval because unknown evidence is not a passing verdict; that suppression also applies when a selected-arm probe or warm-up explicitly carries `foreground=NA`, since its state conditions later measurements. A zero between-block sample variance is non-estimable rather than a zero-width CI/MDE; a zero baseline mean keeps the absolute estimate and interval but makes the relative percentage undefined. That percentage uses the same equal block weighting as the primary absolute delta, even when pooled files used different launches per cell. `--allow-aborted` and `--allow-missing-endpoint` expose survivor diagnostics only; the primary interval stays suppressed and is not reportable. `--recover-completed-blocks` is the one exception, and only for an aborted run whose whole completed blocks the harness itself counted in `# completed_blocks=N`: it re-declares the matrix to that even-floored prefix and leaves every other refusal in force. See [Recovering an aborted run](#recovering-an-aborted-run). With a complete valid endpoint, the primary result is also gated on each contributing block recording exactly one stable, complementary `{1}`/`{2}` position pair for the selected arms and on equal baseline-first and treatment-first block counts; unrelated labels cannot provide or overwrite that evidence |
| `capture_trace.sh` | Perfetto capture with attestation, cold-launch/endpoint checks and liveness gating. Its first discarded launch reproduces the benchmark's liveness-probe cadence; the remaining discards reproduce its warm-up cadence. Perfetto starts during the final conditioning launch's existing wait, not in a second app-running delay, before the measured sequence of force-stop, five-second wait, log boundary and launch. The required `EXPECTED_*` values bind the trace to the selected benchmark arm's APK, permission outcome, achieved dexopt and performance modes, warm-up count, Android user and SDK-liveness expectation; `EXPECTED_APP_TRACE_ID` additionally binds an `app_trace_ms` capture to the benchmarked regex. Every discard must report `Status=ok`, `LaunchState=COLD`, a numeric `TotalTime`, draw the target without a foreign activity reaching first draw, end with the target in the foreground, and satisfy a fully readable SDK-liveness check. A contaminated or unverifiable launch aborts rather than silently moving the trace earlier in or preparing a different post-install JIT/profile ramp. Atomically reserves the `.pftrace` path before changing device state and refuses to overwrite an existing capture |
| `verify_trace.py` | decides whether a trace shows the SDK running, and whether it *can* answer that at all. SDK liveness is scoped to the package processes started after the in-trace force-stop, including final-launch private processes but excluding the conditioning generation that was alive when Perfetto began. In practice that boundary is the conditioning generation's last scheduler activity: `sched/sched_process_free` is recorded, but on the target device it does not populate `process.end_ts` (measured: NULL on 849/849 processes), so the exact process-lifetime closure is preferred where available and depended on nowhere. Because a boundary cannot prove nothing was created on the wrong side of it, the verifier checks the separation the protocol provides: the traced launch must start at least a second after the boundary against the five the protocol leaves, and both the method and the margin are printed. A boundary neither method can establish, no process starting after it, or a launch too close to it is unusable (exit 3) rather than scoped to everything. `--require-foreground` additionally fails (exit 4) a capture the app did not own for the *whole* window, which an end-of-capture check cannot see. It applies to **both arms**, since a baseline capture is as contaminable as a treatment one, and it fails equally when lifecycle or ActivityManager launch evidence is unavailable: an unrunnable check must not report as a passed one (`ALLOW_MISSING_LAUNCH_MARKER=1` / `--allow-missing-launch-marker` is the one exception, and it downgrades the verdict rather than the report). Ownership is tracked as the set of resumed activities across every process of the final launch, `<pkg>` and `<pkg>:<private>` alike, so a splash activity handing over to the next one still counts as held while an activity that pauses and comes back as itself does not. Global ActivityManager `launching:` slices expose a foreign permission/system activity even when a later target activity makes the lifecycle gap resemble a valid handoff. Rejection currently spans the whole capture rather than only the interval the A/B measures: the endpoint is observed on the host, so its timestamp is not in the trace, and a takeover in the tail, which cannot have changed the A/B number, is refused on the same footing as one inside the measured interval. Conservative, not exact; the detail lines are timestamped (relative to the app's first resume), so read them before re-capturing |
| `fp_simulation.py` | reproduces the false-positive table below, using `ab_stats.py`'s own interval code |
| `lib.sh` | shared helpers, so the three device-touching workflows cannot drift into different definitions of the same thing: `adb`/`aapt2` resolution, Android-user and permission handling, device controls, verified uninstall, achieved compilation state, cold-launch validation and the `datadog-*` liveness probe. Full `ps` enumeration is mandatory because exact-name `pidof` cannot prove absence from private processes. All three read thread names through one helper, which reports zero only after every discovered process returned a readable thread list. A failed read is re-checked against `/proc/<pid>`: still running means a thread exited under the glob and it reads once more, gone means unknown liveness and it refuses. Unknown is never reported as absent |
| `tests/test_harness_regressions.py` | regression tests for the cross-script contracts, driven by a fake `adb`, so they need no device. They assert the refusals, not just the happy path. Run them after any change: `python3 -m unittest discover -s tests` |

## Usage

```bash
export PKG=com.example.app

# 1. Prove the SDK initializes. Nothing else matters if this fails.
./verify_sdk_active.sh app-with-datadog.apk "$PKG"

# 2. Validate the protocol: same APK both arms, expect a null result.
EXPECT_B=0 LABEL_A=A1 LABEL_B=A2 \
  ./coldstart_bench.sh app-no-datadog.apk app-no-datadog.apk
./ab_stats.py results_<timestamp>.csv --baseline A1 --treatment A2

# 3. The real comparison.
./coldstart_bench.sh app-no-datadog.apk app-with-datadog.apk
./ab_stats.py results_<timestamp>.csv

# 4. Optional: attribute the cost -- bound to the run it explains. The arm's own
#    expect_a/expect_b IS the SDK expectation, so the third argument is derived.
BENCHMARK_CSV=results_<timestamp>.csv BENCHMARK_ARM=B_withDD \
  ./capture_trace.sh app-with-datadog.apk treatment

# Or give the identities by hand; an explicit value overrides the header:
EXPECTED_APK_MD5=<treatment_md5> \
EXPECTED_PERMISSION_STATE_ID=<permission_b> \
EXPECTED_COMPILE_STATUS=verify \
EXPECTED_PERF_MODE=<perf_mode> \
EXPECTED_WARMUP=<warmup> \
EXPECTED_ANIMATIONS=<animations from the CSV header> \
EXPECTED_AIRPLANE=<airplane from the CSV header> \
EXPECTED_FP=<fp from the CSV header> \
EXPECTED_ANDROID_USER=<android_user from the CSV header> \
EXPECTED_SDK_LIVENESS=<expect_b for this arm> \
  ./capture_trace.sh app-with-datadog.apk treatment 1
./.venv/bin/python verify_trace.py treatment.pftrace --package "$PKG"
```

`BENCHMARK_CSV` and `BENCHMARK_ARM` read every expected identity from that run's own header, so
none of them is transcribed by hand. `BENCHMARK_ARM` is the label the CSV recorded, the same name
the rows and `ab_stats.py` use, so a typo is an error rather than the silent selection of the
other arm. An `EXPECTED_*` passed explicitly still overrides the header and still faces the same
attestation.

The binding refuses rather than guesses. A file with no `# device=` line is not a benchmark CSV;
more than one means it pools several runs and cannot identify one; an aborted run has no completed
A/B result to explain; and a CSV predating a stamp is named rather than defaulted. It is read
before every gate and changes only where an expected value comes from, never what is compared.

**Nine of the eleven identities are attested; two are derived.** Each of the nine is compared
against an independent observable: the APK's own digest, the device's fingerprint, the achieved
permission, dexopt and performance state, the md5 of the regex in use, or the animation and radio
scales read back after they were applied. The other two have none. `warmup` has no trace-time
observable at all, because the settle launches run before Perfetto starts, and the SDK expectation
is a choice rather than a reading. Both are derived from the bound CSV instead, and the capture
prints which source each came from: `header` is derived from the run, `explicit` is the operator
asserting the same fact a second time. An unbound capture asserts both, and says so.

Each `coldstart_bench.sh` run prints the `results_<timestamp>.csv` it wrote; pass that path
to `ab_stats.py`. The CSV and matching `bench_<timestamp>.log` are atomically reserved before
device access, so parallel runs that choose the same second fail rather than interleave or
overwrite evidence. Step 2 sets `EXPECT_B=0` because both arms are the baseline APK; to A/A the
treatment APK instead, use `EXPECT_A=1 EXPECT_B=1`. A `coldstart-benchmark` skill under
`.claude/skills/` drives these same steps if you are working through a coding agent.

Arguments are `<baseline.apk> <treatment.apk> [runs-per-block] [blocks]`, defaulting to
**4 runs × 8 blocks**: 32 measured launches per arm, and roughly an hour on a mid-range
device. `blocks` must be even, for ABBA counterbalancing.

**Add blocks, not runs, when a result is underpowered.** The primary endpoint is a paired
test over per-block deltas, so the confidence interval narrows with the square root of the
number of *blocks*. Fewer than 3 complete blocks and `ab_stats.py` refuses to report an
interval at all.

### Environment variables

| var | default | meaning |
|---|---|---|
| `PKG` | *required* | your application id |
| `EXPECT_A` / `EXPECT_B` | `0` / `1` | per-arm SDK-liveness expectation; set `EXPECT_B=0` for an A/A run of the baseline APK. A value other than `0`/`1` is rejected rather than silently disabling the gate |
| `LABEL_A` / `LABEL_B` | `A_noDD` / `B_withDD` | arm labels in the CSV. Use only letters, digits, `.`, `_` and `-`; delimiters and whitespace are rejected before the run. Leaving the variable **unset** takes the default; setting it to the **empty string** is rejected rather than silently defaulted |
| `WARMUP` | `3` (benchmark); rejected by `capture_trace.sh` | discarded launches at the start of each arm×block cell. The preceding liveness probe and every warm-up must pass the same numeric cold-launch and foreground gates as a measured launch; an invalid conditioning launch is recorded as rejected and aborts instead of silently changing the requested post-install ramp. Pre-registered: nothing else is ever dropped. For `capture_trace.sh`, `WARMUP` is not an input at all: pass the CSV header's value as required `EXPECTED_WARMUP`, and the settle count derives from that alone. Setting `WARMUP` for a capture aborts, including when it agrees with the header, because a variable that is no longer read must not look honored. The trace performs `EXPECTED_WARMUP + 1` settle launches: the first uses the probe's 3-second pre-launch/8-second validation cadence, while later launches use the warm-up's 5-second pre-launch, 6-second validation and 4-second post-validation cadence. Each also gets its own verified logcat boundary, target/foreign-display checks, final-foreground check and SDK-liveness gate. Perfetto readiness consumes the last conditioning wait: the warm-up's final four seconds when the header's `warmup` is above zero, or the final four seconds of the probe check when it is zero. The traced launch's force-stop follows immediately, matching the measured launch rather than adding another app-running delay. `ab_stats.py` refuses to pool CSVs recorded with different warm-up values for the same reason |
| `COMPILE_FILTER` | `speed-profile` | requested AOT filter. `speed-profile` is what Play installs converge to, but a fresh install with no profile commonly achieves `verify`. The harness reads the achieved status after every compile, aborts if it is unreadable or changes across cells, stamps it as `compile_status`, and refuses pooled runs with a different status. `speed` gives lower variance but removes much of the class-load/verify cost the SDK contributes and overrides any Baseline Profile. Use it as a secondary run, not a headline |
| `BENCHMARK_CSV` | unset | `capture_trace.sh`: the completed benchmark CSV this trace explains. Its header supplies every `EXPECTED_*` below, so none is transcribed. Refuses a file with no `# device=` line, one holding several (a pooled file cannot identify a single run), an aborted run, and a CSV predating a stamp it would have to invent |
| `BENCHMARK_ARM` | unset | `capture_trace.sh`: which arm, by the `label_a` or `label_b` the CSV recorded. Selecting by label rather than by position means a typo is an error, not the silent selection of the other arm |
| `EXPECTED_APK_MD5` | *required for trace* | the selected arm's `baseline_md5` or `treatment_md5` from the benchmark CSV header. The host APK must match before any device state changes; host-to-device attestation then separately proves those bytes were installed |
| `EXPECTED_PERMISSION_STATE_ID` | *required for trace* | the selected arm's `permission_a` or `permission_b` value. `capture_trace.sh` aborts unless permission setup reproduces the benchmark's effective granted/denied identity, including under `ALLOW_PARTIAL_PERMISSIONS=1` |
| `EXPECTED_COMPILE_STATUS` | *required for trace* | the `compile_status` value from the benchmark CSV header. `capture_trace.sh` aborts unless its freshly installed APK reaches the same achieved dexopt state, so a `verify`/`speed-profile` difference cannot be attributed to the SDK |
| `EXPECTED_ANIMATIONS` | *required for trace* | the `animations` value from the benchmark CSV header. It supplies the trace's `ANIMATIONS` when that is unset; an explicit different value aborts. Defaulting to `0` reintroduced the very hazard the `ANIMATIONS` note describes: the guide recommends `ANIMATIONS=1` as the honest per-frame measurement, so a non-default benchmark value is the expected case and omitting it here traced the other scenario |
| `EXPECTED_AIRPLANE` | *required for trace* | the `airplane` value from the benchmark CSV header, handled exactly like `EXPECTED_ANIMATIONS`. Both are `ab_stats.py` `_MUST_MATCH` keys, so two CSVs that disagree on them cannot be pooled; a trace disagreeing with the CSV it explains is the same error, and was unchecked |
| `EXPECTED_FP` | *required for trace* | the `fp` value from the benchmark CSV header. The connected device's `ro.build.fingerprint` must equal it, checked before anything is installed or uninstalled. Nothing else pinned device identity, which is the largest scenario difference there is and one `ab_stats.py` refuses to pool across |
| `EXPECTED_ANDROID_USER` | *required for trace* | the `android_user` value from the benchmark CSV header. The active user must match before installation, permission setup or output reservation, because Android user profiles have independent package data and permission state |
| `EXPECTED_SDK_LIVENESS` | *required for trace* | the selected arm's `expect_a` or `expect_b` value from the benchmark CSV header, which drives every runtime liveness gate. Bound to a CSV it also supplies the positional SDK expectation, so the third argument may be omitted; passing a contradicting one still aborts. Unbound, that argument is required and the equality between the two is the only thing that catches labeling a trace as the wrong arm while it still passes its own liveness check |
| `EXPECTED_APP_TRACE_ID` | *required when `TRACE_ENDPOINT=app_trace_ms`* | the benchmark CSV header's `app_trace_id`, which is the md5 of `APP_TRACE_REGEX`. The capture hashes its regex and refuses a mismatch before device access, so the trace cannot stop on a different app-owned endpoint than the metric it explains |
| `EXPECTED_PERF_MODE` | *required for trace* | the `perf_mode` value from the benchmark CSV header, `fixed` or `dynamic`. The trace aborts unless its fixed-performance request reaches the same outcome; `ALLOW_DYNAMIC_PERFORMANCE=1` only permits a rejected request to continue and does not permit a trace/benchmark mismatch |
| `EXPECTED_WARMUP` | *required for trace* | the `warmup` value from the benchmark CSV header, and the only source of the trace's settle count: `EXPECTED_WARMUP + 1` discarded launches put the traced launch at the benchmarked position in the post-install JIT/profile ramp. Unlike the nine attested identities it has no trace-time observable, because those launches precede the capture, so nothing in the trace can show how many there were. Derivation from the CSV is the strongest guarantee available; the capture prints whether the value came from the header or from an explicit override |
| `APP_TRACE_REGEX` | unset | an ERE matching a log line where your app reports its **own** startup duration; the last number in the match is recorded per launch as `app_trace_ms` and is analyzable with `--metric app_trace_ms`. Use this to A/B the metric your team already quotes, e.g. `APP_TRACE_REGEX='cold_launch total duration: [0-9]+'`. Both the benchmark scrape and `TRACE_ENDPOINT=app_trace_ms` watcher are restricted to the installed package's unique UID, so an unrelated process cannot provide the value; a legacy shared-UID install is refused because it cannot be attributed to one package. The pattern is compile-tested at preflight, so a malformed ERE aborts immediately instead of yielding `app_trace_ms=NA` on every row of an hour-long run. Check first that the trace actually emits, and that it ends where you think: one app's "first frame" trace ran 140 ms past `am start -W TotalTime` |
| `ANIMATIONS` | `0` | animation scales during the run. `0` removes a large variance source but **understates any per-frame SDK cost** (vitals / long-task `Choreographer` callbacks, Session Replay snapshots) because fewer frames are drawn during the launch. `1` measures with animations on. All three scales are read back in both benchmark and trace; any mismatch aborts before a result can be stamped with the requested value. Run both to quantify the bias |
| `AIRPLANE` | `0` | legacy name for the controlled Wi-Fi/mobile-radio state, not Android airplane mode. `1` requests both settings off; `0` requests at least one enabled. Readback is enforced in both directions: under `1`, both values must be exactly `0`; under `0`, at least one must be `1`. An unreadable value aborts unless `ALLOW_UNVERIFIED_RADIOS=1` accepts the weaker guarantee; a literal contradictory value is never overridable. An enabled radio does **not** prove association, validated internet, DNS or app/Datadog endpoint reachability. Keep that external condition stable yourself. Ethernet and USB tethering are not represented by these settings, and `svc data disable` needs root on most retail devices |
| `ALLOW_DYNAMIC_PERFORMANCE` | `0` | `1` proceeds when the device's power HAL rejects fixed-performance mode, measuring under dynamic CPU behavior instead of aborting. Not every HAL implements the control. What the harness knows either way is only that the request was accepted or refused: Android exposes no way to read the mode back, unlike the animation scales and radio settings, whose readback is their gate. So the outcome is recorded rather than assumed, as `perf_mode=fixed` or `perf_mode=dynamic`, and `ab_stats.py` refuses to pool the two. A trace must still equal its required `EXPECTED_PERF_MODE`; this flag is not a cross-scenario override. Expect wider intervals under `dynamic`; it is a noisier estimate of the same effect, not a different one |
| `ALLOW_VERSION_MISMATCH` | `0` | `1` lets the preflight through when the two APKs declare different `versionCode`/`versionName`. Only use it if you know why they differ; otherwise the SDK is not the only variable between the arms |
| `ALLOW_UNVERIFIED_PKG` | `0` | `1` disables the APK↔`PKG` check entirely in all three device-touching scripts. Only for the case where `aapt2` is genuinely unavailable **and** you have confirmed the package by hand. Every block runs `adb uninstall $PKG` |
| `ANDROID_SERIAL` | unset | target a specific device when more than one is attached |
| `ADB` / `ANDROID_HOME` / `ANDROID_SDK_ROOT` | auto-detected | tool locations |
| `ALLOW_NO_DISPLAYED_MARKER` | `0` | `coldstart_bench.sh` only. `1` accepts launches whose logcat buffer holds no `ActivityTaskManager: Displayed <pkg>/` line, which is the anchor for the mid-window foreground check. Those rows carry `foreground=NA`; `ab_stats.py` keeps them descriptive but suppresses the primary CI, MDE and verdict. Prefer fixing the device: the same line is the source of `displayed` and `ttfd` |
| `ALLOW_MISSING_LAUNCH_MARKER` | `0` | `capture_trace.sh` only. `1` forwards `--allow-missing-launch-marker` to the verifier, which then runs the whole-window foreground check on this app's lifecycle slices alone when ActivityManager's global `launching: <pkg>` slice is absent. That slice is an `ActivityMetricsLogger` implementation detail, not a documented contract, so a device that never emits it would otherwise fail every capture with a message pointing at atrace config. The degraded verdict is `held-lifecycle-only`, never `held`: a foreign activity taking over *between* two of this app's activities is invisible without the slice, so such a capture is partially verified and must not be described as clean |
| `ALLOW_PARTIAL_PERMISSIONS` | `0` | all three device-touching scripts. Pre-granting is retried across passes so a permission that depends on another still lands, and a permission the app can never hold (a hard-restricted one such as SMS or call-log, refused for any app that is not the exempt role holder) otherwise aborts the run, because a partially granted app is not the permissions-already-decided scenario the benchmark measures. `1` accepts the weaker guarantee and names the ungrantable permissions. A dialog for them can still appear mid-run; nothing prevents that, and the per-launch foreground gate is what catches it. The benchmark stamps each arm's effective granted/denied outcome, aborts if it changes within a run and refuses to pool a different outcome |
| `SETTLE` | `20` | `verify_sdk_active.sh` only: seconds to wait after launch before sampling threads. The verifier pre-grants the same declared runtime permissions as the benchmark and trace before launching, then revokes exactly those grants on exit, so permission-gated initialization is tested under the measured scenario |
| `ANIMATIONS` / `AIRPLANE` (trace) | from `EXPECTED_ANIMATIONS` / `EXPECTED_AIRPLANE` | `capture_trace.sh` takes each from the benchmark header when the variable is unset and aborts on an explicit disagreement, so there is no default left to differ from the A/B. Both are applied to the device and read back, which is what makes them attested rather than asserted. External reachability is not covered by that readback and must be held stable by the operator. A trace with animations off omits the per-frame SDK work an `ANIMATIONS=1` benchmark included |
| `TRACE_ENDPOINT` (trace) | `total_ms` | endpoint that `capture_trace.sh` must observe before Perfetto stops: `total_ms` validates the cold `am start -W` first-frame result, `ttfd` requires the app's `Fully drawn` marker, and `app_trace_ms` requires a matching `APP_TRACE_REGEX`. Set this to the metric the trace is meant to explain |

## Recovering an aborted run

A run that aborts in block 7 of 8 has already spent 45 minutes collecting six complete blocks.
Those blocks are whole cells, each with its own install, AOT compilation and full set of passed
gates, so nothing that happened in block 7 changed them. Only their number changed.

The harness records what it finished. On abort the CSV gets:

```
# RUN ABORTED (exit 1) -- another activity took the foreground: com.android.settings/...
# completed_blocks=6
# recover: collect 2 more blocks, then pool both CSVs
```

and the run prints the exact command to collect the remainder. To recover:

```bash
# 1. fix whatever the abort message named, then collect the missing blocks.
#    Prefer the command the aborted run printed: it already carries that run's
#    COMPILE_FILTER, ANIMATIONS, AIRPLANE, WARMUP, labels, expectations, any
#    ALLOW_* it needed and its APP_TRACE_REGEX. Dropping one of those is what makes
#    the second collection unpoolable with the first.
PKG=<app.id> WARMUP=3 ./coldstart_bench.sh baseline.apk treatment.apk 4 2   # 4 runs, 2 blocks

# 2. analyze the surviving prefix together with the new run
./ab_stats.py --recover-completed-blocks results_<aborted>.csv results_<new>.csv
```

You end up with the registered 8-block design and a fully reportable interval: no
`--allow-mixed`, no `--allow-aborted`, no diagnostic-only downgrade. Block ids are namespaced
per file, so blocks from the two collections are never merged, and the analyzer reports the
first-arm balance it observed rather than assuming it.

What `--recover-completed-blocks` does and does not relax:

- The block count comes from the harness's own `completed_blocks=` line. Nothing lets you
  choose a prefix, so a prefix cannot be picked to suit a result. A file with no such line (a
  `kill -9`, a power cut) is still refused outright.
- The count is floored to an even number, because the arm order alternates by block: an odd
  prefix is not counterbalanced, and carrying a 1/k residual order effect into the estimate is
  worse than discarding one good block.
- Rows after the prefix, including the rejected launch that aborted the run, are excluded and
  counted in the output.
- Every other refusal still applies to what remains: matrix completeness against the
  re-declared block count, validity columns, endpoint completeness, position pairs, metadata
  compatibility between the two files.
- Fewer blocks means a wider interval and a larger MDE, both reported. Recovery costs
  precision, never correctness.
- If the file has no `completed_blocks=` line, or fewer than 2 whole blocks, the flag
  prints why it does not apply and the file stays refused. It never silently does
  nothing.
- Rows whose block number is unreadable are kept rather than excluded, so the checks
  below still judge them, and are counted separately from the post-prefix rows.

**The one judgement it cannot make for you.** A one-off failure (a dialog, a foreign activity,
a single bad launch) leaves the prefix sound. A drifting failure (thermal throttling, storage
filling, an app-side change) degrades the tail *before* it aborts, so truncating at the failure
point keeps the fast blocks and drops the slow ones, which is exactly the outcome-dependent
censoring this tool refuses everywhere else. The abort reason is printed for that reason. If it
looks like drift, repeat the run instead of recovering it.

## What the benchmark does per arm, per block

Before the first install, the harness snapshots the current numeric Android user. Package lookup,
installation, runtime-permission state, launcher resolution, force-stop and launch are all scoped
to that same user; the value is stamped as `android_user` so results collected under a personal
profile and a work profile cannot be pooled as one experiment.

1. enumerate Android users and refuse to continue if another user/work profile owns the package,
   because host-side `adb uninstall` is global. Then verify the selected user has no installed
   package, uninstall when needed, require its package path to disappear, install and
   **md5-attest** against your local file. A protected or device-admin package aborts instead of
   falling through to `install -r` with preserved state. Use a dedicated test device rather than
   expecting the harness to preserve another profile's installation.
   Each APK is
   also re-hashed before every install and the run aborts if it no longer matches the digest
   recorded at preflight: the CSV header stamps the preflight digests, so an APK rebuilt in
   place mid-run would leave later blocks labeled with a build identity that is not theirs
2. `cmd package compile -m $COMPILE_FILTER -f` for a stable AOT profile
3. pre-grant every runtime permission the app declares for the selected Android user, including
   custom permission names and ignoring conflicting state from other users/work profiles,
   aborting if any grant is rejected and recording only denied-to-granted transitions so that
   permissions already granted at install are preserved when the run exits. Canonicalize and
   stamp each arm's effective granted/denied outcome; abort if it changes across fresh installs
4. run a validated cold launch, then probe `/proc/<pid>/task/*/comm`; **abort** if the
   launch lacks a numeric `TotalTime`, is not `Status=ok` / `LaunchState=COLD`, loses foreground
   ownership, or SDK liveness contradicts the arm's expectation
5. `WARMUP` launches, recorded as `phase=warmup` and excluded from analysis
6. `RUNS` measured launches via the real launcher intent, aborting if any is not
   `LaunchState=COLD` / `Status=ok`, lacks a numeric `TotalTime`, if the app is not the foreground activity afterwards, if
   another app's activity reached first draw during the collection window, or if the SDK's
   liveness in the process just measured contradicts the arm. The mid-window check is scraped
   from the same logcat buffer as the timings, so a dialog that appeared and vanished before
   the end-of-window check is still caught. When the buffer holds no `Displayed <pkg>/`
   line to anchor it, the launch is rejected rather than passed, because the check did not run
7. thermal snapshot after each block

Before any of that, a preflight reads both APKs with `aapt2` and refuses the run if they
declare a different application id from `PKG` (every block runs `adb uninstall $PKG`) or
different `versionCode`/`versionName` from each other (then the SDK is not the only variable).
`ALLOW_VERSION_MISMATCH=1` overrides the second. A missing `aapt2` is a **hard failure**, not a
warning: skipping the check because a tool is absent trades a fixable setup problem for an
unrecoverable one. `capture_trace.sh` applies the same package check before its own uninstall.
All three workflows also refuse a package installed for any other Android user and verify the
uninstall postcondition. APK-byte attestation alone cannot prove a fresh install: reinstalling the
same signed APK can preserve data, caches, permissions and profile state while producing the
expected md5.

The launcher activity is resolved **after each install**, from the build just installed, never
once up front. `resolve-activity` asks the package manager about the *installed* app, so hoisting
it made the harness unusable on a clean device and, worse, let it reuse a component read off a
leftover build. If the two arms resolve different components the run aborts: they would not be
entering through the same path.

Arm order is counterbalanced across blocks (odd blocks baseline→treatment, even blocks
treatment→baseline) and each launch's position is recorded, so `ab_stats.py` can test for an
ordering bias rather than assume it away. Device settings are snapshotted before the run and
restored from an `EXIT` trap; `INT`/`TERM` exit into it, so Ctrl-C stops the run and restores
the device exactly once.

## Output

`results_<timestamp>.csv`, one row per launch, warm-ups marked rather than deleted:

```
# device=... sdk=... abi=... emulator=0 android_user=0 compile_filter=... compile_status=... perf_mode=... blocks=... runs=... warmup=... fp=... launcher=... airplane=0 baseline_md5=... treatment_md5=... label_a=... label_b=... expect_a=0 expect_b=1 app_trace_id=...
# permission_a=...  # effective granted/denied outcome for the baseline arm
# permission_b=...  # effective granted/denied outcome for the treatment arm
label,block,pos_in_block,phase,run,total_ms,launch_state,status,foreground,displayed,ttfd,app_trace_ms,dd_enabled,dd_threads,dd_native_init_ms,dd_rn_init_ms
```

| column | meaning |
|---|---|
| `label` | arm (`LABEL_A` / `LABEL_B`) |
| `block`, `pos_in_block` | block number, and whether this arm ran 1st or 2nd within it |
| `phase` | `probe` or `warmup` (excluded), or `measure`; a failed probe, warm-up or measurement is suffixed `_rejected` and aborts the run |
| `run` | launch index within the phase. Every selected measured cell must contain each declared ID `1..runs` exactly once; a duplicate cannot compensate for an omitted observation |
| `total_ms` | `am start -W` `TotalTime`, the primary metric |
| `launch_state`, `status` | `am start -W` `LaunchState` / `Status`; a probe, warm-up or measured launch that is not `COLD`/`ok`, or has no numeric `total_ms`, aborts the run |
| `foreground` | `ok` if the app was the resumed activity after the launch; `OTHER` if something else was; `OTHER_MID` if another app's activity reached first draw after the post-settle logcat boundary, before or after the app's own first frame, which catches a dialog that appeared and vanished before the end-of-window check; `NO_MARKER` (aborts) if the buffer holds no `Displayed <pkg>/` line proving the app reached first draw. Absence of the check is not evidence of a clean window, and `ALLOW_NO_DISPLAYED_MARKER=1` accepts the weaker guarantee by recording `NA` instead |
| `displayed` | logcat `ActivityTaskManager: Displayed` (TTID). Anchored on the AOSP format; some vendors log their own line first |
| `ttfd` | logcat `Fully drawn`, present only if the app calls `reportFullyDrawn()`. `NA` on every row usually means the app never reached its own ready state (a pending permission dialog will do it), not that the metric is unavailable |
| `app_trace_ms` | the app's own reported duration, if `APP_TRACE_REGEX` was set. Matching is restricted to the installed package's unique UID, including all of its private processes |
| `dd_threads` | `datadog-*` threads counted across **every** process of the package at the end of this launch's collection window. This is the per-launch liveness oracle: a contradiction with the arm's expectation aborts the run. Zero means a complete `ps` listing succeeded, every discovered package process was read and none matched. An unavailable full listing or a failed/empty read from even one PID is `NA` and rejects the launch, never evidence of SDK absence. The pre-cell probe cannot cover it, because init that is first-launch-only, consent-gated or remote-config-gated passes the probe and then never happens again |
| `dd_enabled`, `dd_native_init_ms`, `dd_rn_init_ms` | populated only if the host app logs its own initialization state and timing. `dd_enabled` is the *app's* marker and most apps never emit it, which is why `dd_threads` exists |

`bench_<timestamp>.log` holds preflight assertions, per-arm thread counts and thermal snapshots.

Emulator runs are stamped `emulator=1` and `ab_stats.py` prints a warning banner. Emulator
timings are for harness validation only, never for reporting.

## Interpreting results

- **The paired per-block delta is the primary endpoint.** The unpaired Welch figures are
  printed as `[diagnostic]` and are anti-conservative: launches within one arm×block cell
  share an install, an AOT compilation and a thermal state, so pooling them estimates the
  standard error from within-cell scatter only. At a realistic 4 ms between-block shift, a
  2×15 unpaired design false-positives 23% of the time against a nominal 5%. Run
  `./fp_simulation.py` to reproduce that.
- CI includes zero → no regression demonstrated. The interval's upper bound is your
  defensible upper bound.
- mean and median disagree materially → skewed; quote neither alone.
- per-block deltas falling either side of zero is **normal** when the effect is comparable to
  the between-block sd. Judge the spread by the CI and the MDE, not by counting signs. For an
  A/A run in particular, deltas that all share a sign are evidence of a directional bias, not
  of a clean protocol.
- always check the printed MDE before believing a null result.
- **On a framework app, analyze `--metric ttfd` as well as the default `total_ms`.** TTID
  ends at first frame; if the app calls `reportFullyDrawn()` this is the only way to see
  cost landing in the later window. Measured on one React Native app, TTID was ~630 ms
  against a TTFD of ~2075 ms: first frame was under a third of startup, so TTID alone
  could not have seen an SDK cost in the remaining two thirds.

## Known limits

- The metric is a **process-cold, page-cache-warm** start to first frame. `am force-stop`
  does not evict the page cache, so by the first measured launch the app's dex, oat and
  native libraries are resident, so this protocol sees very little of the SDK's page-in cost.
- `am start -W TotalTime` ends at **first frame**. For React Native and Flutter apps much of
  startup follows, so this understates any cost landing later. Have the app call
  `reportFullyDrawn()` so `ttfd` is populated too.
- Several controls (forced AOT, discarded warm-ups, TTID-only, pre-granted permissions) bias
  the measured SDK cost *downward*. The direction of each is tabulated in the
  [guide](../../docs/benchmarking_sdk_cold_start.md#which-controls-bias-the-result-and-in-which-direction).
- `dumpsys thermalservice` returns stubbed values on some devices; a flat reading is not
  evidence of no drift.
- Content variance is absorbed by n and counterbalancing for the A/B, but **not** for single
  traces, so keep the app on the same screen/state across trace captures. Expect the same window
  to vary by hundreds of milliseconds of CPU between two captures of the *same* APK: use traces
  to find out what work exists and where, and the A/B for how much it costs.
- A trace is only comparable to a benchmark launch if the app owned the foreground for the whole
  capture. `capture_trace.sh` pre-grants runtime permissions and fails the capture if anything
  else was on top at the end, because SDK liveness verification does not catch this: a paused
  or stopped app still has all of its `datadog-*` threads, it just stops producing frames and
  never reaches `reportFullyDrawn()`.
- Keep the screen awake for the whole session. `capture_trace.sh` restores the screen timeout it
  found, so back-to-back captures on a device with a short timeout and a PIN will re-lock in the
  gap and every run after the first dies on the lockscreen check. Raise `screen_off_timeout` and
  `stay_on_while_plugged_in` yourself before a batch, and put them back afterwards.
- `capture_trace.sh` records **device-wide** process, thread and window data from every
  running app. Review a trace before sharing it.
- Never compare emulator to device, or across device models.
