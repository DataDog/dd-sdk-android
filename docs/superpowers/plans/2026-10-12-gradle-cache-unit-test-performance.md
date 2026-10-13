# Gradle Cache and Unit-Test Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce `test:debug` wall time and repeated compilation without weakening test coverage or allowing untrusted branches to write shared build-cache entries.

**Architecture:** Measure the existing cache and task-execution behavior first, then optimize in two independent layers. GitLab cache remains responsible for downloaded Gradle distributions and dependency artifacts; a content-addressed Gradle remote build cache becomes responsible for task outputs. Unit-test parallelism is tuned separately through controlled worker-count and test-fork experiments.

**Tech Stack:** Gradle 9.4, Android Gradle Plugin 9, Kotlin, JUnit 5, GitLab CI, Datadog CI Visibility, `pup` CLI.

## Global Constraints

- Preserve every test currently run by `test:debug`; coverage-policy changes to `test:kover` require a separate decision and plan.
- Shared remote-cache writes are allowed only from protected branches or tags. Pull-request branches are read-only.
- Keep `org.gradle.configuration-cache=true`, `org.gradle.caching=true`, and `org.gradle.parallel=true`.
- Do not increase concurrency and cache behavior in the same experiment; each change needs an attributable before/after result.
- A candidate is accepted only after at least 20 representative PR runs, with no statistically meaningful increase in failures or OOMs.
- Do not commit until the user provides the Jira ticket. Every eventual commit title must begin with that exact ticket according to `AGENTS.md`.

## Evidence and Baseline

- `test:debug`: 12.36-minute average, 17.00-minute p95, 84.0 runner-hours over 28 days, and 5.9% job-event error share.
- The main Gradle test session averages 9.41 minutes and has a 13.92-minute p95. The gap between Gradle-session time and job time is approximately 2.95 minutes on average; this includes runner startup, GitLab cache transfer/extraction, and job setup.
- The job requests 20 CPUs and 32 GiB but writes `org.gradle.workers.max=4` in CI.
- Average serial test-span time per `test:debug` run is concentrated in `dd-sdk-android-core` (159 s), RUM (149 s), and Session Replay (134 s).
- The highest cumulative slow-suite candidates include `DatadogCoreTest`, `RotatingDnsResolverTest`, `ActivityViewTrackingStrategyTest`, and several integration listener/provider suites.
- GitLab caches are keyed by `$CI_COMMIT_REF_SLUG`, fall back to `develop`, and most jobs use `policy: pull`. Only `test:kover` currently writes its branch archive. Consequently, repeated pushes to a PR usually begin from the `develop` cache rather than a cache containing that PR's prior outputs.

---

### Task 1: Add a Repeatable Cache and Unit-Test Benchmark

**Files:**
- Modify: `ci/pipelines/default-pipeline.yml:49-60`
- Modify: `ci/pipelines/default-pipeline.yml:216-238`
- Create: `ci/scripts/summarize-gradle-performance.sh`
- Create: `docs/ci/gradle-performance.md`

**Interfaces:**
- Consumes: the existing `test:debug` Gradle invocation and GitLab job environment.
- Produces: `build/reports/profile/`, a concise task-outcome summary, and a documented `pup` query set for before/after comparisons.

- [ ] **Step 1: Add opt-in benchmark variables**

  Add `GRADLE_PERFORMANCE_PROFILE` and `GRADLE_MAX_WORKERS` handling to `.snippets.setup-gradle`. Keep production defaults unchanged: profiling off and worker count `4`.

  The Gradle invocation must add `--profile` only when `GRADLE_PERFORMANCE_PROFILE=true`. It must pass `--max-workers=$GRADLE_MAX_WORKERS` explicitly so experiment runs record the chosen value.

- [ ] **Step 2: Add profile artifacts to `test:debug`**

  Upload `build/reports/profile/` with `when: always` and a seven-day expiry when profiling is enabled. Retain the existing JUnit artifact declaration.

- [ ] **Step 3: Add the local summary script**

  `ci/scripts/summarize-gradle-performance.sh` must accept a Gradle log file and print counts for `FROM-CACHE`, `UP-TO-DATE`, `SUCCESS`, and non-cacheable tasks. It must fail on a missing input file and contain a shell test fixture that proves each category is counted correctly.

- [ ] **Step 4: Document the benchmark protocol**

  Define two cohorts on the same commit and runner class:

  1. Cold dependency/task cache.
  2. Warm dependency/task cache.

  Each cohort runs at least five times during the experiment phase. Record job wall time, Gradle-session wall time, GitLab cache archive size, cache restore/upload time from the GitLab job log, task outcome counts, peak memory, and job result.

