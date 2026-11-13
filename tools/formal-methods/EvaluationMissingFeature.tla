---- MODULE EvaluationMissingFeature ----
EXTENDS Naturals

VARIABLES pending, featurePresent, writerReturned

vars == <<pending, featurePresent, writerReturned>>

Init ==
    /\ pending = TRUE
    /\ featurePresent = FALSE
    /\ writerReturned = FALSE

DrainAndLookUpFeature ==
    /\ pending
    /\ ~featurePresent
    /\ pending' = FALSE
    /\ writerReturned' = TRUE
    /\ UNCHANGED featurePresent

Next == DrainAndLookUpFeature

Spec == Init /\ [][Next]_vars

NormalWriterReturnTransfersOwnership == ~(writerReturned /\ ~pending /\ ~featurePresent)

====
