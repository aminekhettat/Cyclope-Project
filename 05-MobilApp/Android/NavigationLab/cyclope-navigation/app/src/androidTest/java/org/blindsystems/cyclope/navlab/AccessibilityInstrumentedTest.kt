package org.blindsystems.cyclope.navlab

import android.Manifest
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native UI smoke check. An emulator test is not a real TalkBack usability session. */
@RunWith(AndroidJUnit4::class)
class AccessibilityInstrumentedTest {
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
        )

    @Test fun skipLinkMovesInputFocusToAddress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.btnSkipNavigation).performClick()
                assertTrue(activity.findViewById<EditText>(R.id.etAddress).hasFocus())
            }
        }
    }

    @Test fun automaticGuidanceAndSearchStatusArePoliteRegions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (id in listOf(R.id.tvAnnouncements, R.id.tvStatus)) {
                    assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, activity.findViewById<View>(id).accessibilityLiveRegion)
                }
            }
        }
    }
}
