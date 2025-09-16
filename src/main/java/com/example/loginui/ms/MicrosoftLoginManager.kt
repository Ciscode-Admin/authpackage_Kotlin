package com.example.loginui.ms

import androidx.fragment.app.Fragment
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import java.util.concurrent.CancellationException

object MicrosoftLoginManager {

    private var pca: IPublicClientApplication? = null

    private fun ensurePca(fragment: Fragment, msalConfigResId: Int): IPublicClientApplication {
        val existing = pca
        if (existing != null) return existing
        // Synchronous create from resource config
        val created = PublicClientApplication.create(
            fragment.requireContext().applicationContext,
            msalConfigResId
        )
        pca = created
        return created
    }

    /**
     * Launch interactive sign-in and return the MSAL result (with idToken) via callback.
     */
    fun signIn(
        fragment: Fragment,
        msalConfigResId: Int,
        onResult: (result: IAuthenticationResult?, error: Exception?) -> Unit
    ) {
        val app = ensurePca(fragment, msalConfigResId)

        val parameters = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(fragment.requireActivity())
            .withScopes(listOf("openid", "profile", "email")) // enough to get idToken
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    onResult(authenticationResult, null)
                }

                override fun onError(exception: MsalException) {
                    onResult(null, exception)
                }

                override fun onCancel() {
                    onResult(null, CancellationException("User cancelled"))
                }
            })
            .build()

        app.acquireToken(parameters)
    }
}