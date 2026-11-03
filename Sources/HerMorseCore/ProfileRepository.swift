import Foundation

/// One atomic JSON file, never filenames derived from imported profile names.
public final class ProfileRepository {
    public let url: URL
    public init(url: URL) { self.url = url }
    public func load() throws -> [ProfileDocument] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        let profiles = try JSONDecoder().decode([ProfileDocument].self, from: data)
        guard Set(profiles.map(\.name)).count == profiles.count else { throw ProfileError.invalidFormat }
        return profiles
    }
    public func save(_ profiles: [ProfileDocument]) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        try encoder.encode(profiles).write(to: url, options: .atomic)
    }
}
