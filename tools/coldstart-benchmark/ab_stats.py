#!/usr/bin/env python3
# Unless explicitly stated otherwise all files in this repository are licensed
# under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
"""
Statistics for a cold-start A/B run set produced by coldstart_bench.sh.

PRIMARY ENDPOINT: the paired block-level delta.
-----------------------------------------------
Launches are not independent. They are collected in contiguous arm x block cells
(uninstall -> install -> AOT compile -> warm-ups -> N measured launches). Anything
that shifts a whole cell -- thermal state, dexopt, install recency, background
work -- is a cell-level random effect. Pooling all launches and running an
unpaired test estimates the standard error from WITHIN-cell scatter only and
ignores BETWEEN-cell variance, so it is anti-conservative.

Measured by simulation (true effect zero, within-launch sd 11 ms, per-cell shift
sd sigma_b), false-positive rate of the nominal-95% interval:

    design                sigma_b=0   2       4       8
    2x15 unpaired           4.8%   10.0%   23.4%   45.5%
    8x4  paired on blocks   4.8%    4.9%    5.0%    4.8%

Reproduce with ./fp_simulation.py -- it drives the interval code in this module,
so the table cannot drift away from what the tool actually does.

Counterbalancing (ABBA) removes the ordering BIAS; it does nothing about this
variance underestimate. So the primary analysis here computes one delta per
block and runs a paired test on those. The unpaired Welch result is still shown,
labeled [diagnostic], because it is what most tools report and the contrast is
informative.

Usage: ab_stats.py <results.csv> [more.csv ...] [--baseline A] [--treatment B]
                   [--metric total_ms|displayed|ttfd|app_trace_ms]
                   [--allow-missing-endpoint]

METRIC CHOICE MATTERS on framework apps. `total_ms` (and the identical `displayed`)
end at first frame. For React Native / Flutter apps a large part of startup runs
after that, so an SDK cost landing in the later window is invisible to TTID. If the
app calls reportFullyDrawn(), `--metric ttfd` measures through to it. Report both.
"""
import argparse
import csv
import hashlib
import math
import os
import random
import re
import statistics as st
from collections import Counter, defaultdict

# Two-sided 97.5th percentile of Student-t. Keys are exact df.
_T = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365,
      8: 2.306, 9: 2.262, 10: 2.228, 12: 2.179, 15: 2.131, 20: 2.086,
      25: 2.060, 30: 2.042, 40: 2.021, 60: 2.000, 120: 1.980}

# One-sided 80th percentile of Student-t, i.e. the power term for 80% power.
_T80 = {1: 1.376, 2: 1.061, 3: 0.978, 4: 0.941, 5: 0.920, 6: 0.906, 7: 0.896,
        8: 0.889, 9: 0.883, 10: 0.879, 12: 0.873, 15: 0.866, 20: 0.860,
        25: 0.856, 30: 0.854, 40: 0.851, 60: 0.848, 120: 0.845}


def _t_lookup(table, df):
    """Table lookup rounding df DOWN, so every quantile is conservative.

    Above the largest tabulated df, retain that finite-df value. Switching to
    the asymptotic normal value would round the quantile down and make both the
    confidence interval and MDE optimistic at exactly the largest table key.
    """
    best = table[min(table)]
    for k in sorted(table):
        if k <= df:
            best = table[k]
        else:
            break
    return best


def t_crit(df):
    """97.5th percentile of t. Rounds df DOWN so the interval is never too narrow."""
    return _t_lookup(_T, df)


def t_power80(df):
    """80th percentile of t. Rounds df DOWN so the MDE is never too optimistic."""
    return _t_lookup(_T80, df)


def mde(sd_b, k):
    """Smallest true block delta this design detects with 80% power at alpha=.05.

    (t_{.975,k-1} + t_{.80,k-1}) * sd_b / sqrt(k). The normal approximation
    (1.96 + 0.84 = 2.8) is what you usually see, but at the block counts this
    harness runs it understates the MDE by ~15% -- at k=8, 2.8 against 3.26.
    An MDE that reads too small makes an underpowered null look stronger than it
    is, which is the exact failure the number exists to prevent.
    """
    return (t_crit(k - 1) + t_power80(k - 1)) * sd_b / math.sqrt(k)


def blocks_for(sd_b, target, cap=200):
    """Supported blocks needed to bring the MDE down to `target` ms.

    Solved by search rather than in closed form because both t quantiles depend
    on k. The collector accepts only even block counts: that is what makes the
    ABBA schedule counterbalanced, so an odd mathematical solution is not a
    runnable recommendation.
    """
    for k in range(4, cap + 1, 2):
        if mde(sd_b, k) <= target:
            return k
    return None


def welch(a, b):
    m1, m2 = st.mean(a), st.mean(b)
    v1, v2 = st.variance(a), st.variance(b)
    n1, n2 = len(a), len(b)
    se = math.sqrt(v1 / n1 + v2 / n2)
    if se == 0:
        return m2 - m1, 0.0, float("inf"), 0.0
    t = (m2 - m1) / se
    df = (v1 / n1 + v2 / n2) ** 2 / (
        (v1 / n1) ** 2 / (n1 - 1) + (v2 / n2) ** 2 / (n2 - 1))
    return m2 - m1, se, df, t


def perm_p(a, b, stat=st.mean, iters=200_000, seed=42):
    """Two-sided permutation p. Floored at 1/(iters+1): a permutation test can
    never yield exactly zero, and printing 0.0000 would overstate the evidence."""
    random.seed(seed)
    obs = abs(stat(b) - stat(a))
    pool = list(a) + list(b)
    n = len(b)
    hits = 0
    for _ in range(iters):
        random.shuffle(pool)
        if abs(stat(pool[:n]) - stat(pool[n:])) >= obs - 1e-12:
            hits += 1
    return (hits + 1) / (iters + 1)


_DUR = re.compile(r"(?:(\d+)h)?(?:(\d+)m(?!s))?(?:(\d+)s)?(?:(\d+)ms)?$")


def parse_ms(raw):
    """Parse a duration cell. Plain numbers are already ms; logcat writes the
    Displayed/Fully-drawn values as '+702ms', '+2s308ms', '+1m2s30ms'."""
    if raw is None:
        return None
    raw = raw.strip().lstrip("+")
    if raw in ("", "NA", "null"):
        return None
    try:
        value = float(raw)
        return value if math.isfinite(value) and value >= 0 else None
    except ValueError:
        pass
    m = _DUR.match(raw)
    if not m or not any(m.groups()):
        return None
    h, mi, se, ms = (int(g) if g else 0 for g in m.groups())
    return float(((h * 60 + mi) * 60 + se) * 1000 + ms)