- [ ] **Step 5: Record reusable `pup` queries**

  Use `--no-agent` because these commands are part of a human-run runbook:

  ```bash
  pup --no-agent cicd events aggregate \
    --query='@git.repository.id_v2:github.com/datadog/dd-sdk-android @ci.provider.name:gitlab @ci.job.name:"test:debug"' \
    --from=28d --compute='avg(@duration)' --group-by='@ci.job.name' --limit=10

  pup --no-agent cicd events aggregate \
    --query='@git.repository.id_v2:github.com/datadog/dd-sdk-android @ci.provider.name:gitlab @ci.job.name:"test:debug"' \
    --from=28d --compute='percentile(@duration, 95)' --group-by='@ci.job.name' --limit=10
  ```

- [ ] **Step 6: Validate the instrumentation**

  Run one manual profiled pipeline and confirm that profiling changes neither the selected Gradle tasks nor the JUnit test count. Compare the task graph with a non-profiled run.

**Commit boundary:** benchmark instrumentation and runbook. Use the supplied Jira ticket in the final commit title.

---

### Task 2: Make the Existing GitLab Cache Useful Across PR Commits

**Files:**
- Modify: `.gitlab-ci.yml:1-30`
- Modify: `ci/pipelines/default-pipeline.yml:120-127`
- Modify: `ci/pipelines/default-pipeline.yml:216-309`

**Interfaces:**
- Consumes: `GRADLE_USER_HOME=cache/`, current `develop` fallback, and the Task 1 benchmark.
- Produces: exactly one branch-cache writer and pull-only consumers.

- [ ] **Step 1: Separate dependency/distribution data from task outputs conceptually**

  Inventory the actual directories under `cache/` after `test:debug`. Record sizes for the wrapper distribution, downloaded modules, transformed artifacts, Kotlin/Gradle metadata, and `build-cache-1`. Do not hard-code Gradle internal subdirectories until this inventory confirms their names under Gradle 9.4.

- [ ] **Step 2: Select a single writer**

  Change `test:debug` to `policy: pull-push`. Keep all analysis, FIT, emulator, and tool jobs pull-only. Change `test:kover` from an implicit writer to pull-only so concurrent jobs cannot race while uploading the same branch archive.

- [ ] **Step 3: Version the cache key**

  Prefix cache keys with `CURRENT_CI_IMAGE` and the Gradle wrapper checksum. Preserve the `develop` fallback with the same prefix. This invalidates caches when the runtime image or Gradle distribution changes, without discarding reuse for every source commit.

- [ ] **Step 4: Validate sequential PR reuse**

  Run `test:debug` on commit A, push a source-only commit B, then rerun. Verify that B downloads A's branch cache rather than falling back to `develop`, and compare task outcomes with the current baseline.

- [ ] **Step 5: Apply an explicit acceptance gate**

  Keep the change only if the second-commit cohort improves median `test:debug` wall time by at least 10% after including cache download/upload overhead. Roll back if archive transfer consumes the saved execution time or if cache upload is unreliable on canceled jobs.

**Commit boundary:** one-writer GitLab cache and versioned invalidation. Use the supplied Jira ticket in the final commit title.

---

### Task 3: Pilot a True Remote Gradle Build Cache

**Prerequisite:** Obtain an internal authenticated HTTP build-cache endpoint and CI secret names from the build-infrastructure owner. Do not use public Gradle Build Scan or public cache storage.

**Files:**
- Modify: `settings.gradle.kts:7-24`
- Modify: `.gitlab-ci.yml:1-30`
- Modify: `ci/pipelines/default-pipeline.yml:27-60`
- Test: `build-logic/src/test/kotlin/com/datadog/gradle/config/RemoteBuildCacheConfigurationTest.kt`

**Interfaces:**
- Consumes environment variables `DD_GRADLE_BUILD_CACHE_URL`, `DD_GRADLE_BUILD_CACHE_USERNAME`, and `DD_GRADLE_BUILD_CACHE_PASSWORD`.
- Produces a content-addressed cache read by all CI jobs and written only by protected refs.

- [ ] **Step 1: Add configuration tests before production wiring**

  Add TestKit coverage for these cases:

  - No cache URL: remote cache disabled, local cache enabled.
  - Pull-request environment: remote cache enabled, `isPush=false`.
  - Protected branch: remote cache enabled, `isPush=true`.
  - Missing credentials with a URL: configuration fails with a precise error before task execution.

- [ ] **Step 2: Configure `HttpBuildCache` in `settings.gradle.kts`**

  Use Gradle providers for every environment lookup so configuration-cache behavior remains stable. Set `isPush` from `CI_COMMIT_REF_PROTECTED == "true"`. Never print credentials or include them in Gradle properties committed to the repository.

