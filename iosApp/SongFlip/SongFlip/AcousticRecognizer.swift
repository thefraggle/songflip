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

private nonisolated final class ShazamSessionBox: NSObject, SHSessionDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var hasResumed = false
    var onMatch: (@Sendable (SHMatchedMediaItem) -> Void)?
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
            onMatch?(first)
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

            let sigGen = SHSignatureGenerator()
            self.signatureGenerator = sigGen

            let session = SHSession()
            let box = ShazamSessionBox()
            session.delegate = box
            self.shazamSession = session
            self.sessionBox = box

            var matchedItem: SHMatchedMediaItem? = nil
            box.onMatch = { item in
                Task { @MainActor in
                    matchedItem = item
                }
            }

            let startTime = Date()
            let onlineAtStart = self.isOnline

            inputNode.installTap(onBus: 0, bufferSize: 2048, format: recordingFormat) { [weak self] buffer, audioTime in
                try? sigGen.append(buffer, at: audioTime)
                if onlineAtStart {
                    session.matchStreamingBuffer(buffer, at: audioTime)
                }
                let rms = Self.computeRms(from: buffer)
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

            // Poll up to 12.0 seconds for either a live match or offline 6-second signature capture
            while !Task.isCancelled && Date().timeIntervalSince(startTime) < 12.0 {
                if matchedItem != nil {
                    break
                }
                let elapsed = Date().timeIntervalSince(startTime)
                if !self.isOnline && isPro && elapsed >= 6.0 {
                    let signature = sigGen.signature()
                    teardownAudioEngine()
                    saveOfflineSignature(signature)
                    return
                }
                try? await Task.sleep(nanoseconds: 150_000_000)
            }

            guard !Task.isCancelled else {
                teardownAudioEngine()
                return
            }

            let finalSignature = sigGen.signature()
            teardownAudioEngine()

            if matchedItem == nil && !self.isOnline {
                if isPro {
                    saveOfflineSignature(finalSignature)
                } else {
                    self.state = .error(messageKey: "listen_error_offline")
                }
                return
            }

            if matchedItem == nil && self.isOnline && finalSignature.duration >= 3.0 {
                matchedItem = await matchSingleSignature(finalSignature)
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

            let title = (winner.title?.isEmpty == false) ? winner.title! : "Unknown Track"
            let artist = (winner.artist?.isEmpty == false) ? winner.artist! : (winner.subtitle ?? "")
            let artworkUrl = winner.artworkURL?.absoluteString

            self.state = .resolving(title: title, artist: artist, artworkUrl: artworkUrl)

            let targetPlatform = MusicPlatform.companion.fromKey(key: targetPlatformKey)
            let flipResult = try await AcousticQuotaManager.companion.shared.resolveAndStoreAcousticMatch(
                engine: SongLinkEngine.shared,
                title: title,
                artist: artist,
                artworkUrl: artworkUrl,
                appleMusicUrl: winner.appleMusicURL?.absoluteString,
                appleMusicId: winner.appleMusicID,
                webUrl: winner.webURL?.absoluteString,
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
            guard let sigData = Data(base64Encoded: scan.signatureBase64),
                  let signature = try? SHSignature(dataRepresentation: sigData) else {
                manager.deletePendingAcousticScan(id: scan.id)
                continue
            }

            let matched = await matchSingleSignature(signature)
            if let winner = matched {
                let title = (winner.title?.isEmpty == false) ? winner.title! : "Unknown Track"
                let artist = (winner.artist?.isEmpty == false) ? winner.artist! : (winner.subtitle ?? "")
                _ = try? await manager.resolveAndStoreAcousticMatch(
                    engine: SongLinkEngine.shared,
                    title: title,
                    artist: artist,
                    artworkUrl: winner.artworkURL?.absoluteString,
                    appleMusicUrl: winner.appleMusicURL?.absoluteString,
                    appleMusicId: winner.appleMusicID,
                    webUrl: winner.webURL?.absoluteString,
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

    private func matchSingleSignature(_ signature: SHSignature) async -> SHMatchedMediaItem? {
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

    nonisolated private static func computeRms(from buffer: AVAudioPCMBuffer) -> Float {
        guard let channelData = buffer.floatChannelData?[0] else { return 0.15 }
        let frameLength = Int(buffer.frameLength)
        guard frameLength > 0 else { return 0.15 }
        var sum: Float = 0
        let strideStep = max(1, frameLength / 256)
        var count = 0
        var i = 0
        while i < frameLength {
            let s = channelData[i]
            sum += s * s
            count += 1
            i += strideStep
        }
        let rms = sqrt(sum / Float(max(1, count)))
        let scaled = min(1.0, max(0.08, rms * 5.0))
        return scaled
    }
}

extension AcousticQuotaManager {
    public nonisolated static let shared = AcousticQuotaManager.companion.shared
    public nonisolated static let maxFreeScansPerMonth = Int(AcousticQuotaManager.companion.MAX_FREE_SCANS_PER_MONTH)
}