# Metadata whose disagreement makes two runs incomparable. Deliberately NOT
# blocks/runs -- concatenating a 4-block and an 8-block run of the same build with
# the same recorded device/protocol metadata is legitimate and is the reason
# multi-file exists. The harness assumes the operator keeps one physical device;
# it does not stamp a stable handset identity or implement a multi-device model.
# `launcher` belongs here: the harness itself aborts when two ARMS resolve
# different launcher components, so pooling two FILES that entered through
# different components would contradict the rule it enforces internally.
# `warmup` belongs here for the reason capture_trace.sh derives its own settle count
# from the header's value: every cell is a fresh install, so the first measured launch sits at
# post-install position warmup+2, and under the default `speed-profile` filter there
# is no AOT code on that install -- the profile fills and the JIT warms over the
# first few launches. Two files with different warmups therefore measure the SDK's
# cost at different points of that ramp, which is a different startup condition, not
# a larger sample of one. blocks/runs only lengthen the tail from a common start.
_MUST_MATCH = ("fp", "emulator", "android_user", "compile_filter", "animations",
               "airplane", "abi", "launcher", "warmup")

# The md5 of each arm's APK, stamped by coldstart_bench.sh. Every field in
# _MUST_MATCH can agree across two runs from SUCCESSIVE APK pairs on the same
# device -- versionCode/versionName included, since the harness requires the two
# ARMS to match on those and a rebuild of both keeps them equal -- so without the
# digests, block deltas from two different experiments pool into one interval with
# no warning at all.
#
# Kept separate from _MUST_MATCH so disagreement can explain exactly which
# measured binaries differ.
_BUILD_KEYS = ("baseline_md5", "treatment_md5")

# Which LABEL named which arm. The digests above pin the pair of binaries; they do
# not pin the mapping. Swap LABEL_A and LABEL_B between two runs of the same apk
# pair and both digests still agree, while every row in the second file labels the
# other binary -- so the block deltas pooled here have opposite signs and the
# interval straddles zero for a reason that has nothing to do with the SDK. Rename
# them instead of swapping and the second file contributes no rows at all, while the
# header line still lists it as pooled.
_ARM_KEYS = ("label_a", "label_b")

# The per-arm SDK-liveness contract. Two otherwise identical APKs can exercise
# different runtime states when initialization is remotely or consent gated; if
# one run expects the treatment active and another expects it absent, their deltas
# estimate different experiments even though the APK digests and labels match.
_LIVENESS_KEYS = ("expect_a", "expect_b")

# Identity of each arm's effective runtime-permission result (granted and denied
# sets), stamped after that arm's first install.
_PERMISSION_KEYS = ("permission_a", "permission_b")

# What the device actually did with the two runtime controls it cannot be asked to
# confirm afterwards.
#
#   compile_status  the achieved dexopt state, which is NOT the requested filter:
#                   `speed-profile` commonly leaves a fresh install at `verify`,
#                   so equal `compile_filter` values do not prove equal AOT/JIT
#                   code. A set like `speed-profile+verify` covers every code path
#                   and ABI in the package.
#   perf_mode       `fixed` when the power HAL accepted fixed-performance mode,
#                   `dynamic` under ALLOW_DYNAMIC_PERFORMANCE. Pooling the two
#                   averages a pinned-CPU experiment with an unpinned one.
#
# Kept separate from _MUST_MATCH so disagreement can explain whether the achieved
# compilation state or performance-mode outcome changed.
_CONTROL_KEYS = ("compile_status", "perf_mode")

# md5 of APP_TRACE_REGEX, checked ONLY when --metric app_trace_ms is what gets
# pooled. That metric's window is defined by the host app's own log line, so two
# files captured with different regexes can hold a native-init duration and a total
# launch duration under the same column name. Every other metric is defined by us
# and is unaffected, which is why this is not in _MUST_MATCH.
_APP_TRACE_KEY = "app_trace_id"

# There is no released legacy CSV format to preserve. Every reportable run must
# carry enough evidence to bind its binaries, scenario, matrix and selected arm.
# --allow-mixed may acknowledge a disagreement; it cannot manufacture missing
# evidence.
_CURRENT_META_KEYS = (
    "device", "sdk", *_MUST_MATCH, *_BUILD_KEYS, *_ARM_KEYS, *_LIVENESS_KEYS,
    *_PERMISSION_KEYS, *_CONTROL_KEYS, _APP_TRACE_KEY, "blocks", "runs"
)

_CURRENT_COLUMNS = {
    "label", "block", "pos_in_block", "phase", "run", "total_ms",
    "launch_state", "status", "foreground"
}


def parse_meta(lines):
    """Pull `key=value` pairs out of a run's `#` header line(s)."""
    kv = {}
    for ln in lines:
        for tok in ln.lstrip("#").split():
            if "=" in tok:
                k, _, v = tok.partition("=")
                kv.setdefault(k, v)
    return kv


def present_meta_differences(metas, keys):
    """Return known disagreements without treating missing values as agreement."""
    differing = {}
    for key in keys:
        present = sorted({meta[key] for meta in metas if key in meta})
        if len(present) > 1:
            differing[key] = present
    return differing


