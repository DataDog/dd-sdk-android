---- MODULE UploadSchedulerStop ----
EXTENDS Naturals

VARIABLES uploadActive, stopReturned

vars == <<uploadActive, stopReturned>>

Init ==
    /\ uploadActive = FALSE
    /\ stopReturned = FALSE

StartUpload ==
    /\ ~uploadActive
    /\ uploadActive' = TRUE
    /\ UNCHANGED stopReturned

StopScheduling ==
    /\ uploadActive
    /\ stopReturned' = TRUE
    /\ UNCHANGED uploadActive

UploadCompletes ==
    /\ uploadActive
    /\ uploadActive' = FALSE
    /\ UNCHANGED stopReturned

Next == StartUpload \/ StopScheduling \/ UploadCompletes

Spec == Init /\ [][Next]_vars

StopWaitsForCurrentUpload == ~(stopReturned /\ uploadActive)

====
