package org.blindsystems.cyclope.navlab

import android.Manifest
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runtime smoke coverage only: no route requests, UI interactions or GPS fix required. */
@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    // Avoid system permission dialogs interrupting MainActivity's RESUMED state.
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION,
    )

    @Test
    fun appContext_hasExpectedPackageName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.blindsystems.cyclope.navlab", context.packageName)
    }

    @Test
    fun mainActivity_launchesToResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<TextView>(R.id.tvNav))
            }
        }
    }
}
