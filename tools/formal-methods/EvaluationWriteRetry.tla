---- MODULE EvaluationWriteRetry ----
EXTENDS Naturals

VARIABLES pending, writerOwns, writeFailed

vars == <<pending, writerOwns, writeFailed>>

Init ==
    /\ pending = TRUE
    /\ writerOwns = FALSE
    /\ writeFailed = FALSE

Drain ==
    /\ pending
    /\ pending' = FALSE
    /\ writerOwns' = TRUE
    /\ UNCHANGED writeFailed

WriteFails ==
    /\ writerOwns
    /\ writerOwns' = FALSE
    /\ writeFailed' = TRUE
    /\ UNCHANGED pending

Next == Drain \/ WriteFails

Spec == Init /\ [][Next]_vars

FailedBatchRemainsOwned ==
    ~writeFailed \/ pending \/ writerOwns

====
