import XCTest
@testable import HerMorseCore

final class MorseTests: XCTestCase {
    func testAndroidAlphabetAndKochOrder() {
        XCTAssertEqual(Morse.all.count, 60)
        XCTAssertEqual(Morse.kochOrder.count, 40)
        XCTAssertEqual(Set(Morse.kochOrder).count, 40)
        XCTAssertTrue(Morse.kochOrder.allSatisfy { Morse.character($0) != nil })
        XCTAssertEqual(Morse.character("K")?.code, "-.-")
        XCTAssertEqual(Morse.character("É")?.code, "..-..")
    }
    func testProsignPriorityMatchesAndroid() {
        XCTAssertEqual(Morse.decode(".-.-."), "<AR>")
        XCTAssertEqual(Morse.decode("-...-"), "<BT>")
        XCTAssertEqual(Morse.decode("-.--."), "<KN>")
        XCTAssertEqual(Morse.character("+")?.code, ".-.-.")
        XCTAssertNil(Morse.decode("........."))
        XCTAssertTrue(Morse.equivalent("=","<BT>"))
        XCTAssertTrue(Morse.equivalent("+","<AR>"))
        XCTAssertFalse(Morse.equivalent("K","M"))
    }
    func testTokenizationKeepsProsignsAtomic() {
        XCTAssertEqual(Morse.tokens("  cq  <ar>\nÉ  "), ["C","Q"," ","<AR>"," ","É"])
        XCTAssertEqual(Morse.tokens("A%",preserveUnknown: true),["A","%"])
        XCTAssertEqual(Morse.tokens("A%"),["A"])
    }
    func testKochLessonPools() {
        XCTAssertEqual(Levels.characters(1),["K","M"])
        XCTAssertEqual(Levels.characters(39),Morse.kochOrder)
        XCTAssertEqual(Levels.title(40),"Opérateur · mots")
    }
    func testFarnsworthParisWordDuration() {
        for speeds in [(20,20),(20,5),(12,5),(30,30)] {
            let sequence = MorseTiming.sequence(tokens: ["P","A","R","I","S"," "],characterWpm: speeds.0,effectiveWpm: speeds.1)
            XCTAssertEqual(sequence.reduce(0) { $0+$1.milliseconds },60000/Double(speeds.1),accuracy: 0.0001)
        }
    }
    func testElementDurations() {
        let sequence = MorseTiming.sequence(tokens: ["A"],characterWpm: 20,effectiveWpm: 20)
        XCTAssertEqual(sequence.map(\.milliseconds),[60,60,180,180])
        XCTAssertEqual(sequence.map(\.on),[true,false,true,false])
    }
    func testContentUsesOnlyLessonCharacters() {
        for level in 1...39 {
            let text = TrainingContent.make(level: level,short: false,settings: Settings())
            let tokens = Morse.tokens(text).filter { $0 != " " }
            XCTAssertEqual(tokens.count,25)
            XCTAssertTrue(tokens.allSatisfy { Levels.characters(level).contains($0) })
        }
    }
    func testCustomProsignsGenerateAtomicTokens() {
        var s = Settings(); s.guided = false; s.useLetters = false; s.useDigits = false; s.useProsigns = true
        let text = TrainingContent.make(level: 1,short: true,settings: s)
        let tokens = Morse.tokens(text).filter { $0 != " " }
        XCTAssertEqual(tokens.count,20)
        XCTAssertTrue(tokens.allSatisfy { Morse.character($0)?.category == .prosigns })
    }
}

