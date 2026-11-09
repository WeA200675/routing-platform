# Production discovery policy

The final production path is centralized in ProductionDiscoveryController.

A source can affect production only when all gates pass:

1. HTTPS and host allowlist
2. trusted manifest key and valid signature
3. rate and response-size limits
4. valid schema and fresh data
5. acceptable rolling reputation
6. no active anomaly
7. lifecycle stage ACTIVE
8. no quarantine or kill switch

All observations create training records and lifecycle audit entries. Any failed gate is fail-closed and leaves the existing navigation/provider configuration unchanged.