- [ ] **Step 3: Wire protected CI secrets**

  Expose the endpoint to all CI jobs. Expose credentials only in protected contexts capable of writing; PR contexts receive read-only credentials if the cache service distinguishes read and write access.

- [ ] **Step 4: Run a read-only PR pilot**

  Seed the cache from `develop`, then run at least ten PR pipelines with remote reads enabled. Compare hit counts and wall time with Task 2's GitLab-only cohort.

- [ ] **Step 5: Reduce the GitLab archive only after the pilot wins**

  Remove `build-cache-1` from the GitLab archive after the remote cache proves reliable. Retain wrapper distributions and dependency artifacts in GitLab cache; those are not Gradle task outputs.

- [ ] **Step 6: Apply an explicit acceptance gate**

  Adopt the remote cache if it produces at least a 20% median reduction in Gradle execution time on source-only follow-up commits, with a cache error rate below 1%. Configure failures as warnings initially so a cache outage cannot block CI.

**Commit boundary:** protected remote Gradle build-cache pilot. Use the supplied Jira ticket in the final commit title.

---

### Task 4: Tune Gradle Worker Concurrency Against the Existing 20-CPU Runner

**Files:**
- Modify: `ci/pipelines/default-pipeline.yml:49-60`
- Modify: `ci/pipelines/default-pipeline.yml:216-238`
- Document results: `docs/ci/gradle-performance.md`

**Interfaces:**
- Consumes: Task 1 profiling and stable cache behavior from Task 2 or Task 3.
- Produces: a measured `org.gradle.workers.max` value for `test:debug`.

- [ ] **Step 1: Establish the four-worker control cohort**

  Run five cold and five warm-cache pipelines with `GRADLE_MAX_WORKERS=4`. Record the complete metric set from Task 1.

- [ ] **Step 2: Run the eight-worker cohort**

  Repeat the same commits and cache state with `GRADLE_MAX_WORKERS=8`. Do not change JVM heap, test forking, or runner resources.

- [ ] **Step 3: Run the twelve-worker cohort only if eight workers remain resource-safe**

  Proceed only when the eight-worker cohort stays below 24 GiB peak memory, has no OOMs, and does not increase test failures. Repeat with `GRADLE_MAX_WORKERS=12`.

- [ ] **Step 4: Select the smallest efficient value**

  Choose the lowest worker count within 5% of the fastest median. Require at least a 15% median improvement over four workers and no p95 regression. This avoids paying memory and scheduler overhead for negligible gains.

- [ ] **Step 5: Roll out only to `test:debug` first**

  Keep the global default at four workers. Set the selected value as a job-level variable for `test:debug`, soak for 20 PR pipelines, then consider Kover and other Gradle-heavy jobs separately.

**Commit boundary:** measured unit-test worker concurrency. Use the supplied Jira ticket in the final commit title.

---

### Task 5: Parallelize Within the Three Heavy Test Modules

**Files:**
- Modify: `build-logic/src/main/kotlin/com/datadog/gradle/config/JUnitConfig.kt:17-59`
- Test: `build-logic/src/test/kotlin/com/datadog/gradle/config/JUnitConfigTest.kt`
- Modify: `ci/pipelines/default-pipeline.yml:216-238`

**Interfaces:**
- Consumes Gradle property `datadog.ci.test.maxParallelForks`.
- Produces opt-in JUnit process-level parallelism without changing local defaults.

- [ ] **Step 1: Add a failing convention-plugin test**

  Verify that the property is absent by default and every `Test` task retains `maxParallelForks == 1`. Verify that setting the property to `2` configures `maxParallelForks == 2`, while `0` and negative values fail during configuration with a clear message.

- [ ] **Step 2: Implement the opt-in property**

  Read `datadog.ci.test.maxParallelForks` through `providers.gradleProperty`. Apply it in `tasks.withType<Test>().configureEach`. Do not set `forkEvery`; process reuse is important for JVM startup cost.

- [ ] **Step 3: Run a two-fork experiment**

  Use the selected worker count from Task 4 and set `datadog.ci.test.maxParallelForks=2`. Compare module durations for core, RUM, and Session Replay, which currently contribute approximately 159, 149, and 134 serial test seconds per run.

- [ ] **Step 4: Check test isolation explicitly**

  Run each of the three modules repeatedly with randomized test order if the current JUnit tooling supports it. Reject the change if parallel forks expose shared static state, port collisions, file-system collisions, or a measurable increase in flaky failures.

- [ ] **Step 5: Apply an explicit acceptance gate**

  Keep two forks only if `test:debug` improves by at least another 10% beyond Task 4 while peak memory remains below 24 GiB. Do not try more than two forks until the slow-suite work in Task 6 is complete.

**Commit boundary:** opt-in test-fork configuration. Use the supplied Jira ticket in the final commit title.

---

