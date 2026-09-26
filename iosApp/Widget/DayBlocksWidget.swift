import SwiftUI
import WidgetKit

/// Today on the home screen. The app writes everything this shows into the App Group — the
/// timeline, one entry per block boundary, and the buddy's faces as images — so the extension
/// only reads files: it never opens the database and never links the Kotlin framework.
@main
struct DayBlocksWidgets: WidgetBundle {
    var body: some Widget {
        TodayWidget()
    }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TodayWidget", provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName("Today")
        .description("What's on now, what's next, and how the buddy feels about it.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Timeline

struct TodayEntry: TimelineEntry {
    let date: Date
    let buddyName: String
    let current: WidgetFile.Block?
    let next: WidgetFile.Block?
    let done: Int
    let planned: Int
    let mood: String

    static let placeholder = TodayEntry(
        date: .now, buddyName: "Kubi",
        current: WidgetFile.Block(title: "Deep work", category: "Work", starts: 0, ends: Date.now.addingTimeInterval(3600).timeIntervalSince1970, startClock: "09:00", endClock: "12:00"),
        next: nil, done: 1, planned: 4, mood: "Happy"
    )
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        completion(Shared.read()?.entries.first.map { entry(from: $0, name: Shared.read()?.buddyName) } ?? .placeholder)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        guard let file = Shared.read(), !file.entries.isEmpty else {
            // Nothing written yet: the app has not run since install. Look again in an hour.
            completion(Timeline(entries: [.placeholder], policy: .after(.now.addingTimeInterval(3600))))
            return
        }
        let entries = file.entries.map { entry(from: $0, name: file.buddyName) }
        // The app rewrites the timeline on every change; the rollover is when a new day's plan
        // takes over and the app has to be asked again.
        completion(Timeline(entries: entries, policy: .after(Date(timeIntervalSince1970: file.refreshAt))))
    }

    private func entry(from e: WidgetFile.Entry, name: String?) -> TodayEntry {
        TodayEntry(
            date: Date(timeIntervalSince1970: e.at), buddyName: name ?? "Kubi",
            current: e.current, next: e.next, done: e.done, planned: e.planned, mood: e.mood
        )
    }
}

// MARK: - What the app writes

/// Mirrors Kotlin's WidgetFile. Times are seconds since 1970.
struct WidgetFile: Decodable {
    let buddyName: String
    let refreshAt: TimeInterval
    let entries: [Entry]

    struct Entry: Decodable {
        let at: TimeInterval
        let current: Block?
        let next: Block?
        let done: Int
        let planned: Int
        let mood: String
    }

    struct Block: Decodable {
        let title: String
        let category: String
        let starts: TimeInterval
        let ends: TimeInterval
        let startClock: String
        let endClock: String
    }
}

enum Shared {
    static let group = "group.io.github.meko123456.dayblocks"

    static var container: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) }

    static func read() -> WidgetFile? {
        guard let url = container?.appendingPathComponent("widget.json"), let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(WidgetFile.self, from: data)
    }

    static func face(_ mood: String) -> UIImage? {
        guard let url = container?.appendingPathComponent("kubi-\(mood).png") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }
}

// MARK: - Views

private let sunrise = Color(red: 0xF2 / 255, green: 0x99 / 255, blue: 0x4A / 255)
private let paper = Color(red: 0xFA / 255, green: 0xF7 / 255, blue: 0xF2 / 255)
private let ink = Color(red: 0x1B / 255, green: 0x1D / 255, blue: 0x22 / 255)

struct TodayWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    let entry: TodayEntry

    var body: some View {
        content
            .widgetURL(URL(string: "dayblocks://today"))
            .modifier(WidgetBackground(color: scheme == .dark ? ink : paper))
    }

    @ViewBuilder private var content: some View {
        if family == .systemMedium {
            HStack(spacing: 12) {
                face(size: 64)
                VStack(alignment: .leading, spacing: 2) {
                    Text("NOW").font(.caption2.bold()).foregroundColor(sunrise)
                    title
                    detail
                    if entry.current != nil, let next = entry.next {
                        Text("Then \(next.title) at \(next.startClock)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    if entry.planned > 0 {
                        ProgressView(value: Double(entry.done), total: Double(entry.planned)).tint(sunrise).padding(.top, 6)
                        Text("\(entry.done) of \(entry.planned) \(entry.planned == 1 ? "block" : "blocks") done").font(.caption2).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    face(size: 32)
                    Text(entry.buddyName).font(.caption.bold()).foregroundColor(sunrise)
                }
                Spacer(minLength: 0)
                title
                detail
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var title: some View {
        Text(entry.current?.title ?? "Free right now").font(.headline).lineLimit(2)
    }

    /// Until when, and a countdown WidgetKit keeps current by itself between entries.
    @ViewBuilder private var detail: some View {
        if let current = entry.current {
            Text("until \(current.endClock) · \(Text(Date(timeIntervalSince1970: current.ends), style: .relative)) left")
                .font(.caption).foregroundStyle(.secondary).lineLimit(2)
        } else if let next = entry.next {
            Text("Next: \(next.title) at \(next.startClock)").font(.caption).foregroundStyle(.secondary).lineLimit(2)
        } else {
            Text("Nothing else planned today").font(.caption).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private func face(size: CGFloat) -> some View {
        if let image = Shared.face(entry.mood) {
            Image(uiImage: image).resizable().frame(width: size, height: size)
                .accessibilityLabel("\(entry.buddyName), \(entry.mood.lowercased())")
        } else {
            RoundedRectangle(cornerRadius: size * 0.3).fill(sunrise).frame(width: size, height: size)
        }
    }
}

/// iOS 17 wants the background declared as the widget's container; iOS 16 takes a plain one.
private struct WidgetBackground: ViewModifier {
    let color: Color
    func body(content: Content) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            content.containerBackground(for: .widget) { color }
        } else {
            content.padding().background(color)
        }
    }
}
