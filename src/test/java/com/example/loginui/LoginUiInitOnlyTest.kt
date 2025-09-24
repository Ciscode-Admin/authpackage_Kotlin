package com.example.loginui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.loginui.net.LoginUi
import com.example.loginui.net.SocialConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class LoginUiInitOnlyTest {

    private fun <T> getStatic(name: String): T? {
        val f = LoginUi::class.java.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return f.get(null) as T?
    }

    private fun setStatic(name: String, value: Any?) {
        val f = LoginUi::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(null, value)
    }

    @Test
    fun init_withBaseUrl_only_createsService_and_usesDefaultSocialConfig() {
        // clean slate so we know init is doing the work
        setStatic("service", null)
        setStatic("socialCfg", null)

        val ctx: Context = ApplicationProvider.getApplicationContext()
        LoginUi.init(ctx, "https://example.com")

        // service should now exist
        assertNotNull("service should be created by init()", getStatic<Any>("service"))

        // socialCfg should be present and have default values (all false)
        val cfg = getStatic<SocialConfig>("socialCfg")
        assertNotNull("socialCfg should be set by init()", cfg)
        assertFalse(cfg!!.showGoogle)
        assertFalse(cfg.showFacebook)
        assertFalse(cfg.showMicrosoft)
    }
}