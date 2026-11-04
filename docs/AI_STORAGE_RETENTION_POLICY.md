# Hard FIFO Storage Policy

Status: Required for every cloud deployment

## Objective

The configured storage maximum is a hard ceiling. The system must reject or locally buffer a write before cloud storage can exceed that ceiling.

## Capacity model

Let:

- MAX_BYTES = absolute provider-safe maximum;
- RESERVED_BYTES = protected operational headroom;
- TARGET_BYTES = MAX_BYTES - RESERVED_BYTES;
- CURRENT_BYTES = authoritative accounted bytes;
- IN_FLIGHT_BYTES = bytes reserved by concurrent writes;
- INCOMING_BYTES = validated object size.

A write is admitted only when:

~~~text
CURRENT_BYTES + IN_FLIGHT_BYTES + INCOMING_BYTES <= TARGET_BYTES
~~~

The provider absolute limit is never used as the application target.

## Mandatory write path

1. Receive metadata and exact content length.
2. Reject unknown or unbounded uploads.
3. Acquire the storage-capacity lock.
4. Reconcile the authoritative usage ledger.
5. Remove eligible FIFO objects until the admission condition is true.
6. Reserve the exact incoming byte count.
7. Commit the object.
8. Verify the committed size.
9. Convert the reservation into accounted usage.
10. Release the lock.

No client receives direct write credentials for the object store. Uploads use the API or a short-lived pre-signed URL created only after reservation.

## FIFO rules

Objects are ordered by an immutable created sequence, not by an unreliable client timestamp.

Deletion order:

1. expired raw events;
2. oldest raw events;
3. oldest normalized events;
4. compacted data only when derived knowledge is preserved;
5. never delete active releases, rollback targets, protected replay fixtures or audit records.

If enough bytes cannot be freed without deleting protected data, the write is rejected and placed in the bounded local buffer.

## Concurrency and failure safety

- Use a database transaction and advisory lock for capacity admission.
- Store reservations with an expiry time.
- Reconcile reservations after worker or network failure.
- Verify object size after upload; never trust the requested size alone.
- Use idempotency keys to prevent duplicate accounting.
- Treat incomplete multipart uploads as reserved space and clean them up.
- Run periodic ledger reconciliation against the object store.
- If ledger and provider report different values, enter safe mode and stop cloud writes until reconciled.

## Local fallback

When a cloud write is rejected or unavailable:

- write only to a bounded local queue;
- apply the same FIFO ceiling locally;
- prioritize safety-relevant and high-value learning data;
- drop low-value duplicates first;
- retry with backoff;
- never allow the local queue to grow without a hard limit.

Navigation and the last released knowledge version remain available.

## Operational thresholds

Recommended initial values:

- absolute provider limit: 100%;
- internal hard target: 75%;
- warning: 65%;
- aggressive compaction: 70%;
- emergency write restrictions: 73%;
- protected headroom: at least 25%.

These are configuration values. The effective target must be recalculated from the provider quota and verified storage accounting.

## Required metrics

Expose:

- absolute limit;
- internal target;
- current accounted bytes;
- provider-reported bytes;
- in-flight reservations;
- local queue bytes;
- oldest retained object;
- rejected writes;
- deleted bytes by reason;
- reconciliation status.

The dashboard must distinguish accounted, reserved, queued and provider-reported storage.

## Invariant

The following condition must always hold:

~~~text
CURRENT_BYTES + IN_FLIGHT_BYTES <= TARGET_BYTES < MAX_BYTES
~~~

If the invariant cannot be proven, the system must fail closed for new cloud writes.
