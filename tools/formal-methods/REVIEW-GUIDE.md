# Review guide

## Scope

The suite examines places where work is accepted or drained by one component
and a different thread, queue, or lifecycle method later claims it has been
retried, flushed, persisted, or stopped. It makes no production-frequency or
customer-impact claim.

No production code is changed. The purpose of this branch is to let component
owners decide whether each invariant describes the intended contract before a
remediation is designed.

## Evidence chain

For every finding, review in this order:

1. Read the production transition named in [FINDINGS.md](FINDINGS.md).
2. Run the focused JUnit replay. It controls the relevant schedule with queues
   or latches and asserts the current behavior.
3. Read the matching TLA+ module. The model deliberately contains only the
   ownership states needed for that schedule.
4. Run TLC and confirm it violates the invariant named in the `.cfg` file.

The model is not proof that the consequence occurs in production. The replay is
not proof that the proposed invariant is the product contract. Together they
show that the implementation order is reachable and make the contract question
explicit.

## Priority rubric

- **P1:** possible retained or drained telemetry with no immediate retry owner,
  or flush/shutdown acknowledgement while accepted work remains active.
- **P2:** lifecycle ordering that invokes a dependency after it was released.
- **P3:** narrow or explicitly best-effort behavior whose contract may be
  intentionally weaker than the diagnostic invariant.

## Review questions

1. Does `UploadWorker.Result.success()` mean all retained retryable batches have
   another guaranteed owner, or should WorkManager be asked to retry?
2. Is `EvaluationEventsProcessor.flush()` a persistence barrier, an ownership
   transfer barrier, or only a best-effort drain request?
3. If an `EvaluationEventWriter` throws, which component owns the drained
   aggregation batch?
4. Must `EvaluationEventsProcessor.stop()` wait for threshold-triggered writes
   issued by concurrent callers?
5. Must `RemoteConfigService.stop()` cancel or fence queued sync work before
   releasing the fetcher?
6. May `DataFlusher` delete crash/last-event files after a retryable upload
   result?
7. Does a successful queue `offer()` mean the submitted task is now owned by
   the executor, including under `IGNORE_NEWEST` backpressure?
8. Is `stopScheduling()` only a future-scheduling switch, or a shutdown barrier
   for the current upload?
9. Should lifecycle methods reject new work after stop, or is post-stop use a
   supported state for evaluation and remote-config components?
10. If the evaluations feature is temporarily unavailable, who owns the batch
    drained by `EvaluationEventRecordWriter`?

## Limitations

- The replay tests use controlled collaborators to make rare schedules
  deterministic.
- The evaluation writer normally transfers work again to SDK Core; this suite
  does not claim that `writeAll()` completion means bytes are already on disk.
- WorkManager may be scheduled again by an external trigger. The retry finding
  is specifically about ownership at the end of the current successful run.
- The remote-config test confirms call ordering, not the exact behavior of
  every OkHttp/cache version after close.
- The upload-scheduler stop behavior is explicitly documented as fire-and-forget
  in production. Its finding is retained to ask whether feature shutdown needs
  a stronger contract than scheduling shutdown.
- Device, API-level, and cross-runtime stress validation are still required.
