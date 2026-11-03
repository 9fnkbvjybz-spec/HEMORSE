import Foundation

/// Stateful linear resampling preserves sample timing across microphone buffers.
public struct CWResampler {
    private var index: Int64 = -1
    private var next = 0.0, previous: Float = 0
    public init() {}
    public mutating func process(_ samples: [Float], sampleRate: Double) -> [Float] {
        guard sampleRate >= 8000 else { return [] }
        let step = sampleRate / 8000
        var result: [Float] = []
        for sample in samples {
            index += 1
            while next <= Double(index) {
                let fraction = Float(next - Double(index - 1))
                result.append(index == 0 ? sample : previous + (sample - previous) * fraction)
                next += step
            }
            previous = sample
        }
        return result
    }
}

public struct SignalStatus {
    public let level: Double
    public let keyed: Bool
    public let signal: Bool
    public let spectrum: [Double]
    public let frequency: Double
    public let wpm: Double
}
/// Android's 10ms Goertzel detector and 512-sample spectral/AFC analysis, at 8kHz.
/// Feed and configure on one serial queue. No AVFoundation dependency.
public final class SignalAnalyzer {
    public var frequency = 600.0
    public var afc = true
    public var sensitivity = 0.5
    public var onDecode: (DecoderEvent) -> Void = { _ in }
    public var onStatus: (SignalStatus) -> Void = { _ in }
    private var pending: [Float] = []
    private var ring = Array(repeating: 0.0, count: 512), ringPosition = 0
    private var floor = 0.0001, peak = 0.0001
    private var keyed = false, stateMs = 0.0, blocks = 0
    private var candidate = 0.0, candidateCount = 0
    private let decoder = CWDecoder()
    public init() {}
    public func feed(_ samples: [Float]) {
        pending.append(contentsOf: samples)
        var offset = 0
        while offset + 80 <= pending.count {
            process(Array(pending[offset..<(offset+80)])); offset += 80
        }
        if offset > 0 { pending.removeFirst(offset) }
    }
    private func process(_ samples: [Float]) {
        let coeff = 2 * cos(2 * Double.pi * frequency / 8000)
        var s1 = 0.0, s2 = 0.0
        for sample in samples {
            let x = Double(sample)
            ring[ringPosition] = x; ringPosition = (ringPosition + 1) % 512
            let s0 = x + coeff * s1 - s2; s2 = s1; s1 = s0
        }
        let mag = sqrt(max(0, s1*s1+s2*s2-coeff*s1*s2)) / 80
        floor += (mag-floor) * (mag < floor ? 0.1 : 0.005)
        peak = mag > peak ? mag : peak - (peak-floor) * 0.004
        let span = peak-floor, k = 0.65 - 0.4 * sensitivity
        let signal = peak > floor*4 && peak > 0.0002
        let on = signal && mag > floor + span*k*(keyed ? 0.6 : 1)
        if on == keyed {
            stateMs += 10
            if !keyed { decoder.silence(stateMs).forEach(onDecode) }
        } else {
            if keyed { decoder.mark(stateMs).forEach(onDecode) }
            keyed = on; stateMs = 10
        }
        blocks += 1
        guard blocks % 10 == 0 else { return }
        let bins = (19...76).map { bin -> Double in
            var re = 0.0, im = 0.0
            for i in 0..<512 {
                let window = 0.5 - 0.5 * cos(2 * Double.pi * Double(i) / 511)
                let value = ring[(ringPosition+i)%512] * window
                let angle = 2 * Double.pi * Double(bin*i) / 512
                re += value*cos(angle); im -= value*sin(angle)
            }
            return sqrt(re*re + im*im)
        }
        let maximum = bins.max() ?? 0, index = bins.firstIndex(of: maximum) ?? 0
        let median = max(1e-9, bins.sorted()[bins.count/2])
        if afc && maximum / median > 6 && index > 0 && index < bins.count-1 {
            let a = bins[index-1], b = bins[index], c = bins[index+1]
            let denom = a-2*b+c
            let delta = abs(denom) > 1e-12 ? 0.5*(a-c)/denom : 0
            let f = (Double(index+19)+delta) * 8000 / 512
            if abs(f-candidate) < 20 { candidateCount += 1 } else { candidate = f; candidateCount = 1 }
            if candidateCount >= 3 && abs(f-frequency) > 10 { frequency = min(1200, max(300, f)) }
        }
        onStatus(.init(level: min(1,max(0,mag/(peak+1e-9))), keyed: keyed, signal: signal,
                       spectrum: bins.map { $0 / max(1e-6,maximum) }, frequency: frequency, wpm: decoder.wpm))
    }
}