final class ProfileTests: XCTestCase {
    func testMinimalAndroidProfileDefaults() throws {
        let data = Data(#"{"format":"hermorse","version":1,"name":"F4ABC","settings":{},"stats":{}}"#.utf8)
        let profile = try ProfileDocument.read(data)
        XCTAssertEqual(profile.settings,Settings())
        XCTAssertEqual(profile.stats,Stats())
        XCTAssertEqual(profile.name,"F4ABC")
    }
    func testFullAndroidJSONRoundTrip() throws {
        let data = Data(#"{"format":"hermorse","version":1,"name":"Élodie","settings":{"keyMode":"iambicB","volume":0.4,"callsign":"F4ABC","qsoAfc":false},"stats":{"rxSpeed3":4,"rxLevel":18,"txLevel":7,"rxChars":{"K":[8,10]},"history":[{"t":1700000000000,"k":"rx","l":17,"a":0.92,"w":12}],"trophies":{"km":1700000000000},"days":{"2026-11-03":10000}}}"#.utf8)
        let profile = try ProfileDocument.read(data)
        XCTAssertEqual(profile.stats.rxSpeed3,4)
        XCTAssertEqual(profile.stats.rxChars["K"],[8,10])
        XCTAssertEqual(profile.settings.keyMode,"iambicB")
        let roundTrip = try ProfileDocument.read(profile.data())
        XCTAssertEqual(profile,roundTrip)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: profile.data()) as? [String:Any])
        let stats = try XCTUnwrap(json["stats"] as? [String:Any])
        XCTAssertEqual(stats["rxSpeed3"] as? Int,4)
        XCTAssertNil(stats["rxSpeedIdx"])
    }
    func testInvalidFormatAndFutureVersionRejected() {
        for raw in [#"{"format":"other"}"#,#"{"format":"hermorse","version":2}"#,"not json"] {
            XCTAssertThrowsError(try ProfileDocument.read(Data(raw.utf8)))
        }
    }
    func testMalformedCharacterStatisticsRejected() {
        let raw = #"{"format":"hermorse","stats":{"rxChars":{"A":[3]}}}"#
        XCTAssertThrowsError(try ProfileDocument.read(Data(raw.utf8)))
    }
    func testImportedRangesAreClamped() throws {
        let raw = #"{"format":"hermorse","settings":{"keyerWpm":0,"volume":9,"listenWpm":10,"farnsworthWpm":50},"stats":{"rxLevel":800,"rxSpeed3":-9}}"#
        let p = try ProfileDocument.read(Data(raw.utf8))
        XCTAssertEqual(p.settings.keyerWpm,5); XCTAssertEqual(p.settings.volume,1)
        XCTAssertEqual(p.settings.farnsworthWpm,10); XCTAssertEqual(p.stats.rxLevel,42); XCTAssertEqual(p.stats.rxSpeed3,0)
    }
    func testImportNameCollisions() {
        XCTAssertEqual(ProfileDocument.availableName("Paul",existing: ["Paul","Paul (2)"]),"Paul (3)")
    }
    func testAtomicRepositorySaveReloadAndDelete() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let repo = ProfileRepository(url: dir.appendingPathComponent("profiles.json"))
        XCTAssertEqual(try repo.load(),[])
        let profile = ProfileDocument(name: "../../Élodie")
        try repo.save([profile])
        XCTAssertEqual(try repo.load(),[profile])
        try repo.save([]); XCTAssertEqual(try repo.load(),[])
    }
    func testCorruptRepositoryIsNotSilentlyOverwritten() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let data = Data("broken".utf8); try data.write(to: url)
        XCTAssertThrowsError(try ProfileRepository(url: url).load())
        XCTAssertEqual(try Data(contentsOf: url),data)
    }
}

