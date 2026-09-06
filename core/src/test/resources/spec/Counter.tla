------------------------------ MODULE Counter ------------------------------
(* A bounded counter used by the Apalache integration tests. The action taken
   at each step is recorded in `mbt_action_taken`, shaped like a Quint sum type
   so that DriverConfig.nondetPath("mbt_action_taken") can extract it. *)
EXTENDS Integers

VARIABLES
    \* @type: Int;
    count,
    \* @type: { tag: Str, value: { amount: Int } };
    mbt_action_taken

Init ==
    /\ count = 0
    /\ mbt_action_taken = [tag |-> "Init", value |-> [amount |-> 0]]

Increment ==
    \E n \in 1..3:
        /\ count' = count + n
        /\ mbt_action_taken' = [tag |-> "Increment", value |-> [amount |-> n]]

Decrement ==
    \E n \in 1..3:
        /\ count >= n
        /\ count' = count - n
        /\ mbt_action_taken' = [tag |-> "Decrement", value |-> [amount |-> n]]

Reset ==
    /\ count > 0
    /\ count' = 0
    /\ mbt_action_taken' = [tag |-> "Reset", value |-> [amount |-> 0]]

Next == Increment \/ Decrement \/ Reset

NonNegative == count >= 0

\* Violated by any trace that reaches 3 or more; used with `check` to make
\* Apalache emit such traces as counterexamples.
BelowThree == count < 3

\* Partitions counterexamples by their final count (`--view`).
View == count
=============================================================================
