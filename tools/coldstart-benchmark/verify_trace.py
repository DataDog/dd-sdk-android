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


# The protocol leaves five seconds between the in-trace force-stop and the traced
# launch. A scheduler-derived boundary is accurate to one scheduling slice, so a
# one-second floor is three orders of magnitude above the error and five times below
# what the protocol provides: it accepts every capture that followed the protocol and
# refuses any where that gap is not visible.
MIN_SEPARATION_NS = 1_000_000_000


def final_launch_processes(tp, procs):
    """Return the package processes belonging to the final traced launch.

    capture_trace.sh starts Perfetto during the last conditioning wait, so the trace
    holds TWO generations of the package: the conditioning one, already alive when
    tracing began, and the one `am start -W` starts after the in-trace force-stop.
    Reading liveness across both lets a conditioning process that DID initialize the
    SDK satisfy the verdict for a traced launch that did not.

    The boundary is the latest process end in the complete conditioning generation.
    Seed it with every package process already alive when tracing began, then expand
    it to every package process whose lifetime starts before the current end boundary.
    That closure is necessary for a private process created during the conditioning
    wait: its start can be later than the old main process's last scheduler slice, but
    it still belongs to the generation killed by the same force-stop.

    capture_trace.sh records `sched/sched_process_free`, which populates process.end_ts.

    A LIFETIME BOUNDARY IS NOT AVAILABLE IN PRACTICE, which is why the scheduler
    boundary below is not a fallback so much as the working path. MEASURED on the
    target device (moto g(60)s, Android 12, sdk 31) with `sched/sched_process_free`
    enabled and the app force-stopped INSIDE the capture: `end_ts` is NULL on 849 of
    849 processes and 4429 of 4429 threads, including both conditioning processes the
    force-stop killed. Enabling the event does not populate it there. The lifetime
    path is kept because it is exact where it does work and costs nothing where it
    does not -- it issues no query at all -- but nothing may depend on it.

    The scheduler boundary is the last moment any thread of the processes alive at
    trace start was scheduled. On the same capture that is the force-stop to within
    one scheduling slice: the two conditioning processes last ran at +0.00s and
    -0.07s relative to it, while the traced launch's processes started at +2.09s and
    +2.77s.

    What this cannot see is a conditioning process CREATED during the capture whose
    start falls after every pre-existing process's last scheduler slice; it would be
    scored as final-launch. That requires the app to go entirely unscheduled between
    that creation and the force-stop, which does not happen to a foreground app about
    to be stopped -- but it is not proven by the boundary itself. What IS checkable is
    the separation: the protocol leaves five seconds between the force-stop and the
    traced launch, so the caller requires a margin far above scheduling granularity
    and refuses the verdict when it is absent, rather than assuming the gap was there.

    Returns (processes, boundary_ts, basis):
      (None, None, None)     the trace began with no conditioning generation, so
                             every package process in it belongs to the traced
                             launch. This is what a capture taken before Perfetto
                             moved inside the wait looks like.
      (procs, ts, "lifetime") the boundary came from process lifetimes.
      (procs, ts, "sched")    the boundary came from scheduler activity; the caller
                              must check the separation margin (see above).
      ([], None, None)       a conditioning generation exists but NEITHER a lifetime
                             nor a scheduler boundary could be established.
      ([], ts, basis)        the boundary is known, but no package process started
                             after it, so this trace holds no traced launch.
    """
    preexisting = [p for p in procs if p.start_ts is None]
    if not preexisting:
        return None, None, None
    conditioning = {p.upid for p in preexisting}
    boundary = None
    while True:
        members = [p for p in procs if p.upid in conditioning]
        if any(p.end_ts is None for p in members):
            conditioning = None
            break
        boundary = max(p.end_ts for p in members)
        expanded = {
            p.upid for p in procs
            if p.start_ts is None or p.start_ts <= boundary
        }
        if expanded == conditioning:
            break
        conditioning = expanded
    if conditioning is not None:
        return [p for p in procs
                if p.upid not in conditioning and p.start_ts is not None
                and p.start_ts > boundary], boundary, "lifetime"

    # No pipeline, no closure: the fallback can only start from the processes whose
    # membership needs no boundary to establish, i.e. the ones alive at trace start.
    upids = ",".join(str(p.upid) for p in preexisting)
    boundary = list(tp.query(
        "select max(s.ts + s.dur) as b from sched s "
        "join thread t on s.utid = t.utid "
        f"where t.upid in ({upids})"))[0].b
    if boundary is None:
        return [], None, None
    return [p for p in procs
            if p.start_ts is not None and p.start_ts > boundary], boundary, "sched"


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
        "select upid, pid, name, start_ts, end_ts from process "
        f"where name = '{args.package}' or name glob '{args.package}:*' order by pid"))
    if not procs:
        print(f"FAIL: process {args.package} not present in trace")
        return 1

    def scalar(sql):
        return list(tp.query(sql))[0].c

    final_procs, force_stop_ts, boundary_basis = final_launch_processes(tp, procs)
    if final_procs is not None and not final_procs:
        # Two different failures, and the operator needs to know which: one says the
        # trace lacks the data to locate the force-stop at all, the other says it
        # located it and the launch is simply not in the capture.
        print("  VERDICT: UNUSABLE FOR COLD-START ANALYSIS")
        if force_stop_ts is None:
            print("  A package process existed when tracing began, but neither its process")
            print("  lifetime nor any scheduler activity for it is recorded, so the")
            print("  force-stop boundary cannot be located by either method.")
            print("  Capture with sched/sched_process_free and sched/sched_switch enabled.")
        else:
            print(f"  The conditioning generation ended at ts {force_stop_ts},")
            print("  but no package process started after that, so this trace holds no traced")
            print("  launch -- only the conditioning one that preceded it.")
        print("  SDK liveness from the conditioning process cannot be attributed to the")
        print("  final traced launch.")
        return 3
    verdict_procs = final_procs if final_procs is not None else procs
    upids = ",".join(str(r.upid) for r in verdict_procs)

    # The launch lives in whichever process ran `bindApplication` -- the private one
    # when the launcher activity is private. Reported so the operator can see which
    # process the verdict is about; liveness spans only this final process generation.
    launch_procs = [r for r in verdict_procs if scalar(
        "select count(*) c from slice s join thread_track tt on s.track_id = tt.id "
        f"join thread t on tt.utid = t.utid where t.upid = {r.upid} "
        "and s.name = 'bindApplication'")]
    main = (launch_procs or verdict_procs)[0]
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
          + (f"  [+{len(verdict_procs) - 1} more final-launch process(es): "
             f"{', '.join(r.name for r in verdict_procs if r.upid != main.upid)}]"
             if len(verdict_procs) > 1 else ""))
    separation_ns = None
    if force_stop_ts is not None:
        separation_ns = min(r.start_ts for r in verdict_procs) - force_stop_ts
        print(f"  final process generation after force-stop ts {force_stop_ts}"
              + ("  [from process lifetimes]" if boundary_basis == "lifetime"
                 else "  [from scheduler activity]"))
        print(f"  launch starts {separation_ns / 1e9:+.2f}s after that boundary"
              f"   (protocol leaves 5s; {MIN_SEPARATION_NS / 1e9:.0f}s required)")
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
        tp, {r.upid for r in verdict_procs}, args.package,
        args.allow_missing_launch_marker)
    print(f"  foreground for whole capture  {fg_verdict}"
          + (f" -> {fg_detail[:3]}" if fg_detail else ""))

    # A scheduler boundary separates the two generations only if the protocol's gap
    # is actually present in the trace. Refusing on the method's NAME instead made
    # the treatment arm permanently unverifiable, because `end_ts` is unavailable on
    # the target device and so the basis is "sched" on every real capture. Check the
    # separation the protocol guarantees: five seconds, against a boundary accurate
    # to a scheduling slice. Below the floor, a process on the wrong side of the
    # boundary is possible and no verdict about these processes is reportable.
    if boundary_basis == "sched" and separation_ns is not None \
            and separation_ns < MIN_SEPARATION_NS:
        print()
        print("  VERDICT: UNUSABLE -- THE TWO PROCESS GENERATIONS ARE NOT SEPARATED")
        print(f"  The traced launch starts {separation_ns / 1e9:+.2f}s after a boundary read")
        print("  from scheduler activity, which is not enough to tell it apart from a")
        print("  conditioning process created just before the force-stop. The protocol")
        print("  leaves five seconds; this capture does not show them, so neither an")
        print("  active nor an inactive verdict can be attributed to the traced launch.")
        print("  Re-capture without inserting work between the force-stop and the launch.")
        return 3

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
