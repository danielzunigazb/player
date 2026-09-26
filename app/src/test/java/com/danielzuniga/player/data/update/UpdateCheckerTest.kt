package com.danielzuniga.player.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateCheckerTest {

    private fun release(tag: String, apk: String? = "Player-${tag.removePrefix("v")}.apk", prerelease: Boolean = false) =
        JSONObject()
            .put("tag_name", tag)
            .put("prerelease", prerelease)
            .put("html_url", "https://github.com/danielzunigazb/player/releases/tag/$tag")
            .put(
                "assets",
                JSONArray().apply {
                    put(JSONObject().put("name", "notes.sha256").put("browser_download_url", "https://example.com/x.sha256"))
                    if (apk != null) put(JSONObject().put("name", apk).put("browser_download_url", "https://github.com/dl/$apk"))
                },
            )
            .toString()

    @Test
    fun comparesVersionsAsNumbers() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateChecker.isNewer("1.5.1", "1.5.0"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.5.0", "1.5.0"))
        assertFalse(UpdateChecker.isNewer("1.4.2", "1.5.0"))
        assertFalse(UpdateChecker.isNewer("latest", "1.5.0"))
        // Someone on a dev build gets the stable release of those numbers, and nothing older.
        assertTrue(UpdateChecker.isNewer("1.5.0", "1.5.0-dev.12"))
        assertFalse(UpdateChecker.isNewer("1.4.2", "1.5.0-dev.12"))
    }

    @Test
    fun readsTheApkOfTheLatestStableRelease() {
        val parsed = UpdateChecker.parse(release("v1.6.0"))!!
        assertEquals("1.6.0", parsed.version)
        assertEquals("https://github.com/dl/Player-1.6.0.apk", parsed.apkUrl)
        assertNull(UpdateChecker.parse(release("v1.6.0", apk = null)))
        assertNull(UpdateChecker.parse(release("v1.6.0-beta", prerelease = true)))
        assertNull(UpdateChecker.parse("not json"))
    }

    @Test
    fun offersANewVersionOnceButAlwaysOnAManualCheck() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var calls = 0
        val repo = UpdateRepository(
            context = context,
            currentVersion = "1.5.0",
            checker = UpdateChecker("test", fetch = { _, _ -> calls++; release("v1.6.0") }),
            autoCheck = { true },
            scope = CoroutineScope(Dispatchers.Default),
        )

        repo.checkIfDue()
        val offered = withTimeout(5_000) { repo.offer.first { it != null } }!!
        assertEquals("1.6.0", offered.version)
        assertTrue(repo.state.value is UpdateState.Available)

        repo.dismiss(offered)
        repo.checkIfDue()
        assertEquals("checked today already", 1, calls)
        assertNull(repo.offer.value)

        repo.checkNow()
        assertEquals("1.6.0", withTimeout(5_000) { repo.offer.first { it != null } }!!.version)
        assertEquals(2, calls)
    }

    @Test
    fun upToDateAndOfflineAreReported() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var body: String? = release("v1.5.0")
        val repo = UpdateRepository(context, "1.5.0", UpdateChecker("test", fetch = { _, _ -> body }), { true }, CoroutineScope(Dispatchers.Default))
        repo.checkNow()
        withTimeout(5_000) { repo.state.first { it == UpdateState.UpToDate } }
        body = null
        repo.checkNow()
        withTimeout(5_000) { repo.state.first { it == UpdateState.Failed } }
        assertNull(repo.offer.value)
    }
}
