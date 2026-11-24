package com.example.musicsmd.support

import com.example.musicsmd.settings.SettingsStore
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** When the Diet Coke popup shows by itself: every two days, a random moment into a visit. */
internal object SupportPromptSchedule {
    const val INTERVAL_MS = 2L * 24 * 60 * 60 * 1000
    const val MIN_DELAY_MS = 10_000L
    const val MAX_DELAY_MS = 90_000L

    fun isDue(lastShownMs: Long, nowMs: Long): Boolean = nowMs - lastShownMs >= INTERVAL_MS

    /** How long into a visit the popup waits, so it never lands the instant the window opens. */
    fun delayMs(random: Random): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
}

/**
 * The "Buy me a Diet Coke" popup that shows by itself, as on the phone. The dock button opens the
 * same dialog any time.
 */
class SupportPromptState(private val settings: SettingsStore) {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * Call while the window is on screen, and cancel when it is hidden or minimised: a wait cut
     * short that way is simply tried again, with a new random delay, on the next visit.
     *
     * The first call only starts the clock, so nobody is asked on their first day.
     */
    suspend fun onForeground() {
        val now = System.currentTimeMillis()
        val last = settings.current.supportPromptShownAt
        // Never started, or the clock went back past the last showing: restart the clock.
        if (last <= 0L || last > now) {
            settings.update { it.copy(supportPromptShownAt = now) }
            return
        }
        if (!SupportPromptSchedule.isDue(last, now)) return
        delay(SupportPromptSchedule.delayMs(Random.Default))
        settings.update { it.copy(supportPromptShownAt = System.currentTimeMillis()) }
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }
}
