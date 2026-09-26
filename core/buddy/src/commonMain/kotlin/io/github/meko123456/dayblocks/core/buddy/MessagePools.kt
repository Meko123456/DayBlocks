package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BuddyTone

/** What a notification is about, as far as its words go. */
enum class Situation { BlockStart, CheckIn, BackOnTrack, PlanTomorrow, PlanToday, Streak, Comeback, ReviewDay }

/** What the speech bubble on Today is about. */
enum class Bubble { Night, SleepBlock, EmptyDay, RunningGood, RunningSteady, RunningBehind, Free, DoneGood, DoneSteady, DoneBehind }

/**
 * Everything the buddy can say, as templates.
 *
 * Placeholders: `{name}` the buddy's name, `{title}` a block's title (quoted), `{emoji}` its
 * category's emoji, `{time}` a clock reading, `{length}` how long a block lasts, said aloud,
 * `{left}` the time left in it, `{streak}` and `{streakNext}` days on plan, `{next}` and
 * `{nextTime}` the next block and when it starts.
 *
 * The tone changes the pool, not just a word or two: Gentle never raises its voice, Pushy never
 * lowers it, and both stay kind — disappointment is allowed to be playful, never a guilt trip.
 * The first line of each Normal pool is the spec's own example.
 */
class MessagePools(
    private val notifications: Map<Situation, Map<BuddyTone, List<String>>>,
    private val bubbles: Map<Bubble, List<String>>,
) {
    init {
        Situation.entries.forEach { s -> BuddyTone.entries.forEach { t -> require(lines(s, t).isNotEmpty()) { "no lines for $s in $t" } } }
        Bubble.entries.forEach { b -> require(lines(b).isNotEmpty()) { "no lines for $b" } }
    }

    fun lines(situation: Situation, tone: BuddyTone): List<String> = notifications[situation]?.get(tone).orEmpty()

    fun lines(bubble: Bubble): List<String> = bubbles[bubble].orEmpty()

    /** The same pools cut to their first line each, for tests that need to know the exact words. */
    fun firstLinesOnly(): MessagePools = MessagePools(
        notifications.mapValues { (_, byTone) -> byTone.mapValues { (_, lines) -> lines.take(1) } },
        bubbles.mapValues { (_, lines) -> lines.take(1) },
    )

    companion object {
        val Default = MessagePools(
            notifications = mapOf(
                Situation.BlockStart to mapOf(
                    BuddyTone.Normal to listOf(
                        "Hey! It's {time}. Time for {title} {emoji} You've got {length}.",
                        "{time}, and {title} starts now {emoji} {length} on the clock.",
                        "Knock knock. It's {title} o'clock {emoji} You have {length}.",
                        "Right on time: {title} {emoji} {length}, let's make them count.",
                        "New block! {title} {emoji} for {length}. I'll check in later.",
                        "It's {time} and the plan says {title} {emoji} {length}, you've got this.",
                    ),
                    BuddyTone.Gentle to listOf(
                        "It's {time}. Whenever you're ready: {title} {emoji} ({length}).",
                        "A soft reminder: {title} {emoji} is up now, with {length} set aside for it.",
                        "{title} {emoji} starts now. No rush, just a nudge in the right direction.",
                        "Hi! Next on your day: {title} {emoji}. {length} to enjoy it.",
                        "It's {time}. Time for {title} {emoji}, at your own pace.",
                    ),
                    BuddyTone.Pushy to listOf(
                        "{time}. {title} {emoji} Now. {length}, and not a minute to waste!",
                        "Phone down, {title} up {emoji} {length} starts NOW.",
                        "It's {title} time {emoji} Go go go, {length} on the clock!",
                        "{title} {emoji} starts now. I'll be watching 👀 ({length})",
                        "No snoozing this one: {title} {emoji}, {length}, right now.",
                    ),
                ),
                Situation.CheckIn to mapOf(
                    BuddyTone.Normal to listOf(
                        "Still on {title}? Or did the phone win again? 👀",
                        "Halfway through {title}. How's it going?",
                        "Quick check: still doing {title}? {left} to go.",
                        "Psst. {title} {emoji} Still in it?",
                        "Checking in on {title} {emoji} On track?",
                    ),
                    BuddyTone.Gentle to listOf(
                        "How's {title} going? {left} left, no pressure.",
                        "Just peeking in: still with {title}?",
                        "Halfway there with {title} {emoji} Doing okay?",
                        "A gentle check-in: are you still on {title}?",
                    ),
                    BuddyTone.Pushy to listOf(
                        "Be honest: still on {title}, or scrolling? 👀",
                        "{title}. Still. Doing it? {left} left!",
                        "Checkpoint! Is {title} actually happening? 👀",
                        "Eyes on the prize: still on {title}? {left} to go.",
                    ),
                ),
                Situation.BackOnTrack to mapOf(
                    BuddyTone.Normal to listOf(
                        "No stress, {left} left. Let's go back to it.",
                        "Happens to everyone. {title} is still there, {left} to go.",
                        "Fresh start? {title} has {left} left, plenty for a comeback.",
                        "Distraction over? {left} of {title} is still yours.",
                        "Small reset, big win: {left} left on {title}.",
                    ),
                    BuddyTone.Gentle to listOf(
                        "It's okay to drift. {title} is waiting whenever you're ready, {left} left.",
                        "No judgement here. {left} left for {title}, if you'd like.",
                        "Deep breath. {title} still has {left}. Pick it back up?",
                    ),
                    BuddyTone.Pushy to listOf(
                        "Okay, break's over! {left} of {title} left, back to it 💪",
                        "That was plenty of distraction. {title}: {left} left, go!",
                        "The phone had its turn. {left} left on {title}, finish strong.",
                        "Back to {title}! {left} left and I believe in you.",
                    ),
                ),
                Situation.PlanTomorrow to mapOf(
                    BuddyTone.Normal to listOf(
                        "Tomorrow's still empty. Want to plan it in 2 minutes?",
                        "Future you says thanks for planning tomorrow. Two minutes, tops.",
                        "Tomorrow is a blank page. Sketch a few blocks before bed?",
                        "Quick one: tomorrow has nothing planned yet. Set it up?",
                    ),
                    BuddyTone.Gentle to listOf(
                        "When you have a moment: tomorrow's still open. Plan a block or two?",
                        "No plan for tomorrow yet. Even one block helps.",
                        "Tomorrow's wide open. Want to give it a little shape?",
                    ),
                    BuddyTone.Pushy to listOf(
                        "Tomorrow: zero blocks. Let's fix that right now, 2 minutes!",
                        "Planning tomorrow takes 2 minutes. Winging it costs a day. Go!",
                        "Before you relax: tomorrow needs a plan. Now's the time.",
                    ),
                ),
                Situation.PlanToday to mapOf(
                    BuddyTone.Normal to listOf(
                        "Good morning! Today's still a blank page. Plan just one block?",
                        "Nothing planned today yet. One block is enough to start.",
                        "Morning! Want to give today a shape? Two minutes is all it takes.",
                    ),
                    BuddyTone.Gentle to listOf(
                        "Good morning. No plan yet, and that's okay. One block when you're ready?",
                        "Morning! Today's open. A single block can set the tone.",
                    ),
                    BuddyTone.Pushy to listOf(
                        "Rise and plan! Today has zero blocks. Let's change that.",
                        "Good morning! No plan, no streak. Two minutes, let's go!",
                    ),
                ),
                Situation.Streak to mapOf(
                    BuddyTone.Normal to listOf(
                        "You've followed your plan {streak} days in a row 🔥 Don't break it today!",
                        "{streak}-day streak 🔥 Let's make it {streakNext}.",
                        "{streak} days on plan! Today's another chance to keep the fire going 🔥",
                    ),
                    BuddyTone.Gentle to listOf(
                        "{streak} days in a row on plan 🔥 Proud of you. Keep it cozy today.",
                        "A {streak}-day streak! Keep it going at your own pace 🔥",
                    ),
                    BuddyTone.Pushy to listOf(
                        "{streak} days straight 🔥 Breaking it today is NOT an option.",
                        "Streak: {streak} 🔥 Guard it like the last cookie.",
                    ),
                ),
                Situation.Comeback to mapOf(
                    BuddyTone.Normal to listOf(
                        "I miss you. Just plan one block today?",
                        "It's been a while! Your day is waiting. Plan one block?",
                        "Hey, it's {name}. Come back and plan something small?",
                    ),
                    BuddyTone.Gentle to listOf(
                        "Hi, just saying hello. Your plans will be here whenever you want them.",
                        "No pressure, but I'd love to see you. One small block today?",
                    ),
                    BuddyTone.Pushy to listOf(
                        "Hello?! It's been a whole day. One block. Right now. Please? 🥺",
                        "I've been waiting and waiting! Let's plan something, even tiny.",
                    ),
                ),
                Situation.ReviewDay to mapOf(
                    BuddyTone.Normal to listOf(
                        "How did today go? Tap to review.",
                        "Day's done! Two taps to check in on how it went.",
                        "Let's look back on today. How did the blocks go?",
                    ),
                    BuddyTone.Gentle to listOf(
                        "When you're winding down: how did today feel? Tap to review.",
                        "The day is over. Want to note how it went?",
                    ),
                    BuddyTone.Pushy to listOf(
                        "Check-in time! How did today really go?",
                        "Don't skip the review: thirty seconds, tap now.",
                    ),
                ),
            ),
            bubbles = mapOf(
                Bubble.Night to listOf(
                    "Zzz… see you in the morning.",
                    "It's late. Tomorrow's plan will keep.",
                    "Resting up for tomorrow. You should too.",
                ),
                Bubble.SleepBlock to listOf(
                    "Sleep time! I'll stay quiet 😴",
                    "Shh… {title} is on. Good night.",
                ),
                Bubble.EmptyDay to listOf(
                    "Nothing planned yet. One block is enough to start.",
                    "A blank day! Want to plan something?",
                    "Today's wide open. Tap + to add a block.",
                ),
                Bubble.RunningGood to listOf(
                    "{title} now, {left} to go. You're on a roll!",
                    "Nice pace today. {title} {emoji}, {left} left.",
                    "Look at you go! {title}, {left} to finish.",
                ),
                Bubble.RunningSteady to listOf(
                    "{title} now. {left} left, you've got this.",
                    "One block at a time: {title} {emoji}, {left} to go.",
                    "{title} is on. {left} left, steady does it.",
                ),
                Bubble.RunningBehind to listOf(
                    "Let's win this one back: {title}, {left} left.",
                    "Fresh start with {title}? {left} to go.",
                    "A bumpy day, but {title} is a great place to turn it around.",
                ),
                Bubble.Free to listOf(
                    "Free until {nextTime}. Want to fill it?",
                    "A breather until {next} at {nextTime}.",
                    "Nothing on right now. Next up: {next} at {nextTime}.",
                ),
                Bubble.DoneGood to listOf(
                    "What a day! Check in when you're ready.",
                    "All done, and it went well. Time to review?",
                ),
                Bubble.DoneSteady to listOf(
                    "That's the plan done. How did it go?",
                    "Day complete. A quick check-in closes it out.",
                ),
                Bubble.DoneBehind to listOf(
                    "Tough day? Tomorrow's a new page. Check in and let it go.",
                    "Not every day goes to plan. Let's look back, then rest.",
                ),
            ),
        )
    }
}
