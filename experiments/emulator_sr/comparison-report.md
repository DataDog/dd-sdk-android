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

    95% CI of difference of means:     [-11; +6] or [-2.97%; +1.77%] (no difference)
    95% CI of difference of medians:   [-23; +8] or [-6.14%; +2.26%] (no difference)

                  Baseline                       Candidate

    Range:        372 .. 397                     367 .. 402

    Mean ± SD:    384 ± 9                        382 ± 11
    Median ± MAD: 386 ± 9                        378 ± 7

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-109.027µs; +377.432µs] or [-3.17%; +10.97%] (no difference)
    95% CI of difference of medians:   [-152.382µs; +243.132µs] or [-4.46%; +7.12%] (no difference)

                  Baseline                       Candidate

    Range:        3.163ms .. 4.202ms             3.397ms .. 4.250ms

    Mean ± SD:    3.441ms ± 0.290ms              3.575ms ± 0.265ms
    Median ± MAD: 3.414ms ± 0.114ms              3.459ms ± 0.054ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-3.678ms; +1.489ms] or [-61.41%; +24.87%] (no difference)
    95% CI of difference of medians:   [-703.830µs; +760.971µs] or [-14.95%; +16.16%] (no difference)

                  Baseline                       Candidate

    Range:        4.157ms .. 17.718ms            4.469ms .. 5.905ms

    Mean ± SD:    5.989ms ± 4.144ms              4.894ms ± 0.447ms
    Median ± MAD: 4.708ms ± 0.269ms              4.737ms ± 0.177ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-3515.367µs; +1523.717µs] or [-48.63%; +21.08%] (no difference)
    95% CI of difference of medians:   [-721.531µs; +1299.002µs] or [-12.16%; +21.89%] (no difference)

                  Baseline                       Candidate

    Range:        5.240ms .. 18.575ms            5.496ms .. 7.302ms

    Mean ± SD:    7.228ms ± 4.029ms              6.233ms ± 0.539ms
    Median ± MAD: 5.935ms ± 0.561ms              6.223ms ± 0.352ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-5.875ms; +1.092ms] or [-27.43%; +5.10%] (no difference)
    95% CI of difference of medians:   [-6.139ms; +0.587ms] or [-29.14%; +2.79%] (no difference)

                  Baseline                       Candidate

    Range:        16.722ms .. 30.341ms           14.576ms .. 28.268ms

    Mean ± SD:    21.418ms ± 3.956ms             19.026ms ± 3.992ms
    Median ± MAD: 21.065ms ± 1.902ms             18.289ms ± 0.455ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P50
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-146.670µs; +450.044µs] or [+1.18%; -3.62%] (significant difference)
    95% CI of difference of medians:   [-138.552µs; +347.107µs] or [+1.11%; -2.78%] (significant difference)

                  Baseline                       Candidate

    Range:        -12.754ms .. -11.442ms         -12.510ms .. -11.502ms

    Mean ± SD:    -12.427ms ± 0.368ms            -12.275ms ± 0.311ms
    Median ± MAD: -12.473ms ± 0.109ms            -12.369ms ± 0.128ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P90
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-3.636ms; +1.399ms] or [+38.06%; -14.65%] (no difference)
    95% CI of difference of medians:   [-664.234µs; +805.165µs] or [+6.07%; -7.36%] (no difference)

                  Baseline                       Candidate

    Range:        -11.344ms .. 1.848ms           -11.135ms .. -9.680ms

    Mean ± SD:    -9.551ms ± 4.034ms             -10.670ms ± 0.474ms
    Median ± MAD: -10.936ms ± 0.327ms            -10.866ms ± 0.103ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P95
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-3.644ms; +1.385ms] or [+46.31%; -17.59%] (no difference)
    95% CI of difference of medians:   [-926.247µs; +1172.192µs] or [+10.05%; -12.72%] (no difference)

                  Baseline                       Candidate

    Range:        -9.758ms .. 3.319ms            -9.901ms .. -7.323ms

    Mean ± SD:    -7.870ms ± 3.975ms             -9.000ms ± 0.811ms
    Median ± MAD: -9.213ms ± 0.519ms             -9.090ms ± 0.299ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
# scenario:frameTimingWithSessionReplay:frameOverrunMs:P99
-------------------------------------------------------------------------------

 execution_time:

  * Comparison:

    95% CI of difference of means:     [-9.820ms; +0.611ms] or [-98.73%; +6.14%] (no difference)
    95% CI of difference of medians:   [-10.885ms; +2.999ms] or [-144.57%; +39.83%] (no difference)

                  Baseline                       Candidate

    Range:        2.803ms .. 18.990ms            1.444ms .. 18.477ms

    Mean ± SD:    9.946ms ± 6.526ms              5.341ms ± 5.312ms
    Median ± MAD: 7.529ms ± 3.572ms              3.586ms ± 2.136ms

    Sample size:  10                             10
    Runs:         10                             10

  * Baseline:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

  * Candidate:

    1. WARNING: Sample size is 10, which is lower than 105.
       
       The minimal sample size in case of normal distribution to achieve significance
       level of 0.05 for difference of means with effect size Cohen's d = 0.5 must be at
       least 105.
       
       The conclusions from confidence intervals may be invalid.

-------------------------------------------------------------------------------
 SUMMARY
-------------------------------------------------------------------------------
group                                                         metric          mean                 median              
scenario:frameTimingWithSessionReplay:frameCount              iterations      no difference        no difference       
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50  execution_time  unstable             unstable            
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90  execution_time  unstable             unstable            
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95  execution_time  unstable             unstable            
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99  execution_time  unstable             unstable            
scenario:frameTimingWithSessionReplay:frameOverrunMs:P50      execution_time  significantly worse  significantly worse 
scenario:frameTimingWithSessionReplay:frameOverrunMs:P90      execution_time  unstable             unstable            
scenario:frameTimingWithSessionReplay:frameOverrunMs:P95      execution_time  unstable             unstable            
scenario:frameTimingWithSessionReplay:frameOverrunMs:P99      execution_time  unstable             unstable            

