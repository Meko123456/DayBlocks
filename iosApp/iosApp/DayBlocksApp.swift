import SwiftUI
import ComposeApp

@main
struct DayBlocksApp: App {
    init() {
        // Koin has to exist before the first composable asks for a ViewModel, and before any
        // notification action or widget refresh the system delivers without a visible screen.
        // The UI tests launch with DAYBLOCKS_UITEST set so each run starts from an empty plan.
        let underUITest = ProcessInfo.processInfo.environment["DAYBLOCKS_UITEST"] != nil
        KoinIosKt.doInitKoin(inMemoryDatabase: underUITest)
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

/// Everything on screen is shared Compose; this only hands UIKit the Kotlin view controller.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
