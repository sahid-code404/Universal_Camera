package com.sahidcode404.camera.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HdrModeTest {
    @Test fun cycleIsStable() {
        assertEquals(HdrMode.HDR, HdrMode.NORMAL.next())
        assertEquals(HdrMode.HDR_PLUS_AUTO, HdrMode.HDR.next())
        assertEquals(HdrMode.NORMAL, HdrMode.HDR_PLUS_AUTO.next())
    }
}
