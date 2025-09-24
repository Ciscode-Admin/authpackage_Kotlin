package com.example.loginui

import android.os.Bundle
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import androidx.fragment.app.testing.launchFragmentInContainer
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class LoginFragmentNavTest {

    @Test
    fun clickGoSignup_emitsSignupAction() {
        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        val box = arrayOfNulls<Bundle>(1)

        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(
                LoginFragment.RESULT_KEY, act
            ) { _, b -> box[0] = b }

            // use the actual id and action constant from your layout/fragment
            f.requireView()
                .findViewById<TextView>(R.id.linkSignup)
                .performClick()
        }

        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val result = requireNotNull(box[0])
        assertEquals(LoginFragment.ACTION_SIGNUP, result.getString(LoginFragment.ACTION))
    }
}