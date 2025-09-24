package com.example.loginui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.loginui.net.AuthService
import com.example.loginui.net.LoginUi
import com.example.loginui.net.SocialConfig
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginUiInitTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun init_withSocialConfig_buildsService_andStoresConfig() {
        // given
        val cfg = SocialConfig(showGoogle = true, showFacebook = false, showMicrosoft = true)

        // when
        LoginUi.init(ctx, "https://example.com", cfg)
        val service1 = LoginUi.api()
        val service2 = LoginUi.api() // call twice to exercise the "already built" path

        // then
        assertNotNull(service1)
        assertSame("api() should return the same singleton instance", service1, service2)
        assertTrue(LoginUi.social().showGoogle)
        assertFalse(LoginUi.social().showFacebook)
        assertTrue(LoginUi.social().showMicrosoft)

        // sanity: type is the expected Retrofit interface
        assertTrue(service1 is AuthService)
    }

    @Test
    fun init_withoutSocialConfig_usesDefaults_and_apiNotNull() {
        // when
        LoginUi.init(ctx, "https://another.example/") // trailing slash ok
        val service = LoginUi.api()

        // then
        assertNotNull(service)
        // defaults should exist even if we didn't pass a config
        val cfg = LoginUi.social()
        // We don't assert specific booleans (implementation may change), just that it's present.
        assertNotNull(cfg)
    }
}