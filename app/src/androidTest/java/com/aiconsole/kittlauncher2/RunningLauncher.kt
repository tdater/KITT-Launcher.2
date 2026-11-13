package com.aiconsole.kittlauncher2

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The launcher started for a test, without ActivityScenario.
 *
 * ActivityScenario waits for the main thread to go idle before launch, before every onActivity call
 * and on close. The scanner and the hologram redraw continuously, so the main thread is only idle in
 * the odd gap between frames. On a CI emulator, where every frame is software-rendered, one such
 * wait took from seconds to several minutes, which is why the smoke test ran anywhere from 1 to over
 * 9 minutes for the same build. Here every step runs as one message on the main thread and polls
 * the activity's lifecycle, so it never waits for idle.
 */
class RunningLauncher private constructor(private val activity: Activity) : AutoCloseable {

    /** Whether the launcher is finishing, read on the main thread. */
    val isFinishing: Boolean get() = onMain { activity.isFinishing }

    /** Finishes the launcher and waits until it is destroyed. */
    override fun close() {
        onMain { activity.finish() }
        awaitState(activity, Lifecycle.State.DESTROYED, "destroyed")
    }

    companion object {
        private const val TIMEOUT_MS = 45_000L // ActivityScenario's own limit for a lifecycle change

        private val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()

        /** Starts [intent]'s activity in a new task and waits until it is resumed. */
        fun launch(intent: Intent): RunningLauncher {
            val component = requireNotNull(intent.component) { "The intent must name its activity" }
            val monitor = instrumentation.addMonitor(component.className, null, false)
            try {
                instrumentation.targetContext.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val activity = monitor.waitForActivityWithTimeout(TIMEOUT_MS)
                    ?: throw AssertionError("${component.className} did not start within ${TIMEOUT_MS / 1000} s")
                awaitState(activity, Lifecycle.State.RESUMED, "resumed")
                return RunningLauncher(activity)
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }

        private fun awaitState(activity: Activity, state: Lifecycle.State, name: String) {
            val owner = activity as LifecycleOwner
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (true) {
                val current = onMain { owner.lifecycle.currentState }
                val reached = if (state == Lifecycle.State.DESTROYED) current == state else current.isAtLeast(state)
                if (reached) return
                if (SystemClock.uptimeMillis() > deadline) {
                    throw AssertionError("${activity.javaClass.name} was not $name within ${TIMEOUT_MS / 1000} s (still $current)")
                }
                Thread.sleep(100)
            }
        }

        /** Runs [block] on the main thread and returns its result, without waiting for idle. */
        private fun <T> onMain(block: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            return result!!.getOrThrow()
        }
    }
}
