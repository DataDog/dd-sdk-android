---- MODULE EvaluationStop ----
EXTENDS Naturals

VARIABLES accepted, writeActive, stopped

vars == <<accepted, writeActive, stopped>>

Init ==
    /\ accepted = FALSE
    /\ writeActive = FALSE
    /\ stopped = FALSE

ThresholdDrainStartsWrite ==
    /\ ~accepted
    /\ accepted' = TRUE
    /\ writeActive' = TRUE
    /\ UNCHANGED stopped

StopReturns ==
    /\ writeActive
    /\ stopped' = TRUE
    /\ UNCHANGED <<accepted, writeActive>>

WriteCompletes ==
    /\ writeActive
    /\ writeActive' = FALSE
    /\ UNCHANGED <<accepted, stopped>>

Next == ThresholdDrainStartsWrite \/ StopReturns \/ WriteCompletes

Spec == Init /\ [][Next]_vars

StopWaitsForAcceptedWrites ==
    ~(stopped /\ accepted /\ writeActive)

====
