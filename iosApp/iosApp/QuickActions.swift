import UIKit
import ComposeApp

/// The Home Screen quick action, listed under UIApplicationShortcutItems in Info.plist: Add a
/// block, as the Android launcher's shortcut does. It goes to AppLinks, which holds it until the
/// navigation graph takes it, so it may arrive before the graph exists.
enum QuickAction {
    static let newBlock = "io.github.meko123456.dayblocks.new-block"

    /// False for an item this version doesn't know, which tells iOS it wasn't handled.
    static func perform(_ item: UIApplicationShortcutItem) -> Bool {
        guard item.type == newBlock else { return false }
        RemindersIosKt.openNewBlock()
        return true
    }
}

/// SwiftUI has no hook for quick actions. A quick action that launches the app comes with the
/// scene's connection options; one used while the app is running goes to the scene's delegate.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let item = options.shortcutItem { _ = QuickAction.perform(item) }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(QuickAction.perform(shortcutItem))
    }
}
