import Foundation
import AVFAudio
import ShazamKit
import Network
import Combine
import SongFlipKit

enum AcousticRecognitionState: Equatable {
    case idle
    case listening(amplitude: Float, elapsedSeconds: Int)
    case resolving(title: String, artist: String, artworkUrl: String?)
    case matched(flipResult: AcousticFlipResult)
    case offlineQueued(pendingCount: Int)
    case noMatch
    case quotaReached
    case permissionDenied
    case error(messageKey: String)

    static func == (lhs: AcousticRecognitionState, rhs: AcousticRecognitionState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle),
             (.noMatch, .noMatch),
             (.quotaReached, .quotaReached),
             (.permissionDenied, .permissionDenied):
            return true
        case let (.listening(a1, s1), .listening(a2, s2)):
            return abs(a1 - a2) < 0.01 && s1 == s2
        case let (.resolving(t1, ar1, u1), .resolving(t2, ar2, u2)):
            return t1 == t2 && ar1 == ar2 && u1 == u2
        case let (.matched(r1), .matched(r2)):
            return r1.cacheKey == r2.cacheKey && r1.targetUrl == r2.targetUrl
        case let (.offlineQueued(c1), .offlineQueued(c2)):
            return c1 == c2
        case let (.error(k1), .error(k2)):
            return k1 == k2
        default:
            return false
        }
    }
}

private struct MatchedTrackInfo: Sendable {
    let title: String
    let artist: String
    let artworkUrl: String?
    let appleMusicUrl: String?
    let appleMusicId: String?
    let webUrl: String?
}

private nonisolated final class ShazamSessionBox: NSObject, SHSessionDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var hasResumed = false
    var onMatch: (@Sendable (MatchedTrackInfo) -> Void)?
    var onNoMatch: (@Sendable (Error?) -> Void)?

    nonisolated func tryMarkResumed() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if hasResumed { return false }
        hasResumed = true
        return true
    }

    nonisolated func session(_ session: SHSession, didFind match: SHMatch) {
        if let first = match.mediaItems.first {
            let title = (first.title?.isEmpty == false) ? first.title! : "Unknown Track"
            let artist = (first.artist?.isEmpty == false) ? first.artist! : (first.subtitle ?? "")
            let info = MatchedTrackInfo(
                title: title,
                artist: artist,
                artworkUrl: first.artworkURL?.absoluteString,
                appleMusicUrl: first.appleMusicURL?.absoluteString,
                appleMusicId: first.appleMusicID,
                webUrl: first.webURL?.absoluteString
            )
            onMatch?(info)
        } else {
            onNoMatch?(nil)
        }
    }

    nonisolated func session(_ session: SHSession, didNotFindMatchFor signature: SHSignature, error: Error?) {
        onNoMatch?(error)
    }
}

@MainActor
final class AcousticRecognizer: ObservableObject {
    static let shared = AcousticRecognizer()

    @Published private(set) var state: AcousticRecognitionState = .idle

    private var audioEngine: AVAudioEngine?
    private var shazamSession: SHSession?
    private var sessionBox: ShazamSessionBox?
    private var signatureGenerator: SHSignatureGenerator?
    private var listeningTask: Task<Void, Never>?
    private let pathMonitor = NWPathMonitor()
    private(set) var isOnline: Bool = true
    private var cachedDeveloperToken: String? = nil
    private var cachedTokenExpiryEpochSec: Int64 = 0

    private init() {
        pathMonitor.pathUpdateHandler = { [weak self] path in
            let online = (path.status == .satisfied)
            Task { @MainActor [weak self] in
                self?.isOnline = online
            }
        }
        pathMonitor.start(queue: DispatchQueue(label: "de.goork.songflip.network.monitor"))
    }

    func getRemainingFreeScans(isPro: Bool) -> Int {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        return Int(AcousticQuotaManager.companion.shared.getRemainingFreeScans(
            isPro: isPro,
            timestampMs: nowMs
        ))
    }

    func resetState() {
        stopListening()
        state = .idle
    }

