package com.danielzuniga.player.ui

import com.danielzuniga.player.ui.player.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSpeedTest {

    @Test
    fun formatsWithoutTrailingZerosOrRounding() {
        assertEquals("1x", formatSpeed(1f))
        assertEquals("0.75x", formatSpeed(0.75f))
        assertEquals("1.25x", formatSpeed(1.25f))
        assertEquals("2x", formatSpeed(2f))
    }
}
