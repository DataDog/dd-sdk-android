---- MODULE BackPressureAcceptance ----
EXTENDS Naturals

VARIABLES full, newestDropped, offerReturnedTrue

vars == <<full, newestDropped, offerReturnedTrue>>

Init ==
    /\ full = TRUE
    /\ newestDropped = FALSE
    /\ offerReturnedTrue = FALSE

IgnoreNewest ==
    /\ full
    /\ newestDropped' = TRUE
    /\ offerReturnedTrue' = TRUE
    /\ UNCHANGED full

Next == IgnoreNewest

Spec == Init /\ [][Next]_vars

SuccessfulOfferEnqueuesItem == ~(newestDropped /\ offerReturnedTrue)

====
