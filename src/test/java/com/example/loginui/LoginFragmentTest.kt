package com.example.loginui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.widget.AppCompatButton
import org.junit.Test
import androidx.fragment.app.testing.launchFragmentInContainer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.loginui.net.AuthService
import com.example.loginui.net.LoginRequest
import com.example.loginui.net.LoginResponse
import com.example.loginui.net.LoginUi
import com.example.loginui.net.SocialConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.mockito.kotlin.any
import retrofit2.Call
import org.mockito.kotlin.*
import retrofit2.Callback
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class LoginFragmentTest {

    @Test
    fun loginButton_enabled_only_when_email_and_password_nonBlank() {
        // Launch fragment with an AppCompat theme so views inflate correctly.
        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        scenario.onFragment { fragment ->
            val root = fragment.requireView()
            val inputEmail = root.findViewById<EditText>(R.id.inputEmail)
            val inputPassword = root.findViewById<EditText>(R.id.inputPassword)
            val btnLogin = root.findViewById<Button>(R.id.btnLogin)

            // Initial state: both empty -> disabled
            assertFalse(btnLogin.isEnabled)

            // Only email -> disabled
            inputEmail.setText("user@example.com")
            assertFalse(btnLogin.isEnabled)

            // Email + password -> enabled
            inputPassword.setText("secret")
            assertTrue(btnLogin.isEnabled)

            // Clear password -> disabled
            inputPassword.setText("")
            assertFalse(btnLogin.isEnabled)

            // Spaces-only should be treated as blank -> disabled
            inputPassword.setText("   ")
            assertFalse(btnLogin.isEnabled)

            // Restore valid password -> enabled
            inputPassword.setText("p@ss")
            assertTrue(btnLogin.isEnabled)

            // Clear email -> disabled
            inputEmail.setText("")
            assertFalse(btnLogin.isEnabled)
        }
    }

    @Test
    fun clickingLogin_callsAuthService_withEnteredEmailAndPassword() {
        // 1) Inject mocked AuthService into LoginUi
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<LoginResponse>>()
        whenever(mockService.login(any())).thenReturn(mockCall)
        // Don’t trigger callbacks for this verification-only test
        doAnswer { /* no-op */ }.whenever(mockCall).enqueue(any())

        setLoginUiService(mockService)

        // 2) Launch fragment with a working theme
        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        val email = "user@example.com"
        val password = "secret123"

        // 3) Type values and click Login
        scenario.onFragment { fragment ->
            val root = fragment.requireView()
            val inputEmail = root.findViewById<EditText>(R.id.inputEmail)
            val inputPassword = root.findViewById<EditText>(R.id.inputPassword)
            val btnLogin = root.findViewById<Button>(R.id.btnLogin)

            inputEmail.setText(email)
            inputPassword.setText(password)
            btnLogin.performClick()
        }

        // 4) Verify call once with the same values
        val reqCaptor = argumentCaptor<LoginRequest>()
        verify(mockService, times(1)).login(reqCaptor.capture())
        assertEquals(email, reqCaptor.firstValue.email)
        assertEquals(password, reqCaptor.firstValue.password)

        // (optional) clean-up
        setLoginUiService(null)
    }

    @Test
    fun onSuccessfulLogin_emitsSuccessActionWithTokens() {
        // Mock service + call that triggers success
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<LoginResponse>>()
        whenever(mockService.login(any())).thenReturn(mockCall)
        doAnswer { inv ->
            val cb = inv.arguments[0] as Callback<LoginResponse>
            cb.onResponse(mockCall, Response.success(LoginResponse("acc123", "ref456")))
            null
        }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        // Register listener BEFORE click, using the Activity as owner
        val box = arrayOfNulls<Bundle>(1)
        scenario.onFragment { f ->
            val activity = f.requireActivity()
            activity.supportFragmentManager.setFragmentResultListener(
                LoginFragment.RESULT_KEY, activity
            ) { _, bundle -> box[0] = bundle }
        }

        // Enter creds and click
        scenario.onFragment { f ->
            val v = f.requireView()
            v.findViewById<EditText>(R.id.inputEmail).setText("user@example.com")
            v.findViewById<EditText>(R.id.inputPassword).setText("secret")
            v.findViewById<Button>(R.id.btnLogin).performClick()
        }

        // Let posted UI tasks run (LoginFragment uses view.post { ... })
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // Assert emitted success with tokens
        val result = box[0]
        assertNotNull("Expected a FragmentResult", result)
        assertEquals(LoginFragment.ACTION_LOGIN_SUCCESS, result!!.getString(LoginFragment.ACTION))
        assertEquals("acc123", result.getString(LoginFragment.ACCESS_TOKEN))
        assertEquals("ref456", result.getString(LoginFragment.REFRESH_TOKEN))

        setLoginUiService(null)
    }

    @Test
    fun onHttpError_emitsErrorActionWithStatusAndMessage() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<LoginResponse>>()
        whenever(mockService.login(any())).thenReturn(mockCall)

        // Make enqueue() invoke onResponse with HTTP 400
        doAnswer { inv ->
            val cb = inv.arguments[0] as Callback<LoginResponse>
            val body = ResponseBody.create(
                "application/json".toMediaTypeOrNull(),
                """{"message":"Incorrect password."}"""
            )
            cb.onResponse(mockCall, Response.error(400, body))
            null
        }.whenever(mockCall).enqueue(any())

        setLoginUiService(mockService)

        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        val box = arrayOfNulls<Bundle>(1)
        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(LoginFragment.RESULT_KEY, act) { _, b -> box[0] = b }

            val v = f.requireView()
            v.findViewById<EditText>(R.id.inputEmail).setText("user@example.com")
            v.findViewById<EditText>(R.id.inputPassword).setText("wrong-pass")
            v.findViewById<Button>(R.id.btnLogin).performClick()
        }

        // Process posted UI work
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val result = box[0]
        assertNotNull(result)
        assertEquals(LoginFragment.ACTION_LOGIN_ERROR, result!!.getString(LoginFragment.ACTION))
        assertEquals(400, result.getInt(LoginFragment.STATUS_CODE))
        assertTrue(result.getString(LoginFragment.ERROR)!!.contains("Incorrect password."))

        setLoginUiService(null)
    }

    @Test
    fun onNetworkFailure_emitsErrorActionWithMinusOneAndMessage() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<LoginResponse>>()
        whenever(mockService.login(any())).thenReturn(mockCall)

        // enqueue() triggers onFailure
        doAnswer { inv ->
            val cb = inv.arguments[0] as Callback<LoginResponse>
            cb.onFailure(mockCall, RuntimeException("Network down"))
            null
        }.whenever(mockCall).enqueue(any())

        setLoginUiService(mockService)

        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        val box = arrayOfNulls<Bundle>(1)
        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(LoginFragment.RESULT_KEY, act) { _, b -> box[0] = b }

            val v = f.requireView()
            v.findViewById<EditText>(R.id.inputEmail).setText("user@example.com")
            v.findViewById<EditText>(R.id.inputPassword).setText("secret")
            v.findViewById<Button>(R.id.btnLogin).performClick()
        }

        // Process posted UI work
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val result = box[0]
        assertNotNull(result)
        assertEquals(LoginFragment.ACTION_LOGIN_ERROR, result!!.getString(LoginFragment.ACTION))
        assertEquals(-1, result.getInt(LoginFragment.STATUS_CODE))
        assertTrue(result.getString(LoginFragment.ERROR)!!.contains("Network down"))

        setLoginUiService(null)
    }

    @Test
    fun socialButtons_visibility_matchesSocialConfig() {
        // Case A: all false → all GONE
        setSocialConfig(SocialConfig(showGoogle = false, showFacebook = false, showMicrosoft = false))

        var scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )
        scenario.onFragment { f ->
            val v = f.requireView()
            assertEquals(View.GONE, v.findViewById<AppCompatButton>(R.id.btnGoogle).visibility)
            assertEquals(View.GONE, v.findViewById<AppCompatButton>(R.id.btnFacebook).visibility)
            assertEquals(View.GONE, v.findViewById<AppCompatButton>(R.id.btnMicrosoft).visibility)
        }
        scenario.close()

        // Case B: all true → all VISIBLE
        setSocialConfig(SocialConfig(showGoogle = true, showFacebook = true, showMicrosoft = true))

        scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )
        scenario.onFragment { f ->
            val v = f.requireView()
            assertEquals(View.VISIBLE, v.findViewById<AppCompatButton>(R.id.btnGoogle).visibility)
            assertEquals(View.VISIBLE, v.findViewById<AppCompatButton>(R.id.btnFacebook).visibility)
            assertEquals(View.VISIBLE, v.findViewById<AppCompatButton>(R.id.btnMicrosoft).visibility)
        }
        scenario.close()
    }

    @Test
    fun buttonDisablesDuringRequest_andReenablesOnSuccess() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<LoginResponse>>()
        whenever(mockService.login(any())).thenReturn(mockCall)

        // Capture the callback; don't call it yet
        val cbHolder = arrayOfNulls<Callback<LoginResponse>>(1)
        doAnswer { inv ->
            cbHolder[0] = inv.arguments[0] as Callback<LoginResponse>
            null
        }.whenever(mockCall).enqueue(any())

        setLoginUiService(mockService)

        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        lateinit var btnLogin: Button
        scenario.onFragment { f ->
            val v = f.requireView()
            v.findViewById<EditText>(R.id.inputEmail).setText("user@example.com")
            v.findViewById<EditText>(R.id.inputPassword).setText("secret")
            btnLogin = v.findViewById(R.id.btnLogin)
            // Precondition: enabled
            assertTrue(btnLogin.isEnabled)

            // Click → should disable
            btnLogin.performClick()
            assertFalse("Button should be disabled during request", btnLogin.isEnabled)
        }

        // Now deliver success
        val cb = requireNotNull(cbHolder[0]) { "Callback not captured" }
        cb.onResponse(mockCall, Response.success(LoginResponse("acc", "ref")))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // Button re-enabled after response
        scenario.onFragment { _ ->
            assertTrue("Button should be re-enabled after success", btnLogin.isEnabled)
        }

        setLoginUiService(null)
    }

    @Test
    fun buttonDisablesDuringRequest_andReenablesOnError() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<LoginResponse>>()
        whenever(mockService.login(any())).thenReturn(mockCall)

        val cbHolder = arrayOfNulls<Callback<LoginResponse>>(1)
        doAnswer { inv ->
            cbHolder[0] = inv.arguments[0] as Callback<LoginResponse>
            null
        }.whenever(mockCall).enqueue(any())

        setLoginUiService(mockService)

        val scenario = launchFragmentInContainer<LoginFragment>(
            fragmentArgs = Bundle(),
            themeResId = com.example.loginui.R.style.LoginUiTestTheme
        )

        lateinit var btnLogin: Button
        scenario.onFragment { f ->
            val v = f.requireView()
            v.findViewById<EditText>(R.id.inputEmail).setText("user@example.com")
            v.findViewById<EditText>(R.id.inputPassword).setText("secret")
            btnLogin = v.findViewById(R.id.btnLogin)
            assertTrue(btnLogin.isEnabled)

            btnLogin.performClick()
            assertFalse("Button should be disabled during request", btnLogin.isEnabled)
        }

        // Deliver HTTP error
        val cb = requireNotNull(cbHolder[0])
        val errBody = okhttp3.ResponseBody.create("text/plain".toMediaTypeOrNull(), "boom")
        cb.onResponse(mockCall, Response.error(500, errBody))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        scenario.onFragment { _ ->
            assertTrue("Button should be re-enabled after error", btnLogin.isEnabled)
        }

        setLoginUiService(null)
    }

    // Helper
    private fun setSocialConfig(cfg: SocialConfig) {
        val f = LoginUi::class.java.getDeclaredField("socialCfg")
        f.isAccessible = true
        f.set(null, cfg)
    }

    private fun setLoginUiService(service: AuthService?) {
        val f = LoginUi::class.java.getDeclaredField("service")
        f.isAccessible = true
        f.set(null, service)
    }
}