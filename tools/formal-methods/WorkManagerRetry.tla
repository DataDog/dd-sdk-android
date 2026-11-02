---- MODULE WorkManagerRetry ----
EXTENDS Naturals

VARIABLES phase, batchRetained, workResult

vars == <<phase, batchRetained, workResult>>

Init ==
    /\ phase = "queued"
    /\ batchRetained = TRUE
    /\ workResult = "pending"

RetryableUploadFailure ==
    /\ phase = "queued"
    /\ phase' = "confirmed-retained"
    /\ UNCHANGED <<batchRetained, workResult>>

WorkerReturnsSuccess ==
    /\ phase = "confirmed-retained"
    /\ phase' = "done"
    /\ workResult' = "success"
    /\ UNCHANGED batchRetained

Next == RetryableUploadFailure \/ WorkerReturnsSuccess

Spec == Init /\ [][Next]_vars

RetryableBatchHasOwner ==
    ~(phase = "done" /\ batchRetained /\ workResult = "success")

====