def fmt_p(p, iters=200_000):
    """perm_p() floors at 1/(iters+1) so it can never be zero. '%.5f' would still
    render that floor as 0.00000, which reads as 'impossible' rather than 'below
    the resolution of this many permutations'."""
    floor = 1 / (iters + 1)
    return f"<{floor:.2g} (permutation floor)" if p <= floor else f"{p:.5f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", nargs="+", help="one or more results CSVs (concatenated)")
    ap.add_argument("--baseline", default="A_noDD")
    ap.add_argument("--treatment", default="B_withDD")
    ap.add_argument("--allow-aborted", action="store_true",
                    help="inspect an aborted/truncated run or one with invalid rows "
                         "(diagnostic only; primary interval stays suppressed)")
    ap.add_argument("--allow-mixed", action="store_true",
                    help="pool CSVs whose device/protocol metadata disagrees "
                         "(refused by default)")
    ap.add_argument("--allow-missing-endpoint", action="store_true",
                    help="inspect rows after the selected metric is missing from one or more "
                         "eligible launches (diagnostic only; primary interval stays suppressed)")
    ap.add_argument("--metric", default="total_ms",
                    choices=["total_ms", "displayed", "ttfd", "app_trace_ms"],
                    help="total_ms/displayed = time to initial display (default); "
                         "ttfd = time to fully drawn, needs reportFullyDrawn(); "
                         "app_trace_ms = the host app's own metric, captured via "
                         "APP_TRACE_REGEX")
    a = ap.parse_args()
    diagnostic_only_reasons = []
    if a.baseline == a.treatment:
        raise SystemExit(
            f"--baseline and --treatment are both {a.baseline!r}. That compares an arm "
            "against itself:\n  every block delta is 0 and the result is a null that "
            "looks convincing and means nothing.\n  For an A/A run give the two arms "
            "distinct labels (LABEL_A=A1 LABEL_B=A2).")

    # The same file passed as `results.csv` and `./results.csv`, or through a
    # symlink/hard link, is not another experiment. A copied archive is the same
    # evidence too, despite having a new inode. Namespacing any of those by their
    # command-line spelling would count every block twice and narrow the CI
    # without adding evidence. This catches ordinary input mistakes; it is not a
    # tamper-resistance boundary for files and code the operator owns.
    seen_inputs = {}
    seen_contents = {}
    for path in a.csv:
        stat = os.stat(path)
        identity = (stat.st_dev, stat.st_ino)
        if identity in seen_inputs:
            raise SystemExit(
                "refusing duplicate CSV inputs that resolve to the same file:\n"
                f"    {seen_inputs[identity]}\n"
                f"    {path}\n"
                "  Repeating a run would double its apparent block count and narrow the\n"
                "  confidence interval without adding any independent observations.")
        seen_inputs[identity] = path
        digest = hashlib.sha256()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(1024 * 1024), b""):
                digest.update(chunk)
        content_identity = digest.hexdigest()
        if content_identity in seen_contents:
            raise SystemExit(
                "refusing byte-identical CSV inputs:\n"
                f"    {seen_contents[content_identity]}\n"
                f"    {path}\n"
                "  A copied/archive path is still the same observations. Counting it\n"
                "  twice would narrow the confidence interval without new evidence.")
        seen_contents[content_identity] = path

    # Parse each file SEPARATELY. Concatenating them fed later header lines to the
    # reader as data, and merged "block 1" from different runs into one block.
    per_file, meta, per_meta = [], [], []
    for path in a.csv:
        body, own_meta = [], []
        with open(path, encoding="utf-8") as fh:
            for ln in fh:
                (own_meta if ln.startswith("#") else body).append(ln)
        per_file.append((path, body))
        per_meta.append(own_meta)
        meta.extend(own_meta)
    metas = [parse_meta(m) for m in per_meta]
    format_errors = []
    for (path, body), own_meta in zip(per_file, metas):
        missing = [key for key in _CURRENT_META_KEYS if not own_meta.get(key)]
        if missing:
            format_errors.append(
                f"  {path}: missing metadata {', '.join(missing)}"
            )
        columns = set(csv.DictReader(body).fieldnames or ())
        missing_columns = sorted(_CURRENT_COLUMNS - columns)
        if missing_columns:
            format_errors.append(
                f"  {path}: missing columns {', '.join(missing_columns)}"
            )
    if format_errors:
        raise SystemExit(
            "refusing a CSV that is not in the complete current harness format:\n"
            + "\n".join(format_errors)
            + "\n  This benchmark was not released with a legacy format to preserve."
              "\n  Re-run it with this version of coldstart_bench.sh."
        )

    incomplete = []
    for (path, _), own_meta in zip(per_file, per_meta):
        count = sum(line.strip() == "# RUN COMPLETE" for line in own_meta)
        if count != 1:
            incomplete.append(f"  {path}: {count} RUN COMPLETE markers (expected 1)")
    if incomplete:
        print("!" * 78)
        print("!! INCOMPLETE RUN -- positive completion evidence is missing or ambiguous.")
        for line in incomplete:
            print("!!" + line)
        print("!" * 78)
        if not a.allow_aborted:
            raise SystemExit(
                "  refusing to analyze a run that did not positively complete. Re-run it.\n"
                "  For investigation only, pass --allow-aborted; the output is then\n"
                "  diagnostic and the primary interval remains suppressed."
            )
        diagnostic_only_reasons.append("positive RUN COMPLETE evidence is absent")
        print("[WARNING: --allow-aborted without positive completion. DIAGNOSTIC ONLY.]")
    if len(per_file) > 1:
        print(f"[{len(per_file)} files: block ids are namespaced per file, so blocks from "
              f"different runs are never merged]")
        # Namespacing block ids stops blocks being merged; it does NOT make the runs
        # comparable. Pooling launches from two device models, or from an
        # animations-on and an animations-off run, yields one confidence interval
        # over two different experiments. Refuse unless told otherwise.
        mismatched = present_meta_differences(metas, _MUST_MATCH)
        if mismatched:
            lines = [f"    {k}: {' vs '.join(v)}" for k, v in mismatched.items()]
            if not a.allow_mixed:
                raise SystemExit(
                    "refusing to pool CSVs whose runs are not comparable:\n"
                    + "\n".join(lines)
                    + "\n  These disagree on recorded device or protocol metadata, so a"
                      "\n  single pooled interval over them is not a valid estimate of"
                      "\n  anything. Analyze them separately, or pass --allow-mixed if you"
                      "\n  genuinely intend to pool them and will caveat the result.")
            print("[WARNING: --allow-mixed, pooling runs that disagree on:]")
            for ln in lines:
                print(ln)
        differing = present_meta_differences(metas, _BUILD_KEYS)
        if differing:
            lines = [f"    {k}: {' vs '.join(v)}" for k, v in differing.items()]
            if not a.allow_mixed:
                raise SystemExit(
                    "refusing to pool CSVs whose arms were built from different APKs:\n"
                    + "\n".join(lines)
                    + "\n  Same device, same protocol, same launcher -- but not the same"
                      "\n  binaries, so these are two different experiments and one pooled"
                      "\n  interval over them estimates nothing. Analyze them separately,"
                      "\n  or pass --allow-mixed if you genuinely intend to pool them.")
            print("[WARNING: --allow-mixed, pooling runs built from different APKs:]")
            for ln in lines:
                print(ln)
        arm_differing = present_meta_differences(metas, _ARM_KEYS)
        if arm_differing:
            lines = [f"    {k}: {' vs '.join(v)}" for k, v in arm_differing.items()]
            if not a.allow_mixed:
                raise SystemExit(
                    "refusing to pool CSVs that map their arm LABELS differently:\n"
                    + "\n".join(lines)
                    + "\n  Same device, same protocol, same two APKs -- but a label does not"
                      "\n  name the same binary in every file. If the labels were swapped,"
                      "\n  the second run's deltas enter this pool with the sign reversed;"
                      "\n  if they were merely renamed, that file contributes no rows at all"
                      "\n  while the header above still lists it as pooled.")
            print("[WARNING: --allow-mixed, pooling runs whose label -> arm mapping differs:]")
            for ln in lines:
                print(ln)
        liveness_differing = present_meta_differences(metas, _LIVENESS_KEYS)
        if liveness_differing:
            lines = [f"    {k}: {' vs '.join(v)}" for k, v in liveness_differing.items()]
            if not a.allow_mixed:
                raise SystemExit(
                    "refusing to pool CSVs with different SDK-liveness expectations:\n"
                    + "\n".join(lines)
                    + "\n  A label names an SDK-active arm in one run and an SDK-absent arm"
                      "\n  in another, so their deltas estimate different runtime states even"
                      "\n  though the APK pair is unchanged. Analyze them separately, or pass"
                      "\n  --allow-mixed only for an explicitly diagnostic mixed-state view.")
            print("[WARNING: --allow-mixed, pooling runs whose SDK-liveness expectations differ:]")
            for ln in lines:
                print(ln)
        permission_differing = present_meta_differences(metas, _PERMISSION_KEYS)
        if permission_differing:
            lines = [f"    {k}: {' vs '.join(v)}"
                     for k, v in permission_differing.items()]
            if not a.allow_mixed:
                raise SystemExit(
                    "refusing to pool CSVs with different runtime-permission outcomes:\n"
                    + "\n".join(lines)
                    + "\n  An app can take a different startup path under a different"
                      "\n  effective grant set even when no permission dialog appears."
                      "\n  Analyze these runs separately, or pass --allow-mixed only for"
                      "\n  an explicitly diagnostic mixed-permission view.")
            print("[WARNING: --allow-mixed, pooling runs whose permission outcomes differ:]")
            for ln in lines:
                print(ln)
        control_differing = present_meta_differences(metas, _CONTROL_KEYS)
        if control_differing:
            lines = [f"    {k}: {' vs '.join(v)}"
                     for k, v in control_differing.items()]
            if not a.allow_mixed:
                raise SystemExit(
                    "refusing to pool CSVs collected under different runtime controls:\n"
                    + "\n".join(lines)
                    + "\n  compile_status is the AOT/JIT state the device actually reached,"
                      "\n  and perf_mode is whether CPU behavior was pinned. Either one"
                      "\n  differing makes these two experiments rather than more samples"
                      "\n  of one, and the difference is attributed to the SDK. Analyze them"
                      "\n  separately, or pass --allow-mixed only for an explicitly"
                      "\n  diagnostic pool whose caveat you will carry into the result.")
            print("[WARNING: --allow-mixed, pooling runs collected under different"
                  " runtime controls:]")
            for ln in lines:
                print(ln)
        if a.metric == "app_trace_ms":
            ids = {m[_APP_TRACE_KEY] for m in metas}
            if len(ids) > 1:
                if not a.allow_mixed:
                    raise SystemExit(
                        "refusing to pool --metric app_trace_ms across CSVs recorded with\n"
                        "different APP_TRACE_REGEX values:\n"
                        f"    {_APP_TRACE_KEY}: {' vs '.join(sorted(ids))}\n"
                        "  This metric's window is whatever the host app's log line measures.\n"
                        "  Different patterns can match different events -- native init in one\n"
                        "  file, total launch in the other -- and pooling them produces one\n"
                        "  interval over two different quantities. Analyze them separately, or\n"
                        "  re-run both arms with the same regex.")
                print("[WARNING: --allow-mixed, pooling app_trace_ms captured with different]")
                print(f"[         APP_TRACE_REGEX values: {' vs '.join(sorted(ids))}]")
    for m in meta:
        print(m.rstrip())

    # Each banner is decided over ALL metadata lines, independently of the others.
    # These were briefly chained together -- the emulator check ended up nested under
    # the aborted branch and testing the loop variable, so it never fired on a normal
    # completed emulator run, which is the case it exists for.
    if any("emulator=1" in m for m in meta):
        print("!" * 78)
        print("!! EMULATOR DATA. Emulator timings do not transfer to real devices:")
        print("!! native library loading, dex verification, disk I/O and thread")
        print("!! contention are all distorted, and there is no thermal throttling.")
        print("!! Do not report these as your app's startup cost.")
        print("!" * 78)

    if any("RUN ABORTED" in m for m in meta):
        print("!" * 78)
        print("!! This CSV is a PARTIAL run: the harness aborted before finishing.")
        print("!! Whatever it contains was not collected under the protocol as designed.")
        print("!" * 78)
        # Printing a banner and then computing a reportable interval anyway is the
        # same mistake the whole design argues against: an aborted run can stop
        # part-way through an arm, and the block logic accepts a cell with a single
        # sample, so a rejected protocol could still produce a primary endpoint.
        if not a.allow_aborted:
            raise SystemExit(
                "  refusing to analyze an aborted run. Fix the cause and re-run.\n"
                "  If you need to inspect it anyway, pass --allow-aborted -- the output\n"
                "  is then diagnostic only and must not be reported as a result.")
        diagnostic_only_reasons.append("the CSV is marked RUN ABORTED")
        print("[WARNING: --allow-aborted. This output is DIAGNOSTIC ONLY. Do not report it.]")

    # ---- does the CSV contain the experiment its own header describes? ----------
    # The completion marker proves the script returned; the matrix check separately
    # proves that the selected arms contain exactly the experiment the header
    # declares. A wrong label selection can expose the same structural mismatch.
    shortfalls = []
    malformed_matrix_metadata = []
    for (path, body), own_meta in zip(per_file, per_meta):
        kv = parse_meta(own_meta)
        blocks_value, runs_value = kv.get("blocks"), kv.get("runs")
        name = path if len(per_file) > 1 else "this CSV"
        if (blocks_value is None or runs_value is None
                or not blocks_value.isdigit() or not runs_value.isdigit()
                or int(blocks_value) < 1 or int(runs_value) < 1):
            malformed_matrix_metadata.append(
                f"  {name}: blocks={blocks_value or '<absent>'}, "
                f"runs={runs_value or '<absent>'}"
            )
            continue
        want_blocks, want_runs = int(blocks_value), int(runs_value)
        cell_runs = defaultdict(list)
        for r in csv.DictReader(body):
            if r.get("phase") in ("measure", "measure_rejected"):
                block = r.get("block") or "<missing>"
                run = r.get("run") or "<missing>"
                cell_runs[(r.get("label"), block)].append(run)
        expected_blocks = {str(block) for block in range(1, want_blocks + 1)}
        expected_runs = Counter(str(run) for run in range(1, want_runs + 1))
        short = []
        for label in (a.baseline, a.treatment):
            for block in range(1, want_blocks + 1):
                runs = cell_runs.get((label, str(block)), [])
                count = len(runs)
                if count != want_runs:
                    short.append(
                        f"{label} block {block}: {count}/{want_runs} launches"
                    )
                elif Counter(runs) != expected_runs:
                    observed = Counter(runs)
                    # Both lists walk `expected_runs`, which is already in declared
                    # order, so they read 1,2,...,10 rather than the lexicographic
                    # 1,10,2 that sorting the observed string keys would give.
                    missing = [run for run in expected_runs if observed[run] == 0]
                    duplicate = [f"{run}x{observed[run]}" for run in expected_runs
                                 if observed[run] > 1]
                    # Named apart from the per-label `unexpected` blocks below: the two
                    # count different things and shared one name in the same scope.
                    unexpected_runs = sorted(r for r in observed if r not in expected_runs)
                    detail = []
                    if missing:
                        detail.append(f"missing {','.join(missing)}")
                    if duplicate:
                        detail.append(f"duplicate {','.join(duplicate)}")
                    if unexpected_runs:
                        detail.append(f"unexpected {','.join(unexpected_runs)}")
                    short.append(
                        f"{label} block {block}: invalid run IDs ({'; '.join(detail)}); "
                        f"expected 1..{want_runs} exactly once"
                    )
            unexpected = sorted(
                block for label_in_file, block in cell_runs
                if label_in_file == label and block not in expected_blocks
            )
            if unexpected:
                short.append(
                    f"{label}: unexpected block(s) {', '.join(unexpected)}"
                )
        # Say what was left out. A silently capped list reads as the complete
        # set of problems, which is how a partly-analyzed matrix looks whole.
        shortfalls += [f"  {name}: {item}" for item in short[:10]]
        if len(short) > 10:
            shortfalls.append(f"  {name}: ... and {len(short) - 10} more cell(s) not listed")
    if malformed_matrix_metadata:
        raise SystemExit(
            "refusing to analyze malformed experiment matrix metadata:\n"
            + "\n".join(malformed_matrix_metadata)
            + "\n  `blocks` and `runs` must both be positive integers so the declared"
              "\n  matrix can be validated."
        )
    if shortfalls:
        print("!" * 78)
        print("!! INCOMPLETE EXPERIMENT MATRIX -- the selected baseline/treatment do")
        print("!! not contain the exact block/run matrix declared by the CSV header.")
        print("!! Collection may have been interrupted without an abort trailer, or")
        print("!! the requested labels may not name the experiment in this file.")
        for line in shortfalls[:10]:
            print("!!" + line)
        if len(shortfalls) > 10:
            print(f"!!  ... and {len(shortfalls) - 10} more line(s) not listed")
        print("!" * 78)
        if not a.allow_aborted:
            raise SystemExit(
                "  refusing to analyze a truncated run. Re-run it.\n"
                "  --allow-aborted analyzes it anyway, diagnostic only.")
        diagnostic_only_reasons.append("the experiment matrix is truncated")
        print("[WARNING: --allow-aborted over a truncated matrix. DIAGNOSTIC ONLY.]")

    arms, blocks, by_pos = defaultdict(list), defaultdict(list), defaultdict(list)
    by_block_pos, positions_by_arm_block = defaultdict(list), defaultdict(set)
    skipped_warmup = skipped_na = skipped_invalid = no_fg = unverified_validity = 0
    conditioning_no_fg = 0
    missing_validity_fields = set()
    labels_seen = defaultdict(set)
    reader = ((path, r) for path, body in per_file for r in csv.DictReader(body))
    for path, r in reader:
        phase = r.get("phase")
        # Include ONLY accepted measured launches. A rejected measured row is an
        # invalid observation, not an ordinary non-measured phase: the harness
        # writes it before its EXIT trap appends RUN ABORTED. Everything else --
        # warm-ups and the liveness-probe launch -- remains excluded by construction.
        if phase not in ("measure", "measure_rejected"):
            if (phase in ("probe", "warmup")
                    and r.get("label") in (a.baseline, a.treatment)
                    and r.get("foreground") == "NA"):
                conditioning_no_fg += 1
            skipped_warmup += 1
            continue
        # Belt and braces on top of the phase filter. These columns record the
        # harness's own verdict, so honour them rather than trusting the phase label
        # alone.
        labels_seen[path].add(r["label"])
        validity = {field: r.get(field)
                    for field in ("status", "launch_state", "foreground")}
        missing = [field for field, value in validity.items() if value in (None, "")]
        invalid = phase == "measure_rejected" \
            or validity["status"] not in (None, "", "ok") \
            or validity["launch_state"] not in (None, "", "COLD") \
            or validity["foreground"] not in (None, "", "ok", "NA")
        if invalid:
            # Only rows in the requested comparison can select observations out
            # of its interval. A pooled file may legitimately contain a third A/A
            # label; an invalid row there contributes nothing either way.
            if r["label"] in (a.baseline, a.treatment):
                skipped_invalid += 1
            continue
        if missing and r["label"] in (a.baseline, a.treatment):
            unverified_validity += 1
            missing_validity_fields.update(missing)
        # foreground=NA is accepted -- the end-of-window snapshot can legitimately
        # fail to read -- but it is the absence of evidence, not evidence of a clean
        # window, and it is also what the harness records when its mid-window check
        # could not run (no `Displayed` anchor line, ALLOW_NO_DISPLAYED_MARKER=1).
        # Counted and reported here so the operator sees it whatever metric is being
        # analyzed; the displayed/ttfd NA warning only fires when that IS the metric.
        if (r.get("foreground") == "NA"
                and r["label"] in (a.baseline, a.treatment)):
            no_fg += 1
        v = parse_ms(r.get(a.metric))
        if v is None:
            # Only the two arms under analysis. A pooled file may carry a third
            # label (an A/A cell, a renamed arm) whose rows could never have
            # contributed; counting its NAs blocked the whole run and the refusal
            # called them "otherwise eligible", which they were not.
            if r["label"] in (a.baseline, a.treatment):
                skipped_na += 1
            continue
        arms[r["label"]].append(v)
        blk = r["block"] if len(per_file) == 1 else f"{path}#{r['block']}"
        blocks[(r["label"], blk)].append(v)
        if r["label"] in (a.baseline, a.treatment):
            pos = r.get("pos_in_block")
            normalized_pos = "<missing>" if pos in (None, "", "0") else pos
            positions_by_arm_block[(r["label"], blk)].add(normalized_pos)
            if pos in ("1", "2"):
                by_pos[pos].append(v)
                by_block_pos[(blk, pos)].append(v)

    if skipped_warmup:
        print(f"[excluded {skipped_warmup} non-measured rows (warm-ups and liveness probes)]")
    if conditioning_no_fg:
        print(f"[WARNING: {conditioning_no_fg} selected-arm conditioning launch(es) carry")
        print(" foreground=NA: nothing proves the app owned the screen while preparing")
        print(" the permission, migration and JIT/profile state inherited by measurements.]")
        diagnostic_only_reasons.append(
            f"{conditioning_no_fg} conditioning launch(es) have foreground=NA evidence")
    if unverified_validity:
        fields = ", ".join(sorted(missing_validity_fields))
        print(f"[WARNING: {unverified_validity} selected-arm measured launch row(s) lack")
        print(f" launch-validity evidence ({fields}). Missing is not a passing verdict.]")
        diagnostic_only_reasons.append(
            f"{unverified_validity} measured launch(es) lack {fields} evidence")
    if skipped_invalid:
        print(f"[WARNING: {skipped_invalid} measured launch row(s) were rejected or failed the"
              f" harness's status/LaunchState/foreground checks and were EXCLUDED. A completed run"
              f" should never contain these -- treat this CSV as an aborted run.]")
        if not a.allow_aborted:
            raise SystemExit(
                f"refusing to report after excluding {skipped_invalid} invalid measured "
                "launch(es).\n"
                "  Launch failure or foreground contamination can correlate with startup time;\n"
                "  complete-case analysis can therefore manufacture an improvement. Re-run the\n"
                "  experiment. For investigation only, pass --allow-aborted; the primary\n"
                "  interval will remain suppressed.")
        diagnostic_only_reasons.append(
            f"{skipped_invalid} invalid measured launch(es) were excluded")
        print("[WARNING: --allow-aborted with invalid rows. DIAGNOSTIC ONLY. Do not report it.]")
    if skipped_na:
        print(f"[WARNING: {skipped_na} launches had no {a.metric} value (NA). Investigate "
              f"before trusting this run -- do not ignore.]")
        if not a.allow_missing_endpoint:
            raise SystemExit(
                f"refusing to report --metric {a.metric}: {skipped_na} otherwise eligible "
                "measured launch(es) have no endpoint value.\n"
                "  Dropping them is a complete-case analysis, and endpoint missingness can be\n"
                "  outcome-dependent: a launch that exceeds the collection window is both slow\n"
                "  and NA. Removing precisely those launches can manufacture an improvement.\n"
                "  Fix the endpoint/collection window and re-run. For investigation only, pass\n"
                "  --allow-missing-endpoint; the primary interval will remain suppressed.")
        print("[WARNING: --allow-missing-endpoint. DIAGNOSTIC ONLY. The selected endpoint]")
        print("[         is incomplete, so no primary confidence interval or result is reportable.]")
    if no_fg:
        print(f"[WARNING: {no_fg} measured launch(es) carry foreground=NA: nothing proves the"
              f" app owned]")
        print("[         the screen for the whole measured window. A dialog that took the"
              " foreground]")
        print("[         and handed it back inside the window looks exactly like a clean"
              " launch here.]")
        diagnostic_only_reasons.append(
            f"{no_fg} measured launch(es) have foreground=NA evidence")

    # A requested label missing from a file means that file contributed nothing,
    # while the header line above lists it among the pooled inputs. That is the
    # renamed-label case, and it is silent: the interval is real, it just is not the
    # experiment the command line describes.
    if len(per_file) > 1:
        empty = [pth for pth, labels in labels_seen.items()
                 if a.baseline not in labels or a.treatment not in labels]
        missing_files = [pth for pth, _ in per_file if pth not in labels_seen]
        empty += missing_files
        if empty:
            detail = "\n".join(f"    {pth}: has "
                               f"{sorted(labels_seen.get(pth, set())) or 'no measured rows'}"
                               for pth in empty)
            if not a.allow_mixed:
                raise SystemExit(
                    f"refusing to pool: {len(empty)} of {len(per_file)} file(s) contain no "
                    f"row\nlabelled {a.baseline!r} or {a.treatment!r}:\n" + detail
                    + f"\n  Those files contribute NOTHING to this result, which is reported"
                      f"\n  as if it covered all {len(per_file)}. Pass the labels those runs"
                      f"\n  actually used, analyze them separately, or --allow-mixed to"
                      f"\n  proceed on the subset that does match.")
            print(f"[WARNING: --allow-mixed, {len(empty)} file(s) contribute no rows to this"
                  " result:]")
            print(detail)

    found = sorted(arms)
    if a.baseline not in arms or a.treatment not in arms:
        raise SystemExit(f"missing arm(s) {a.baseline!r}/{a.treatment!r}; "
                         f"CSV contains: {found}")
    A, B = arms[a.baseline], arms[a.treatment]
    for lbl, v in ((a.baseline, A), (a.treatment, B)):
        if len(v) < 2:
            raise SystemExit(f"arm {lbl!r} has {len(v)} usable launch(es); need >= 2.")

    print("=" * 78)
    print(f"COLD START A/B  --  {', '.join(a.csv)}")
    _WINDOW = {"total_ms": "time to initial display (first frame)",
               "displayed": "time to initial display (logcat Displayed)",
               "ttfd": "time to FULLY DRAWN (reportFullyDrawn)",
               "app_trace_ms": "the host app's OWN metric (APP_TRACE_REGEX) -- "
                               "window defined by the app, not by us"}
    print(f"metric: {a.metric}  --  {_WINDOW[a.metric]}")
    if a.metric in ("total_ms", "displayed"):
        print("note: ends at first frame. On React Native / Flutter apps much of startup")
        print("      follows, so this understates any SDK cost landing after it. If the")
        print("      app calls reportFullyDrawn(), also run with --metric ttfd.")
    print("=" * 78)
    print(f"{'arm':12s} {'n':>3s} {'mean':>8s} {'median':>8s} {'sd':>7s} "
          f"{'min':>6s} {'max':>6s} {'IQR':>7s}")
    print("-" * 78)
    for lbl, v in ((a.baseline, A), (a.treatment, B)):
        qs = st.quantiles(v, n=4) if len(v) >= 4 else [min(v), st.median(v), max(v)]
        print(f"{lbl:12s} {len(v):3d} {st.mean(v):8.1f} {st.median(v):8.1f} "
              f"{st.stdev(v):7.1f} {min(v):6.0f} {max(v):6.0f} {qs[2]-qs[0]:7.1f}")

    # ---- PRIMARY: paired block-level delta -------------------------------------
    bset = sorted({b for (_, b) in blocks}, key=lambda s: (len(s), s))
    deltas, delta_blocks, baseline_block_means = [], [], []
    print("\n--- per-block deltas (the primary unit of analysis) ---")
    for blk in bset:
        va, vb = blocks.get((a.baseline, blk)), blocks.get((a.treatment, blk))
        if va and vb and len(va) >= 1 and len(vb) >= 1:
            baseline_block_mean = st.mean(va)
            d = st.mean(vb) - baseline_block_mean
            deltas.append(d)
            delta_blocks.append(blk)
            baseline_block_means.append(baseline_block_mean)
            print(f"  block {blk:>3s}  n={len(va):2d}/{len(vb):<2d}  "
                  f"baseline={baseline_block_mean:7.1f}  treatment={st.mean(vb):7.1f}  "
                  f"delta={d:+7.1f}")

    # Counterbalancing is a property of the blocks that CONTRIBUTE, not of the run as
    # designed. Each block delta is (treatment - baseline) + order for a block where
    # baseline ran first, and (treatment - baseline) - order where treatment did, so
    # `order` cancels only when each arm ran first equally often here. Missing values
    # -- sparse `ttfd`, a rejected launch -- drop whole blocks, and the survivors can
    # be lopsided even though the harness alternated the order faithfully.
    # A position-1 row alone does not identify the first arm. Validate the whole
    # selected pair so duplicated, mixed or same-position cells cannot manufacture
    # counterbalancing through last-row-wins assignment. Unselected labels never
    # participate in this evidence.
    valid_first_arm, malformed_position_pairs = {}, []
    for blk in delta_blocks:
        baseline_positions = positions_by_arm_block[(a.baseline, blk)]
        treatment_positions = positions_by_arm_block[(a.treatment, blk)]
        if baseline_positions == {"<missing>"} or treatment_positions == {"<missing>"}:
            continue
        if baseline_positions == {"1"} and treatment_positions == {"2"}:
            valid_first_arm[blk] = a.baseline
        elif baseline_positions == {"2"} and treatment_positions == {"1"}:
            valid_first_arm[blk] = a.treatment
        else:
            malformed_position_pairs.append(
                (blk, baseline_positions, treatment_positions)
            )

    pri_firsts = Counter(valid_first_arm[b] for b in delta_blocks if b in valid_first_arm)
    # Denominator is the number of blocks whose first arm is KNOWN, not len(deltas):
    # comparing with the larger figure could report balance even when some blocks
    # carry no pos_in_block.
    pri_known = sum(pri_firsts.values())

    print("\n--- PRIMARY ENDPOINT: paired block-level delta ---")
    if diagnostic_only_reasons:
        print("  NOT REPORTABLE: this analysis is diagnostic only because:")
        for reason in diagnostic_only_reasons:
            print(f"  - {reason}")
        print("  The per-block deltas above use only surviving observations and are not")
        print("  an estimate of the registered experiment.")
    elif skipped_na:
        print(f"  NOT REPORTABLE: {skipped_na} otherwise eligible measured launch(es) have")
        print(f"  no {a.metric} endpoint. The per-block deltas below use only observed")
        print("  survivors and are diagnostics, not an estimate of the registered experiment.")
    elif malformed_position_pairs:
        print(f"  NOT REPORTABLE: {len(malformed_position_pairs)} contributing block(s) do")
        print("  not record one stable, complementary pos_in_block pair for the selected")
        print("  arms. Expected exactly {1}/{2} or {2}/{1}; observed:")
        for blk, baseline_positions, treatment_positions in malformed_position_pairs[:5]:
            print(f"    block {blk}: {a.baseline}={sorted(baseline_positions)}, "
                  f"{a.treatment}={sorted(treatment_positions)}")
        if len(malformed_position_pairs) > 5:
            print(f"    ... and {len(malformed_position_pairs) - 5} more block(s)")
        print("  Counterbalancing is therefore not verifiable. The per-block deltas above")
        print("  are retained as diagnostics; fix the position evidence and re-run.")
    elif len(deltas) >= 3 and pri_known < len(deltas):
        print(f"  NOT REPORTABLE: only {pri_known} of {len(deltas)} contributing block(s)")
        print("  record which arm ran first. Counterbalancing is therefore not verifiable")
        print("  for the observations entering this estimate, and an order effect may be")
        print("  folded into the apparent treatment effect. The per-block deltas above are")
        print("  retained as diagnostics; re-run with complete pos_in_block evidence.")
    elif (len(deltas) >= 3
          and pri_firsts[a.baseline] != pri_firsts[a.treatment]):
        print("  NOT REPORTABLE: contributing blocks are not counterbalanced:")
        print(f"  first-arm counts are {dict(pri_firsts)}. Equal baseline-first and")
        print("  treatment-first counts are required; otherwise part of any order effect")
        print("  remains in the treatment estimate. The per-block deltas above are retained")
        print("  as diagnostics; restore the ABBA schedule and re-run.")
    elif len(deltas) < 3:
        print(f"  NOT ESTIMABLE: {len(deltas)} complete block(s). A paired interval needs")
        print("  >= 3 blocks (at 2 blocks t_crit(df=1) = 12.7, which cannot support any")
        print("  significance claim). Re-run with more blocks, e.g. `... 4 8`.")
        print("  Treat this run as diagnostic only.")
    elif st.stdev(deltas) == 0:
        print(f"  NOT ESTIMABLE: all {len(deltas)} contributing block deltas are "
              f"{deltas[0]:+.1f} ms,")
        print("  so their sample variance is zero. Integer-millisecond observations in a")
        print("  small run do not establish zero population variance or a zero detectable")
        print("  effect. No primary CI, MDE or significance verdict is reported; re-run")
        print("  with more counterbalanced blocks.")
    else:
        k = len(deltas)
        m = st.mean(deltas)
        sd_b = st.stdev(deltas)
        se_b = sd_b / math.sqrt(k)
        tc = t_crit(k - 1)
        lo, hi = m - tc * se_b, m + tc * se_b
        # Match the primary estimand: every contributing block has equal weight,
        # even when pooled files used different numbers of launches per cell.
        baseline_mean = st.mean(baseline_block_means)
        relative_effect = (
            f"{100 * m / baseline_mean:+.2f}% of baseline"
            if baseline_mean != 0
            else "relative % undefined: baseline mean is 0"
        )
        print(f"  blocks                {k}")
        print(f"  mean of block deltas  {m:+8.1f} ms   ({relative_effect})")
        print(f"  between-block sd      {sd_b:8.1f} ms   (SE {se_b:.2f}, t_crit(df={k-1}) {tc})")
        print(f"  95% CI                [{lo:+.1f}, {hi:+.1f}] ms")
        print(f"  median of block deltas {st.median(deltas):+8.1f} ms   "
              f"(the descriptive median for this design)")
        print(f"  MDE at {k} blocks       ~{mde(sd_b, k):.0f} ms  "
              f"(80% power, alpha=.05, two-sided)")
        for target in (10, 25):
            need = blocks_for(sd_b, target)
            shown = f"~{need}" if need else "> 200 (not worth chasing at this sd)"
            print(f"  blocks to resolve {target:>2d} ms  {shown}")
        print()
        if lo <= 0 <= hi:
            print("  => No significant difference. Upper bound on any real")
            print(f"     regression is ~{hi:.0f} ms.")
        else:
            print(f"  => Significant. Best estimate {m:+.0f} ms, plausible range "
                  f"[{lo:+.0f}, {hi:+.0f}] ms.")

    # ---- DIAGNOSTICS ----------------------------------------------------------
    d_mean, se, df, t = welch(A, B)
    tc_u = t_crit(df)
    print("\n--- [diagnostic] unpaired Welch over pooled launches ---")
    print("  Anti-conservative: ignores between-block variance. Shown for contrast")
    print("  with the primary endpoint above, not for reporting.")
    print(f"  mean delta {d_mean:+.1f} ms   pooled median difference "
          f"{st.median(B)-st.median(A):+.1f} ms")
    print("  NB: 'pooled median difference' is median(treatment)-median(baseline) over all")
    print("      launches. It is NOT a median treatment effect and must not be quoted as")
    print("      one; the design's unit is the block. See the primary endpoint above.")
    if df < 3:
        print(f"  CI suppressed: Welch df={df:.1f} < 3, interval not meaningful.")
    else:
        print(f"  95% CI [{d_mean-tc_u*se:+.1f}, {d_mean+tc_u*se:+.1f}] ms  "
              f"(df {df:.1f}, t_crit {tc_u})")
    print(f"  permutation p (mean)   {fmt_p(perm_p(A, B, st.mean))}")
    print(f"  permutation p (median) {fmt_p(perm_p(A, B, st.median))}")

    # ---- order effect ---------------------------------------------------------
    # PAIRED ON BLOCKS, for exactly the reason the primary endpoint is: an
    # unpaired Welch over pooled position-1 vs position-2 launches commits the
    # independence violation this module rejects two sections above, and can
    # manufacture an order effect out of cell-level shifts.
    #
    # Under ABBA the per-block (2nd - 1st) delta also cancels the TREATMENT
    # effect, because the arm that runs first alternates: odd blocks give
    # (treatment - baseline) + order, even blocks give (baseline - treatment) +
    # order. Averaged over an equal number of each, only `order` survives -- so
    # the balance of first-arms is checked before the result is trusted.
    print("\n--- [diagnostic] order effect (position within block), paired on blocks ---")
    # Balance is counted over the SAME blocks that contribute a delta, not over
    # every block in the run. A block missing one position (sparse `ttfd`, say) is
    # dropped from order_deltas, so counting first-arms across all blocks could
    # report a perfectly balanced run whose contributing subset is lopsided -- and
    # then the uncancelled treatment effect shows up as an "order effect".
    order_deltas, contributing = [], []
    for blk in bset:
        if blk not in valid_first_arm:
            continue
        v1, v2 = by_block_pos.get((blk, "1")), by_block_pos.get((blk, "2"))
        if v1 and v2:
            order_deltas.append(st.mean(v2) - st.mean(v1))
            contributing.append(blk)
    firsts = Counter(valid_first_arm[b] for b in contributing)

    if len(by_pos) < 2 or len(bset) < 2:
        print("  NOT ESTIMABLE: arm and position are confounded (needs >= 2 blocks with")
        print("  counterbalanced order). With a single block, arm A is always first and")
        print("  arm B always second, so any 'order effect' IS the treatment effect.")
    elif len(order_deltas) < 3:
        print(f"  NOT ESTIMABLE: {len(order_deltas)} block(s) have both positions. A paired")
        print("  interval needs >= 3, the same rule as the primary endpoint.")
    elif len(firsts) != 2 or len(set(firsts.values())) != 1:
        # The treatment effect only cancels out of (2nd - 1st) when each arm runs
        # first equally often ACROSS THE CONTRIBUTING BLOCKS. Anything else leaves a
        # fraction of it in the estimate, and the single-key case -- one arm always
        # first -- is fully confounded, which is precisely when a warning would be
        # least likely to be heeded. Suppress rather than print a contaminated number.
        print(f"  NOT ESTIMABLE: among the {len(order_deltas)} blocks with both positions,")
        print(f"  the first-arm counts are {dict(firsts)}.")
        if len(firsts) < 2:
            print("  Only one arm ever ran first there, so 'order effect' IS the treatment")
            print("  effect -- they cannot be told apart.")
        else:
            print("  Each arm must run first equally often for the treatment effect to")
            print("  cancel out of the 2nd-minus-1st deltas; here it does not.")
        print("  (Usually caused by missing values dropping whole positions from some")
        print("   blocks -- check the NA warning above.)")
    else:
        p1, p2 = by_pos.get("1", []), by_pos.get("2", [])
        print(f"  ran 1st n={len(p1):3d} mean={st.mean(p1):7.1f} | "
              f"ran 2nd n={len(p2):3d} mean={st.mean(p2):7.1f}   (descriptive)")
        print(f"  first-arm balance {dict(firsts)} -- treatment effect cancels")
        k = len(order_deltas)
        m = st.mean(order_deltas)
        se = st.stdev(order_deltas) / math.sqrt(k)
        tc = t_crit(k - 1)
        lo, hi = m - tc * se, m + tc * se
        print(f"  2nd-minus-1st = {m:+.1f} ms over {k} blocks  95% CI "
              f"[{lo:+.1f}, {hi:+.1f}]  (between-block sd {st.stdev(order_deltas):.1f})")
        if abs(m) > 5 and not (lo <= 0 <= hi):
            print("  !! Ordering moves the measurement independently of the build.")
            print("     ABBA cancels this in the paired primary endpoint; it would")
            print("     masquerade as a real effect under a fixed A-then-B order.")
        else:
            print("  no significant order effect -- counterbalancing is holding.")


if __name__ == "__main__":
    main()
