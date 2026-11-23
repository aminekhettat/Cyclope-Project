package org.blindsystems.cyclope.navlab

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Real native view wiring under JVM Android shadows, not a real phone or a TalkBack speech test. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28])
class AccessibilityActivityTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity

    @Before fun createActivity() {
        controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        activity = controller.get()
    }

    @After fun closeActivity() {
        controller.pause().stop().destroy()
    }

    @Test fun skipLinkMovesInputFocusFromPositioningToAddress() {
        val positioning = activity.findViewById<Button>(R.id.btnLog)
        positioning.isFocusableInTouchMode = true
        positioning.requestFocus()
        assertTrue(positioning.hasFocus())
        activity.findViewById<Button>(R.id.btnSkipNavigation).performClick()
        assertTrue(activity.findViewById<EditText>(R.id.etAddress).hasFocus())
        assertFalse(positioning.hasFocus())
    }

    @Test fun allInteractiveControlsHaveLabelsAndMinimumTargets() {
        val ids =
            listOf(
                R.id.btnSkipNavigation, R.id.btnLog, R.id.btnMark, R.id.btnShare,
                R.id.etAddress, R.id.btnSearch, R.id.btnShort, R.id.btnLong, R.id.btnDouble,
                R.id.btnSimulate, R.id.btnStopNav, R.id.btnMute, R.id.btnReadGuidance,
            )
        val minimum = (48 * activity.resources.displayMetrics.density).toInt()
        for (id in ids) {
            val view = activity.findViewById<View>(id)
            assertNotNull(view)
            assertFalse(view.contentDescription.isNullOrBlank())
            assertTrue(view.minimumHeight >= minimum)
            assertTrue(view.minimumWidth >= minimum)
        }
    }

    @Test fun onlyShortAutomaticRegionsArePolite() {
        for (id in listOf(R.id.tvStatus, R.id.tvAnnouncements)) {
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, activity.findViewById<View>(id).accessibilityLiveRegion)
        }
        for (id in listOf(R.id.tvSources, R.id.tvNav)) {
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, activity.findViewById<View>(id).accessibilityLiveRegion)
        }
    }

    @Test fun addressHasPersistentAssociatedLabelWithoutMaskedHint() {
        assertEquals(R.id.etAddress, activity.findViewById<TextView>(R.id.labelAddress).labelFor)
        assertTrue(activity.findViewById<EditText>(R.id.etAddress).hint.isNullOrBlank())
    }

    @Test fun muteButtonUpdatesItsReadableActionAndCanBeRestored() {
        val button = activity.findViewById<Button>(R.id.btnMute)
        button.performClick()
        assertEquals(activity.getString(R.string.unmute_voice), button.contentDescription)
        button.performClick()
        assertEquals(activity.getString(R.string.mute_voice), button.contentDescription)
    }
}
