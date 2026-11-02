---- MODULE DataFlusherRetry ----
EXTENDS Naturals

VARIABLES filePresent, uploadRetryable, flushReturned

vars == <<filePresent, uploadRetryable, flushReturned>>

Init ==
    /\ filePresent = TRUE
    /\ uploadRetryable = FALSE
    /\ flushReturned = FALSE

UploadFailsRetryably ==
    /\ filePresent
    /\ uploadRetryable' = TRUE
    /\ UNCHANGED <<filePresent, flushReturned>>

DeleteAndReturn ==
    /\ uploadRetryable
    /\ filePresent' = FALSE
    /\ flushReturned' = TRUE
    /\ UNCHANGED uploadRetryable

Next == UploadFailsRetryably \/ DeleteAndReturn

Spec == Init /\ [][Next]_vars

RetryableUploadRetainsFile == ~uploadRetryable \/ filePresent

====