final class ProgressionTests: XCTestCase {
    private func finish(_ stats: inout Stats, accuracy: Double, reception: Bool = true, settings: Settings = Settings()) {
        _ = Progression.finish(stats: &stats,settings: settings,reception: reception,
                               results: [.init("K",true)],charWpm: 12,effectiveWpm: 5,accuracy: accuracy)
    }
    func testNinetyPercentAdvancesOnlySelectedTrack() {
        var stats = Stats(); finish(&stats,accuracy: 0.9)
        XCTAssertEqual(stats.rxLevel,2); XCTAssertEqual(stats.txLevel,1)
        XCTAssertEqual(stats.rxChars["K"],[1,1]); XCTAssertEqual(stats.history.last?.l,1)
        finish(&stats,accuracy: 0.89,reception: false)
        XCTAssertEqual(stats.txLevel,1)
    }
    func testSpeedLadderEveryFourLessons() {
        var stats = Stats(); stats.rxLevel = 3
        finish(&stats,accuracy: 1)
        XCTAssertEqual(stats.rxLevel,4); XCTAssertEqual(stats.rxSpeed3,1)
        stats.rxLevel = 39; finish(&stats,accuracy: 1)
        XCTAssertEqual(stats.rxLevel,40)
    }
    func testPhaseRequiresThreeConsecutiveSuccesses() {
        var stats = Stats(); stats.rxLevel = 40
        finish(&stats,accuracy: 1); finish(&stats,accuracy: 1); finish(&stats,accuracy: 0.5)
        XCTAssertEqual(stats.rxStreak,0); XCTAssertEqual(stats.rxLevel,40)
        for _ in 0..<3 { finish(&stats,accuracy: 0.9) }
        XCTAssertEqual(stats.rxLevel,41); XCTAssertEqual(stats.rxStreak,0)
    }
    func testCustomModeDoesNotAdvance() {
        var stats = Stats(), settings = Settings(); settings.guided = false
        finish(&stats,accuracy: 1,settings: settings)
        XCTAssertEqual(stats.rxLevel,1); XCTAssertEqual(stats.series,1)
    }
    func testHistoryRemainsBounded() {
        var stats = Stats()
        for _ in 0..<220 { finish(&stats,accuracy: 0.5) }
        XCTAssertEqual(stats.history.count,200)
    }
    func testEditAlignmentHandlesMissingAndExtraCharacters() {
        let r = Alignment.compare(expected: ["K","M","R"],typed: ["K","R"])
        XCTAssertEqual(r.correct,[true,false,true])
        let extras = Alignment.compare(expected: ["K","M"],typed: ["K","M","X"])
        XCTAssertLessThan(extras.accuracy,1)
        XCTAssertEqual(extras.insertions,1)
        XCTAssertEqual(Alignment.compare(expected: [],typed: []).accuracy,0)
        let substitution = Alignment.compare(expected: ["K"],typed: ["M"])
        XCTAssertEqual(substitution.substitutions.first?.0,"K")
    }
    func testTrophyConditions() {
        var stats = Stats(); XCTAssertEqual(Trophies.check(stats),[])
        stats.totalChars = 1; stats.rxLevel = 2; stats.errors = 51
        XCTAssertTrue(Set(Trophies.check(stats)).isSuperset(of: ["premier_bip","km","dyslexique"]))
        stats.recentWpm = Array(repeating: 30,count: 50)
        XCTAssertTrue(Trophies.check(stats).contains("lievre"))
        XCTAssertFalse(Trophies.check(stats).contains("live"))
    }
    func testStreakAcrossDaylightSavingChange() throws {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Europe/Paris"))
        let date = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026,month: 10,day: 26,hour: 12)))
        let days: [String:Int64] = ["2026-10-24":1,"2026-10-25":1,"2026-10-26":1]
        XCTAssertEqual(TrainingDays.streak(days,now: date,calendar: calendar),3)
    }
}

