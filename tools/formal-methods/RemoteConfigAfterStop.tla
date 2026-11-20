---- MODULE RemoteConfigAfterStop ----
EXTENDS Naturals

VARIABLES stopped, syncAccepted, fetchAfterRelease

vars == <<stopped, syncAccepted, fetchAfterRelease>>

Init ==
    /\ stopped = FALSE
    /\ syncAccepted = FALSE
    /\ fetchAfterRelease = FALSE

Stop ==
    /\ ~stopped
    /\ stopped' = TRUE
    /\ UNCHANGED <<syncAccepted, fetchAfterRelease>>

RequestSync ==
    /\ stopped
    /\ syncAccepted' = TRUE
    /\ fetchAfterRelease' = TRUE
    /\ UNCHANGED stopped

Next == Stop \/ RequestSync

Spec == Init /\ [][Next]_vars

StoppedServiceRejectsSync == ~(syncAccepted /\ fetchAfterRelease)

====
