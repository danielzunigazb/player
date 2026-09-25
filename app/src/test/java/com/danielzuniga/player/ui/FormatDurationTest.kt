package com.danielzuniga.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9_999))
        assertEquals("3:05", formatDuration(185_000))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun clampsNegativeValues() {
        assertEquals("0:00", formatDuration(-5_000))
    }
}
