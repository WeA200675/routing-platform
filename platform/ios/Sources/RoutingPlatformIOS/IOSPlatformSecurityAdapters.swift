import Foundation
#if canImport(Security)
import Security
#endif
#if canImport(LocalAuthentication)
import LocalAuthentication
#endif

public enum IOSSecureStoreError: Error, Equatable {
    case unavailable
    case invalidKey
    case osStatus(Int32)
}

public struct IOSKeychainSecureStore {
    public let service: String
    public init(service: String) { self.service = service }

    public func store(_ data: Data, account: String) throws {
        guard !service.isEmpty, !account.isEmpty else { throw IOSSecureStoreError.invalidKey }
        #if canImport(Security)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else { throw IOSSecureStoreError.osStatus(status) }
        #else
        throw IOSSecureStoreError.unavailable
        #endif
    }

    public func load(account: String) throws -> Data? {
        guard !service.isEmpty, !account.isEmpty else { throw IOSSecureStoreError.invalidKey }
        #if canImport(Security)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw IOSSecureStoreError.osStatus(status)
        }
        return data
        #else
        throw IOSSecureStoreError.unavailable
        #endif
    }

    public func delete(account: String) throws {
        guard !service.isEmpty, !account.isEmpty else { throw IOSSecureStoreError.invalidKey }
        #if canImport(Security)
        let status = SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ] as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw IOSSecureStoreError.osStatus(status)
        }
        #else
        throw IOSSecureStoreError.unavailable
        #endif
    }
}

public enum IOSLocalAuthenticationCapability: Equatable {
    case available
    case unavailable
}

public enum IOSLocalAuthenticationAdapter {
    public static func capability() -> IOSLocalAuthenticationCapability {
        #if canImport(LocalAuthentication)
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) ? .available : .unavailable
        #else
        return .unavailable
        #endif
    }
}
