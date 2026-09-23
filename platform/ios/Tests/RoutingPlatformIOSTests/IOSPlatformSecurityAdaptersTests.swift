import XCTest
@testable import RoutingPlatformIOS

final class IOSPlatformSecurityAdaptersTests: XCTestCase {
    func testKeychainRejectsBlankIdentityFailClosed() {
        let store = IOSKeychainSecureStore(service: "")
        XCTAssertThrowsError(try store.store(Data([1]), account: "account"))
        XCTAssertThrowsError(try store.load(account: "account"))
        XCTAssertThrowsError(try store.delete(account: "account"))
    }

    func testKeychainRejectsBlankAccountFailClosed() {
        let store = IOSKeychainSecureStore(service: "org.routingplatform.tests")
        XCTAssertThrowsError(try store.store(Data([1]), account: ""))
        XCTAssertThrowsError(try store.load(account: ""))
        XCTAssertThrowsError(try store.delete(account: ""))
    }

    func testKeychainRoundTripWhenSecurityBackendIsAvailable() throws {
        #if canImport(Security)
        let service = "org.routingplatform.tests.\(UUID().uuidString)"
        let account = "round-trip"
        let payload = Data([0x52, 0x50, 0x32, 0x20])
        let store = IOSKeychainSecureStore(service: service)

        defer { try? store.delete(account: account) }
        try store.store(payload, account: account)
        XCTAssertEqual(try store.load(account: account), payload)
        try store.delete(account: account)
        XCTAssertNil(try store.load(account: account))
        #endif
    }

    func testAuthenticationCapabilityIsExplicit() {
        let capability = IOSLocalAuthenticationAdapter.capability()
        XCTAssertTrue(capability == .available || capability == .unavailable)
    }
}
