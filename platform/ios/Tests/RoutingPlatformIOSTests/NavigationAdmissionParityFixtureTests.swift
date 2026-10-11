import XCTest
@testable import RoutingPlatformIOS

final class NavigationAdmissionParityFixtureTests: XCTestCase {
    private func rows() throws -> [[String]] {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<4 { url.deleteLastPathComponent() }
        url.appendPathComponent("shared/parity/navigation-admission-fixtures.csv")
        let text = try String(contentsOf: url, encoding: .utf8)
        return text.split(separator: "\n").map(String.init)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") }
            .map { $0.split(separator: ",", omittingEmptySubsequences: false).map(String.init) }
    }

    func testSharedParityCorpusMatchesIOSSemantics() throws {
        for p in try rows() {
            switch p[0] {
            case "capability":
                XCTAssertEqual(p.count, 5)
                let c = IOSNavigationDeviceCapabilities(
                    preciseLocationAvailable: p[1] == "true",
                    directObservationAvailable: p[2] == "true",
                    monotonicTimestampAvailable: p[3] == "true")
                XCTAssertEqual(c.calibrationAvailable, p[4] == "true", p.joined(separator: ","))
            case "observation":
                XCTAssertEqual(p.count, 6)
                let confidence: IOSNavigationObservationConfidence
                switch p[1] {
                case "high": confidence = .high
                case "medium": confidence = .medium
                case "low": confidence = .low
                default: XCTFail("unknown confidence"); continue
                }
                let fusion: IOSNavigationFusionMode
                switch p[2] {
                case "direct": fusion = .directObservation
                case "fused": fusion = .fusedEstimate
                case "dead_reckoning": fusion = .deadReckoning
                default: XCTFail("unknown fusion"); continue
                }
                let o = IOSNavigationCalibrationObservation(
                    confidence: confidence,
                    fusionMode: fusion,
                    horizontalAccuracyM: Double(p[3]),
                    monotonicTimestampNanos: p[4] == "true" ? 1 : nil)
                XCTAssertEqual(o.directFreshObservation, p[5] == "true", p.joined(separator: ","))
            default:
                XCTFail("unknown fixture type")
            }
        }
    }
}
