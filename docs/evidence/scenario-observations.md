# Historical scenario observations

These observations were read from local workflow snapshots during the feedback
review, before repository cleanup and the clean verification build. They describe
historical executions, not the current source or successful autonomous delivery.
Raw workflow files were under build/agent-runs, which is disposable build output.
Only the observed status/error/question excerpts below are retained here.

| Scenario | Workflow ID | Observed output |
| --- | --- | --- |
| Greenfield | d163beda-23ff-4c61-b6bb-b10ed02d8764 | Revision 1 FAILED. ANALYZE through APPLY ran; validation output failed; repair-1 failed with IllegalArgumentException. |
| Brownfield | f9e88c5d-2840-431a-8186-92813842ebf6 | Revision 1 FAILED. Component generation ran, then TEST failed: Generation stage must produce files. APPLY and VALIDATE remained pending. |
| Ambiguous | bd7ded7d-00ca-4406-82dc-0b1c4dc44caa | Revision 1 AWAITING_CLARIFICATION. Revision 2 FAILED at ANALYZE with ValueInstantiationException. |

The ambiguous run asked:

> What is the required aggregation period for analytics? (e.g., hourly, daily, weekly)

> Is retention of individual visitor data allowed? If yes, for how long?

The predefined answer specifies daily UTC aggregates and no individual visitor
identifiers. The pause demonstrates clarification behavior; the later failure means
it is not a completed ambiguous implementation. Fresh runs must save their full
workflow, summary and events before cleaning build output. Do not reuse these IDs
as proof that a current run passed.
