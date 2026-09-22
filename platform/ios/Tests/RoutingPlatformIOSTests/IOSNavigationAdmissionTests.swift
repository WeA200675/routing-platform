import XCTest
@testable import RoutingPlatformIOS

final class IOSNavigationAdmissionTests: XCTestCase {
    func testCalibrationRequiresAllCapabilities() {
        XCTAssertTrue(IOSNavigationDeviceCapabilities(
            preciseLocationAvailable: true,
            directObservationAvailable: true,
            monotonicTimestampAvailable: true
        ).calibrationAvailable)

        XCTAssertFalse(IOSNavigationDeviceCapabilities(
            preciseLocationAvailable: false,
            directObservationAvailable: true,
            monotonicTimestampAvailable: true
        ).calibrationAvailable)
        XCTAssertFalse(IOSNavigationDeviceCapabilities(
            preciseLocationAvailable: true,
            directObservationAvailable: false,
            monotonicTimestampAvailable: true
        ).calibrationAvailable)
        XCTAssertFalse(IOSNavigationDeviceCapabilities(
            preciseLocationAvailable: true,
            directObservationAvailable: true,
            monotonicTimestampAvailable: false
        ).calibrationAvailable)
    }

    func testDirectObservationFailsClosed() {
        let valid = IOSNavigationCalibrationObservation(
            confidence: .high,
            fusionMode: .directObservation,
            horizontalAccuracyM: 4.0,
            monotonicTimestampNanos: 1
        )
        XCTAssertTrue(valid.directFreshObservation)

        XCTAssertFalse(IOSNavigationCalibrationObservation(
            confidence: .medium,
            fusionMode: .directObservation,
            horizontalAccuracyM: 4.0,
            monotonicTimestampNanos: 1
        ).directFreshObservation)
        XCTAssertFalse(IOSNavigationCalibrationObservation(
            confidence: .high,
            fusionMode: .fusedEstimate,
            horizontalAccuracyM: 4.0,
            monotonicTimestampNanos: 1
        ).directFreshObservation)
        XCTAssertFalse(IOSNavigationCalibrationObservation(
            confidence: .high,
            fusionMode: .directObservation,
            horizontalAccuracyM: nil,
            monotonicTimestampNanos: 1
        ).directFreshObservation)
        XCTAssertFalse(IOSNavigationCalibrationObservation(
            confidence: .high,
            fusionMode: .directObservation,
            horizontalAccuracyM: .infinity,
            monotonicTimestampNanos: 1
        ).directFreshObservation)
        XCTAssertFalse(IOSNavigationCalibrationObservation(
            confidence: .high,
            fusionMode: .directObservation,
            horizontalAccuracyM: 100.1,
            monotonicTimestampNanos: 1
        ).directFreshObservation)
        XCTAssertFalse(IOSNavigationCalibrationObservation(
            confidence: .high,
            fusionMode: .directObservation,
            horizontalAccuracyM: 4.0,
            monotonicTimestampNanos: nil
        ).directFreshObservation)
    }
}
