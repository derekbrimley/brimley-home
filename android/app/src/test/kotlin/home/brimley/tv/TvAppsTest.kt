package home.brimley.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvAppsTest {
    @Test fun knownAppsHaveLabels() {
        assertEquals("Disney+", TvApps.label("com.disney.disneyplus"))
        assertEquals("Netflix", TvApps.label("com.netflix.ninja"))
        assertEquals("Max", TvApps.label("com.wbd.stream"))
        assertEquals("YouTube", TvApps.label("com.google.android.youtube.tv"))
    }
    @Test fun unknownAppsHaveNone() {
        assertNull(TvApps.label("com.google.android.apps.tv.launcherx"))
        assertNull(TvApps.label(null))
    }
}
