# Measuring the SDK's cold-start impact on your own app

The Datadog Android SDK adds measurable work to application startup. Any observability
SDK does: it initializes before your app can report anything, and that initialization has
a cost. What matters is knowing *how much*, on your app, on the devices your users
actually hold.

This guide gives you a reproducible way to measure that. We use the same methodology and
the same scripts internally; they live in
[`tools/coldstart-benchmark`](../tools/coldstart-benchmark) so you can run exactly what we
run, and inspect how every number is produced.

**Contents**

- [Quick start](#quick-start)
- [Prerequisites](#prerequisites)
- [What this measures — and what it does not](#what-this-measures--and-what-it-does-not)
- [Step 1 — Build APKs that represent what you ship](#step-1--build-apks-that-represent-what-you-ship)
- [Step 2 — Use a physical device, not an emulator](#step-2--use-a-physical-device-not-an-emulator)
- [Step 3 — Prove the SDK is actually running](#step-3--prove-the-sdk-is-actually-running)
- [Step 4 — Validate your protocol with an A/A run](#step-4--validate-your-protocol-with-an-aa-run)
- [Step 5 — The controls the harness applies](#step-5--the-controls-the-harness-applies)
- [Step 6 — Run the comparison](#step-6--run-the-comparison)
- [Step 7 — Interpret the result](#step-7--interpret-the-result)
- [Where the SDK's startup cost comes from](#where-the-sdks-startup-cost-comes-from)
- [If the number is high](#if-the-number-is-high)
- [Reference](#reference)

---

## Quick start

> [!WARNING]
> **All three device-touching scripts (`verify_sdk_active.sh`, `coldstart_bench.sh` and
> `capture_trace.sh`) uninstall and reinstall your app, which deletes all app data**:
> accounts, caches, databases, preferences. That includes the step-zero verifier. Use a test
> device, or one whose state you are willing to lose. It also pre-grants the app's runtime
> permissions and changes device settings (animation scales, screen timeout, stay-awake,
> Wi-Fi). Everything it changes is snapshotted first and put back on exit, including on
> Ctrl-C, which stops the run.
> See [What the harness changes on your device](#what-the-harness-changes-on-your-device).

You need two release-configured APKs of the same app version, differing only by the
Datadog SDK. [Step 1](#step-1--build-apks-that-represent-what-you-ship) explains why that
matters more than anything else here.

```bash
git clone https://github.com/DataDog/dd-sdk-android.git
cd dd-sdk-android/tools/coldstart-benchmark
export PKG=com.example.app

# 1. Prove the SDK actually initializes. Nothing below means anything if this fails.
./verify_sdk_active.sh app-with-datadog.apk "$PKG"

# 2. Validate the protocol: same APK in both arms, so the true delta is zero.
EXPECT_B=0 LABEL_A=A1 LABEL_B=A2 \
  ./coldstart_bench.sh app-no-datadog.apk app-no-datadog.apk
./ab_stats.py results_<timestamp>.csv --baseline A1 --treatment A2

# 3. The real comparison.
./coldstart_bench.sh app-no-datadog.apk app-with-datadog.apk
./ab_stats.py results_<timestamp>.csv
```

Each `coldstart_bench.sh` invocation prints the exact `results_<timestamp>.csv` filename
it wrote. The CSV and matching log are atomically reserved before the device is touched; a
same-second parallel-run collision aborts rather than mixing or truncating evidence. Pass that
file to `ab_stats.py`.

If you use a coding agent, this repository also ships a `coldstart-benchmark` skill under
`.claude/skills/` that drives the same steps in the same order. Cloning the repo is enough
to pick it up.


Defaults are 4 measured launches per block × 8 blocks = 32 measured launches per arm.
Budget roughly an hour per run on a mid-range device, longer for a large APK: the
uninstall / install / AOT-compile cycle at the start of each block costs more than the
launches it precedes.

Do not skip the A/A run. It is the only thing that tells you whether to believe the A/B.

---

## Prerequisites

| requirement | notes |
|---|---|
| a `dd-sdk-android` checkout | `git clone https://github.com/DataDog/dd-sdk-android.git`. You only need `tools/coldstart-benchmark`; you do not need to build the SDK |
| `adb` | on `PATH`, or `ANDROID_HOME` / `ANDROID_SDK_ROOT` set, or `ADB=/path/to/adb`. The harness also probes the usual macOS and Linux SDK locations |
| `aapt2` | from Android SDK build-tools, or `AAPT2=/path/to/aapt2`. Required: it is what verifies the APKs declare the `PKG` the harness is about to `adb uninstall` |
| exactly one authorized device | `adb devices` must show `device`, not `unauthorized` or `offline`. With more than one attached, set `ANDROID_SERIAL=<serial>` |
| the device **unlocked**, on the home screen | a locked device still *resumes* the activity but never draws a frame, so `am start -W` returns no `TotalTime` and `LaunchState=UNKNOWN`, so every launch is unmeasurable. If the phone has a PIN, pattern or password, `adb` cannot dismiss it; unlock it by hand. The harness refuses to start rather than collect nothing |
| a **physical** device | [Step 2](#step-2--use-a-physical-device-not-an-emulator). Emulator runs are stamped `emulator=1` and are for validating the harness, never for reporting |
| Python ≥ 3.8 | for `ab_stats.py` and `fp_simulation.py`. No third-party packages needed |
| the `perfetto` package | **only** for the optional trace scripts: `python3 -m venv .venv && ./.venv/bin/pip install perfetto` |
| two release-configured APKs | same app version (matching `versionCode` / `versionName`), differing only by the Datadog SDK |
| the **host machine** kept awake for the whole run | a run is ~an hour of continuous `adb`. If the host sleeps, USB is suspended, `adb` drops the device and the run aborts. On macOS the default is to sleep after 10 minutes idle *even on AC*: check with `pmset -g` and hold it off with `caffeinate -s -w <pid>`, which releases automatically when the run exits. Locking the screen is fine; sleeping is not |

If you installed `perfetto` into a virtualenv, run the trace verifier with that
interpreter. The script's shebang is `#!/usr/bin/env python3`, which is your *system*
Python and will not see the venv:

```bash
./.venv/bin/python verify_trace.py treatment.pftrace --package "$PKG"
```

---

## What this measures — and what it does not

The harness measures a **process-cold, page-cache-warm start, to first frame**. Naming all
three parts matters, because each one bounds what the number means:

- **Process-cold.** Every launch is preceded by `am force-stop`, so the app process is gone
  and `am start -W` confirms `LaunchState: COLD`. A launch that is not cold aborts the run.
- **Page-cache-warm.** `am force-stop` kills the process; it does **not** evict the kernel
  page cache. By the first measured launch of a block, the app's dex, oat and native
  libraries are already resident. A user's genuine first launch after install or reboot
  pays page-in cost that this protocol does not see, and page-in is exactly where the
  SDK's *size* contribution would show up.
- **To first frame.** The endpoint is `am start -W`'s `TotalTime`, i.e. time to initial
  display (TTID). For React Native, Flutter and similar frameworks, a large part of startup
  happens after first frame. See
  [Know what your metric actually measures](#know-what-your-metric-actually-measures).

Every discretionary control in this harness reduces variance, and most of them bias the
measured SDK cost *downward*. That trade is deliberate and it is spelled out in
[Which controls bias the result, and in which direction](#which-controls-bias-the-result-and-in-which-direction).
Read that table before quoting a number to anyone.

### Glossary

| term | meaning |
|---|---|
| **arm** | one of the two builds being compared: the baseline (no SDK) and the treatment (with SDK). Named `A_noDD` / `B_withDD` in the CSV by default |
| **block** | one contiguous baseline-and-treatment pair: uninstall, install, AOT-compile, warm up, measure, for each arm in turn. One delta is computed per block, and those per-block deltas are the unit of statistical analysis |
| **ABBA / counterbalancing** | alternating which arm runs first across blocks (block 1 baseline→treatment, block 2 treatment→baseline, …) so that drift across the session cannot masquerade as a treatment effect |
| **pooled sd** | the standard deviation of launch times within an arm, combined across arms. A measure of how noisy individual launches are |
| **MDE** (minimum detectable effect) | the smallest true difference your run had a good chance of detecting. A null result from a run whose MDE is 40 ms does not tell you the SDK costs less than 40 ms; it tells you the run could not have seen it |

---

## Step 1 — Build APKs that represent what you ship

**This is the step most likely to make your numbers meaningless.**

A debug build and a release build are different programs. Debug builds are not optimized,
not shrunk, not obfuscated, and carry tooling your users never run. Startup cost measured
on one does not translate to the Play Store build, and the difference can run in either
direction: R8 shrinking and inlining are absent, but so are some of the checks and
allocations a release build performs.

If you take one thing from this guide: **do not benchmark a debug build and conclude
anything about your users.**

### Build both APKs release-configured

Both arms must be built the way you ship:

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true       // R8: shrinking + obfuscation
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            // your production ProGuard/R8 rules
        }
    }
}
```

Then build the same variant you publish:

```bash
./gradlew assembleRelease
```

### The two builds must differ only by the SDK

Same commit, same build type, same R8 rules, same resources, same everything else. Verify
it rather than assume it:

```bash
aapt2 dump badging app-no-datadog.apk   | grep -E "^package:"
aapt2 dump badging app-with-datadog.apk | grep -E "^package:"
```

`versionCode` and `versionName` should match. If they don't, you're comparing two different
app versions and the SDK is not the only variable.

`coldstart_bench.sh` runs this check for you before it touches the device, and refuses the run
on a mismatch (`ALLOW_VERSION_MISMATCH=1` overrides it if you know why they differ). The same
preflight asserts both APKs declare the application id you put in `PKG`: every block runs
`adb uninstall $PKG`, and a wrong `PKG` would wipe an unrelated app's data sixteen times over.
`verify_sdk_active.sh` and `capture_trace.sh` apply the same single-APK package check before
their own uninstalls.

Both checks need `aapt2`, and a missing one **aborts the run** rather than degrading to a
warning: the whole point is to stand between a typo and an irreversible `adb uninstall`, so
skipping it when a tool is absent would defeat it. Install build-tools, set `AAPT2=`, or (having
verified the package by hand with the commands above) set `ALLOW_UNVERIFIED_PKG=1`.

### Checklist

| requirement | why |
|---|---|
| `isMinifyEnabled = true` on both | R8 shrinking and optimization change startup cost materially, including for SDK code |
| `isDebuggable = false` on both | debuggable builds disable ART optimizations and slow class loading |
| release signing on both | debug signing can alter install and verification behavior |
| no debug-only tooling | LeakCanary, Chucker, Stetho, Flipper and dev menus all add startup work that is not in your production build |
| same Baseline Profile in both | if you ship one, both arms must have it; a profile in one arm only invalidates the comparison |
| measure the artifact you ship | if you publish an App Bundle, extract the device-specific APK with `bundletool build-apks --connected-device`; a universal APK contains every ABI and is not what users install |
| identical `versionCode` / `versionName` | proves the builds differ only by the SDK |

### If you want method-level attribution, add a `profileable` build

The A/B numbers need nothing special, and neither does the phase-level trace breakdown:
atrace slices, including the `bindApplication` slice
[`verify_trace.py`](../tools/coldstart-benchmark/verify_trace.py) uses to confirm a trace
contains a cold start, are captured from a plain release build.

What *does* require an extra manifest flag is **method-level** attribution: Perfetto's
callstack sampling and heap profiling only work on an app that is `profileable` or
`debuggable`. On a production (`user`) build, a request against a process that is neither
[returns an empty profile][perfetto-heapprofd]. So if you want to know which methods the
time is spent in, build a **separate** APK with:

```xml
<profileable android:shell="true" />
```

This is release-safe and, unlike `debuggable`, does not disable optimizations, so it is the
right tool for investigation. Use your normal release build for the headline numbers, and
this one when you need to see inside them.

[perfetto-heapprofd]: https://perfetto.dev/docs/data-sources/native-heap-profiler

---

## Step 2 — Use a physical device, not an emulator

**Emulator measurements are close to worthless for this question.** This is not a caution
to keep in mind; it is a reason to discard the result entirely.

An emulator runs on your development machine's CPU. A modern laptop core is many times
faster than a mid-range phone core, has vastly more cache and memory bandwidth, is backed
by an SSD instead of eMMC, and never thermally throttles. The emulator also has no
big.LITTLE scheduling, so the core contention that dominates real startup simply does not
occur.

Those differences land hardest on precisely the phases that matter here:

| phase | why the emulator misleads |
|---|---|
| `System.loadLibrary` / `Runtime.nativeLoad` | host SSD and page cache instead of eMMC |
| dex verification and JIT | far faster CPU, different compilation target |
| disk I/O during initialization | host filesystem |
| thread contention during startup | many fast cores, no big.LITTLE |
| thermal throttling | does not exist |

Emulator results can be wrong in either direction, and there is no correction factor that
recovers the real answer.

**Use a physical device that resembles what your users have.** Pull your actual device
distribution from your analytics and pick a common mid- or low-end model, not the newest
flagship on the team's desk. Startup cost is most visible on constrained hardware, which is
exactly where your real users will notice it.

The harness detects emulators, stamps `emulator=1` into the results file, and prints a
warning banner, so an emulator run can never be mistaken later for a device run.

---

## Step 3 — Prove the SDK is actually running

**Do this before measuring anything.**

A build that *contains* the SDK is not necessarily one that *initializes* it. Feature flags,
remote configuration, experiment buckets, consent gating and deferred initialization can all
leave the SDK compiled in but inert. If that happens, your "with SDK" arm measures dex size
and nothing else, and you will conclude the SDK is free when you simply never turned it on.

The SDK gives you a reliable oracle. `Datadog.initialize()` creates its internal executors
and immediately submits a clock-sync task to one of them, which forces creation of a thread
named `datadog-storage-thread-1`. Linux truncates thread names to 15 characters, so it
appears as `datadog-storage`. The name is assembled at runtime, so R8/ProGuard cannot rename
it.

**A completed `Datadog.initialize()` always leaves at least one `datadog-*` thread.**

```bash
adb shell am force-stop <your.app.id>
adb shell am start -W -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER -n <your.app.id>/<your.launcher.activity>
sleep 20   # let any deferred/async initialization finish
for PID in $(adb shell "ps -A -o PID -o NAME" | tr -d '\r' \
             | awk -v p=<your.app.id> '$2 == p || index($2, p ":") == 1 {print $1}'); do
  adb shell "cat /proc/$PID/task/*/comm" | tr -d '\r' | grep '^datadog-'
done
```

Enumerate the processes rather than calling `pidof <your.app.id>`: `pidof` matches the exact
process name, so on an app that initializes the SDK in a private process (`<your.app.id>:startup`
is a common one for a startup-work provider) it reports the default process and you conclude the
SDK is dead in a build where it is live. `adb shell` output carries `\r` on many devices, hence
the `tr`.

Expected on a working build:

```
datadog-storage
datadog-upload
```

Empty output means the SDK never initialized. Find out why before benchmarking:

```bash
adb shell logcat -d | grep -iE 'datadog|DD_SDK'
```

Or use the script, which also verifies the installed APK matches the file you think you
installed:

```bash
tools/coldstart-benchmark/verify_sdk_active.sh app-with-datadog.apk <your.app.id>
```

If you use NDK crash reporting, `libdatadog-ndk.so` appearing in `/proc/<pid>/maps` is a
second signal, corroborating only, since the SDK loads it with a plain
`System.loadLibrary`, which does not always emit a trace slice.

---

## Step 4 — Validate your protocol with an A/A run

**Run the same APK in both arms before running the real comparison.**

The true difference is zero, so whatever your protocol reports *is* its error. This gives
you three things you cannot get any other way:

- **A smoke test on the protocol.** If A/A reports a significant difference, the protocol is
  broken and any A/B result from it is meaningless. Note the asymmetry: a *failure* is
  decisive, a *pass* is not. One A/A run is a single significant-or-not outcome, so it
  cannot estimate a false-positive rate. Even the badly broken 2×15 unpaired design in the
  table below passes about three quarters of individual A/A runs. Treat a clean A/A as
  necessary but not sufficient; if you want an actual rate, you need repeated independent
  A/A experiments, which is what `fp_simulation.py` does in simulation.
- **Your noise floor.** The A/A confidence interval is the smallest effect you can
  credibly claim to detect.
- **Your required sample size.** The between-block spread tells you how many blocks you need
  for the effect size you care about.

```bash
cd tools/coldstart-benchmark
PKG=<your.app.id> EXPECT_B=0 LABEL_A=A1 LABEL_B=A2 \
  ./coldstart_bench.sh app-no-datadog.apk app-no-datadog.apk
./ab_stats.py results_<timestamp>.csv --baseline A1 --treatment A2
```

`EXPECT_B=0` tells the liveness gate not to expect `datadog-*` threads in the second arm,
because both arms are the baseline APK. If you A/A the *treatment* APK instead, use
`EXPECT_A=1 EXPECT_B=1`.

**Pass criteria:**

- the 95% CI on the paired block delta straddles zero
- the reported order effect is not significant
- the interval is **tight enough to be useful**: compare the printed MDE against the
  effect you intend to detect in the A/B. A null from a ±100 ms interval means the
  protocol cannot see anything, not that it is clean

**Do not require the per-block deltas to share a sign.** The true delta here is zero, so
they *should* fall either side of it. Across eight null blocks unanimity happens under
1% of the time, so demanding it would reject ~99% of healthy runs, and it selects for
precisely the persistent directional bias an A/A exists to detect. Judge the spread by
the interval and the MDE, never by counting signs.

If it fails, fix the protocol before proceeding. In our reference run, reaching a clean A/A
dropped the pooled standard deviation from 18.9 ms to 11.7 ms: a 38% reduction in sd, which
is a 62% reduction in variance.

---

## Step 5 — The controls the harness applies

Each fixes a specific, measured failure mode. The scripts apply all of them; the snippets
here show what is being done, so you can reproduce it by hand if you need to.

### Counterbalance the arm order (ABBA)

If arm A always runs before arm B, any drift across the session (page cache, background
dexopt, charging behavior, thermals) becomes a fake treatment effect. This produced the
`p = 0.011` false positive shown in
[Why the methodology is this careful](#why-the-methodology-is-this-careful); the entire
"effect" was position, not build.

Odd-numbered blocks run baseline→treatment, even-numbered blocks treatment→baseline. Each
launch's position within its block is recorded, so `ab_stats.py` can test for an order
effect explicitly rather than assume it away.

That test is itself **paired on blocks**, for the same reason the primary endpoint is: pooling
every position-1 launch against every position-2 launch would commit the independence violation
described in the next section and could manufacture an order effect out of cell-level shifts.
One `2nd − 1st` delta is computed per block. Because ABBA alternates which arm runs first, the
treatment effect cancels out of those deltas and only the ordering term survives, provided the
first-arm counts are balanced, which `ab_stats.py` checks and warns about if they are not.

### Analyze per-block deltas, not pooled launches

Launches within one arm×block cell are not independent of each other. They share an install,
an AOT compilation, a thermal state and a page-cache state. Anything that shifts a whole
cell is a cell-level random effect, and an unpaired test over pooled launches estimates its
standard error from *within-cell* scatter only, so it understates the real uncertainty.
Counterbalancing removes the ordering *bias*; it does nothing about this.

Measured by simulation (true effect zero, within-launch sd 11 ms, per-cell shift sd σ_b),
false-positive rate of a nominal-95% interval:

| design | σ_b = 0 | σ_b = 2 | σ_b = 4 | σ_b = 8 |
|---|---|---|---|---|
| 2 blocks × 15 launches, unpaired | 4.8% | 10.0% | **23.4%** | **45.5%** |
| 8 blocks × 4 launches, paired on block deltas | 4.8% | 4.9% | **5.0%** | 4.8% |

Reproduce it. It calls the same interval code `ab_stats.py` uses, so the table cannot drift
away from the tool:

```bash
./fp_simulation.py            # ~20 s; --trials 50000 for tighter Monte-Carlo error
```

A 4 ms between-block shift is entirely ordinary, and at that level the unpaired design calls
a nonexistent effect significant nearly a quarter of the time. So `ab_stats.py` computes one
delta per block and runs a paired test on those; the unpaired Welch result is still printed,
labeled `[diagnostic]`, because it is what most tools report and the contrast is
informative.

The CSV must also preserve `pos_in_block` for every contributing block. If any first-arm value is
missing, the analyzer still prints the block deltas for diagnosis but suppresses the confidence
interval, MDE and significance verdict: without complete order evidence it cannot prove that the
counterbalancing contract held for the observations being reported.

The practical consequence: **blocks buy statistical power, launches per block buy less of
it.** The confidence interval narrows with the square root of the number of blocks. If a run
is underpowered, add blocks before adding launches. At least 3 blocks are required for an
interval at all; below that, `ab_stats.py` refuses to produce one rather than print a
number no one should use.

### Pre-grant runtime permissions

If your app requests runtime permissions on first launch, an unattended benchmark never
dismisses the dialog. It reappears every launch and the instances **accumulate**: we
observed 23 stacked `GrantPermissionsActivity` instances, with the dialog rather than the app
as the resumed activity.

Cost of leaving it unhandled: ~23 ms of absolute time, and most of the 18.9 → 11.7 ms drop
in pooled sd reported in Step 4. In the A/A table above, counterbalancing alone barely
narrowed the interval; pre-granting is what narrowed it.

```bash
USER_ID=$(adb shell am get-current-user)
for p in $(adb shell dumpsys package <your.app.id> \
           | awk -v target="$USER_ID" '
               /^[[:space:]]*User [0-9]+:/ {
                 user=$0; sub(/^[[:space:]]*User /, "", user); sub(/:.*/, "", user)
                 selected=(user == target); runtime=0; next
               }
               selected && /^[[:space:]]*runtime permissions:/ { runtime=1; next }
               selected && /^[^[:space:]]/ { exit }
               selected && runtime && /:[[:space:]]*granted=/ {
                 name=$0; sub(/:[[:space:]]*granted=.*/, "", name)
                 sub(/^[[:space:]]+/, "", name); print name
               }' \
           | sort -u); do
  adb shell pm grant --user "$USER_ID" <your.app.id> "$p" || exit 1
done
```

This measures the "permissions already decided" path, which is what returning users
experience and therefore what your field metrics mostly reflect. It is not the first-install
path. Grants are retried across passes, so one that depends on another still lands. The
harness aborts if any declared runtime permission still cannot be granted: continuing with a
partially granted app would silently measure a different permission-dependent scenario. Some
permissions are hard-restricted, so no app outside the exempt role can hold them. SMS,
call-log and some location permissions behave this way. If that is your app, set
`ALLOW_PARTIAL_PERMISSIONS=1` to accept the weaker guarantee. The ungrantable permissions are
named in the log, and a dialog for them can still appear mid-run; the per-launch foreground
gate is what catches that. A dialog is not the only possible effect: the app can silently branch
on a grant. The harness therefore hashes the effective granted/denied sets for each arm, stamps
them in the CSV, aborts if an arm changes across fresh installs, and refuses to pool different
outcomes.

The harness snapshots `am get-current-user` once, parses only that user's package section and uses
the same numeric user for install, grant/revoke, launcher resolution, force-stop and launch. This
matters on devices with a work profile: `dumpsys package` contains both users, and combining their
opposite grant states can make setup look complete and cleanup revoke a grant it did not create.
The CSV stamps `android_user`; `ab_stats.py` refuses to pool different users.

### Assert your app is in the foreground

Verify the resumed activity is yours after every launch, not once per arm: dialog
contamination accumulates, so a single check at the start of a block cannot see it. This
catches crashes, ANR dialogs and system prompts as well.

```bash
adb shell dumpsys activity activities | grep -m1 -E 'm?ResumedActivity[:=]'
```

Match both spellings. Some devices print `mResumedActivity`, others (Android 12 on a
Motorola moto g60s, for one) print `ResumedActivity:` inside the Task dump with no `m`
prefix. An anchored `grep mResumedActivity` matches nothing there, and every foreground
assertion silently reports a failure.

A check that runs only after the launch still misses an activity that took over and handed back
mid-window. After the force-stop settle, the harness clears and verifies logcat immediately before
launch, then treats any foreign `Displayed` line after that conservative boundary as contamination,
whether it appears before or after the app's first frame. It records the launch as
`foreground=OTHER_MID` and aborts. The scan costs no extra `adb` round-trip because the buffer is
already in hand.

### Confirm each launch really was cold

`am start -W` reports `LaunchState` and `Status`. The harness records both and aborts the run
if a measured launch comes back as anything other than `COLD` with `Status: ok`, rather than
averaging a warm launch into the result.

The analyzer never supplies those verdicts for old data. If a selected measured row omits or leaves
empty `status`, `launch_state` or `foreground`, its timing remains visible for diagnosis but the
primary confidence interval, MDE and significance verdict are suppressed.

### Disable animations — but know what that costs you

```bash
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global $s 0
done
```

Animation time is not startup time, and animation variance is large. But this is **not a
bias-free control if the SDK does per-frame work**, which this one does: RUM vitals and
long-task tracking register `Choreographer` callbacks, and Session Replay snapshots on view
changes. Fewer animated frames in the launch window means fewer of those callbacks fire, which
understates their contribution.

The harness defaults to animations off for low variance and comparability. `ANIMATIONS=1` runs
with them on. The benchmark and trace both read back all three scales and abort if the device did
not apply the requested numeric value; only then is it stamped into the CSV header. If per-frame
cost matters for your app, run both and compare: that difference *is* the bias.

**Measured, and it did not go the way we expected.** On the app above (same comparison, 32
launches per arm each way):

| | animations off | animations on |
|---|---|---|
| baseline TTID | 630 ms | 680 ms |
| baseline TTFD | 2074 ms | 2265 ms |
| SDK delta, TTID | +33.6 ms | +31.7 ms, unchanged; CI on the change [−15.4, +11.6] |
| SDK delta, TTFD | +88.6 ms | +40.2 ms, **smaller**; CI on the change [−80.0, −16.7] |

TTID was unaffected. On TTFD the delta more than halved with animations *on*. The likely reason
is the opposite of the per-frame concern: animations add ~190 ms of wall-clock to startup, and
the SDK's asynchronous initialization overlaps that slack instead of extending the total. So on
this app, animations-off is the **conservative** setting: it reports the larger SDK cost.

Do not assume this generalizes. The point is that it is measurable, and that "animations off"
is a choice you should be able to defend rather than a default you inherited.

Note that Choreographer callbacks execute on the **main thread** and emit no atrace slices, so
a Perfetto trace cannot bound this for you; only the A/B can.

### Force AOT compilation and discard warm-ups

A freshly installed app has no AOT profile, so early launches are slow and variable:

```bash
adb shell cmd package compile -m speed-profile -f <your.app.id>
```

`speed-profile` is what a Play Store install *converges to* over time, which is why it is the
default. **Be clear about what it does on a fresh install, though: nothing.** `speed-profile`
compiles only the methods in the app's profile, and a newly installed app has no profile, so
the app lands at `status=verify`, with no AOT code, and the startup path is JIT-compiled on
every launch.

Check what you actually got rather than assuming:

```bash
adb shell dumpsys package dexopt | grep -A3 "\[<your.app.id>\]"
#       arm64: [status=verify] [reason=cmdline]      <- no AOT
#       arm64: [status=speed] [reason=cmdline]       <- fully AOT compiled
```

The harness logs this per arm and warns when it sees `verify`. A `verify` run is a legitimate
condition (it is what a sideloaded or freshly updated install looks like) but it is **not**
what a long-installed Play user experiences, because Play ships a cloud profile and
`bg-dexopt` recompiles against accumulated local profile data. If you want the well-compiled
end of the range, run `COMPILE_FILTER=speed` as a second arm; that forces full AOT, removes
most of the class-load and verify cost the SDK contributes, and overrides any Baseline Profile
you ship.

The harness also disables `bg-dexopt-job` for the duration, so whatever state the compile leaves
is pinned for the whole run rather than drifting between blocks. If the device does not support or
permit that control, benchmark and trace capture abort before collection instead of recording a
compilation scenario that can change underneath the experiment.

Three warm-up launches per block are discarded. That count is fixed in advance and nothing
else is ever dropped: post-hoc outlier removal is how a null result becomes a "finding".
Warm-ups are written to the CSV and marked `phase=warmup`, so you can see what was excluded
rather than take it on trust. The preceding SDK-liveness probe is also a conditioning launch.
Probe, warm-up and measured launches all require `Status=ok`, `LaunchState=COLD`, a numeric
`TotalTime`, a verified post-settle foreground window and the target in the final foreground;
an invalid launch is recorded as rejected and aborts instead of advancing the ramp.

### Use the real launcher intent

`am start -n <component>` is not what tapping the icon does; apps commonly route the
launcher through activity aliases:

```bash
ACT=$(adb shell cmd package resolve-activity --brief \
      -c android.intent.category.LAUNCHER <your.app.id> | tail -1 | tr -d '\r')
adb shell am start -W -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER -n "$ACT"
```

### Verify the APK on the device is the APK you built

```bash
adb install -r app.apk
REMOTE=$(adb shell pm path <your.app.id> | head -1 | sed 's/package://' | tr -d '\r')
adb shell md5sum "$REMOTE"   # must match md5 of your local file
```

The harness also stamps both APKs' digests into the CSV header as `baseline_md5` and
`treatment_md5`, and `ab_stats.py` refuses to pool CSVs whose arms were built from different
binaries. Every other header field can agree across two runs from successive APK pairs on the
same device, so without the digests those runs pool into one interval with no warning.

### Pin the device state

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put global stay_on_while_plugged_in 3
```

The harness snapshots these first and restores them on exit, including on Ctrl-C. See
[What the harness changes on your device](#what-the-harness-changes-on-your-device), and
[Disable animations](#disable-animations--but-know-what-that-costs-you) for why the animation
scales are not a neutral choice.

Note that `dumpsys thermalservice` returns **stubbed, non-live values on some devices**: we
have seen five consecutive byte-identical snapshots. A flat temperature reading is not
evidence that nothing drifted.

---

## Step 6 — Run the comparison

```bash
cd tools/coldstart-benchmark
PKG=<your.app.id> ./coldstart_bench.sh app-no-datadog.apk app-with-datadog.apk
./ab_stats.py results_<timestamp>.csv
```

The defaults are 4 measured launches per block across 8 blocks. To change them, pass them
positionally: `./coldstart_bench.sh <baseline.apk> <treatment.apk> <launches-per-block>
<blocks>`. `blocks` must be even, for ABBA.

The script refuses to proceed if the "with SDK" arm shows no `datadog-*` threads, or if the
baseline arm unexpectedly shows some, and it rechecks that **after every measured launch**, on
every process the package owns, not only once per cell. A probe launch cannot answer the question
for the launches that follow it: initialization that is first-launch-only, consent-gated or
remote-config-gated passes the probe and then never happens again, and every such launch measures
an SDK-absent start as treatment. The app's own log marker is not a substitute, because most apps
never emit one and that gate passes vacuously.

**Give heavy features their own arm.** Run a third arm with Session Replay disabled, so you
can attribute cost to the feature that carries it and make an informed trade rather than a
blanket one.

Do not assume in advance which way that will come out. Session Replay is the SDK's most
expensive feature *while a session is recording*, but that is not the same as being expensive
at **startup**. Measured on a React Native app on a mid-range device (8×4 design, 32 launches
per arm), enabling it added `+8.9 ms` to TTID (95% CI `[−3.2, +21.0]`) and `+10.0 ms` to TTFD
(95% CI `[−20.7, +40.6]`), neither separable from zero, against a core-SDK cost of `+24.7 ms`
and `+78.7 ms` on the same app. Startup cost there was dominated by the core SDK, not by
Session Replay. Your app may differ; the point is that this is a question to measure, not to
assume.

---

## Step 7 — Interpret the result

`ab_stats.py` reports all of the below.

**The paired block-level delta is the primary result.** Report it. The unpaired Welch
figures are printed as a diagnostic and are anti-conservative: see
[Analyze per-block deltas, not pooled launches](#analyze-per-block-deltas-not-pooled-launches).

**Quote a confidence interval, never a bare average.** "+11 ms" and "+11 ms, 95% CI
[−5, +27]" support completely different conclusions. If the interval includes zero, you have
not demonstrated a regression, and the interval's upper bound is your defensible upper bound
on the cost.

**Report mean *and* median.** If they disagree materially the distribution is skewed and
neither should be quoted alone. One real dataset gave a mean delta of +8 ms and a median
delta of +40 ms: the choice of statistic changed the headline fivefold.

The relative percentage is descriptive and requires a non-zero baseline mean. If a synthetic
or coarse timing source produces a zero baseline, the analyzer keeps the absolute millisecond
estimate and interval but prints the percentage as undefined instead of inventing a denominator.
Like the absolute primary delta, its baseline denominator gives each contributing block equal
weight; pooled files with different launches per cell cannot make the numerator and denominator
describe different estimands.

**Check your minimum detectable effect before believing a null result.** "No significant
impact" from an under-powered run means "we couldn't have detected it either way".
`ab_stats.py` prints your MDE and the number of blocks needed to resolve 10 ms and 25 ms,
computed from your own between-block spread. Recommendations are rounded up to an even number of
blocks because those are the counterbalanced designs the collector accepts. If every observed
block delta is identical, the sample variance is zero but the population variance is not known;
the analyzer reports the primary inference as non-estimable instead of printing a zero-width CI
or zero MDE.

Do not transplant a required-n from this guide. The script's figure is in **blocks**, derived
from your run's own between-block spread, which is a different quantity from the pooled launch
sd; both vary enormously with the app and the device. Our well-controlled reference run had a
pooled sd of 11.7 ms. A separate, uncontrolled run with 4 launches per arm on a heavier app had
a pooled sd near 66 ms and a 95% CI of **[−106, +122] ms**, which could not distinguish zero
overhead from 120 ms of overhead. Both are real runs; the difference is the protocol and the
app. Read the required-n the script computes for *your* run.

**Look at the per-block deltas, but read them against the interval, not by sign.** Blocks
falling either side of zero is normal whenever the effect is comparable to the between-block
sd, and says nothing on its own; the CI and the MDE already quantify it. What a scan of the
deltas *is* good for is spotting a single contaminated cell, which the next point covers.

**Expect the occasional bad block, and let the design absorb it.** In one run, two of a block's
four treatment launches came in ~150 ms above that block's median while the other seven blocks
were tight. Something ran on the device during that cell. Because the primary endpoint is
paired on blocks, the contaminated cell inflated the between-block sd and widened the interval
(MDE ~62 ms against ~27 ms for comparable runs) instead of shifting the point estimate. The
unpaired Welch diagnostic on the same data reported a visibly tighter interval, which is
exactly the anti-conservatism the paired endpoint exists to avoid. Report the widened interval;
do **not** drop the block.

**Read the bias table before quoting the number.** See
[Which controls bias the result, and in which direction](#which-controls-bias-the-result-and-in-which-direction).
The figure this harness produces is closer to a lower bound than to a worst case.

**Put the result in context.** A cold-start delta is worth weighing against what the SDK gives
you: crash reporting, RUM and traces. A number measured properly lets you make that trade
deliberately, and lets you tune it. Disabling a feature you don't need, or sampling it, moves
the number.

---

## Where the SDK's startup cost comes from

What contributes, and what you can do about each:

- **APK and dex size.** More dex means more class loading and verification at startup,
  independent of initialization. R8 shrinking reduces this, which is part of why Step 1
  matters. Note that this harness measures page-cache-warm launches, so it sees the
  class-load and verify cost but very little of the page-in cost.
- **A `ContentProvider` that runs before `Application.onCreate`.** `DdRumContentProvider` is
  registered by the SDK to capture app-start time accurately, and runs whether or not you
  call `Datadog.initialize()`. A build with the dependency but initialization disabled is
  therefore not a zero-cost build.
- **A small amount of main-thread disk I/O during initialization**: storage directory
  resolution, and for NDK crash reporting a directory creation plus a `System.loadLibrary`.
  Clock synchronization is deliberately offloaded to a background executor.
- **Executor creation.** Initialization creates the SDK's thread pools. On devices with few
  fast cores this competes with your app's own startup work.
- **JIT compilation of SDK classes on an install with no AOT profile**: the largest block of
  SDK CPU we have measured, and **not** a startup cost on the app we measured it on. Across ten
  traces of one React Native app: ~149 ms of JIT on `com.datadog.*` classes with Session Replay
  off and ~390 ms with it on, against 0 ms in the baseline build. Then we timestamped it.
  **Zero** of those compilations began before `reportFullyDrawn()`; the first started 406–500 ms
  *after* it, in all ten traces. It is post-launch background CPU. It costs nothing on TTID or
  TTFD, and a Baseline Profile covering the SDK's init path would not have moved either number
  on that app. It is still real work worth reducing, because it competes with whatever your app
  does after launch, but do not put it in a startup budget. This is the clearest example in this
  guide of why [locating work relative to your
  endpoint](#four-things-that-will-mislead-you) has to come before attributing it.
- **Per-frame callbacks** if you enable vitals or long-task tracking.
- **Session Replay.** The SDK's most expensive feature during a recording session, though on
  one measured React Native app its *startup* increment was ~9 ms and not distinguishable
  from zero. Give it its own arm rather than folding it into a headline "SDK on / SDK off"
  number ([Step 6](#step-6--run-the-comparison) has the figures).

Worth knowing where this work does *not* land: in traces of the app above, there were **zero
Datadog-named slices on the main thread** in either arm. The SDK's cost showed up as background
thread CPU and as JIT contention, not as blocking main-thread work. That is why the app's own
reported initialization durations were ~10x the measured end-to-end delta: most of it runs
concurrently. It also means a Perfetto trace will not show you a neat main-thread block to
point at.

Most of these scale with the feature set you enable, which is the main lever you have.

If you're on React Native, also check whether the SDK is being initialized **twice**: once
natively in `Application.onCreate` and again from JavaScript. The core guards against
re-initialization, but the JS entry point still rebuilds its configuration and registers
frame callbacks.

---

## If the number is high

Work down this list before escalating. Most of it you can act on without us, and each step
either fixes the number or tells you something specific about where it comes from.

1. **Re-read Steps 1 and 2.** A debug build or an emulator run explains most surprising
   numbers, and no amount of statistics rescues either.
2. **Check the A/A result.** If your A/A run does not straddle zero, the A/B number is
   measuring your protocol, not the SDK. Fix that first.
3. **Check the MDE.** A large point estimate with a CI that spans zero means the run is
   underpowered, not that the effect is real. Add blocks and re-run before drawing a
   conclusion. (Per-block deltas falling either side of zero is not itself a warning sign;
   see [Step 7](#step-7--interpret-the-result).)
4. **Split Session Replay into its own arm.** Cheap to test and it removes a large unknown.
   Its startup increment can be small even at `replaySampleRate: 100` (~9 ms on one measured
   app, well inside the noise), so if disabling it does not move your number, the cost is in
   the core SDK and the next step is a per-feature breakdown.
5. **Turn features off one at a time.** Vitals and long-task tracking add per-frame
   callbacks; NDK crash reporting adds a `System.loadLibrary` and a directory creation. A
   per-feature breakdown turns one unusable number into a set of decisions.
6. **Sample instead of disabling.** Sample rates move the cost without giving up the signal
   entirely.
7. **On React Native, check for double initialization** (see above). It is easy to hit and
   easy to fix.
8. **Capture a trace and see where the time lands.** See
   [Attribute the cost with a trace](#attribute-the-cost-with-a-trace). Distinguishing
   `DdRumContentProvider` from `Datadog.initialize()` from your own startup work changes what
   you do next.

### Getting help

If you've worked through that list and the number still doesn't make sense, open an issue on
[dd-sdk-android](https://github.com/DataDog/dd-sdk-android/issues/new/choose) or contact
[Datadog Support](https://docs.datadoghq.com/help/). Please include:

1. **Proof the SDK was live in the treatment arm**: the `datadog-*` thread list from
   [Step 3](#step-3--prove-the-sdk-is-actually-running).
2. **Your A/A validation output**, which is what tells us the numbers are trustworthy.
3. **Confirmation both builds were release-configured**, and the `aapt2 dump badging` output
   showing matching `versionCode` / `versionName`.
4. **The raw CSV**, every launch, warm-ups marked rather than deleted.
5. **`ab_stats.py` output**: the paired block delta, its CI, the per-block breakdown and the
   MDE.
6. **Device details**: exact model and Android version, confirming a physical device.
7. **Your SDK configuration**: features and sample rates, especially Session Replay.
8. **Perfetto traces** from both arms, captured with `capture_trace.sh` so they pass the
   liveness check. Note what these contain before sending them; see
   [What is in a Perfetto trace](#what-is-in-a-perfetto-trace).

Items 1–5 are enough for us to reproduce and act on straight away.

---

## Reference

### Why the methodology is this careful

Cold-start A/B benchmarking looks trivial and is not. The obvious approach (build two APKs,
launch each a few times, compare averages) reliably produces numbers that are **wrong by
more than the effect being measured**.

These are real results from a mid-range device (Helio G95, Android 12) running **the same APK
in both arms**, so the true difference was zero by construction:

| protocol | measured "difference" | 95% CI | p | verdict |
|---|---|---|---|---|
| fixed arm order, permission dialogs unhandled | **+12.7 ms** | [+3.0, +22.5] | **0.011** | **false positive** |
| counterbalanced order, dialogs unhandled | +5.0 ms | [−4.4, +14.4] | 0.30 | null, but noisy |
| counterbalanced + permissions pre-granted | **−0.8 ms** | [−6.9, +5.2] | 0.79 | correct |

The first row is a *statistically significant* result on two identical builds. Anyone running
that protocol would have reported a regression that did not exist. None of this is exotic: it
is the ordinary difficulty of measuring a small effect in a noisy system, and it is why the
steps above exist.

> The figures published in [`sdk_performance.md`](sdk_performance.md) predate this harness and
> were produced with a lighter protocol (5 launches per arm, fixed arm order, no A/A
> validation and no confidence intervals) on one reference app, SDK version, device and
> configuration. Treat them as indicative of scale only, not as a number to compare your own
> measurement against. Measure your own app.

### Which controls bias the result, and in which direction

Every control here was added to remove a known error source, and several of them remove
variance at the cost of a directional bias:

| control | variance it removes | direction it biases the measured SDK cost |
|---|---|---|
| forced compile at `speed-profile` | first-launch compilation *variability*; it pins a known state, identical across arms | **not downward, despite appearances.** On a freshly installed app there is no profile to compile against, so this lands at `status=verify`, with **no AOT code at all**, and the startup path is JIT-compiled on every launch. Verify it rather than assume: `adb shell dumpsys package dexopt`. Relative to a steady-state Play install (cloud profile + `bg-dexopt`) this is *pessimistic*. `COMPILE_FILTER=speed` forces full AOT and **that** biases downward |
| discarding warm-up launches | first-run migrations, cold caches | **downward**: by the first measured launch the SDK's dex, oat and `libdatadog-ndk.so` are page-cache resident |
| `am force-stop` instead of reboot | process and system state | **downward**: force-stop does not evict the page cache, so page-in cost for the SDK's extra code is largely absent |
| TTID (`TotalTime`) as the endpoint | post-first-frame variance | **downward** for apps whose startup continues past first frame; SDK work landing after first frame is excluded entirely |
| pre-granting runtime permissions | accumulating permission dialogs | **downward relative to a first-install user**, accurate for a returning user. It is the returning-user path that dominates field metrics |
| animations off | frame-count variance during the launch transition | **downward for any per-frame SDK work**: fewer animated frames means fewer `Choreographer` callbacks for vitals and long-task tracking, and fewer Session Replay snapshots. Set `ANIMATIONS=1` to measure with them on and quantify this for your app |
| screen pinned on, stable charge state | environmental noise | **neutral**, no known directional effect |
| ABBA counterbalancing | drift across the session | **neutral**: removes an ordering bias that could point either way |
| paired block-level analysis | between-cell variance being ignored | **neutral**: widens the interval, does not move the point estimate |
| release builds, physical device | debug-build and emulator distortion | **corrective**: moves the measurement toward what users actually experience; direction depends on your app |

**Net effect:** the number this harness produces is a good estimate of what a *returning* user
on a warm page cache experiences, to first frame, and a **lower bound** on what a first-install
or post-reboot user experiences. If you need the first-launch-after-install figure, reboot the
device between launches instead of using `am force-stop` and expect both a larger number and
much more variance.

### Know what your metric actually measures

`am start -W TotalTime` ends at the **first frame** of your launched activity. For React
Native, Flutter and other framework apps, a large part of startup happens *after* that: JS
bundle evaluation, bridge setup, first framework render. Within a single Perfetto trace of one
RN app, first frame landed ~963 ms after `bindApplication` while `setupReactContext` /
`attachRootViewToInstance` ran on to ~1838 ms, so `TotalTime` excluded more than half of real
startup. (Those two timestamps come from the same trace, so their *ordering* is sound; the
absolute values are not comparable to the A/B figures below, because a traced launch runs long;
see [Tracing changes what you are measuring](#four-things-that-will-mislead-you).)

If most of your startup follows first frame, `TotalTime` will *understate* any SDK cost landing
in that later window.

**Call `reportFullyDrawn()`** when your app is genuinely ready for interaction. Without it,
neither you nor any monitoring tool can measure time-to-fully-drawn. It costs one line and it
is the metric that reflects what users feel. The harness captures it as `ttfd` when the app
emits it, alongside `TotalTime` and logcat's `Displayed`.

Two things to know before treating TTFD as the better number:

- **TTFD ends wherever the app puts the call.** If that point sits behind a network request or
  other I/O, the TTFD delta absorbs variance the SDK does not control. Check your A/A run's
  TTFD interval: on the app above the TTFD noise floor was ~25 ms against a ~9 ms TTID floor,
  so the wider window costs real resolution. TTID is the more tightly attributable of the two;
  TTFD is the more complete. Report both.
- **It does not always fire.** On a fresh install where a runtime-permission dialog was still
  pending, `reportFullyDrawn()` never fired at all and no `Fully drawn` line appeared, while it
  fired on 64/64 launches of the same build once permissions were pre-granted. A `ttfd` column
  that is `NA` everywhere usually means the app never reached its own ready state, not that the
  metric is unavailable.

When the app does emit it, analyze both windows — `ab_stats.py` takes `--metric`:

```bash
./ab_stats.py results_<timestamp>.csv                 # total_ms (TTID), the default
./ab_stats.py results_<timestamp>.csv --metric ttfd    # through to fully drawn
```

On one React Native app measured with this harness, TTID averaged ~630 ms while TTFD averaged
~2075 ms. First frame was under a third of startup, so a TTID-only comparison could not have
detected an SDK cost landing anywhere in the other two thirds.

### Attribute the cost with a trace

An A/B delta tells you *how much*. A Perfetto trace tells you **what work exists and where**,
which is not the same as telling you where the cost is, and is emphatically not a second opinion
on the magnitude. Set expectations accordingly before you spend a day on this: on a real customer
app, traces of all three arms found no SDK work at all inside the window that carried most of the
measured cost. That was a useful answer, but not the one the exercise was set up to get.

```bash
cd tools/coldstart-benchmark
PKG=<your.app.id> ./capture_trace.sh app-with-datadog.apk treatment 1
./.venv/bin/python verify_trace.py treatment.pftrace --package <your.app.id>
```

The third argument to `capture_trace.sh` is the arm's SDK expectation: `1` for the treatment
arm, `0` for the baseline. The capture is discarded if the expectation is violated.

The default `TRACE_ENDPOINT=total_ms` requires a successful cold `am start -W` result and proves
that first frame occurred before Perfetto stopped. If the trace is meant to explain TTFD or an
app-owned endpoint, set `TRACE_ENDPOINT=ttfd` or
`TRACE_ENDPOINT=app_trace_ms APP_TRACE_REGEX='<the same ERE used by the A/B>'`. Those modes stream
logcat and refuse the capture unless the selected marker occurs inside the trace window. A trace
that never reaches the metric's endpoint cannot explain that metric. The output path is atomically
reserved before the script changes device state, so an existing or concurrently claimed
`.pftrace` is never overwritten; choose a new capture name or move the old file first.

**Set `WARMUP` to whatever the A/B ran with.** The capture performs `WARMUP + 1` discarded
launches before the traced one, reproducing the benchmark's liveness probe plus its warm-ups, so
the traced launch sits at the same position in the post-install JIT/profile ramp as a measured
one. That matters most under the default fresh-install `speed-profile`, where the profile is
empty at install and each launch adds to it. Every discarded launch gets a fresh post-settle
logcat boundary and must draw the target without a foreign activity drawing, end with the target
in the foreground, and satisfy the arm's SDK-liveness expectation. A contaminated conditioning
launch aborts the capture instead of silently preparing a different ramp.

`verify_trace.py` exits `0` if the SDK is demonstrably active (or correctly absent), `1` if it
is not detected, and `3` if the trace is unusable: no `bindApplication` slice, meaning the
trace does not contain a cold start and cannot answer the question either way.

Three requirements that are easy to get wrong:

1. **`am force-stop` before tracing**, and launch *inside* the trace window. A trace with no
   `bindApplication` slice contains no cold start.
2. **Enable full process stats.** With a bare `linux.process_stats` data source, thread names
   come only from scheduler events, so an idle thread is invisible, and you cannot conclude
   the SDK is absent from the absence of its threads:
   ```
   process_stats_config { scan_all_processes_on_start: true proc_stats_poll_ms: 1000 }
   ```
3. **Add `sched_blocked_reason`** to attribute I/O wait.

Keep app content consistent between traces. A single trace per arm has no averaging, so one run
loading video while the other doesn't can swamp everything. In one real pair the difference was
~90 extra ExoPlayer/MediaCodec threads and +24.8% process CPU unrelated to the SDK.

Don't cross-reference a JIT-mode trace with AOT-compiled benchmark numbers. If your trace shows
`Compiling baseline` slices, it wasn't AOT-compiled and the datasets aren't comparable.

#### Four things that will mislead you

**A trace of a different scenario is worse than no trace.** `capture_trace.sh` pre-grants runtime
permissions, proves that the selected endpoint was reached, and `verify_trace.py
--require-foreground` refuses any capture the app did not own for the *whole* window, judged from
the trace's own lifecycle and global ActivityManager launch slices rather than from a snapshot
taken after it. Ownership is the set of activities resumed across every process of the app, so a
splash handing over to the next activity reads as held. A gap that closes on the same activity or
never closes counts as a loss; so does a foreign `launching:` marker during an apparent handoff.
That applies to both arms, and a capture whose ownership cannot be established
at all is refused on the same footing: a check that could not run is not a check that passed. The
`launching:` slice is an `ActivityMetricsLogger` implementation detail rather than a documented
contract, so `ALLOW_MISSING_LAUNCH_MARKER=1` exists for a device that never emits it: the
lifecycle-only checks still run, and the verdict becomes `held-lifecycle-only` instead of `held`
so that a capture whose foreign-takeover clause never ran is never reported as a clean one. One asymmetry to know about: the check spans the whole capture,
so a takeover *after* the endpoint the A/B measures, which cannot have changed the number, is
refused on the same footing as one inside the measured interval. That is conservative rather than
exact, because the endpoint is observed on the host and its timestamp is not in the trace. The
rejection detail lines are timestamped relative to the app's first resume; read them before you
decide a re-capture is warranted.
Both of those went wrong on a real capture set. A permission dialog stopped the baseline app
1030 ms into the launch; it produced no further frames and never reached `reportFullyDrawn()`,
so the window under study had no end in that arm, while both treatment arms ran to completion. Compared
naively, that is a large, entirely fictitious SDK cost. Liveness verification passes such a trace
without complaint: **a stopped app still has every one of its `datadog-*` threads.**

**Tracing changes what you are measuring.** Perfetto is not free. On the app above, the
first-frame → `reportFullyDrawn()` window was ~101 ms longer (+7%) in *every* arm with tracing on
than the untraced A/B measured. The traced launch is a real launch, but it is not the launch your
benchmark numbers describe.

**Don't derive magnitudes from traces.** Captures are slow, so you will have a handful per arm
where the A/B has 32. Five per arm resolved that same window only to about ±32 ms, wide enough
to swallow the effect being investigated. Use the A/B for how much, always.

**Check *when* the work happens, not just how much there is.** On that app, ART JIT-compiled
~149 ms of SDK classes (~390 ms with Session Replay), which looks like a headline startup cost
until you timestamp it. Zero of those compilations occurred before `reportFullyDrawn()`; the
first began 406–500 ms *after* it, in all ten traces. It is post-launch background CPU and costs
nothing on TTID or TTFD. Always locate work relative to your measurement endpoint before
attributing it to startup. (A single trace per arm had put the Session Replay figure at 443 ms;
ten traces settled it at ~390 ms. Quote the number you have the n for.)

Related: if your app's startup ends on a vsync-paced animation, expect the tail of the window to
have per-frame slack that absorbs extra background CPU. Check the main thread's idle share and
device-wide CPU utilization across the window before concluding that background work is
contending with anything.

### Benchmarking the app's own startup metric

Most teams already have their own startup trace and quote *that* number, not `TotalTime`. You
can A/B it directly: set `APP_TRACE_REGEX` to an extended regex matching the log line, and the
last number in the match is recorded per launch as `app_trace_ms`. The pattern is validated at
preflight, so a malformed ERE aborts the run rather than silently recording `app_trace_ms=NA`
for an hour. The scrape and the trace endpoint watcher are restricted to the installed package's
numeric UID, covering its default and private processes from process birth while excluding every
unrelated app. A legacy install that shares its UID with another package is refused because its
custom log line cannot be attributed to one package.

```bash
APP_TRACE_REGEX='cold_launch_total duration: [0-9]+' \
  PKG=<your.app.id> ./coldstart_bench.sh baseline.apk treatment.apk
./ab_stats.py results_<timestamp>.csv --metric app_trace_ms
```

This is worth doing before arguing about whose number is right, because **an app's own trace
often does not end where its name suggests.** One app's `cold_launch_*` trace, documented
internally as ending "at first frame", measured 806 ms on a launch where `am start -W`
reported `TotalTime: 667`: it ran ~140 ms past first frame. Comparing a delta from that trace
against a TTID delta is comparing two different windows, and the wider one will legitimately
show more SDK cost.

Two checks before trusting any app-reported trace:

1. **Confirm it emits at all**, on the device and build you are measuring. One trace in the
   same app never appeared in 1381 logcat lines from a verified-live build; it was gated
   somewhere. A number nobody can reproduce cannot be compared.
2. **Bound the window** by capturing `total_ms`, `ttfd` and `app_trace_ms` on the same launches.
   The harness records all three, so the ordering is visible rather than assumed.

### What the harness changes on your device

`coldstart_bench.sh` needs a stable device to produce stable numbers, so it changes state.
It snapshots the original values first and restores them from an `EXIT` trap; `INT` and `TERM`
exit into that trap, so Ctrl-C stops the run *and* restores the device, once.
`capture_trace.sh` does the same for the animation scales, screen settings and its own
permission grants. It also mirrors the benchmark's fixed-performance and background-dexopt
controls so the trace observes the same scheduling and compilation scenario. Failure to disable
background dexopt aborts either workflow before collection. The two controls
have no readable prior state, so both scripts can only reverse a command they successfully issued,
not prove exact restoration. Before any mutation, an empty, `null`, malformed or failed read of a
restorable numeric setting aborts the workflow; guessing a default would risk leaving a borrowed
device changed. A key the device has never set reads `null`, so writing it once makes it
restorable and that value is what the run puts back. The two radio settings are the one
exception, because a device can genuinely not have one: `ALLOW_UNVERIFIED_RADIOS=1` accepts an
unreadable radio snapshot and restores nothing for it, which is also the override the read-back
gate names when it aborts. Trace capture also uninstalls and reinstalls the app, so it destroys
app data too.

| what | why |
|---|---|
| **uninstalls and reinstalls your app** before every arm of every block (and once in `verify_sdk_active.sh`, and once in `capture_trace.sh`) | guarantees a known install state and lets the md5 attestation prove which APK is being measured. Before the global `adb uninstall`, every Android user is checked; if another profile owns the package, the harness refuses and asks for a dedicated test device instead of deleting that profile's data. The selected user's package path is checked before and after uninstall; a protected/device-admin package that remains installed aborts rather than becoming an in-place `install -r` with preserved data, caches and profile state. **This deletes all app data for the selected single-user scenario.** With the default 8 blocks that is 16 uninstall/install cycles |
| snapshots one Android user and scopes package, permission and activity operations to it | prevents personal/work-profile permission state or an implicit-user change from creating a mixed scenario. The selected user is stamped in the CSV and must match before files are pooled |
| pre-grants every runtime permission your app declares in the verifier, benchmark and trace | removes the accumulating-dialog contamination and makes the liveness preflight test the same permission state as the measured launch. Only the selected Android user's state is parsed; custom permission names are included, and a rejected grant aborts instead of silently producing a partially granted scenario. Cleanup revokes only permissions changed from denied to granted; permissions already granted at install are preserved, and it never runs device-wide `pm reset-permissions` |
| `window_animation_scale`, `transition_animation_scale`, `animator_duration_scale` → 0 | animation time is not startup time. All three are written, read back, and required to match before measurement; they are snapshotted and restored individually |
| `stay_on_while_plugged_in`, `screen_off_timeout` | the screen must stay on for the whole run |
| Wi-Fi enabled by default, or Wi-Fi **and** mobile data settings off when `AIRPLANE=1` | both arms must use the same controlled radio state. The settings are snapshotted, restored and read back: under `AIRPLANE=1` both must be exactly `0`; under `AIRPLANE=0` at least one must be `1`. Contradictory state aborts, while `ALLOW_UNVERIFIED_RADIOS=1` can accept unreadable settings. This proves only those settings. It does not prove association, validated internet, DNS, captive-portal state or reachability of the app's and Datadog's endpoints; keep that external lab condition stable yourself. Ethernet and USB tethering are not represented |
| `cmd package compile -m <filter> -f` | a stable AOT profile |

The app is left installed when the run finishes. Remove it with
`adb uninstall <your.app.id>` if you want a clean device.

### What is in a Perfetto trace

`capture_trace.sh` captures **device-wide** data, not just your app: process and thread names
for everything running, scheduler activity, and window/activity transitions across all apps.
Anything running on the device during the capture appears in it: other apps, notifications,
system services.

Review a trace before attaching it to a support ticket or sharing it, and prefer capturing on
a device without unrelated accounts or apps signed in. The benchmark CSV and `ab_stats.py`
output contain no such data and are safe to share as-is.

### Common pitfalls

| symptom | likely cause |
|---|---|
| delta far larger than the SDK could plausibly cost | debug build; see [Step 1](#step-1--build-apks-that-represent-what-you-ship) |
| numbers wildly larger than expected across the board | running on an emulator; see [Step 2](#step-2--use-a-physical-device-not-an-emulator) |
| A/A run shows a significant difference | fixed arm order; counterbalance it |
| high variance drifting over the run | permission dialogs stacking; pre-grant them |
| "with SDK" arm identical to baseline | SDK never initialized; see [Step 3](#step-3--prove-the-sdk-is-actually-running) |
| first block differs wildly from later blocks | not AOT-compiled, or too few warm-ups |
| `TotalTime` empty and `LaunchState=UNKNOWN` on every launch | the device is locked, or the notification shade is on top. Unlock it and leave it on the home screen |
| every launch reports the wrong foreground activity | your `dumpsys` grep is anchored on `mResumedActivity`; this device prints `ResumedActivity:`. Match `m?ResumedActivity[:=]` |
| `ab_stats.py` refuses to print a CI | fewer than 3 complete blocks, the selected endpoint is `NA` on an otherwise eligible measured launch, a selected row lacks `status`/`launch_state`/`foreground`, or any contributing block lacks `pos_in_block` evidence. Missing endpoints can be the slowest launches censored by the collection window, while missing validity/order evidence leaves the protocol unverifiable, so none is silently accepted; fix the CSV/collection and re-run. `--allow-missing-endpoint` is diagnostic only and still suppresses the primary interval |
| `ab_stats.py` refuses the file entirely | the run aborted, contains a rejected/invalid measured launch, or holds fewer blocks/launches than its own header says (a `kill -9` or power cut can skip the abort marker). Re-run; `--allow-aborted` inspects it diagnostically without producing a reportable primary interval |
| the harness refuses to start, naming another Android user | the app is also installed in a work or secondary profile. Host-side `adb uninstall` has no user selector, so continuing would delete that profile's app data, and no user-scoped removal leaves the measured user a genuinely fresh install. Remove it from those profiles, or use a dedicated test device |
| the harness refuses to start, naming an output path | a results CSV, log or trace of that name already exists, or a parallel run against another device picked the same name. Output paths are atomically reserved before device state is changed, so evidence is never interleaved or overwritten. Move the old file or choose a new name |
| the harness refuses to start, naming an Android setting snapshot | the setting read failed, or returned empty, `null` or a malformed value. No device mutation occurred. The message distinguishes the two causes: a failed command means the device is gone, while `null` means the key was never set. Write it once (`adb shell settings put global window_animation_scale 1`) and re-run. For `wifi_on`/`mobile_data` on a device that has no such setting, `ALLOW_UNVERIFIED_RADIOS=1` accepts it |
| the harness aborts on an animation scale read-back | `settings put` reported success and the device applied something else. The run would have been stamped with a rendering scenario it did not measure |
| `ab_stats.py` refuses to pool two files | mandatory device/protocol metadata is missing or disagrees (device, Android user, compile filter, animations, radios, ABI, launcher, warm-up), their arms were built from different APKs, or they map labels or SDK-liveness expectations to different arms. `--allow-mixed` proceeds if you intend to pool them and will caveat the result |
| `ab_stats.py` refuses two inputs that look different | they are the same evidence: the same file under two spellings or through a symlink, or a byte-identical copy. Counting it twice would narrow the interval without adding observations |
| the harness refuses to start, naming a package mismatch | `PKG` is not the application id the APKs declare. Fix `PKG`; do not work around it, because every block runs `adb uninstall $PKG` |
| the harness refuses to start on differing `versionCode`/`versionName` | the arms are different app versions, so the SDK is not the only variable. Rebuild both from one commit, or set `ALLOW_VERSION_MISMATCH=1` if you know why they differ |
| `displayed` or `ttfd` is `NA` on every row | the app doesn't call `reportFullyDrawn()` (for `ttfd`), or a vendor logcat format; `total_ms` is still valid |
| `verify_trace.py` exits 3 | no `bindApplication` slice, so the launch is not in the trace window: `force-stop` the app before tracing and start it inside the trace |
| trace shows no SDK activity | no `force-stop` before tracing, or a bare `process_stats` data source |
| `ModuleNotFoundError: perfetto` | running `./verify_trace.py` with the system interpreter; use `./.venv/bin/python verify_trace.py` |
| numbers don't match your field metrics | different measurement window (`TotalTime` vs your RUM metric), debug vs release build, page-cache-warm vs genuine first launch, or device mix |
