# Storage Admission Implementation

This package implements the hard FIFO ceiling defined in the storage retention policy.

The provider adapter must validate exact content length, load the ledger inside a database transaction, acquire an advisory lock, call CapacityLedger.admit, delete returned FIFO objects, create a short-lived upload reservation, upload and verify the object size, call commit, and reconcile with the provider.

If any step cannot prove the invariant, cloud writes stop and the bounded local queue is used. Production persistence must use PostgreSQL or an equivalent transactional ledger; it must not rely on process memory.
