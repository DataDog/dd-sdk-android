#!/usr/bin/env python3
# Unless explicitly stated otherwise all files in this repository are licensed
# under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
"""
GUARD: assert that the Datadog Android SDK actually initialized inside a trace.

Why this exists
---------------
A trace labeled "with SDK" can contain no SDK activity at all — if the SDK never
initialized, every conclusion drawn from that trace attributes platform and app
noise to the SDK. Any cold-start trace must pass this check before it is analyzed.

What it looks for (in order of strength)
---------------------------------------
1. `datadog-*` threads. STRONGEST SIGNAL.
   `CoreFeature.initialize()` calls `setupExecutors()` and then immediately
   submits the NTP-sync task to `persistenceExecutorService`
   (dd-sdk-android-core/.../CoreFeature.kt:265-266). That executor uses
   `DatadogThreadFactory`, which names threads
   `datadog-<context>-thread-<n>` (truncated by Linux to 15 chars, e.g.
   `datadog-storage`). So a successful `Datadog.initialize()` ALWAYS leaves at
   least one `datadog-*` thread. R8/ProGuard cannot rename these, because the
   name is built at runtime from a string template.

2. `libdatadog-ndk.so` load, when NDK crash reporting is enabled.
   NOTE: Datadog uses a plain `System.loadLibrary`, which does not always emit
   its own atrace slice, so absence here is suggestive but NOT conclusive.

3. Any slice / track / arg containing "datadog".

Exit codes
----------
0  Datadog demonstrably active -- or correctly absent, under --expect-absent.
1  Datadog NOT active, or the package is not in the trace. Do not analyze.
   Sound as a negative only because the trace contains the cold start.
3  Trace unusable: no `bindApplication`, so there is no launch in it at all.
   Distinct from 1 on purpose -- it says nothing about the SDK either way.
4  With --require-foreground: something else owned the foreground during the
   capture, OR ownership could not be established from the trace at all. The SDK
   may well be active; the trace is just not demonstrably the scenario the
   benchmark measured, so its deltas are not comparable to the benchmark's.
   Applies to BOTH arms -- a baseline capture that lost the foreground is as
   uncomparable as a treatment one.
   --allow-missing-launch-marker downgrades ONE cause of this -- a missing
   ActivityManager `launching: <pkg>` slice -- to a loud partial-verification
   pass, for devices that do not emit it. Nothing else is relaxed.

Usage: verify_trace.py <trace.pftrace> --package <your.app.id>
                       [--expect-ndk] [--expect-session-replay] [--expect-absent]
                       [--require-foreground [--allow-missing-launch-marker]]
"""
import argparse
import re
import sys
from perfetto.trace_processor import TraceProcessor


