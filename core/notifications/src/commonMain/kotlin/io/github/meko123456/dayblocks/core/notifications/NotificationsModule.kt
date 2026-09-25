package io.github.meko123456.dayblocks.core.notifications

import org.koin.dsl.module

/**
 * Only the common half. Each platform's Koin module binds its own [NotificationScheduler]
 * implementation, since one needs an Android Context and the other a UNUserNotificationCenter.
 */
val notificationsModule = module {
}
