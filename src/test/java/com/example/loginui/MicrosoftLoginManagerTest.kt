package com.example.loginui

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.loginui.ms.MicrosoftLoginManager
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import androidx.fragment.app.testing.launchFragmentInContainer
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class MicrosoftLoginManagerTest {

    // ---------- Helpers (reused across tests) ----------
    private fun setPca(pca: IPublicClientApplication?) {
        val f = MicrosoftLoginManager::class.java.getDeclaredField("pca")
        f.isAccessible = true
        f.set(null, pca)
    }

    private fun extractCallback(params: AcquireTokenParameters): AuthenticationCallback {
        // Be resilient to MSAL internals: find any field of type AuthenticationCallback
        var c: Class<*>? = params.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.type == AuthenticationCallback::class.java }?.let {
                it.isAccessible = true
                return it.get(params) as AuthenticationCallback
            }
            c = c.superclass
        }
        throw IllegalStateException("AuthenticationCallback not found in AcquireTokenParameters")
    }

    private fun launchLoginFragment() =
        launchFragmentInContainer<LoginFragment>(Bundle(), com.example.loginui.R.style.LoginUiTestTheme)

    // ---------- Tests ----------
    @Test
    fun signIn_success_invokesOnResultWithAuthenticationResult() {
        val mockApp = mock<IPublicClientApplication>()
        val cbHolder = arrayOfNulls<AuthenticationCallback>(1)

        // Capture AcquireTokenParameters and stash its callback
        doAnswer { inv ->
            val params = inv.arguments[0] as AcquireTokenParameters
            cbHolder[0] = extractCallback(params)
            null
        }.whenever(mockApp).acquireToken(any())

        setPca(mockApp)

        val box = arrayOfNulls<IAuthenticationResult>(1)
        val err = arrayOfNulls<Exception>(1)

        val scenario = launchLoginFragment()
        scenario.onFragment { f ->
            MicrosoftLoginManager.signIn(f, /*msalConfigResId=*/0) { result, error ->
                box[0] = result
                err[0] = error
            }
        }

        val cb = requireNotNull(cbHolder[0]) { "Callback not captured" }
        val mockResult = mock<IAuthenticationResult>()
        cb.onSuccess(mockResult)

        assertSame(mockResult, box[0])
        assertNull(err[0])

        setPca(null)
    }

    @Test
    fun signIn_error_invokesOnResultWithException() {
        val mockApp = mock<IPublicClientApplication>()
        val cbHolder = arrayOfNulls<AuthenticationCallback>(1)

        doAnswer { inv ->
            val params = inv.arguments[0] as AcquireTokenParameters
            cbHolder[0] = extractCallback(params)
            null
        }.whenever(mockApp).acquireToken(any())

        setPca(mockApp)

        val box = arrayOfNulls<IAuthenticationResult>(1)
        val err = arrayOfNulls<Exception>(1)

        val scenario = launchLoginFragment()
        scenario.onFragment { f ->
            MicrosoftLoginManager.signIn(f, 0) { result, error ->
                box[0] = result
                err[0] = error
            }
        }

        val cb = requireNotNull(cbHolder[0])
        val msalEx = mock<MsalException>()
        cb.onError(msalEx)

        assertNull(box[0])
        assertSame(msalEx, err[0])

        setPca(null)
    }

    @Test
    fun signIn_cancel_invokesOnResultWithCancellationException() {
        val mockApp = mock<IPublicClientApplication>()
        val cbHolder = arrayOfNulls<AuthenticationCallback>(1)

        doAnswer { inv ->
            val params = inv.arguments[0] as AcquireTokenParameters
            cbHolder[0] = extractCallback(params)
            null
        }.whenever(mockApp).acquireToken(any())

        setPca(mockApp)

        val box = arrayOfNulls<IAuthenticationResult>(1)
        val err = arrayOfNulls<Exception>(1)

        val scenario = launchLoginFragment()
        scenario.onFragment { f ->
            MicrosoftLoginManager.signIn(f, 0) { result, error ->
                box[0] = result
                err[0] = error
            }
        }

        val cb = requireNotNull(cbHolder[0])
        cb.onCancel()

        assertNull(box[0])
        assertTrue(err[0] is CancellationException)

        setPca(null)
    }
}