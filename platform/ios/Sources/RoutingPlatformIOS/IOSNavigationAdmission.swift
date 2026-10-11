import Foundation

/// P22 Apple-side capability projection for the shared navigation admission contract.
/// This file deliberately contains no Android/manufacturer assumptions.
public struct IOSNavigationDeviceCapabilities: Equatable {
    public let preciseLocationAvailable: Bool
    public let directObservationAvailable: Bool
    public let monotonicTimestampAvailable: Bool

    public init(
        preciseLocationAvailable: Bool,
        directObservationAvailable: Bool,
        monotonicTimestampAvailable: Bool
    ) {
        self.preciseLocationAvailable = preciseLocationAvailable
        self.directObservationAvailable = directObservationAvailable
        self.monotonicTimestampAvailable = monotonicTimestampAvailable
    }

    public var calibrationAvailable: Bool {
        preciseLocationAvailable &&
        directObservationAvailable &&
        monotonicTimestampAvailable
    }
}

public enum IOSNavigationObservationConfidence {
    case high
    case medium
    case low
    case lost
}

public enum IOSNavigationFusionMode {
    case directObservation
    case fusedEstimate
    case deadReckoning
}

/// Minimal authorized observation projection. No route geometry or inferred
/// coordinates are introduced at this boundary.
public struct IOSNavigationCalibrationObservation {
    public let confidence: IOSNavigationObservationConfidence
    public let fusionMode: IOSNavigationFusionMode
    public let horizontalAccuracyM: Double?
    public let monotonicTimestampNanos: UInt64?

    public init(
        confidence: IOSNavigationObservationConfidence,
        fusionMode: IOSNavigationFusionMode,
        horizontalAccuracyM: Double?,
        monotonicTimestampNanos: UInt64?
    ) {
        self.confidence = confidence
        self.fusionMode = fusionMode
        self.horizontalAccuracyM = horizontalAccuracyM
        self.monotonicTimestampNanos = monotonicTimestampNanos
    }

    public var directFreshObservation: Bool {
        guard
            confidence == .high,
            fusionMode == .directObservation,
            monotonicTimestampNanos != nil,
            let accuracy = horizontalAccuracyM,
            accuracy.isFinite,
            accuracy >= 0.0,
            accuracy <= 100.0
        else {
            return false
        }
        return true
    }
}
