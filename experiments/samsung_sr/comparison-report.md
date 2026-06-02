# Baseline

  className=com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark
  variant=no_regression

# Candidate

  className=com.datadog.benchmark.macrobenchmark.SessionReplayRumAutoBenchmark
  variant=with_regression

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameCount
-------------------------------------------------------------------------------

 iterations:

  * Comparison:

    95% CI of difference of means:     [-98; +21] or [-19.13%; +4.09%] (no difference)
    95% CI of difference of medians:   [-96; -54] or [-17.52%; -9.98%] (significant difference)

                  Baseline                       Candidate

    Range:        67 .. 602                      453 .. 529

    Mean ± SD:    517 ± 135                      478 ± 20
    Median ± MAD: 549 ± 18                       473 ± 12

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [+6.215ms; +6.537ms] or [+285.23%; +300.01%] (no difference)
    95% CI of difference of medians:   [+6.355ms; +6.596ms] or [+300.36%; +311.74%] (no difference)

                  Baseline                       Candidate

    Range:        2.024ms .. 3.417ms             8.178ms .. 9.051ms

    Mean ± SD:    2.179ms ± 0.298ms              8.555ms ± 0.215ms
    Median ± MAD: 2.116ms ± 0.049ms              8.592ms ± 0.106ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [+2.342ms; +2.960ms] or [+13.59%; +17.17%] (significant difference)
    95% CI of difference of medians:   [+2.116ms; +3.026ms] or [+12.15%; +17.38%] (significant difference)

                  Baseline                       Candidate

    Range:        15.843ms .. 17.898ms           18.637ms .. 20.578ms

    Mean ± SD:    17.234ms ± 0.501ms             19.885ms ± 0.496ms
    Median ± MAD: 17.406ms ± 0.249ms             19.976ms ± 0.378ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [+1.581ms; +2.509ms] or [+7.72%; +12.25%] (significant difference)
    95% CI of difference of medians:   [+1.777ms; +2.713ms] or [+8.77%; +13.38%] (significant difference)

                  Baseline                       Candidate

    Range:        19.527ms .. 23.419ms           21.589ms .. 23.671ms

    Mean ± SD:    20.482ms ± 0.876ms             22.528ms ± 0.595ms
    Median ± MAD: 20.274ms ± 0.368ms             22.519ms ± 0.346ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-1.199ms; +14.438ms] or [-3.59%; +43.24%] (no difference)
    95% CI of difference of medians:   [+9.181ms; +11.561ms] or [+31.18%; +39.26%] (significant difference)

                  Baseline                       Candidate

    Range:        26.415ms .. 108.790ms          37.705ms .. 41.420ms

    Mean ± SD:    33.393ms ± 17.812ms            40.013ms ± 0.992ms
    Median ± MAD: 29.447ms ± 1.229ms             39.818ms ± 0.689ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Measurements are autocorrelated.
       
       Autocorrelation is present for lags 3..4.
       
       The measurements are not independent, thus confidence intervals
       may be less precise.

    2. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Measurements are autocorrelated.
       
       Autocorrelation is present for lags 3..4.
       
       The measurements are not independent, thus confidence intervals
       may be less precise.

    2. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P50
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [+6.060ms; +6.535ms] or [-129.44%; -139.58%] (no difference)
    95% CI of difference of medians:   [+6.161ms; +6.656ms] or [-128.98%; -139.36%] (no difference)

                  Baseline                       Candidate

    Range:        -5.172ms .. -2.889ms           1.187ms .. 2.151ms

    Mean ± SD:    -4.682ms ± 0.489ms             1.616ms ± 0.233ms
    Median ± MAD: -4.776ms ± 0.265ms             1.632ms ± 0.169ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P90
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [+4.067ms; +4.501ms] or [+37.93%; +41.97%] (significant difference)
    95% CI of difference of medians:   [+4.014ms; +4.604ms] or [+37.43%; +42.94%] (significant difference)

                  Baseline                       Candidate

    Range:        10.153ms .. 11.314ms           14.343ms .. 15.694ms

    Mean ± SD:    10.723ms ± 0.335ms             15.008ms ± 0.364ms
    Median ± MAD: 10.724ms ± 0.226ms             15.032ms ± 0.226ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P95
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [+4.970ms; +6.356ms] or [+35.28%; +45.12%] (significant difference)
    95% CI of difference of medians:   [+4.964ms; +6.414ms] or [+36.05%; +46.57%] (no difference)

                  Baseline                       Candidate

    Range:        12.826ms .. 16.385ms           17.399ms .. 22.093ms

    Mean ± SD:    14.087ms ± 0.970ms             19.750ms ± 1.248ms
    Median ± MAD: 13.771ms ± 0.392ms             19.460ms ± 0.620ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P99
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-16.221ms; +19.519ms] or [-50.34%; +60.57%] (no difference)
    95% CI of difference of medians:   [+10.551ms; +12.459ms] or [+46.43%; +54.82%] (significant difference)

                  Baseline                       Candidate

    Range:        19.238ms .. 205.006ms          31.938ms .. 34.922ms

    Mean ± SD:    32.225ms ± 40.766ms            33.875ms ± 0.850ms
    Median ± MAD: 22.726ms ± 1.043ms             34.231ms ± 0.415ms

    Sample size:  20                             20
    Runs:         20                             20

  * Baseline:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 20, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
 SUMMARY
-------------------------------------------------------------------------------
group                                                         metric          mean                 median               
scenario:frameTimingWithSessionReplay:frameCount              iterations      unstable             significantly better 
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50  execution_time  unstable             unstable             
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90  execution_time  significantly worse  significantly worse  
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95  execution_time  significantly worse  significantly worse  
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99  execution_time  unstable             significantly worse  
scenario:frameTimingWithSessionReplay:frameOverrunMs:P50      execution_time  unstable             unstable             
scenario:frameTimingWithSessionReplay:frameOverrunMs:P90      execution_time  significantly worse  significantly worse  
scenario:frameTimingWithSessionReplay:frameOverrunMs:P95      execution_time  significantly worse  unstable             
scenario:frameTimingWithSessionReplay:frameOverrunMs:P99      execution_time  unstable             significantly worse  

