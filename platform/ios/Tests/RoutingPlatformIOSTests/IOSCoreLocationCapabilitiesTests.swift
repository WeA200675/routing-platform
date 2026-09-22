import CoreLocation
import XCTest
@testable import RoutingPlatformIOS

final class IOSCoreLocationCapabilitiesTests: XCTestCase {
    func testFullAccuracyAuthorizationAdmitsCalibration() {
        let capabilities = IOSCoreLocationCapabilities.detect(
            authorizationStatus: .authorizedAlways,
            accuracyAuthorization: .fullAccuracy
        )
        XCTAssertTrue(capabilities.calibrationAvailable)
    }

    func testReducedAccuracyFailsClosedForCalibration() {
        let capabilities = IOSCoreLocationCapabilities.detect(
            authorizationStatus: .authorizedAlways,
            accuracyAuthorization: .reducedAccuracy
        )
        XCTAssertFalse(capabilities.calibrationAvailable)
        XCTAssertTrue(capabilities.directObservationAvailable)
    }

    func testDeniedAuthorizationFailsClosed() {
        let capabilities = IOSCoreLocationCapabilities.detect(
            authorizationStatus: .denied,
            accuracyAuthorization: .fullAccuracy
        )
        XCTAssertFalse(capabilities.calibrationAvailable)
        XCTAssertFalse(capabilities.preciseLocationAvailable)
        XCTAssertFalse(capabilities.directObservationAvailable)
    }

    func testUndeterminedAuthorizationFailsClosed() {
        let capabilities = IOSCoreLocationCapabilities.detect(
            authorizationStatus: .notDetermined,
            accuracyAuthorization: .fullAccuracy
        )
        XCTAssertFalse(capabilities.calibrationAvailable)
        XCTAssertFalse(capabilities.directObservationAvailable)
    }
}
