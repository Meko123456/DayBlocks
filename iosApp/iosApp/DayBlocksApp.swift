import BackgroundTasks
import SwiftUI
import UserNotifications
import WidgetKit
import ComposeApp

@main
struct DayBlocksApp: App {
    /// The notification center holds its delegate weakly; this keeps ours alive for the process.
    private static let notifications = NotificationDelegate()

    /// Listed under BGTaskSchedulerPermittedIdentifiers in the Info.plist.
    private static let refreshTask = "io.github.meko123456.dayblocks.refresh"

    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Koin has to exist before the first composable asks for a ViewModel, and before any
        // notification action or widget refresh the system delivers without a visible screen.
        // The UI tests launch with DAYBLOCKS_UITEST set so each run starts from an empty plan.
        let underUITest = ProcessInfo.processInfo.environment["DAYBLOCKS_UITEST"] != nil
        // Before Koin starts the widget updater, whose first publish asks for a reload.
        RemindersIosKt.setWidgetReloader { WidgetCenter.shared.reloadAllTimelines() }
        KoinIosKt.doInitKoin(inMemoryDatabase: underUITest)
        UNUserNotificationCenter.current().delegate = Self.notifications
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .onChange(of: scenePhase) { phase in
                    switch phase {
                    case .active: Self.reschedule()
                    case .background: Self.requestRefresh()
                    default: break
                    }
                }
                // Midnight, a time zone change, the clock being set: the pending notifications were
                // worked out against a clock that is no longer the one on the wall.
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                    Self.reschedule()
                }
                // The widget: open on Today, whatever screen the app was left on.
                .onOpenURL { url in
                    if url.scheme == "dayblocks", url.host == "today" { RemindersIosKt.openToday() }
                }
        }
        .backgroundTask(.appRefresh(Self.refreshTask)) {
            Self.requestRefresh()
            await withCheckedContinuation { continuation in
                RemindersIosKt.rescheduleReminders { continuation.resume() }
            }
        }
    }

    private static func reschedule() {
        // Foundation caches the system time zone until it is told to forget it.
        NSTimeZone.resetSystemTimeZone()
        RemindersIosKt.rescheduleReminders {}
    }

    /// Asks iOS for a chance to roll the window forward while the app is closed. iOS decides when,
    /// and may decide never; opening the app always reschedules.
    nonisolated private static func requestRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTask)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

/// Everything on screen is shared Compose; this only hands UIKit the Kotlin view controller.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
