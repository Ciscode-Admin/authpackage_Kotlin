package com.example.loginui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import com.example.loginui.net.LoginRequest
import com.example.loginui.net.LoginResponse
import com.example.loginui.net.LoginUi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "loginui:result"

        const val ACTION = "action"
        const val ACTION_LOGIN_SUCCESS = "login_success"
        const val ACTION_LOGIN_ERROR = "login_error"
        const val ACTION_GOOGLE = "google"
        const val ACTION_FACEBOOK = "facebook"
        const val ACTION_FORGOT = "forgot"
        const val ACTION_SIGNUP = "signup"
        const val ACTION_MICROSOFT = "microsoft"

        const val EMAIL = "email"            // (not emitted on success; kept for parity if needed)
        const val PASSWORD = "password"      // (never emitted)
        const val ACCESS_TOKEN = "accessToken"
        const val REFRESH_TOKEN = "refreshToken"
        const val STATUS_CODE = "statusCode"
        const val ERROR = "error"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val inputEmail = view.findViewById<EditText>(R.id.inputEmail)
        val inputPassword = view.findViewById<EditText>(R.id.inputPassword)
        val btnLogin   = view.findViewById<AppCompatButton>(R.id.btnLogin)
        val btnGoogle  = view.findViewById<AppCompatButton>(R.id.btnGoogle)
        val btnFacebook= view.findViewById<AppCompatButton>(R.id.btnFacebook)
        val btnMicrosoft = view.findViewById<AppCompatButton>(R.id.btnMicrosoft)
        val linkForgot = view.findViewById<TextView>(R.id.linkForgot)
        val linkSignup = view.findViewById<TextView>(R.id.linkSignup)

        val cfg = LoginUi.social()
        btnGoogle.visibility    = if (cfg.showGoogle) View.VISIBLE else View.GONE
        btnFacebook.visibility  = if (cfg.showFacebook) View.VISIBLE else View.GONE
        btnMicrosoft.visibility = if (cfg.showMicrosoft) View.VISIBLE else View.GONE

        fun updateButton() {
            val enabled = !inputEmail.text.isNullOrBlank() && !inputPassword.text.isNullOrBlank()
            btnLogin.isEnabled = enabled
            btnLogin.alpha = if (enabled) 1f else 0.5f
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateButton()
        }
        inputEmail.addTextChangedListener(watcher)
        inputPassword.addTextChangedListener(watcher)
        updateButton()

        fun setLoading(loading: Boolean) {
            btnLogin.isEnabled = !loading
            btnLogin.alpha = if (loading) 0.5f else 1f
        }

        btnLogin.setOnClickListener {
            val email = inputEmail.text?.toString()?.trim().orEmpty()
            val password = inputPassword.text?.toString().orEmpty()
            if (email.isEmpty() || password.isEmpty()) return@setOnClickListener

            setLoading(true)

            LoginUi.api().login(LoginRequest(email, password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(
                        call: Call<LoginResponse>,
                        response: Response<LoginResponse>
                    ) {
                        view.post {
                            setLoading(false)
                            if (response.isSuccessful && response.body() != null) {
                                val body = response.body()!!
                                parentFragmentManager.setFragmentResult(
                                    RESULT_KEY,
                                    Bundle().apply {
                                        putString(ACTION, ACTION_LOGIN_SUCCESS)
                                        putString(ACCESS_TOKEN, body.accessToken)
                                        putString(REFRESH_TOKEN, body.refreshToken)
                                    }
                                )
                            } else {
                                val msg = runCatching { response.errorBody()?.string() }
                                    .getOrNull()
                                    ?.take(2000)
                                    ?.takeUnless { it.isBlank() }
                                    ?: "HTTP ${response.code()}"
                                parentFragmentManager.setFragmentResult(
                                    RESULT_KEY,
                                    Bundle().apply {
                                        putString(ACTION, ACTION_LOGIN_ERROR)
                                        putInt(STATUS_CODE, response.code())
                                        putString(ERROR, msg)
                                    }
                                )
                            }
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        view.post {
                            setLoading(false)
                            parentFragmentManager.setFragmentResult(
                                RESULT_KEY,
                                Bundle().apply {
                                    putString(ACTION, ACTION_LOGIN_ERROR)
                                    putInt(STATUS_CODE, -1)
                                    putString(ERROR, t.message ?: "Network error")
                                }
                            )
                        }
                    }
                })
        }

        btnGoogle.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY, Bundle().apply { putString(ACTION, ACTION_GOOGLE) }
            )
        }
        btnFacebook.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY, Bundle().apply { putString(ACTION, ACTION_FACEBOOK) }
            )
        }
        btnMicrosoft.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY, Bundle().apply { putString(ACTION, ACTION_MICROSOFT) }
            )
        }
        linkForgot.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY, Bundle().apply { putString(ACTION, ACTION_FORGOT) }
            )
        }
        linkSignup.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY, Bundle().apply { putString(ACTION, ACTION_SIGNUP) }
            )
        }
    }
}