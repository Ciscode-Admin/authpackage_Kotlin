package com.example.loginui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import com.example.loginui.net.LoginUi
import com.example.loginui.net.SignupRequest
import com.example.loginui.net.SignupResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "loginui:signup_result"

        const val ACTION = "action"
        const val ACTION_GO_LOGIN = "go_login"
        const val ACTION_SIGNUP_SUCCESS = "signup_success"
        const val ACTION_SIGNUP_ERROR = "signup_error"

        const val ID = "id"
        const val EMAIL = "email"
        const val NAME = "name"
        const val ROLES = "roles"
        const val STATUS_CODE = "statusCode"
        const val ERROR = "error"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_signup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val inputName = view.findViewById<EditText>(R.id.inputName)
        val inputEmail = view.findViewById<EditText>(R.id.inputEmail)
        val inputPassword = view.findViewById<EditText>(R.id.inputPassword)
        val inputConfirm = view.findViewById<EditText>(R.id.inputConfirm)
        val btnSignup = view.findViewById<AppCompatButton>(R.id.btnSignup)
        val linkGoLogin = view.findViewById<View>(R.id.linkGoLogin)

        fun isValid(): Boolean {
            val emailOk = !inputEmail.text.isNullOrBlank() &&
                    Patterns.EMAIL_ADDRESS.matcher(inputEmail.text.toString().trim()).matches()
            val pass = inputPassword.text?.toString().orEmpty()
            val conf = inputConfirm.text?.toString().orEmpty()
            return emailOk && pass.isNotEmpty() && conf.isNotEmpty() && pass == conf
        }
        fun updateBtn() {
            val enable = isValid()
            btnSignup.isEnabled = enable
            btnSignup.alpha = if (enable) 1f else 0.5f
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateBtn()
        }
        inputEmail.addTextChangedListener(watcher)
        inputPassword.addTextChangedListener(watcher)
        inputConfirm.addTextChangedListener(watcher)
        updateBtn()

        fun setLoading(loading: Boolean) {
            btnSignup.isEnabled = !loading
            btnSignup.alpha = if (loading) 0.5f else 1f
        }

        btnSignup.setOnClickListener {
            if (!isValid()) return@setOnClickListener
            setLoading(true)

            val req = SignupRequest(
                email = inputEmail.text!!.toString().trim(),
                password = inputPassword.text!!.toString(),
                name = inputName.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
            )

            LoginUi.api().register(req).enqueue(object : Callback<SignupResponse> {
                override fun onResponse(
                    call: Call<SignupResponse>, response: Response<SignupResponse>
                ) {
                    view.post {
                        setLoading(false)
                        if (response.isSuccessful && response.body() != null) {
                            val body = response.body()!!
                            parentFragmentManager.setFragmentResult(
                                RESULT_KEY,
                                Bundle().apply {
                                    putString(ACTION, ACTION_SIGNUP_SUCCESS)
                                    putString(ID, body.id)
                                    putString(EMAIL, body.email)
                                    putString(NAME, body.name)
                                    putStringArrayList(ROLES, body.roles?.let { ArrayList(it) })
                                }
                            )
                        } else {
                            val msg = runCatching { response.errorBody()?.string() }
                                .getOrNull()?.take(2000) ?: "HTTP ${response.code()}"
                            parentFragmentManager.setFragmentResult(
                                RESULT_KEY,
                                Bundle().apply {
                                    putString(ACTION, ACTION_SIGNUP_ERROR)
                                    putInt(STATUS_CODE, response.code())
                                    putString(ERROR, msg)
                                }
                            )
                        }
                    }
                }

                override fun onFailure(call: Call<SignupResponse>, t: Throwable) {
                    view.post {
                        setLoading(false)
                        parentFragmentManager.setFragmentResult(
                            RESULT_KEY,
                            Bundle().apply {
                                putString(ACTION, ACTION_SIGNUP_ERROR)
                                putInt(STATUS_CODE, -1)
                                putString(ERROR, t.message ?: "Network error")
                            }
                        )
                    }
                }
            })
        }

        linkGoLogin.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY, Bundle().apply { putString(ACTION, ACTION_GO_LOGIN) }
            )
        }
    }
}