---- MODULE RemoteConfigStop ----
EXTENDS Naturals

VARIABLES queued, released, fetchAfterRelease

vars == <<queued, released, fetchAfterRelease>>

Init ==
    /\ queued = TRUE
    /\ released = FALSE
    /\ fetchAfterRelease = FALSE

Stop ==
    /\ ~released
    /\ released' = TRUE
    /\ UNCHANGED <<queued, fetchAfterRelease>>

RunQueuedSync ==
    /\ queued
    /\ queued' = FALSE
    /\ fetchAfterRelease' = released
    /\ UNCHANGED released

Next == Stop \/ RunQueuedSync

Spec == Init /\ [][Next]_vars

ReleasedFetcherIsNotUsed == ~fetchAfterRelease

====
