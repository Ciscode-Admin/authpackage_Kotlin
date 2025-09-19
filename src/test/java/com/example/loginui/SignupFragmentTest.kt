package com.example.loginui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.loginui.net.AuthService
import com.example.loginui.net.LoginUi
import androidx.fragment.app.testing.launchFragmentInContainer
import com.example.loginui.net.SignupRequest
import com.example.loginui.net.SignupResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import org.junit.Assert.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignupFragmentTest {

    // ---- Helpers ----
    private fun setLoginUiService(service: AuthService?) {
        val f = LoginUi::class.java.getDeclaredField("service")
        f.isAccessible = true
        f.set(null, service)
    }

    private fun idleMain() =
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun launch() =
        launchFragmentInContainer<SignupFragment>(Bundle(), com.example.loginui.R.style.LoginUiTestTheme)

    private fun enter(
        frag: SignupFragment,
        name: String? = null,
        email: String,
        pass: String,
        confirm: String
    ) {
        val v = frag.requireView()
        name?.let { v.findViewById<EditText>(R.id.inputName).setText(it) }
        v.findViewById<EditText>(R.id.inputEmail).setText(email)
        v.findViewById<EditText>(R.id.inputPassword).setText(pass)
        v.findViewById<EditText>(R.id.inputConfirm).setText(confirm)
    }

    // 1) Enable/disable rules
    @Test
    fun signUpButton_enabled_only_when_emailValid_and_passwordsMatchNonEmpty() {
        val scenario = launch()
        scenario.onFragment { f ->
            val v = f.requireView()
            val btn = v.findViewById<Button>(R.id.btnSignup)

            // initial disabled (empty)
            assertFalse(btn.isEnabled)

            // invalid email -> still disabled
            enter(f, email = "not-an-email", pass = "a", confirm = "a")
            assertFalse(btn.isEnabled)

            // valid email but passwords mismatch -> disabled
            enter(f, email = "u@e.com", pass = "abc", confirm = "xyz")
            assertFalse(btn.isEnabled)

            // valid email + matching non-empty -> enabled
            enter(f, email = "u@e.com", pass = "abc", confirm = "abc")
            assertTrue(btn.isEnabled)

            // empty confirm -> disabled
            enter(f, email = "u@e.com", pass = "abc", confirm = "")
            assertFalse(btn.isEnabled)
        }
    }

    // 2) Click calls register with entered fields
    @Test
    fun clickingSignup_callsAuthService_register_withEnteredFields() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<SignupResponse>>()
        whenever(mockService.register(any())).thenReturn(mockCall)
        doAnswer { /* no-op */ }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launch()
        val name = "Alice"
        val email = "alice@example.com"
        val pass = "secret123"

        scenario.onFragment { f ->
            enter(f, name, email, pass, pass)
            f.requireView().findViewById<Button>(R.id.btnSignup).performClick()
        }

        val captor = argumentCaptor<SignupRequest>()
        verify(mockService, times(1)).register(captor.capture())
        assertEquals(email, captor.firstValue.email)
        assertEquals(pass, captor.firstValue.password)
        assertEquals(name, captor.firstValue.name)

        setLoginUiService(null)
    }

    // 3) Success -> emits ACTION_SIGNUP_SUCCESS with payload
    @Test
    fun onSuccessfulSignup_emitsSuccessWithIdEmailNameRoles() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<SignupResponse>>()
        whenever(mockService.register(any())).thenReturn(mockCall)
        doAnswer { inv ->
            val cb = inv.arguments[0] as Callback<SignupResponse>
            cb.onResponse(mockCall, Response.success(SignupResponse(
                id = "id123", email = "a@b.com", name = "Alice", roles = listOf("user","admin")
            )))
            null
        }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launch()
        val box = arrayOfNulls<Bundle>(1)

        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(SignupFragment.RESULT_KEY, act) { _, b -> box[0] = b }

            enter(f, "Alice", "a@b.com", "p", "p")
            f.requireView().findViewById<Button>(R.id.btnSignup).performClick()
        }
        idleMain()

        val result = box[0]
        assertNotNull(result)
        assertEquals(SignupFragment.ACTION_SIGNUP_SUCCESS, result!!.getString(SignupFragment.ACTION))
        assertEquals("id123", result.getString(SignupFragment.ID))
        assertEquals("a@b.com", result.getString(SignupFragment.EMAIL))
        assertEquals("Alice", result.getString(SignupFragment.NAME))
        assertEquals(arrayListOf("user","admin"), result.getStringArrayList(SignupFragment.ROLES))

        setLoginUiService(null)
    }

    // 4) HTTP error -> emits ACTION_SIGNUP_ERROR with status/message
    @Test
    fun onHttpError_emitsErrorWithStatusAndMessage() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<SignupResponse>>()
        whenever(mockService.register(any())).thenReturn(mockCall)
        doAnswer { inv ->
            val cb = inv.arguments[0] as Callback<SignupResponse>
            val body = ResponseBody.create("application/json".toMediaTypeOrNull(), """{"message":"Email already in use."}""")
            cb.onResponse(mockCall, Response.error(409, body))
            null
        }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launch()
        val box = arrayOfNulls<Bundle>(1)

        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(SignupFragment.RESULT_KEY, act) { _, b -> box[0] = b }

            enter(f, "Alice", "a@b.com", "p", "p")
            f.requireView().findViewById<Button>(R.id.btnSignup).performClick()
        }
        idleMain()

        val result = box[0]
        assertNotNull(result)
        assertEquals(SignupFragment.ACTION_SIGNUP_ERROR, result!!.getString(SignupFragment.ACTION))
        assertEquals(409, result.getInt(SignupFragment.STATUS_CODE))
        assertTrue(result.getString(SignupFragment.ERROR)!!.contains("Email already in use."))

        setLoginUiService(null)
    }

    // 5) Network failure -> emits ACTION_SIGNUP_ERROR with -1 and message
    @Test
    fun onNetworkFailure_emitsErrorWithMinusOneAndMessage() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<SignupResponse>>()
        whenever(mockService.register(any())).thenReturn(mockCall)
        doAnswer { inv ->
            val cb = inv.arguments[0] as Callback<SignupResponse>
            cb.onFailure(mockCall, RuntimeException("Network down"))
            null
        }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launch()
        val box = arrayOfNulls<Bundle>(1)

        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(SignupFragment.RESULT_KEY, act) { _, b -> box[0] = b }

            enter(f, "Alice", "a@b.com", "p", "p")
            f.requireView().findViewById<Button>(R.id.btnSignup).performClick()
        }
        idleMain()

        val result = box[0]
        assertNotNull(result)
        assertEquals(SignupFragment.ACTION_SIGNUP_ERROR, result!!.getString(SignupFragment.ACTION))
        assertEquals(-1, result.getInt(SignupFragment.STATUS_CODE))
        assertTrue(result.getString(SignupFragment.ERROR)!!.contains("Network down"))

        setLoginUiService(null)
    }

    // 6) Button disables during request and re-enables after success/error
    @Test
    fun buttonDisablesDuringRequest_andReenablesOnSuccess() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<SignupResponse>>()
        whenever(mockService.register(any())).thenReturn(mockCall)

        val cbHolder = arrayOfNulls<Callback<SignupResponse>>(1)
        doAnswer { inv ->
            cbHolder[0] = inv.arguments[0] as Callback<SignupResponse>
            null
        }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launch()
        lateinit var btn: Button

        scenario.onFragment { f ->
            enter(f, "Alice", "a@b.com", "p", "p")
            btn = f.requireView().findViewById(R.id.btnSignup)
            assertTrue(btn.isEnabled)
            btn.performClick()
            assertFalse(btn.isEnabled)
        }

        val cb = requireNotNull(cbHolder[0])
        cb.onResponse(mockCall, Response.success(SignupResponse("id1","a@b.com","Alice", listOf("user"))))
        idleMain()

        scenario.onFragment { _ -> assertTrue(btn.isEnabled) }
        setLoginUiService(null)
    }

    @Test
    fun buttonDisablesDuringRequest_andReenablesOnError() {
        val mockService = mock<AuthService>()
        val mockCall = mock<Call<SignupResponse>>()
        whenever(mockService.register(any())).thenReturn(mockCall)

        val cbHolder = arrayOfNulls<Callback<SignupResponse>>(1)
        doAnswer { inv ->
            cbHolder[0] = inv.arguments[0] as Callback<SignupResponse>
            null
        }.whenever(mockCall).enqueue(any())
        setLoginUiService(mockService)

        val scenario = launch()
        lateinit var btn: Button

        scenario.onFragment { f ->
            enter(f, "Alice", "a@b.com", "p", "p")
            btn = f.requireView().findViewById(R.id.btnSignup)
            assertTrue(btn.isEnabled)
            btn.performClick()
            assertFalse(btn.isEnabled)
        }

        val cb = requireNotNull(cbHolder[0])
        val errBody = ResponseBody.create("text/plain".toMediaTypeOrNull(), "boom")
        cb.onResponse(mockCall, Response.error(500, errBody))
        idleMain()

        scenario.onFragment { _ -> assertTrue(btn.isEnabled) }
        setLoginUiService(null)
    }

    // 7) Go-login link emits ACTION_GO_LOGIN
    @Test
    fun clickGoLogin_emitsGoLoginAction() {
        val scenario = launch()
        val box = arrayOfNulls<Bundle>(1)

        scenario.onFragment { f ->
            val act = f.requireActivity()
            act.supportFragmentManager.setFragmentResultListener(SignupFragment.RESULT_KEY, act) { _, b -> box[0] = b }
            f.requireView().findViewById<TextView>(R.id.linkGoLogin).performClick()
        }
        idleMain()

        assertEquals(SignupFragment.ACTION_GO_LOGIN, box[0]!!.getString(SignupFragment.ACTION))
    }
}