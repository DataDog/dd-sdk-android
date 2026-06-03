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

    95% CI of difference of means:     [-383; -246] or [-63.07%; -40.53%] (significant difference)
    95% CI of difference of medians:   [-479; -172] or [-77.20%; -27.87%] (significant difference)

                  Baseline                       Candidate

    Range:        449 .. 736                     282 .. 309

    Mean ± SD:    608 ± 110                      293 ± 8
    Median ± MAD: 620 ± 94                       294 ± 2

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

    95% CI of difference of means:     [+10.357ms; +10.911ms] or [+324.64%; +342.01%] (significant difference)
    95% CI of difference of medians:   [+10.182ms; +11.274ms] or [+325.56%; +360.45%] (significant difference)

                  Baseline                       Candidate

    Range:        2.696ms .. 3.734ms             13.513ms .. 14.028ms

    Mean ± SD:    3.190ms ± 0.414ms              13.824ms ± 0.169ms
    Median ± MAD: 3.128ms ± 0.357ms              13.856ms ± 0.105ms

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

    95% CI of difference of means:     [+8.991ms; +10.927ms] or [+51.63%; +62.75%] (significant difference)
    95% CI of difference of medians:   [+8.365ms; +11.585ms] or [+48.71%; +67.45%] (significant difference)

                  Baseline                       Candidate

    Range:        15.919ms .. 19.310ms           26.475ms .. 29.095ms

    Mean ± SD:    17.415ms ± 1.377ms             27.374ms ± 0.738ms
    Median ± MAD: 17.176ms ± 1.146ms             27.151ms ± 0.126ms

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

    95% CI of difference of means:     [+8.505ms; +11.546ms] or [+40.93%; +55.56%] (significant difference)
    95% CI of difference of medians:   [+7.851ms; +13.099ms] or [+38.39%; +64.06%] (significant difference)

                  Baseline                       Candidate

    Range:        18.364ms .. 23.734ms           28.489ms .. 33.504ms

    Mean ± SD:    20.779ms ± 1.958ms             30.805ms ± 1.478ms
    Median ± MAD: 20.450ms ± 1.841ms             30.925ms ± 0.990ms

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

    95% CI of difference of means:     [+5.074ms; +11.141ms] or [+15.43%; +33.87%] (significant difference)
    95% CI of difference of medians:   [+1.709ms; +12.958ms] or [+5.07%; +38.43%] (significant difference)

                  Baseline                       Candidate

    Range:        26.512ms .. 37.367ms           37.272ms .. 46.787ms

    Mean ± SD:    32.889ms ± 3.793ms             40.996ms ± 3.094ms
    Median ± MAD: 33.719ms ± 2.685ms             41.052ms ± 2.669ms

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

    95% CI of difference of means:     [+3.714ms; +5.514ms] or [-141.93%; -210.70%] (significant difference)
    95% CI of difference of medians:   [+4.239ms; +5.779ms] or [-140.45%; -191.48%] (significant difference)

                  Baseline                       Candidate

    Range:        -3.545ms .. 0.789ms            0.916ms .. 2.988ms

    Mean ± SD:    -2.617ms ± 1.303ms             1.997ms ± 0.640ms
    Median ± MAD: -3.018ms ± 0.427ms             1.991ms ± 0.222ms

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

    95% CI of difference of means:     [+1.641ms; +4.051ms] or [+14.48%; +35.75%] (significant difference)
    95% CI of difference of medians:   [+1.401ms; +4.620ms] or [+12.62%; +41.62%] (significant difference)

                  Baseline                       Candidate

    Range:        8.802ms .. 13.818ms            12.608ms .. 15.940ms

    Mean ± SD:    11.333ms ± 1.711ms             14.179ms ± 0.924ms
    Median ± MAD: 11.102ms ± 1.278ms             14.112ms ± 0.328ms

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

    95% CI of difference of means:     [-0.230ms; +3.809ms] or [-1.41%; +23.28%] (no difference)
    95% CI of difference of medians:   [-2.260ms; +4.997ms] or [-13.37%; +29.56%] (no difference)

                  Baseline                       Candidate

    Range:        11.458ms .. 20.225ms           16.312ms .. 20.556ms

    Mean ± SD:    16.361ms ± 2.988ms             18.150ms ± 1.299ms
    Median ± MAD: 16.905ms ± 2.131ms             18.273ms ± 0.903ms

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

    95% CI of difference of means:     [-1.902ms; +9.995ms] or [-6.24%; +32.82%] (no difference)
    95% CI of difference of medians:   [-5.894ms; +13.434ms] or [-20.09%; +45.79%] (no difference)

                  Baseline                       Candidate

    Range:        26.902ms .. 34.866ms           23.377ms .. 52.184ms

    Mean ± SD:    30.455ms ± 3.121ms             34.502ms ± 9.076ms
    Median ± MAD: 29.335ms ± 1.658ms             33.105ms ± 6.978ms

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
group                                                         metric          mean                  median               
scenario:frameTimingWithSessionReplay:frameCount              iterations      significantly better  significantly better 
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P50  execution_time  significantly worse   significantly worse  
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P90  execution_time  significantly worse   significantly worse  
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P95  execution_time  significantly worse   significantly worse  
scenario:frameTimingWithSessionReplay:frameDurationCpuMs:P99  execution_time  significantly worse   significantly worse  
scenario:frameTimingWithSessionReplay:frameOverrunMs:P50      execution_time  significantly better  significantly better 
scenario:frameTimingWithSessionReplay:frameOverrunMs:P90      execution_time  significantly worse   significantly worse  
scenario:frameTimingWithSessionReplay:frameOverrunMs:P95      execution_time  no difference         no difference        
scenario:frameTimingWithSessionReplay:frameOverrunMs:P99      execution_time  no difference         no difference        

