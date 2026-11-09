# Discovery lifecycle

Every discovered source is measured over a rolling window before activation.

- PROPOSED: candidate found, no runtime influence
- SHADOW: observed only, never changes routing
- CANARY: limited influence with rollback monitoring
- ACTIVE: eligible for production decisions
- ROLLED_BACK: quarantined after safety, schema or reliability failure

The lifecycle engine records every transition with a reason and score. A source is quarantined when the rolling error rate reaches 40%, signatures/schema fail, or active quality drops below the safety threshold. This state is suitable for dashboard display and training events.
