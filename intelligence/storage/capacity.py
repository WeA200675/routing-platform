"""Fail-closed storage admission controller."""

from dataclasses import dataclass, field
from enum import Enum
from time import time
from typing import Dict, List, Optional
from uuid import uuid4


class StorageSafeMode(RuntimeError):
    """Raised when the usage ledger cannot prove the hard invariant."""


class AdmissionRejected(RuntimeError):
    """Raised when an upload cannot be admitted without crossing the target."""


class ObjectClass(str, Enum):
    RAW = "raw"
    NORMALIZED = "normalized"
    EVIDENCE = "evidence"
    REPLAY = "replay"
    RELEASE = "release"
    AUDIT = "audit"


PROTECTED_CLASSES = {ObjectClass.REPLAY, ObjectClass.RELEASE, ObjectClass.AUDIT}


@dataclass
class StoredObject:
    object_id: str
    size_bytes: int
    created_seq: int
    object_class: ObjectClass
    protected: bool = False
    expired: bool = False


@dataclass
class Reservation:
    reservation_id: str
    size_bytes: int
    expires_at: float


@dataclass
class CapacityLedger:
    max_bytes: int
    reserved_headroom_bytes: int
    objects: Dict[str, StoredObject] = field(default_factory=dict)
    reservations: Dict[str, Reservation] = field(default_factory=dict)
    next_sequence: int = 1
    safe_mode: bool = False

    @property
    def target_bytes(self) -> int:
        target = self.max_bytes - self.reserved_headroom_bytes
        if target <= 0:
            raise ValueError("reserved headroom must be smaller than max_bytes")
        return target

    @property
    def current_bytes(self) -> int:
        return sum(item.size_bytes for item in self.objects.values())

    @property
    def in_flight_bytes(self) -> int:
        return sum(item.size_bytes for item in self.reservations.values())

    def assert_invariant(self) -> None:
        if self.safe_mode:
            raise StorageSafeMode("storage ledger is in safe mode")
        if self.current_bytes + self.in_flight_bytes > self.target_bytes:
            self.safe_mode = True
            raise StorageSafeMode("hard storage invariant cannot be proven")

    def _eligible_fifo(self) -> List[StoredObject]:
        return sorted(
            (
                item for item in self.objects.values()
                if not item.protected
                and item.object_class not in PROTECTED_CLASSES
            ),
            key=lambda item: item.created_seq,
        )

    def _delete_for_space(self, required_bytes: int) -> List[str]:
        deleted: List[str] = []
        for item in self._eligible_fifo():
            if self.current_bytes + self.in_flight_bytes + required_bytes <= self.target_bytes:
                break
            del self.objects[item.object_id]
            deleted.append(item.object_id)
        return deleted

    def admit(self, size_bytes: int, object_class: ObjectClass,
              *, protected: bool = False, now: Optional[float] = None):
        if size_bytes <= 0:
            raise AdmissionRejected("content length must be positive")
        if size_bytes > self.target_bytes:
            raise AdmissionRejected("single object exceeds the hard target")
        self.assert_invariant()
        deleted = self._delete_for_space(size_bytes)
        if self.current_bytes + self.in_flight_bytes + size_bytes > self.target_bytes:
            raise AdmissionRejected("FIFO cleanup cannot free enough unprotected space")
        reservation = Reservation(
            reservation_id=str(uuid4()),
            size_bytes=size_bytes,
            expires_at=(now if now is not None else time()) + 900,
        )
        self.reservations[reservation.reservation_id] = reservation
        self.assert_invariant()
        return reservation, deleted

    def commit(self, reservation_id: str, object_class: ObjectClass,
               *, protected: bool = False) -> StoredObject:
        reservation = self.reservations.pop(reservation_id, None)
        if reservation is None:
            raise AdmissionRejected("unknown or expired reservation")
        item = StoredObject(
            object_id=str(uuid4()),
            size_bytes=reservation.size_bytes,
            created_seq=self.next_sequence,
            object_class=object_class,
            protected=protected or object_class in PROTECTED_CLASSES,
        )
        self.next_sequence += 1
        self.objects[item.object_id] = item
        self.assert_invariant()
        return item

    def expire_reservations(self, now: Optional[float] = None) -> int:
        current = now if now is not None else time()
        expired = [key for key, value in self.reservations.items()
                   if value.expires_at <= current]
        for key in expired:
            del self.reservations[key]
        return len(expired)
