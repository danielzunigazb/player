package com.danielzuniga.player.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnvelopeTest {

    private val now = 1_790_000_000_000L

    @Test
    fun stampsSenderRisingSeqAndTime() {
        val outbox = Outbox("phone")
        val first = outbox.stamp(JSONObject().put("type", "state"), now)
        val second = outbox.stamp(JSONObject(), now)
        assertEquals("phone", first.getString("from"))
        assertEquals(1L, first.getLong("seq"))
        assertEquals(2L, second.getLong("seq"))
        assertEquals(now, first.getLong("ts"))
    }

    @Test
    fun dropsReplaysOldSeqsStaleAndUnstampedMessages() {
        val web = Outbox("web-1")
        val inbox = Inbox()
        val first = web.stamp(JSONObject(), now)
        val second = web.stamp(JSONObject(), now)
        assertTrue(inbox.accept(first, now))
        assertFalse(inbox.accept(first, now))
        assertTrue(inbox.accept(second, now))
        assertTrue(inbox.accept(Outbox("web-2").stamp(JSONObject(), now), now))
        assertFalse(inbox.accept(Outbox("web-3").stamp(JSONObject(), now - 3 * 60_000), now))
        assertFalse(inbox.accept(JSONObject().put("type", "cmd"), now))
    }
}
