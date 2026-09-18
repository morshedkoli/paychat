package com.paychat.paychat.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is currently covered by its lock screen.
 *
 * The lock is about someone picking up an unattended phone, not about the
 * user's own detours: taking a photo, picking one from the gallery, or
 * answering a notification all leave the app for a moment, and locking on
 * every one of those would make the app tiring to use. So the lock applies
 * only after the app has been away for [GRACE_MS].
 *
 * A cold start locks immediately whatever the grace period says, because the
 * device may have been off for a week.
 */
@Singleton
class AppLock @Inject constructor() {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt: Long? = null

    /** Called once per launch, before anything is drawn. */
    fun onColdStart(enabled: Boolean) {
        _locked.value = enabled
    }

    fun onBackground(now: Long = System.currentTimeMillis()) {
        leftAt = now
    }

    /**
     * @return true when the app should be covered, which the caller uses to
     *   decide whether to ask for the screen lock
     */
    fun onForeground(
        enabled: Boolean,
        graceMs: Long = DEFAULT_GRACE_MS,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val away = leftAt?.let { now - it } ?: 0L
        leftAt = null
        if (enabled && away >= graceMs) _locked.value = true
        return _locked.value
    }

    fun unlocked() {
        _locked.value = false
    }

    /** Switching the lock off must not leave the user staring at it. */
    fun onSettingChanged(enabled: Boolean) {
        if (!enabled) _locked.value = false
    }

    companion object {
        const val DEFAULT_GRACE_MS = 30_000L
    }
}
