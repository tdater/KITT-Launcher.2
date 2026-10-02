package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.browser.MediaFocus
import com.aiconsole.knightriderlauncher.browser.PageScripts
import com.aiconsole.knightriderlauncher.data.EdgeSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMediaTest {

    @Test
    fun focusChangesMapToActions() {
        assertEquals(MediaFocus.Action.Pause, MediaFocus.onChange(MediaFocus.LOSS))
        assertEquals(MediaFocus.Action.PauseUntilGain, MediaFocus.onChange(MediaFocus.LOSS_TRANSIENT))
        assertEquals(MediaFocus.Action.Resume, MediaFocus.onChange(MediaFocus.GAIN))
        // KITT speaking only ducks the page.
        assertEquals(MediaFocus.Action.Nothing, MediaFocus.onChange(MediaFocus.LOSS_TRANSIENT_CAN_DUCK))
    }

    @Test
    fun mediaScriptsTalkToTheBridgeAndMarkWhatTheyPause() {
        assertTrue("KittMedia.state(id, playing)" in PageScripts.MEDIA_WATCH)
        assertTrue("kittPaused" in PageScripts.PAUSE_MEDIA)
        assertTrue("kittPaused" in PageScripts.RESUME_MEDIA)
    }

    @Test
    fun edgeSwipeChangesPageByDistanceOrFling() {
        val width = 1000f
        assertEquals(3, EdgeSwipe.target(2, 7, dragPx = -300f, velocityPx = 0f, widthPx = width))
        assertEquals(1, EdgeSwipe.target(2, 7, dragPx = 300f, velocityPx = 0f, widthPx = width))
        assertEquals(3, EdgeSwipe.target(2, 7, dragPx = -50f, velocityPx = -2000f, widthPx = width))
        assertEquals(2, EdgeSwipe.target(2, 7, dragPx = -50f, velocityPx = 0f, widthPx = width))
    }

    @Test
    fun edgeSwipeStopsAtTheFirstAndLastPage() {
        assertEquals(0, EdgeSwipe.target(0, 7, dragPx = 500f, velocityPx = 0f, widthPx = 1000f))
        assertEquals(6, EdgeSwipe.target(6, 7, dragPx = -500f, velocityPx = 0f, widthPx = 1000f))
    }
}
