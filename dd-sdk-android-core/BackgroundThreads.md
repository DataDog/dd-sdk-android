# Background Threads

This document lists all background threads created by the Datadog SDK (and its features and integrations),
with their purposes, where they are created, and the stress testing done on each of them.

### `CoreFeature.persistenceExecutorService`: `FlushableExecutorService`

##### Purpose and Usage

This instance (one per `CoreFeature`) is used to treat all data writing process.
It is specifically used for:

- (internal) initialize the NTP process on startup
- (internal) used in the `ConsentAwareStorage` to process the `writeCurrentBatch()` calls
- (external) used in the `AbstractStorage` to process the `writeCurrentBatch()` calls
- (internal) used in the `ConsentAwareFileOrchestrator` to migrate files when tracking consent changes
- (internal) used in the `DatadogExceptionHandler` to ensure the crash is written to disk before letting the process
  exit

##### Load and scalability

##### Implementation details

Except for our unit tests the actual implementation is our own `BackPressureExecutorService`.

---

### `CoreFeature.uploadExecutorService`: `ScheduledThreadPoolExecutor`

##### Purpose and Usage

This instance (one per `CoreFeature`) is used to process the data upload process.
It is specifically used for:

- (internal) creates the initial Configuration Telemetry on `DatadogCore` startup
  (delayed to allow cross platform SDKs to update the configuration)
- (internal) used in the `DataUploadScheduler` to schedule regular upload cycles

##### Load and scalability

##### Implementation details

Except for our unit tests, the actual implementation is our own `LoggingScheduledThreadPoolExecutor`.

---

### RUM View Tracking Strategies : `ScheduledExecutorService`

##### Purpose and Usage

This instance (one in our basic Android lifecycle based VTS, i.e.: Activity, Legacy Fragment and Oreo+ Fragment)
is used to delay the call to `RumMonitor.stopView()`. This is linked with ticket `RUM-616` to avoid gaps in the
session coverage. (cf : [Github PR #1578](https://github.com/DataDog/dd-sdk-android/pull/1578)).

##### Load and scalability

##### Implementation details

Except for our unit tests, the actual implementation is our own `LoggingScheduledThreadPoolExecutor`.

---

### `RumFeature.vitalExecutorService`: `ScheduledExecutorService`

##### Purpose and Usage

This instance (one per `RumFeature`) is used to schedule regular readings of the system vitals, namely the CPU and
memory usage.

##### Load and scalability

##### Implementation details

Except for our unit tests, the actual implementation is our own `LoggingScheduledThreadPoolExecutor`.

---

### `RumFeature.anrDetectorExecutorService: `ExecutorService`

##### Purpose and Usage

This instance (one per `RumFeature`) is used for our own API < 30 ANR detection mechanism.

##### Load and scalability

##### Implementation details

Except for our unit tests, the actual implementation is our own `BackPressureExecutorService`.

---

### `DatadogRumMonitor.executorService`: `ExecutorService`

##### Purpose and Usage

This instance (one per `DatadogRumMonitor`) is used to defer the process of raw events (usually captured from the
main thread) and eventually generate actual Json event that needs to be written on disk.

##### Load and scalability

##### Implementation details

Except for our unit tests, the actual implementation is our own `BackPressureExecutorService`.

