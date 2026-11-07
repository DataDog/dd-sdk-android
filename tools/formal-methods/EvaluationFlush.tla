---- MODULE EvaluationFlush ----
EXTENDS Naturals

VARIABLES pending, writeActive, secondFlushReturned

vars == <<pending, writeActive, secondFlushReturned>>

Init ==
    /\ pending = TRUE
    /\ writeActive = FALSE
    /\ secondFlushReturned = FALSE

FirstFlushDrains ==
    /\ pending
    /\ pending' = FALSE
    /\ writeActive' = TRUE
    /\ UNCHANGED secondFlushReturned

ConcurrentFlushReturns ==
    /\ ~pending
    /\ writeActive
    /\ secondFlushReturned' = TRUE
    /\ UNCHANGED <<pending, writeActive>>

WriteCompletes ==
    /\ writeActive
    /\ writeActive' = FALSE
    /\ UNCHANGED <<pending, secondFlushReturned>>

Next == FirstFlushDrains \/ ConcurrentFlushReturns \/ WriteCompletes

Spec == Init /\ [][Next]_vars

FlushReturnImpliesPrecedingWriteComplete ==
    ~(secondFlushReturned /\ writeActive)

====
