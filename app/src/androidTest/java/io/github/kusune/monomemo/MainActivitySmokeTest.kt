package io.github.kusune.monomemo

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** A device-level guard against startup crashes that compilation cannot catch. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun launchesAndShowsEditor() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<CursorAwareEditText>(R.id.editor))
            }
        } finally {
            scenario.close()
        }
    }
}
