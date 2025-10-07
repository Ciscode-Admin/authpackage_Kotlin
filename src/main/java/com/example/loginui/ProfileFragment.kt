package com.example.loginui

import androidx.fragment.app.Fragment
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.motion.widget.MotionLayout
import com.example.loginui.net.LoginUi
import com.example.loginui.net.UpdateUserRequest
import com.example.loginui.net.UserDto
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "loginui:profile_result"
        const val ACTION_PROFILE_SAVED = "profile_saved"
        const val ACTION_LOGOUT = "profile_logout"
        const val NAME = "name"
        const val EMAIL = "email"
    }

    private lateinit var motion: MotionLayout
    private lateinit var avatarInitial: TextView
    private lateinit var textName: TextView
    private lateinit var textEmail: TextView
    private lateinit var inputName: TextInputEditText
    private lateinit var btnEditIcon: ImageButton
    private lateinit var btnSaveFab: FloatingActionButton
    private lateinit var btnLogout: AppCompatButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Apply our theme overlay so ?attr/lu_* resolve to host/theme or library defaults.
        val themedCtx: Context = ContextThemeWrapper(
            requireContext(),
            R.style.ThemeOverlay_LoginUi_ProfileDefaults
        )
        return inflater.cloneInContext(themedCtx)
            .inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        motion = view.findViewById(R.id.profileMotion)
        avatarInitial = view.findViewById(R.id.avatarInitial)
        textName = view.findViewById(R.id.textName)
        textEmail = view.findViewById(R.id.textEmail)
        inputName = view.findViewById(R.id.inputName)
        btnEditIcon = view.findViewById(R.id.btnEditIcon)
        btnSaveFab = view.findViewById(R.id.btnSaveFab)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Wire clicks
        btnEditIcon.setOnClickListener {
            inputName.setText(textName.text)
            motion.transitionToEnd()
        }
        btnSaveFab.setOnClickListener { saveName() }
        btnLogout.setOnClickListener {
            LoginUi.performLogout()
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString("action", ACTION_LOGOUT) }
            )
            Toast.makeText(requireContext(), getString(R.string.lu_logout), Toast.LENGTH_SHORT).show()
        }

        // Fetch profile
        fetchProfile()
    }

    private fun fetchProfile() {
        LoginUi.api().me().enqueue(object : Callback<UserDto> {
            override fun onResponse(call: Call<UserDto>, response: Response<UserDto>) {
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    val name = (user.name ?: "").trim()
                    val email = (user.email ?: "").trim()
                    textEmail.text = email
                    textName.text = if (name.isNotEmpty()) name else deriveNameFromEmail(email)
                    setAvatarInitial(textName.text.toString(), email)
                } else {
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UserDto>, t: Throwable) {
                Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveName() {
        val newName = inputName.text?.toString()?.trim().orEmpty()
        if (newName.isEmpty() || newName.length > 100) {
            Toast.makeText(requireContext(), "Name must be 1–100 chars", Toast.LENGTH_SHORT).show()
            return
        }

        // Optional: simple disable to avoid double taps
        btnSaveFab.isEnabled = false

        LoginUi.api().updateMe(UpdateUserRequest(newName))
            .enqueue(object : Callback<UserDto> {
                override fun onResponse(call: Call<UserDto>, response: Response<UserDto>) {
                    btnSaveFab.isEnabled = true
                    if (response.isSuccessful && response.body() != null) {
                        val updated = response.body()!!
                        val shownName = (updated.name ?: newName)
                        textName.text = shownName
                        setAvatarInitial(shownName, updated.email ?: textEmail.text.toString())
                        motion.transitionToStart()

                        // Emit result for host if they want to react
                        parentFragmentManager.setFragmentResult(
                            RESULT_KEY,
                            Bundle().apply {
                                putString("action", ACTION_PROFILE_SAVED)
                                putString(NAME, shownName)
                                putString(EMAIL, updated.email ?: textEmail.text.toString())
                            }
                        )
                        Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Save failed (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<UserDto>, t: Throwable) {
                    btnSaveFab.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setAvatarInitial(name: String, email: String) {
        val initial = when {
            name.trim().isNotEmpty() -> name.trim().first().uppercaseChar()
            email.isNotBlank() -> email.substringBefore('@').trim().firstOrNull()?.uppercaseChar() ?: '•'
            else -> '•'
        }
        avatarInitial.text = initial.toString()
    }

    private fun deriveNameFromEmail(email: String): String {
        val local = email.substringBefore('@')
        return if (local.isNotBlank())
            local.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        else "—"
    }
}