final class KeyerTests: XCTestCase {
    func testStraightKeyDecodesSOSAndSpace() {
        var settings = Settings(); settings.adaptiveDecode = false; settings.fixedWpm = 12
        let keyer = Keyer(settings: settings); var events: [KeyEvent] = []
        keyer.emit = { events.append($0) }
        var time = 0.0
        for code in ["...","---","..."] {
            for mark in code {
                keyer.contact(dot: true,pressed: true,now: time)
                time += mark == "." ? 100 : 300
                keyer.contact(dot: true,pressed: false,now: time); time += 100
            }
            time += 201; keyer.tick(now: time)
        }
        keyer.tick(now: time + 700)
        let labels = events.compactMap { if case .character(let label,_,_) = $0 { return label }; return nil }
        XCTAssertEqual(labels,["S","O","S"])
        XCTAssertTrue(events.contains(.space))
    }
    func testIambicBRemembersOppositeAfterRelease() {
        for mode in ["iambicA","iambicB"] {
            var s = Settings(); s.keyMode = mode; s.keyerWpm = 12
            let k = Keyer(settings: s); var patterns: [String] = []
            k.emit = { if case .pattern(let p) = $0, !p.isEmpty { patterns.append(p) } }
            k.contact(dot: true,pressed: true,now: 0)
            k.contact(dot: false,pressed: true,now: 10)
            k.contact(dot: true,pressed: false,now: 50)
            k.contact(dot: false,pressed: false,now: 50)
            for t in stride(from: 100.0,through: 800.0,by: 100.0) { k.tick(now: t) }
            XCTAssertEqual(patterns.last,mode == "iambicB" ? ".-" : ".")
        }
    }
    func testResetReleasesHeldKeyAndDropsPendingDecode() {
        let k = Keyer(); var events: [KeyEvent] = []; k.emit = { events.append($0) }
        k.contact(dot: true,pressed: true,now: 0); k.reset(); k.tick(now: 10000)
        XCTAssertEqual(events.last,.pattern("")); XCTAssertTrue(events.contains(.tone(false)))
        XCTAssertFalse(events.contains { if case .character = $0 { return true }; return false })
    }
    func testSimultaneousStraightContactsDoNotReleaseEachOther() {
        let k = Keyer(); var events: [KeyEvent] = []; k.emit = { events.append($0) }
        k.contact(dot: true,pressed: true,now: 0); k.contact(dot: false,pressed: true,now: 10)
        k.contact(dot: true,pressed: false,now: 100)
        XCTAssertFalse(events.contains(.tone(false)))
        k.contact(dot: false,pressed: false,now: 110)
        XCTAssertTrue(events.contains(.tone(false)))
    }
    func testMicrophoneDurationDecoderIgnoresNoise() {
        let d = CWDecoder(); XCTAssertEqual(d.mark(5),[])
        for _ in 0..<3 { _ = d.mark(100); _ = d.silence(100) }
        XCTAssertEqual(d.silence(301),[.character("S")])
        XCTAssertEqual(d.silence(701),[.space]); XCTAssertEqual(d.silence(900),[])
    }
}

final class SignalTests: XCTestCase {
    func testResamplerIsIndependentOfBufferBoundaries() {
        let samples = (0..<4800).map { Float(sin(Double($0)*0.1)) }
        var a = CWResampler(), b = CWResampler()
        let all = a.process(samples,sampleRate: 48000)
        var chunks: [Float] = []
        for start in stride(from: 0,to: samples.count,by: 113) {
            chunks += b.process(Array(samples[start..<min(start+113,samples.count)]),sampleRate: 48000)
        }
        XCTAssertEqual(all,chunks); XCTAssertEqual(all.count,800)
    }
    func testSilenceDoesNotProduceCharactersOrSignal() {
        let analyzer = SignalAnalyzer(); var events: [DecoderEvent] = []; var signal = true
        analyzer.onDecode = { events.append($0) }; analyzer.onStatus = { signal = $0.signal }
        analyzer.feed(Array(repeating: 0,count: 16000))
        XCTAssertEqual(events,[]); XCTAssertFalse(signal)
    }
    func testSyntheticAudioSOSGoesThroughDetector() {
        let analyzer = SignalAnalyzer(); analyzer.afc = false
        var events: [DecoderEvent] = []; analyzer.onDecode = { events.append($0) }
        var samples = Array(repeating: Float(0),count: 4000)
        var phase = 0.0
        for segment in MorseTiming.sequence(tokens: ["S","O","S"],characterWpm: 12,effectiveWpm: 12) {
            for _ in 0..<Int(segment.milliseconds * 8) {
                samples.append(segment.on ? Float(0.7*sin(phase)) : 0)
                phase += 2 * .pi * 600 / 8000
            }
        }
        samples += Array(repeating: 0,count: 8000)
        for start in stride(from: 0,to: samples.count,by: 137) { analyzer.feed(Array(samples[start..<min(start+137,samples.count)])) }
        let labels = events.compactMap { if case .character(let label) = $0 { return label }; return nil }
        XCTAssertEqual(labels,["S","O","S"])
    }
    func testAutomaticFrequencyTrackingFindsTone() {
        let analyzer = SignalAnalyzer(); var tracked = 0.0
        analyzer.onStatus = { tracked = $0.frequency }
        analyzer.feed((0..<16000).map { Float(0.7*sin(2 * .pi * 740 * Double($0)/8000)) })
        XCTAssertEqual(tracked,740,accuracy: 10)
    }
}