def main():
    """Own the TraceProcessor lifetime so every exit path closes it.

    `analyze()` returns from five different places; each used to need its own
    tp.close(), one was missed, and an exception skipped all of them -- leaving
    the trace_processor subprocess running.
    """
    args = parse_args()
    if not re.match(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$', args.package):
        print(f"FAIL: implausible application id {args.package!r}")
        return 1
    tp = TraceProcessor(trace=args.trace)
    try:
        return analyze(tp, args)
    finally:
        tp.close()


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("--package", required=True,
                    help="your application id, e.g. com.example.app")
    ap.add_argument("--expect-ndk", action="store_true",
                    help="config calls NdkCrashReports.enable() / nativeCrashReportEnabled")
    ap.add_argument("--expect-session-replay", action="store_true")
    ap.add_argument("--expect-absent", action="store_true",
                    help="this is the BASELINE arm: passing means NO Datadog activity")
    ap.add_argument("--require-foreground", action="store_true",
                    help="exit 4 unless the app owned the foreground for the whole "
                         "capture, not merely at the end")
    # ActivityManager's `launching: <pkg>` slice is an implementation detail of
    # ActivityMetricsLogger, not a compatibility contract. On a device or Android
    # version that names it differently or omits it, requiring it turns every
    # otherwise-valid capture into exit 4 with a message that points at atrace
    # config instead of at the real cause. This degrades to the lifecycle-only
    # check rather than removing it, and the result is never reported as fully
    # verified.
    ap.add_argument("--allow-missing-launch-marker", action="store_true",
                    help="run the whole-window check on lifecycle slices alone when "
                         "ActivityManager's `launching: <pkg>` slice is absent. "
                         "Foreign-package takeover detection is then INCOMPLETE and "
                         "the capture is reported as partially verified, never held")
    return ap.parse_args()


def analyze(tp, args):
    # EVERY process of this application, not just the one named exactly after the
    # package. A `android:process=":startup"` component runs as `<pkg>:startup`, and
    # apps do put their launcher activity or their SDK initialization there. Looking
    # only at the exact name then reports "package not present", or finds a default
    # process that never ran the launch and calls Datadog absent -- which under
    # --expect-absent is the dangerous direction: a silent PASS for a baseline that
    # was never checked. Private processes cannot be forged from outside the app:
    # the `<pkg>:` prefix is enforced by the framework.
    procs = list(tp.query(
        "select upid, pid, name from process "
        f"where name = '{args.package}' or name glob '{args.package}:*' order by pid"))
    if not procs:
        print(f"FAIL: process {args.package} not present in trace")
        return 1
    upids = ",".join(str(r.upid) for r in procs)

    def scalar(sql):
        return list(tp.query(sql))[0].c

    # The launch lives in whichever process ran `bindApplication` -- the private one
    # when the launcher activity is private. Reported so the operator can see which
    # process the verdict is about; the checks themselves span all of them.
    launch_procs = [r for r in procs if scalar(
        "select count(*) c from slice s join thread_track tt on s.track_id = tt.id "
        f"join thread t on tt.utid = t.utid where t.upid = {r.upid} "
        "and s.name = 'bindApplication'")]
    main = (launch_procs or procs)[0]
    pid = main.pid

    total_threads = scalar(f"select count(*) c from thread where upid in ({upids})")

    # --- Can this trace support a NEGATIVE conclusion at all? -----------------
    # The oracle ("no datadog-* thread => init did not run") only holds if the
    # trace actually enumerates threads that exist but are idle. With a bare
    # `linux.process_stats` data source (no process_stats_config), Perfetto names
    # threads only from ftrace sched events, so an idle thread is INVISIBLE and
    # its absence proves nothing.
    # Test: does any named thread have zero scheduler slices? If none do, thread
    # naming is sched-derived only.
    idle_named = scalar(
        f"select count(*) c from thread t where t.upid in ({upids}) and t.name is not null "
        "and not exists (select 1 from thread_state ts where ts.utid = t.utid)")
    # Is there a cold start in here at all?
    has_bind = scalar(
        "select count(*) c from slice s join thread_track tt on s.track_id = tt.id "
        f"join thread t on tt.utid = t.utid where t.upid in ({upids}) "
        "and s.name = 'bindApplication'")
    proc_started_in_trace = scalar(
        f"select count(*) c from process where upid in ({upids}) and start_ts is not null")
    dd_threads = list(tp.query(
        f"select name, tid from thread where upid in ({upids}) and lower(name) glob 'datadog-*'"))
    dd_slices = scalar(
        "select count(*) c from slice s join thread_track tt on s.track_id = tt.id "
        f"join thread t on tt.utid = t.utid where t.upid in ({upids}) "
        "and lower(s.name) like '%datadog%'")
    ndk_load = scalar("select count(*) c from slice "
                      "where lower(name) like '%datadog-ndk%'")
    total_jit = scalar("select count(*) c from slice where name like 'JIT compiling%'")

    print(f"=== {args.trace} ===")
    print(f"  package                 {args.package} (pid {pid})"
          + (f"  [+{len(procs) - 1} more process(es): "
             f"{', '.join(r.name for r in procs if r.upid != main.upid)}]"
             if len(procs) > 1 else ""))
    print(f"  threads enumerated      {total_threads}")
    print(f"  datadog-* threads       {len(dd_threads)}"
          + (f"  -> {[(r.name, r.tid) for r in dd_threads]}" if dd_threads else ""))
    print(f"  slices matching datadog {dd_slices}")
    print(f"  libdatadog-ndk loads    {ndk_load}")
    print(f"  (JIT slices in trace    {total_jit} — confirms class-name capture works)")

    print(f"  named-but-idle threads  {idle_named}"
          f"   {'(enumerates idle threads)' if idle_named else '(sched-derived naming ONLY)'}")
    print(f"  bindApplication slices  {has_bind}"
          f"   {'' if has_bind else '<- NO COLD START IN THIS TRACE'}")
    print(f"  process start in trace  {'yes' if proc_started_in_trace else 'no (already running)'}")

    # The thread is the ONLY reliable oracle: CoreFeature.initialize() creates it
    # unconditionally and R8 cannot rename it. Slices are corroborating output only --
    # OR-ing them in lets the verdict be right for the wrong reason, or simply wrong.
    active = bool(dd_threads)
    print()

    # Whether a NEGATIVE verdict is sound depends on where init would have run
    # relative to the trace window:
    #
    #  * Cold start IS in the trace (bindApplication present) -> CONCLUSIVE.
    #    `Datadog.initialize()` would run inside the window, and CoreFeature
    #    creates `datadog-storage-thread-1` and IMMEDIATELY submits the NTP-sync
    #    task to it (CoreFeature.kt:265-266). A thread that is created and runs a
    #    task necessarily produces sched events, so it cannot be invisible --
    #    sched-derived naming is sufficient here.
    #
    #  * No cold start (process pre-existed) -> INCONCLUSIVE when naming is
    #    sched-derived only. Init happened before the window; the datadog
    #    executors may simply have been idle and therefore unnamed.
    if not has_bind:
        print("  VERDICT: UNUSABLE FOR COLD-START ANALYSIS")
        print("  No `bindApplication` and no activity lifecycle: the process was already")
        print("  running when tracing began. There is no launch in this trace to measure.")
        if not active:
            print()
            if idle_named == 0:
                print("  Datadog liveness: INCONCLUSIVE, not negative. Thread names here come")
                print("  only from ftrace sched events (zero named-but-idle threads), so an")
                print("  idle `datadog-*` thread would be invisible. Absence is not evidence.")
                print("  Fix: force-stop before tracing, and capture with")
                print("    process_stats_config { scan_all_processes_on_start: true"
                      " proc_stats_poll_ms: 1000 }")
            else:
                print("  Datadog liveness: NEGATIVE (this trace does enumerate idle threads,")
                print("  so an existing `datadog-*` thread would have been listed).")
        return 3

    # Computed for BOTH arms, and BEFORE any success verdict is returned. The
    # baseline arm used to return 0 here, above the lifecycle query, so a baseline
    # verified with --expect-absent --require-foreground (exactly what
    # capture_trace.sh passes) skipped the whole-window check entirely: an activity
    # that took over and handed back mid-capture left the shell's final `dumpsys`
    # snapshot clean and the contaminated trace was accepted.
    fg_verdict, fg_detail = foreground_ownership(
        tp, {r.upid for r in procs}, args.package,
        args.allow_missing_launch_marker)
    print(f"  foreground for whole capture  {fg_verdict}"
          + (f" -> {fg_detail[:3]}" if fg_detail else ""))

    if args.expect_absent:
        if active:
            print("  VERDICT: FAIL — baseline arm, but Datadog IS active in this trace.")
            return 1
        print("  VERDICT: PASS — baseline arm, no Datadog activity (as expected).")
        return foreground_gate(args, fg_verdict, fg_detail)

    if not active:
        print("  VERDICT: *** DATADOG NOT ACTIVE IN THIS TRACE ***")
        print("  This trace contains the cold start (bindApplication present), so")
        print("  `Datadog.initialize()` would have run inside the window. It creates")
        print("  `datadog-storage-thread-1` and immediately submits the NTP-sync task to")
        print("  it (CoreFeature.kt:265-266) — a created-and-running thread always emits")
        print("  sched events, so it could not have been missed. Do NOT attribute any")
        print("  cost to the SDK from this trace. Confirm the Datadog build is the one")
        print("  installed, and that init is not gated behind a flag/consent check.")
        return 1

    print("  VERDICT: Datadog active.")
    if args.expect_ndk and ndk_load == 0:
        print("  WARN: --expect-ndk set but no libdatadog-ndk load slice seen "
              "(inconclusive: System.loadLibrary may not emit a slice).")
    if args.expect_session_replay:
        sr = scalar("select count(*) c from slice where lower(name) like '%sessionrep%'")
        print(f"  session-replay slices: {sr}"
              + ("  WARN: expected Session Replay activity, found none." if sr == 0 else ""))
    return foreground_gate(args, fg_verdict, fg_detail)


def foreground_ownership(tp, upids, package, allow_missing_launch_marker=False):
    """Did the app own the foreground for the WHOLE window? -> (verdict, detail).

    Checking `dumpsys` once after the capture only sees the END state: a permission
    dialog that appears and disappears mid-window leaves the app foreground at the
    end while part of the measured window was paused, produced no frames and never
    reached the fully-drawn point. That is a different scenario from the one the
    A/B measured, and comparing their deltas is meaningless.

    Lifecycle slices are bound to the PROCESSES of this application rather than
    matched by name prefix: an app whose activity classes live outside its
    applicationId namespace would defeat prefix matching.

    Ownership is tracked as a set of resumed activities, not as "did we ever pause".
    A splash or router activity handing over to the next one pauses and stops while
    the app stays foreground throughout, and that is the overwhelmingly common
    startup shape; flagging it made the check reject valid traces. What separates a
    handover from an interruption is which activity ends the gap: a handover comes
    back as a DIFFERENT activity, an interruption returns to the one that paused. A
    gap that never closes is a loss too, whatever caused it.

    Known false positive: an activity destroyed and recreated in place (a
    configuration change mid-launch) returns as the same component and is reported
    lost. That launch is not the benchmarked scenario either, so it is flagged
    rather than tolerated -- the detail lines say which activity and for how long.

    Another package resuming is reported whenever its process lifecycle is visible.
    The harness also captures ActivityManager's global `launching: <package>` slices,
    which are not restricted by `atrace_apps`. This second signal is important when
    activity A pauses, a permission/system activity takes over, and activity B of
    this app later resumes: the different component alone looks like a valid A-to-B
    handoff, but the foreign launch proves the gap was contaminated.

    That slice is an ActivityMetricsLogger implementation detail, not a documented
    contract, so a device that does not emit it would otherwise fail every capture.
    `allow_missing_launch_marker` degrades to the lifecycle-only checks in that
    case and returns `held-lifecycle-only` rather than `held`, so a capture whose
    foreign-takeover clause never ran cannot be reported as a clean one.
    """
    lifecycle = list(tp.query(
        "select s.name, s.ts, t.upid from slice s "
        "join thread_track tt on s.track_id = tt.id "
        "join thread t on tt.utid = t.utid "
        "where s.name glob 'performResume:*' or s.name glob 'performPause:*' "
        "   or s.name glob 'performStop:*' order by s.ts"))
    ours = [r for r in lifecycle if r.upid in upids]
    ours_resume = [r.ts for r in ours if r.name.startswith("performResume:")]
    if not ours_resume:
        return "unknown", []
    t0 = min(ours_resume)

    launches = list(tp.query(
        "select name, ts from slice where name glob 'launching: *' order by ts"))
    target_launches = [
        r.ts for r in launches
        if r.name == f"launching: {package}" and r.ts <= t0
    ]
    no_marker = ("no ActivityManager 'launching: %s' slice before the first resume: "
                 "foreign-package takeover cannot be detected" % package)
    if target_launches:
        launch_t0 = min(target_launches)
    elif allow_missing_launch_marker:
        # Degraded, not skipped: every lifecycle-based clause below still runs. What
        # is lost is the one signal that catches a foreign activity taking over
        # between two activities of this app, so the verdict can no longer be "held".
        launch_t0 = None
    else:
        return "unknown", [no_marker]

    def at(ts):
        return f"{(ts - t0) / 1e6:+.0f} ms"

    # Anyone else resuming after us. The launcher's own pause/stop as we take over
    # happens BEFORE t0, so ordering keeps it from being flagged.
    detail = [f"{r.name} (@{at(r.ts)}) -- another process resumed" for r in lifecycle
              if r.ts > t0 and r.upid not in upids
              and r.name.startswith("performResume:")]
    # Start at the target's LAUNCH marker, not its first resume. A permission
    # activity requested from Application.onCreate()/Activity.onCreate() can launch
    # before the target reaches performResume; filtering from t0 would discard the
    # only evidence of that takeover and report a contaminated launch as held.
    if launch_t0 is not None:
        detail.extend(
            f"{r.name} (@{at(r.ts)}) -- another package launched"
            for r in launches
            if r.ts > launch_t0 and r.name != f"launching: {package}"
        )

    resumed, gap = set(), None
    for r in ours:
        if r.ts < t0:
            continue
        kind, _, comp = r.name.partition(":")
        if kind == "performResume":
            if not resumed and gap is not None:
                left_ts, left_comp = gap
                if comp == left_comp:
                    detail.append(
                        f"{left_comp} paused @{at(left_ts)} and resumed @{at(r.ts)} "
                        f"({(r.ts - left_ts) / 1e6:.0f} ms with no activity of this app "
                        "resumed, then the SAME activity came back)")
                gap = None
            resumed.add(comp)
        else:  # performPause / performStop
            resumed.discard(comp)
            if not resumed:
                gap = (r.ts, comp)
    if gap is not None:
        detail.append(f"{gap[1]} paused @{at(gap[0])} and no activity of this app "
                      "resumed again before the end of the capture")
    if detail:
        return "lost", detail
    # Not "held": the lifecycle clauses found nothing, but the clause that would
    # have caught a foreign takeover between two of this app's activities did not
    # run. Reporting that as held would describe an unverified property as verified.
    if launch_t0 is None:
        return "held-lifecycle-only", [no_marker]
    return "held", detail


def foreground_gate(args, fg_verdict, fg_detail):
    """Turn a foreground verdict into an exit code. 0 unless --require-foreground.

    `unknown` exits 4 too. --require-foreground promises the whole-window check
    RAN; a trace with no `performResume:` slice for this process is one where it
    could not, so passing it would mean reporting an unverified capture as
    verified. On this harness's own perfetto config the slice is always there
    (atrace category `am` + `atrace_apps: <pkg>`), so `unknown` means the capture
    was not taken the way capture_trace.sh takes it.

    `held-lifecycle-only` exits 0: the operator asked for the degraded check with
    --allow-missing-launch-marker, so refusing it would make the flag pointless.
    It is loud instead, and it is a distinct verdict from `held` precisely so that
    no report can call it clean.
    """
    if fg_verdict == "lost":
        print()
        print("  *** THE APP DID NOT OWN THE FOREGROUND FOR THE WHOLE CAPTURE ***")
        print("  Something took over during the capture -- most often a runtime")
        print("  permission dialog. While paused the app produces no frames, so a")
        print("  takeover inside the interval the A/B measures makes this trace a")
        print("  different scenario from the benchmark's. Re-capture.")
        print("  This check does NOT place the takeover relative to that interval: the")
        print("  offsets below are relative to this app's FIRST RESUME, and the capture")
        print("  keeps recording well past first frame. A takeover in that tail cannot")
        print("  have changed the A/B number; it is rejected on the same footing here")
        print("  only because the endpoint is observed on the host and its timestamp is")
        print("  not in the trace. Read the offsets before re-running.")
        for d in fg_detail[:6]:
            print(f"    {d}")
        return 4 if args.require_foreground else 0
    if fg_verdict == "held-lifecycle-only":
        print()
        print("  *** FOREGROUND OWNERSHIP ONLY PARTIALLY VERIFIED ***")
        print("  --allow-missing-launch-marker is set and ActivityManager's")
        print("  `launching: <pkg>` slice is absent from this trace. No activity of")
        print("  this app paused without another one resuming, so the lifecycle-based")
        print("  checks passed -- but the check that catches a FOREIGN activity taking")
        print("  over between two of this app's activities DID NOT RUN. A permission or")
        print("  system dialog in that position would not be visible here. This capture")
        print("  is PARTIALLY verified; do not describe it as clean.")
        for d in fg_detail[:6]:
            print(f"    {d}")
        return 0
    if fg_verdict == "unknown":
        print()
        print("  *** FOREGROUND OWNERSHIP COULD NOT BE ESTABLISHED ***")
        print("  The required lifecycle or global ActivityManager launch evidence is")
        print("  absent, so the whole-window check could not run -- this trace is not")
        print("  verified either way. Capture with atrace category `am` enabled and")
        print("  `atrace_apps: <pkg>` (capture_trace.sh does both), and make sure the")
        print("  launch is inside the trace window.")
        if not args.allow_missing_launch_marker:
            print("  If this device simply does not emit ActivityManager's global")
            print("  `launching: <pkg>` slice, --allow-missing-launch-marker")
            print("  (ALLOW_MISSING_LAUNCH_MARKER=1 for capture_trace.sh) runs the")
            print("  lifecycle-only check instead and says what it could not verify.")
        for d in fg_detail[:6]:
            print(f"    {d}")
        return 4 if args.require_foreground else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
