package com.paychat.paychat.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLockTest {

    private lateinit var appLock: AppLock

    @Before
    fun setUp() {
        appLock = AppLock()
    }

    @Test
    fun coldStart_whenEnabled_locksImmediately() {
        appLock.onColdStart(enabled = true)
        assertTrue(appLock.locked.value)
    }

    @Test
    fun coldStart_whenDisabled_doesNotLock() {
        appLock.onColdStart(enabled = false)
        assertFalse(appLock.locked.value)
    }

    @Test
    fun foreground_withinGracePeriod_doesNotLock() {
        appLock.onColdStart(enabled = false)
        val t0 = 1_000_000L
        appLock.onBackground(now = t0)

        // Return 10s later with 30s grace period
        val locked = appLock.onForeground(enabled = true, graceMs = 30_000L, now = t0 + 10_000L)
        assertFalse(locked)
        assertFalse(appLock.locked.value)
    }

    @Test
    fun foreground_afterGracePeriod_locks() {
        appLock.onColdStart(enabled = false)
        val t0 = 1_000_000L
        appLock.onBackground(now = t0)

        // Return 35s later with 30s grace period
        val locked = appLock.onForeground(enabled = true, graceMs = 30_000L, now = t0 + 35_000L)
        assertTrue(locked)
        assertTrue(appLock.locked.value)
    }

    @Test
    fun foreground_immediatelyTimeout_locksPromptly() {
        appLock.onColdStart(enabled = false)
        val t0 = 1_000_000L
        appLock.onBackground(now = t0)

        // With IMMEDIATELY (0L grace), even 1ms locks
        val locked = appLock.onForeground(enabled = true, graceMs = LockTimeout.IMMEDIATELY.millis, now = t0 + 1L)
        assertTrue(locked)
        assertTrue(appLock.locked.value)
    }

    @Test
    fun foreground_disabledSetting_neverLocks() {
        appLock.onColdStart(enabled = false)
        val t0 = 1_000_000L
        appLock.onBackground(now = t0)

        // Away for 10 minutes, but app lock is disabled by user
        val locked = appLock.onForeground(enabled = false, graceMs = 30_000L, now = t0 + 600_000L)
        assertFalse(locked)
        assertFalse(appLock.locked.value)
    }

    @Test
    fun unlocked_clearsLockState() {
        appLock.onColdStart(enabled = true)
        assertTrue(appLock.locked.value)

        appLock.unlocked()
        assertFalse(appLock.locked.value)
    }

    @Test
    fun onSettingChanged_toDisabled_clearsLockState() {
        appLock.onColdStart(enabled = true)
        assertTrue(appLock.locked.value)

        appLock.onSettingChanged(enabled = false)
        assertFalse(appLock.locked.value)
    }

    @Test
    fun lockTimeout_fromName_resolvesCorrectly() {
        assertEquals(LockTimeout.IMMEDIATELY, LockTimeout.fromName("IMMEDIATELY"))
        assertEquals(LockTimeout.ONE_MINUTE, LockTimeout.fromName("ONE_MINUTE"))
        assertEquals(LockTimeout.FIVE_MINUTES, LockTimeout.fromName("FIVE_MINUTES"))
        assertEquals(LockTimeout.THIRTY_SECONDS, LockTimeout.fromName("THIRTY_SECONDS"))
        assertEquals(LockTimeout.DEFAULT, LockTimeout.fromName("UNKNOWN_VALUE"))
        assertEquals(LockTimeout.DEFAULT, LockTimeout.fromName(null))
    }
}
