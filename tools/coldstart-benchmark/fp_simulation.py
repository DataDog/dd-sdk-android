#!/usr/bin/env python3
# Unless explicitly stated otherwise all files in this repository are licensed
# under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
"""
Reproduces the false-positive table that justifies this harness's primary endpoint.

The claim under test
--------------------
`ab_stats.py` analyzes one delta per BLOCK and runs a paired test on those,
rather than pooling every launch into an unpaired test. The reason is that
launches inside one arm x block cell are not independent -- they share an
install, an AOT compilation, a thermal state and a page-cache state -- so an
unpaired test estimates the standard error from WITHIN-cell scatter only and
ignores the BETWEEN-cell component. That makes it anti-conservative.

"Anti-conservative" is easy to assert and easy to check, so this checks it.

The model
---------
Launch time = 0 (no true effect, by construction) + a per-cell shift drawn from
N(0, sigma_b) + per-launch noise from N(0, 11 ms). 11 ms is the pooled launch sd
from the reference A/A run. sigma_b sweeps 0, 2, 4 and 8 ms; 4 ms is an ordinary
between-block shift on a real device.

Both designs then compute a nominal-95% interval and we count how often it
excludes zero. A correct procedure lands at 5%.

Usage:
    ./fp_simulation.py                 # the published table
    ./fp_simulation.py --trials 50000  # tighter Monte-Carlo error
"""
import argparse
import math
import random
import statistics as st
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ab_stats import t_crit, welch  # noqa: E402  (path set above)

SD_WITHIN = 11.0
SIGMA_B = (0, 2, 4, 8)


def _cell(n_launches, shift, rng):
    return [rng.gauss(shift, SD_WITHIN) for _ in range(n_launches)]


def unpaired_rejects(n_blocks, n_launches, sigma_b, rng):
    """Pool every launch, Welch t-interval. What most tools report."""
    a, b = [], []
    for _ in range(n_blocks):
        a += _cell(n_launches, rng.gauss(0, sigma_b), rng)
        b += _cell(n_launches, rng.gauss(0, sigma_b), rng)
    d, se, df, _ = welch(a, b)
    tc = t_crit(df)
    return not (d - tc * se <= 0 <= d + tc * se)


def paired_rejects(n_blocks, n_launches, sigma_b, rng):
    """One delta per block, paired t-interval on those. What ab_stats.py reports.

    Each ARM x BLOCK cell draws its OWN shift, matching the model: every cell gets
    its own uninstall / install / AOT compile, so the thing that moves it is not
    shared with the other arm in that block. Drawing one shift per block and
    applying it to both arms would cancel it exactly in the delta, leaving sigma_b
    with no effect at all -- the paired column would read ~5% for every sigma_b
    whether or not the design actually worked, which proves nothing.
    """
    deltas = []
    for _ in range(n_blocks):
        deltas.append(st.mean(_cell(n_launches, rng.gauss(0, sigma_b), rng))
                      - st.mean(_cell(n_launches, rng.gauss(0, sigma_b), rng)))
    m = st.mean(deltas)
    se = st.stdev(deltas) / math.sqrt(n_blocks)
    tc = t_crit(n_blocks - 1)
    return not (m - tc * se <= 0 <= m + tc * se)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--trials", type=int, default=20_000)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    rng = random.Random(args.seed)

    designs = (
        ("2 blocks x 15 launches, unpaired", unpaired_rejects, 2, 15),
        ("8 blocks x 4 launches, paired", paired_rejects, 8, 4),
    )
    # +-1 Monte-Carlo standard error on a ~5% rate, so a reader can tell a real
    # difference from sampling noise without re-deriving it.
    mc_se = 100 * math.sqrt(0.05 * 0.95 / args.trials)
    print(f"true effect = 0, within-launch sd = {SD_WITHIN:g} ms, "
          f"{args.trials} trials (MC SE at 5% ~ {mc_se:.2f}pp)")
    print("false-positive rate of a nominal-95% interval\n")
    print(f"{'design':36s}" + "".join(f"{f'sigma_b={s}':>12s}" for s in SIGMA_B))
    for name, fn, nb, nl in designs:
        rates = [100 * sum(fn(nb, nl, s, rng) for _ in range(args.trials)) / args.trials
                 for s in SIGMA_B]
        print(f"{name:36s}" + "".join(f"{r:11.1f}%" for r in rates))
    print("\nA correct procedure sits at 5.0% in every column. The unpaired design")
    print("does not, and the gap widens with the between-block shift.")


if __name__ == "__main__":
    main()
