# Findings

Priority reflects possible loss, false completion, and lifecycle safety. It is
not a claim about measured frequency. No P0 or security vulnerability is
claimed.

## P1 — Retry ownership and false completion

### 1. Retryable WorkManager uploads finish without an in-run retry owner

`UploadWorker.UploadNextBatchTask` retains a batch when `shouldRetry` is true,
but only enqueues another task after `UploadStatus.Success`. The surrounding
worker drains its queue and returns `Result.success()`.

- Model: `WorkManagerRetry.tla` / `WorkManagerRetry.cfg`
- Replay: `FormalMethodsUploadDiagnosticsTest`
- Confirmed behavior: the batch is confirmed with `deleteBatch = false`, the
  task queue is empty, and the worker is then able to report success.
- Contract question: is a later external scheduling event guaranteed, or must
  this work invocation keep/relinquish retry ownership explicitly?

### 2. A concurrent evaluation flush returns while a preceding write is active

`EvaluationEventsProcessor.flush()` protects only the aggregator drain with
`flushMutex`; it unlocks before `writer.writeAll()`. A second flush can acquire
the lock, observe no pending events, and return while the first write is still
active.

- Model: `EvaluationFlush.tla` / `EvaluationFalseAck.cfg`
- Replay: `FormalMethodsEvaluationDiagnosticsTest`
- Confirmed behavior: the second call returns before the blocked first writer.
- Contract question: is `flush()` intended to acknowledge preceding work or
  merely initiate a best-effort drain?

### 3. A failed evaluation write leaves the drained batch without retry ownership

`flush()` drains the aggregator before calling `writer.writeAll()`. If the
writer throws, the batch is neither restored to the aggregator nor retained by
the processor for another attempt.

- Model: `EvaluationWriteRetry.tla` / `EvaluationWriteFailure.cfg`
- Replay: `FormalMethodsEvaluationDiagnosticsTest`
- Confirmed behavior: after an injected writer failure, a later flush makes no
  second write attempt.
- Suspected consequence: loss if a production writer can fail before accepting
  ownership. Production incidence is not established.

### 4. Evaluation stop can return while a threshold-triggered write is active

`processEvaluation()` may drain at the aggregation threshold and call the
writer without taking `flushMutex`. `stop()` waits on that mutex and its own
scheduled executor, but not on concurrent caller-owned writes.

- Model: `EvaluationStop.tla` / `EvaluationStop.cfg`
- Replay: `FormalMethodsEvaluationDiagnosticsTest`
- Confirmed behavior: `stop()` returns while a controlled threshold-triggered
  writer remains blocked.
- Contract question: are callers prohibited from racing feature shutdown, or
  must stop fence all already-accepted evaluation work?

## P2 — Resource lifecycle

### 5. Queued remote-config sync uses the fetcher after stop releases it

`syncWithRemote()` queues `fetchAndCache()` on an executor. `stop()` immediately
calls `fetcher.release()` without marking the service stopped or fencing queued
tasks. A queued task can therefore call `fetch()` afterward.

- Model: `RemoteConfigStop.tla` / `RemoteConfigStop.cfg`
- Replay: `FormalMethodsRemoteConfigDiagnosticsTest`
- Confirmed behavior: the controlled executor invokes `fetch()` after
  `release()`.
- Suspected consequence: work against a closed HTTP cache or resources that
  outlive the feature lifecycle. Exact behavior depends on the fetcher.

### 6. Crash-data flush deletes files after retryable upload failures

`DataFlusher.flush()` ignores the returned `UploadStatus` and unconditionally
deletes the batch and metadata files after `upload()` returns. This includes
`NetworkError`, `DNSError`, rate limiting, and server failures whose status has
`shouldRetry = true`.

- Model: `DataFlusherRetry.tla` / `DataFlusherRetry.cfg`
- Replay: `FormalMethodsUploadDiagnosticsTest`
- Confirmed behavior: an injected `NetworkError` is followed by deletion of the
  source file.
- Suspected consequence: crash or last-event data loss during transient intake
  failure.

### 7. IGNORE_NEWEST backpressure reports a dropped task as accepted

When the bounded `BackPressuredBlockingQueue` is full under `IGNORE_NEWEST`, it
notifies that the new item was dropped but returns `true` from `offer()`. The
executor and submitter therefore observe successful acceptance.

- Model: `BackPressureAcceptance.tla` / `BackPressureAcceptance.cfg`
- Replay: `FormalMethodsBackPressureDiagnosticsTest`
- Confirmed behavior: `offer()` returns true, the new task is absent from the
  queue, and the drop callback receives it.
- Suspected consequence: callers cannot distinguish queued work from silently
  rejected work, so higher-level flush/ownership reasoning may be false.

### 8. Upload scheduling stop returns while the current upload remains active

`DataUploadScheduler.stopScheduling()` sets a flag but deliberately does not
cancel or join a running upload cycle.

- Model: `UploadSchedulerStop.tla` / `UploadSchedulerStop.cfg`
- Replay: `FormalMethodsUploadDiagnosticsTest`
- Confirmed behavior: stop returns while the controlled callable remains active.
- Contract note: production comments describe this as fire-and-forget. The open
  question is whether callers use it as part of a stronger feature-stop barrier.

### 9. Remote-config accepts new sync requests after stop

The service has no stopped state. Calling `syncWithRemote()` after `stop()`
still schedules `fetchAndCache()`, which uses the already released fetcher.

- Model: `RemoteConfigAfterStop.tla` / `RemoteConfigAfterStop.cfg`
- Replay: `FormalMethodsRemoteConfigDiagnosticsTest`
- Confirmed behavior: a direct executor calls `fetch()` after `release()` when
  sync is requested post-stop.
- Contract question: is post-stop use forbidden by an external lifecycle fence,
  and if so can that assumption be made explicit or enforced?

### 10. Evaluation processor accepts work after stop

`EvaluationEventsProcessor.stop()` shuts down its periodic executor but does not
record a stopped state. Later `processEvaluation()` calls continue mutating the
aggregator; no periodic task remains to own the eventual flush.

- Model: `EvaluationAfterStop.tla` / `EvaluationAfterStop.cfg`
- Replay: `FormalMethodsEvaluationDiagnosticsTest`
- Confirmed behavior: a post-stop evaluation is retained and can be written by
  a later manual flush.
- Suspected consequence: indefinitely stranded events, or persistence outside
  the feature lifecycle, depending on subsequent calls.

### 11. Missing evaluations feature silently consumes a drained batch

`EvaluationEventRecordWriter.writeAll()` uses a nullable feature lookup and
returns normally when the feature is absent. The processor has already drained
the batch and treats the normal return as ownership transfer.

- Model: `EvaluationMissingFeature.tla` / `EvaluationMissingFeature.cfg`
- Replay: `FormalMethodsEvaluationDiagnosticsTest`
- Confirmed behavior: the first flush performs one missing-feature lookup; a
  later flush does not retry the drained batch.
- Suspected consequence: evaluation loss during feature initialization or
  teardown races. Production reachability of a missing lookup still needs
  lifecycle validation.
