package com.aiconsole.kittlauncher2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiconsole.kittlauncher2.data.AutoBackup
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** A second backup on the same day (same file name) must not crash the launcher. */
@RunWith(AndroidJUnit4::class)
class AutoBackupTest {

    @Test
    fun sameDayBackupTwiceDoesNotThrow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backup = AutoBackup(context, "autobackuptest")
        val now = System.currentTimeMillis()
        assertNotNull(backup.runIfDue(now, 0L))
        // The file now exists; a "never backed up" run on the same day used to crash on the unique name.
        assertNotNull(backup.runIfDue(now, 0L))
    }
}