### Task 6: Remove Avoidable Time From the Slowest Unit-Test Suites

**Files:**
- Inspect and modify only after profiling identifies the cause:
  - `dd-sdk-android-core/src/test/**/DatadogCoreTest.kt`
  - `dd-sdk-android-core/src/test/**/RotatingDnsResolverTest.kt`
  - `features/dd-sdk-android-rum/src/test/**/ActivityViewTrackingStrategyTest.kt`
  - Relevant integration listener/provider tests reported by Datadog CI Visibility

**Interfaces:**
- Consumes: suite-level durations from Datadog CI Visibility and per-test local profiling.
- Produces faster deterministic tests with unchanged assertions and production behavior.

- [ ] **Step 1: Export the slow-suite list**

  Query tests exceeding one second in `test:debug`, aggregate by suite using both `sum(@duration)` and `avg(@duration)`, and retain the output with the benchmark report.

- [ ] **Step 2: Classify each top suite**

  For the top ten suites, identify whether time is spent in JVM/Robolectric initialization, cryptography/compression, repeated fixture generation, actual I/O, bounded latch waits, or accidental timeout paths. Do not optimize forged/random data unless profiling shows it is material.

- [ ] **Step 3: Replace avoidable waiting with deterministic controls**

  Prefer same-thread executors, fake clocks, explicit latch release, and reused immutable fixtures. Do not introduce `Thread.sleep`, unbounded waits, shared mutable test fixtures, or production-only shortcuts.

- [ ] **Step 4: Prove behavioral sensitivity**

  For every modified suite, temporarily invert the key assertion or break the production behavior and confirm the test fails, then restore it. Follow the repository's existing JUnit 5, Mockito, Elmyr, and Given/When/Then conventions.

- [ ] **Step 5: Verify each changed module**

  Run the module's `ktlintFormat`, `detekt`, and `testDebugUnitTest` tasks, followed by the complete `test:debug` CI-equivalent Gradle command.

- [ ] **Step 6: Apply an explicit acceptance gate**

  Require a 20% reduction for each changed suite and at least a 5% aggregate reduction in `test:debug`. Avoid broad rewrites whose savings are lost in measurement noise.

**Commit boundary:** one commit per independently reviewable slow-suite cluster. Use the supplied Jira ticket in every commit title.

---

### Task 7: Roll Out, Observe, and Remove Experimental Instrumentation

**Files:**
- Modify: `ci/pipelines/default-pipeline.yml`
- Modify: `docs/ci/gradle-performance.md`
- Modify or remove: `ci/scripts/summarize-gradle-performance.sh`

**Interfaces:**
- Consumes: accepted changes from Tasks 2–6.
- Produces stable CI defaults and a permanent performance runbook.

- [ ] **Step 1: Roll out one accepted optimization at a time**

  Use this order: one-writer GitLab cache, remote Gradle cache, worker count, test forks, slow-suite fixes. Observe at least 20 PR runs between changes.

- [ ] **Step 2: Compare against the frozen baseline**

  Report median and p95 for job duration and Gradle-session duration, cache hit share, failure share, runner hours, peak memory, and archive transfer time.

- [ ] **Step 3: Enforce rollback thresholds**

  Roll back the most recent change if p95 regresses by more than 10%, the failure share rises by more than one percentage point, or peak memory exceeds 28 GiB.

- [ ] **Step 4: Remove opt-in profiling from normal pipelines**

  Keep the documented manual profiling switch, but stop uploading profiles on ordinary PR runs. Retain only low-cardinality metrics needed for ongoing monitoring.

- [ ] **Step 5: Set the next performance budget**

  Initial targets are `test:debug` median at or below 8 minutes and p95 at or below 12 minutes, representing roughly a one-third improvement from the observed baseline.

**Commit boundary:** rollout cleanup and performance runbook. Use the supplied Jira ticket in the final commit title.

## Deferred Work

- Moving `test:kover` off every PR or changing its variant can save substantial runner time, but it changes coverage policy and should be a separate plan.
- Changed-module test selection can improve first-feedback time, but it requires a trustworthy dependency-impact model and a guaranteed full-suite merge gate.
- Splitting `test:debug` into multiple GitLab jobs is a last resort. It duplicates configuration and compilation, and should be considered only after remote caching and in-process parallelism are measured.

## Plan Self-Review

- Coverage: cache measurement, branch reuse, remote task-output caching, worker tuning, test forking, slow-suite work, rollout, and rollback are all assigned to concrete tasks.
- Scope: coverage-policy and changed-module selection are explicitly deferred.
- Safety: untrusted branches cannot write shared remote entries; every optimization has an acceptance and rollback threshold.
- Ambiguity: remote-cache infrastructure is an explicit prerequisite rather than an invented endpoint.