    func stopListening() {
        listeningTask?.cancel()
        listeningTask = nil
        teardownAudioEngine()
        if case .listening = state {
            state = .idle
        }
    }

    private func teardownAudioEngine() {
        if let engine = audioEngine {
            engine.inputNode.removeTap(onBus: 0)
            if engine.isRunning {
                engine.stop()
            }
        }
        audioEngine = nil
        shazamSession = nil
        sessionBox = nil
        signatureGenerator = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func startListening(targetPlatformKey: String, isPro: Bool) {
        stopListening()

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        if !AcousticQuotaManager.companion.shared.canStartScan(isPro: isPro, timestampMs: nowMs) {
            state = .quotaReached
            return
        }

        if !isOnline && !isPro {
            state = .error(messageKey: "listen_error_offline")
            return
        }

        listeningTask = Task { @MainActor [weak self] in
            guard let self = self else { return }

            let granted = await Self.requestRecordPermission()
            guard !Task.isCancelled else { return }
            if !granted {
                self.state = .permissionDenied
                return
            }

            await self.runRecognitionLoop(targetPlatformKey: targetPlatformKey, isPro: isPro)
        }
    }

    private static func requestRecordPermission() async -> Bool {
        if #available(iOS 17.0, *) {
            return await AVAudioApplication.requestRecordPermission()
        } else {
            return await withCheckedContinuation { continuation in
                AVAudioSession.sharedInstance().requestRecordPermission { allowed in
                    continuation.resume(returning: allowed)
                }
            }
        }
    }

