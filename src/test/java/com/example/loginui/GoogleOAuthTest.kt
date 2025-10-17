package com.example.loginui

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.loginui.google.GoogleOAuth
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Shadows
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class GoogleOAuthTest {

    // ----- helpers (avoid repetition) -----
    private fun expectedAuthUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val redirect = Uri.encode(GoogleOAuth.CALLBACK_URI)
        return "$base/api/auth/google?redirect=$redirect"
    }

    // ----- parseFromUri -----

    @Test
    fun parseFromUri_happyPath_returnsTokens() {
        val uri = Uri.parse(
            "restosoft://auth/google/callback?accessToken=acc123&refreshToken=ref456"
        )
        val tokens = GoogleOAuth.parseFromUri(uri)

        assertNotNull(tokens)
        assertEquals("acc123", tokens!!.accessToken)
        assertEquals("ref456", tokens.refreshToken)
    }

    @Test
    fun parseFromUri_rejects_invalidSchemeHostPath_orMissingParams() {
        // wrong scheme
        assertNull(GoogleOAuth.parseFromUri(Uri.parse("https://auth/google/callback?accessToken=a&refreshToken=r")))
        // wrong host
        assertNull(GoogleOAuth.parseFromUri(Uri.parse("restosoft://bad/google/callback?accessToken=a&refreshToken=r")))
        // wrong path
        assertNull(GoogleOAuth.parseFromUri(Uri.parse("restosoft://auth/google/bad?accessToken=a&refreshToken=r")))
        // missing accessToken
        assertNull(GoogleOAuth.parseFromUri(Uri.parse("restosoft://auth/google/callback?refreshToken=r")))
        // missing refreshToken
        assertNull(GoogleOAuth.parseFromUri(Uri.parse("restosoft://auth/google/callback?accessToken=a")))
        // null
        assertNull(GoogleOAuth.parseFromUri(null))
    }

    // ----- start() -----

    @Test
    fun start_launchesCustomTab_withCorrectUrl_andBaseTrimming() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        // base with trailing slash to verify trimming
        val base = "https://example.com/"
        val expected = expectedAuthUrl(base)

        GoogleOAuth.start(activity, base)

        val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull("No Intent started", startedIntent)
        assertEquals("android.intent.action.VIEW", startedIntent.action)
        assertEquals(expected, startedIntent.dataString)
    }
}