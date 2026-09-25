package io.github.meko123456.dayblocks.core.buddy

/**
 * How the buddy is feeling about the day so far. Chosen here from adherence and streak; drawn in
 * :core:designsystem. Keeping the choice out of the UI means the same mood drives the Today
 * screen, the notification icon and both widgets without three rules that can disagree.
 */
enum class BuddyMood { Happy, Proud, Encouraging, Worried, Disappointed, Sleepy }

/** How hard the buddy pushes. Changes both the message pool and how often it is allowed to speak. */
enum class BuddyTone { Gentle, Normal, Pushy }
