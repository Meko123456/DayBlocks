import UserNotifications
import ComposeApp

/// Receives what the user does with a notification. A check-in's buttons answer without opening
/// the app: the answer is recorded and a reschedule follows, which is where the follow-up after
/// "Got distracted" comes from.
///
/// It must be the notification center's delegate before launch finishes, or a tap that launched
/// the app would be delivered to no one.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    /// The buttons' identifiers: the names of Kotlin's CheckInAnswer, as the categories register them.
    private static let answers: Set<String> = ["OnIt", "GotDistracted", "SkipBlock"]

    /// Shown in the foreground too: a block starting while the app is open is still worth a banner.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            // A tap on the notification itself opens the app; the review opens its day's check-in.
            if info["kind"] as? String == "EndOfDay" {
                RemindersIosKt.openReview(date: info["date"] as? String)
            }
            completionHandler()
            return
        }
        guard Self.answers.contains(response.actionIdentifier), let block = info["blockId"] as? String else {
            completionHandler()
            return
        }
        RemindersIosKt.answerReminder(blockId: block, answer: response.actionIdentifier) {
            completionHandler()
        }
    }
}
