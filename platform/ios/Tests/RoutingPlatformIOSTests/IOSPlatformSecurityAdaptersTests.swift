import XCTest
@testable import RoutingPlatformIOS

final class IOSPlatformSecurityAdaptersTests: XCTestCase {
    func testKeychainRejectsBlankIdentityFailClosed() {
        let store = IOSKeychainSecureStore(service: "")
        XCTAssertThrowsError(try store.store(Data([1]), account: "account"))
        XCTAssertThrowsError(try store.load(account: "account"))
        XCTAssertThrowsError(try store.delete(account: "account"))
    }

    func testAuthenticationCapabilityIsExplicit() {
        let capability = IOSLocalAuthenticationAdapter.capability()
        XCTAssertTrue(capability == .available || capability == .unavailable)
    }
}
