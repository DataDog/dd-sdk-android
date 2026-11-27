---- MODULE EvaluationAfterStop ----
EXTENDS Naturals

VARIABLES stopped, postStopPending, laterWritten

vars == <<stopped, postStopPending, laterWritten>>

Init ==
    /\ stopped = FALSE
    /\ postStopPending = FALSE
    /\ laterWritten = FALSE

Stop ==
    /\ ~stopped
    /\ stopped' = TRUE
    /\ UNCHANGED <<postStopPending, laterWritten>>

AcceptAfterStop ==
    /\ stopped
    /\ postStopPending' = TRUE
    /\ UNCHANGED <<stopped, laterWritten>>

ManualFlushAfterStop ==
    /\ postStopPending
    /\ postStopPending' = FALSE
    /\ laterWritten' = TRUE
    /\ UNCHANGED stopped

Next == Stop \/ AcceptAfterStop \/ ManualFlushAfterStop

Spec == Init /\ [][Next]_vars

StoppedProcessorAcceptsNoWork == ~(stopped /\ (postStopPending \/ laterWritten))

====
