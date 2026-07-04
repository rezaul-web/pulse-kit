import SwiftUI
import PulseKit

/// Minimal SwiftUI app proving PulseKit's Kotlin Multiplatform core runs on iOS.
struct ContentView: View {
    @State private var sessionId = "—"
    @State private var fpsLine = "—"
    @State private var lastEvent = "—"
    @State private var trackCount = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("PulseKit on iOS").font(.largeTitle).bold()
            Text("Kotlin Multiplatform shared core, called from SwiftUI")
                .font(.subheadline).foregroundStyle(.secondary)

            GroupBox("Runtime (pulse-runtime)") {
                row("Session id", sessionId)
                row("Last tracked", lastEvent)
            }

            GroupBox("FPS aggregator (pulse-core logic)") {
                Text(fpsLine)
                    .font(.system(.callout, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                trackCount += 1
                Pulse.shared.track(
                    name: "ios_button",
                    attributes: ["source": "swiftui", "n": "\(trackCount)"]
                )
                lastEvent = "ios_button #\(trackCount)"
            } label: {
                Text("Track an event").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)

            Spacer()
        }
        .padding()
        .onAppear(perform: runDemo)
    }

    private func runDemo() {
        // 1. Initialize the shared runtime — proves Pulse works natively on iOS.
        sessionId = PulseIosKt.startPulseOnIos()

        // 2. Run the allocation-free FPS aggregator (pure shared logic) on iOS.
        let aggregator = FrameAggregator(window: 120, frameBudgetMs: 1000.0 / 60.0)
        for frameMs in [8.0, 9.0, 40.0, 8.0, 70.0, 8.0, 9.0] {
            aggregator.record(frameMs: frameMs)
        }
        aggregator.publish()
        if let snapshot = aggregator.snapshot.value as? FpsSnapshot {
            fpsLine = "avg \(snapshot.averageFps) fps · worst "
                + String(format: "%.0f", snapshot.worstFrameMs)
                + " ms · jank \(snapshot.jankPercent)%"
        }
    }

    private func row(_ key: String, _ value: String) -> some View {
        HStack {
            Text(key).foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.system(.callout, design: .monospaced))
        }
    }
}