    private func runRecognitionLoop(targetPlatformKey: String, isPro: Bool) async {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothHFP, .mixWithOthers])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

            let engine = AVAudioEngine()
            let inputNode = engine.inputNode
            let recordingFormat = inputNode.outputFormat(forBus: 0)
            let monoFormat = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: recordingFormat.sampleRate,
                channels: 1,
                interleaved: false
            )

            let sigGen = SHSignatureGenerator()
            let sigLock = NSLock()
            self.signatureGenerator = sigGen

            let session = SHSession()
            let box = ShazamSessionBox()
            session.delegate = box
            self.shazamSession = session
            self.sessionBox = box

            var matchedItem: MatchedTrackInfo? = nil
            box.onMatch = { item in
                Task { @MainActor in
                    if matchedItem == nil {
                        matchedItem = item
                    }
                }
            }

            let startTime = Date()
            let onlineAtStart = self.isOnline

            inputNode.installTap(onBus: 0, bufferSize: 2048, format: recordingFormat) { [weak self] buffer, audioTime in
                let (monoBuffer, rms) = Self.prepareMonoBufferWithAgc(from: buffer, monoFormat: monoFormat)
                let targetBuffer = monoBuffer ?? buffer
                sigLock.lock()
                try? sigGen.append(targetBuffer, at: nil)
                sigLock.unlock()
                if onlineAtStart {
                    session.matchStreamingBuffer(targetBuffer, at: audioTime)
                }
                Task { @MainActor [weak self] in
                    guard let self = self, case .listening = self.state else { return }
                    let elapsed = Int(Date().timeIntervalSince(startTime))
                    self.state = .listening(amplitude: rms, elapsedSeconds: elapsed)
                }
            }

            self.audioEngine = engine
            self.state = .listening(amplitude: 0.15, elapsedSeconds: 0)

            engine.prepare()
            try engine.start()

            let snapshotThresholds: [TimeInterval] = [3.5, 6.5, 9.5]
            var nextSnapshotIdx = 0

            // Poll up to 12.0 seconds for either a live match or offline 6-second signature capture
            while !Task.isCancelled && Date().timeIntervalSince(startTime) < 12.0 {
                if matchedItem != nil {
                    break
                }
                let elapsed = Date().timeIntervalSince(startTime)
                if !self.isOnline && isPro && elapsed >= 6.0 {
                    sigLock.lock()
                    let signature = sigGen.signature()
                    sigLock.unlock()
                    teardownAudioEngine()
                    saveOfflineSignature(signature)
                    return
                }
                if self.isOnline && nextSnapshotIdx < snapshotThresholds.count && elapsed >= snapshotThresholds[nextSnapshotIdx] {
                    nextSnapshotIdx += 1
                    sigLock.lock()
                    let snapSignature = sigGen.signature()
                    sigLock.unlock()
                    if snapSignature.duration >= 2.2 {
                        let b64 = snapSignature.dataRepresentation.base64EncodedString()
                        if !b64.isEmpty, let restMatch = await matchSignatureBase64Direct(b64) {
                            matchedItem = restMatch
                            break
                        }
                    }
                }
                try? await Task.sleep(nanoseconds: 150_000_000)
            }

            guard !Task.isCancelled else {
                teardownAudioEngine()
                return
            }

            sigLock.lock()
            let finalSignature = sigGen.signature()
            sigLock.unlock()
            teardownAudioEngine()

            if matchedItem == nil && !self.isOnline {
                if isPro {
                    saveOfflineSignature(finalSignature)
                } else {
                    self.state = .error(messageKey: "listen_error_offline")
                }
                return
            }

            if matchedItem == nil && self.isOnline && finalSignature.duration >= 2.2 {
                let b64 = finalSignature.dataRepresentation.base64EncodedString()
                if !b64.isEmpty {
                    matchedItem = await matchSignatureBase64Direct(b64)
                }
                if matchedItem == nil {
                    matchedItem = await matchSingleSignature(finalSignature)
                }
            }

            guard let winner = matchedItem else {
                AptabaseClient.shared.trackAcousticScanCompleted(
                    target: targetPlatformKey,
                    matched: false,
                    isPro: isPro,
                    isOfflineQueue: false
                )
                self.state = .noMatch
                return
            }

            self.state = .resolving(title: winner.title, artist: winner.artist, artworkUrl: winner.artworkUrl)

            let targetPlatform = MusicPlatform.companion.fromKey(key: targetPlatformKey)
            let flipResult = try await AcousticQuotaManager.companion.shared.resolveAndStoreAcousticMatch(
                engine: SongLinkEngine.shared,
                title: winner.title,
                artist: winner.artist,
                artworkUrl: winner.artworkUrl,
                appleMusicUrl: winner.appleMusicUrl,
                appleMusicId: winner.appleMusicId,
                webUrl: winner.webUrl,
                targetPlatform: targetPlatform,
                isPro: isPro,
                timestampMs: Int64(Date().timeIntervalSince1970 * 1000)
            )

            await HistoryModel.shared.syncFromKmpStore()

            AptabaseClient.shared.trackAcousticScanCompleted(
                target: targetPlatformKey,
                matched: true,
                isPro: isPro,
                isOfflineQueue: false
            )

            self.state = .matched(flipResult: flipResult)
        } catch {
            teardownAudioEngine()
            self.state = .error(messageKey: "listen_no_match_title")
        }
    }

    private func saveOfflineSignature(_ signature: SHSignature) {
        let manager = AcousticQuotaManager.companion.shared
        let base64 = signature.dataRepresentation.base64EncodedString()
        let durationMs = Int64(signature.duration * 1000.0)
        manager.enqueuePendingAcousticScan(
            id: UUID().uuidString,
            signatureBase64: base64,
            durationMs: durationMs,
            createdAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
        let pendingCount = Int(manager.countPendingAcousticScans())
        AptabaseClient.shared.trackAcousticOfflineQueued()
        self.state = .offlineQueued(pendingCount: pendingCount)
    }

    func processPendingOfflineScansIfNeeded(targetPlatformKey: String, isPro: Bool) async -> Int {
        guard isOnline else { return 0 }
        let manager = AcousticQuotaManager.companion.shared
        let pending = manager.getPendingAcousticScans()
        guard !pending.isEmpty else { return 0 }

        let targetPlatform = MusicPlatform.companion.fromKey(key: targetPlatformKey)
        var resolvedCount = 0

        for scan in pending {
            guard !scan.signatureBase64.isEmpty else {
                manager.deletePendingAcousticScan(id: scan.id)
                continue
            }

            var matched = await matchSignatureBase64Direct(scan.signatureBase64)
            if matched == nil,
               let sigData = Data(base64Encoded: scan.signatureBase64),
               let signature = try? SHSignature(dataRepresentation: sigData) {
                matched = await matchSingleSignature(signature)
            }

            if let winner = matched {
                _ = try? await manager.resolveAndStoreAcousticMatch(
                    engine: SongLinkEngine.shared,
                    title: winner.title,
                    artist: winner.artist,
                    artworkUrl: winner.artworkUrl,
                    appleMusicUrl: winner.appleMusicUrl,
                    appleMusicId: winner.appleMusicId,
                    webUrl: winner.webUrl,
                    targetPlatform: targetPlatform,
                    isPro: isPro,
                    timestampMs: Int64(Date().timeIntervalSince1970 * 1000)
                )
                resolvedCount += 1
                AptabaseClient.shared.trackAcousticScanCompleted(
                    target: targetPlatformKey,
                    matched: true,
                    isPro: isPro,
                    isOfflineQueue: true
                )
            }
            manager.deletePendingAcousticScan(id: scan.id)
        }

        if resolvedCount > 0 {
            await HistoryModel.shared.syncFromKmpStore()
        }
        return resolvedCount
    }

    private func getDeveloperToken() async -> String? {
        let nowSec = Int64(Date().timeIntervalSince1970)
        if let cached = cachedDeveloperToken, nowSec < cachedTokenExpiryEpochSec - 120 {
            return cached
        }
        guard let url = URL(string: "https://cache.songflip.link/getShazamToken") else { return cachedDeveloperToken }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        req.setValue("SongFlip-iOS", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let token = json["token"] as? String, !token.isEmpty else {
                return cachedDeveloperToken
            }
            let expiresAt = (json["expiresAt"] as? NSNumber)?.int64Value ?? (nowSec + 3600)
            cachedDeveloperToken = token
            cachedTokenExpiryEpochSec = expiresAt
            return token
        } catch {
            return cachedDeveloperToken
        }
    }

    private func matchSignatureBase64Direct(_ signatureBase64: String) async -> MatchedTrackInfo? {
        guard let token = await getDeveloperToken(), !token.isEmpty else { return nil }
        let rawCountry = Locale.current.region?.identifier.lowercased() ?? "de"
        let storeFront = (rawCountry.count == 2) ? rawCountry : "de"
        let urlString = "https://api.shazam.apple.com/v1/catalog/\(storeFront)/match?types=shazam-songs&include%5Bshazam-songs%5D=songs&include%5Bsongs%5D=music-videos&meta=matchOffset%2CfrequencySkew&format%5Bresources%5D=map"
        guard let url = URL(string: urlString) else { return nil }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 8
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("ShazamKit/2.1.1 iOS", forHTTPHeaderField: "User-Agent")
        req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")

        let payload: [String: Any] = [
            "data": [
                [
                    "type": "signatures",
                    "attributes": [
                        "signature": "data:audio/vnd.shazam.sig;base64,\(signatureBase64)"
                    ]
                ]
            ]
        ]

        do {
            req.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let resources = root["resources"] as? [String: Any],
                  let shazamSongs = resources["shazam-songs"] as? [String: Any],
                  let firstKey = shazamSongs.keys.first,
                  let shazamSongObj = shazamSongs[firstKey] as? [String: Any],
                  let attributes = shazamSongObj["attributes"] as? [String: Any] else {
                return nil
            }

            let title = ((attributes["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 } ?? "Unknown Track"
            let artist = ((attributes["artist"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines))
                ?? ((attributes["primaryArtist"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines))
                ?? ""

            var artworkUrl = (attributes["artwork"] as? [String: Any])?["url"] as? String
            var appleMusicId: String? = nil
            var appleMusicUrl: String? = nil

            if let relationships = shazamSongObj["relationships"] as? [String: Any],
               let songsRel = relationships["songs"] as? [String: Any],
               let relData = songsRel["data"] as? [[String: Any]],
               let firstSong = relData.first,
               let id = firstSong["id"] as? String, !id.isEmpty {
                appleMusicId = id
                if let songsMap = resources["songs"] as? [String: Any],
                   let appleSongObj = songsMap[id] as? [String: Any],
                   let appleAttrs = appleSongObj["attributes"] as? [String: Any] {
                    if let url = appleAttrs["url"] as? String, !url.isEmpty {
                        appleMusicUrl = url
                    }
                    if (artworkUrl ?? "").isEmpty,
                       let appleArt = (appleAttrs["artwork"] as? [String: Any])?["url"] as? String,
                       !appleArt.isEmpty {
                        artworkUrl = appleArt
                    }
                }
            }

            if let rawArt = artworkUrl, !rawArt.isEmpty {
                artworkUrl = rawArt
                    .replacingOccurrences(of: "{w}", with: "800")
                    .replacingOccurrences(of: "{h}", with: "800")
            }

            let webUrl = (attributes["webUrl"] as? String) ?? appleMusicUrl
            return MatchedTrackInfo(
                title: title,
                artist: artist,
                artworkUrl: artworkUrl,
                appleMusicUrl: appleMusicUrl,
                appleMusicId: appleMusicId,
                webUrl: webUrl
            )
        } catch {
            return nil
        }
    }

    private func matchSingleSignature(_ signature: SHSignature) async -> MatchedTrackInfo? {
        await withCheckedContinuation { continuation in
            let session = SHSession()
            let box = ShazamSessionBox()
            box.onMatch = { item in
                guard box.tryMarkResumed() else { return }
                continuation.resume(returning: item)
            }
            box.onNoMatch = { _ in
                guard box.tryMarkResumed() else { return }
                continuation.resume(returning: nil)
            }
            session.delegate = box
            // Retain session & box until callback fires
            objc_setAssociatedObject(session, "box", box, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            session.match(signature)
        }
    }

    nonisolated private static func prepareMonoBufferWithAgc(
        from buffer: AVAudioPCMBuffer,
        monoFormat: AVAudioFormat?
    ) -> (AVAudioPCMBuffer?, Float) {
        let frameLength = Int(buffer.frameLength)
        let channels = Int(buffer.format.channelCount)
        guard frameLength > 0, channels > 0, let channelData = buffer.floatChannelData else {
            return (nil, 0.15)
        }

        guard let format = monoFormat,
              let outBuffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: buffer.frameCapacity),
              let outChannel = outBuffer.floatChannelData?[0] else {
            return (nil, 0.15)
        }
        outBuffer.frameLength = buffer.frameLength

        var maxPeak: Float = 0
        for i in 0..<frameLength {
            var sample: Float = 0
            for c in 0..<channels {
                sample += channelData[c][i]
            }
            sample /= Float(channels)
            outChannel[i] = sample
            let absVal = abs(sample)
            if absVal > maxPeak {
                maxPeak = absVal
            }
        }

        let targetPeak: Float = 0.55
        let gain: Float
        if maxPeak > 0.0005 && maxPeak < targetPeak {
            gain = min(35.0, targetPeak / maxPeak)
        } else {
            gain = 1.0
        }

        var sumSq: Float = 0
        for i in 0..<frameLength {
            var boosted = outChannel[i] * gain
            if boosted > 0.98 { boosted = 0.98 }
            if boosted < -0.98 { boosted = -0.98 }
            outChannel[i] = boosted
            sumSq += boosted * boosted
        }

        let rms = sqrt(sumSq / Float(frameLength))
        let uiAmplitude = min(1.0, max(0.08, rms * 3.8))
        return (outBuffer, uiAmplitude)
    }
}

extension AcousticQuotaManager {
    public nonisolated static let shared = AcousticQuotaManager.companion.shared
    public nonisolated static let maxFreeScansPerMonth = Int(AcousticQuotaManager.companion.MAX_FREE_SCANS_PER_MONTH)
}
