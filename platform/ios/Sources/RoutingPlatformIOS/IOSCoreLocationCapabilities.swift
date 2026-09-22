import CoreLocation
import Foundation

/// Apple-authorized Core Location capability adapter.
///
/// This adapter reports only what the OS authorization/accuracy state proves.
/// It does not synthesize permission or measurement availability.
public enum IOSCoreLocationCapabilities {
    public static func detect(
        authorizationStatus: CLAuthorizationStatus,
        accuracyAuthorization: CLAccuracyAuthorization
    ) -> IOSNavigationDeviceCapabilities {
        let locationAuthorized =
            authorizationStatus == .authorizedAlways ||
            authorizationStatus == .authorizedWhenInUse

        return IOSNavigationDeviceCapabilities(
            preciseLocationAvailable:
                locationAuthorized && accuracyAuthorization == .fullAccuracy,
            directObservationAvailable: locationAuthorized,
            monotonicTimestampAvailable: true
        )
    }
}
