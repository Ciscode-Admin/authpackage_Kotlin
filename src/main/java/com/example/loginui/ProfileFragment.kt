package com.example.loginui

import androidx.fragment.app.Fragment
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.motion.widget.MotionLayout
import com.example.loginui.net.LoginUi
import com.example.loginui.net.MeResponse
import com.example.loginui.net.UpdateUserRequest
import com.example.loginui.net.UserDto
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    private lateinit var labelNameView: TextView
    private lateinit var labelNameEdit: TextView
    private lateinit var labelEmailView: TextView
    private lateinit var labelEmailEdit: TextView

    // View mode
    private lateinit var textName: TextView
    private lateinit var textEmail: TextView

    // Edit mode
    private lateinit var inputNameLayout: TextInputLayout
    private lateinit var inputName: TextInputEditText
    private lateinit var inputEmailLayout: TextInputLayout
    private lateinit var inputEmail: TextInputEditText

    // Card
    private lateinit var infoCard: MaterialCardView
    @ColorInt private var cardColorViewMode: Int = 0
    private var origCardColor: ColorStateList? = null
    private var origCardElevation: Float = 0f
    private var origUseCompatPadding: Boolean = false

    // Actions
    private lateinit var btnEditIcon: ImageButton
    private lateinit var btnSaveFab: FloatingActionButton
    private lateinit var btnLogout: AppCompatButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
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

        inputNameLayout = view.findViewById(R.id.inputNameLayout)
        inputName = view.findViewById(R.id.inputName)
        inputEmailLayout = view.findViewById(R.id.inputEmailLayout)
        inputEmail = view.findViewById(R.id.inputEmail)

        labelNameView = view.findViewById(R.id.labelNameView)
        labelNameEdit = view.findViewById(R.id.labelNameEdit)
        labelEmailView = view.findViewById(R.id.labelEmailView)
        labelEmailEdit = view.findViewById(R.id.labelEmailEdit)

        infoCard = view.findViewById(R.id.infoCard)
        cardColorViewMode = resolveAttrColor(R.attr.lu_profileCardColor)

        // Capture original card appearance to restore after editing
        origCardColor = infoCard.cardBackgroundColor
        origCardElevation = infoCard.cardElevation
        origUseCompatPadding = infoCard.useCompatPadding

        btnEditIcon = view.findViewById(R.id.btnEditIcon)
        btnSaveFab = view.findViewById(R.id.btnSaveFab)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Enter edit → transparent & flat card; inputs stay white
        btnEditIcon.setOnClickListener {
            inputName.setText(textName.text)
            inputEmail.setText(textEmail.text)

            labelNameView.visibility = View.GONE
            labelEmailView.visibility = View.GONE
            labelNameEdit.visibility = View.VISIBLE
            labelEmailEdit.visibility = View.VISIBLE

            textName.visibility = View.GONE
            inputNameLayout.visibility = View.VISIBLE
            textEmail.visibility = View.GONE
            inputEmailLayout.visibility = View.VISIBLE

            infoCard.setCardBackgroundColor(Color.TRANSPARENT)
            infoCard.cardElevation = 0f
            infoCard.useCompatPadding = false

            motion.transitionToEnd()
        }

        btnSaveFab.setOnClickListener {
            saveName()

            labelNameView.visibility = View.VISIBLE
            labelEmailView.visibility = View.VISIBLE
            labelNameEdit.visibility = View.GONE
            labelEmailEdit.visibility = View.GONE
        }

        btnLogout.setOnClickListener {
            LoginUi.performLogout()
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString("action", ACTION_LOGOUT) }
            )
            Toast.makeText(requireContext(), getString(R.string.lu_logout), Toast.LENGTH_SHORT).show()
        }

        fetchProfile()
    }

    private fun fetchProfile() {
        LoginUi.api().me().enqueue(object : Callback<MeResponse> {
            override fun onResponse(call: Call<MeResponse>, response: Response<MeResponse>) {
                if (!isAdded) return
                if (response.isSuccessful) {
                    val body = response.body()
                    val user = body?.toUser()
                    if (user != null && (user.name != null || user.email != null)) {
                        val name = (user.name ?: "").trim()
                        val email = (user.email ?: "").trim()
                        textEmail.text = email
                        textName.text = if (name.isNotEmpty()) name else deriveNameFromEmail(email)
                        setAvatarInitial(textName.text.toString(), email)
                    } else {
                        Toast.makeText(requireContext(),
                            "Profile empty/unexpected shape", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val err = runCatching { response.errorBody()?.string() }.getOrNull()
                    Toast.makeText(requireContext(),
                        "Failed to load profile (${response.code()}) ${err ?: ""}".trim(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<MeResponse>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun saveName() {
        val newName = inputName.text?.toString()?.trim().orEmpty()
        if (newName.isEmpty() || newName.length > 100) {
            Toast.makeText(requireContext(), "Name must be 1–100 chars", Toast.LENGTH_SHORT).show()
            return
        }

        btnSaveFab.isEnabled = false

        LoginUi.api().updateMe(UpdateUserRequest(newName))
            .enqueue(object : Callback<UserDto> {
                override fun onResponse(call: Call<UserDto>, response: Response<UserDto>) {
                    btnSaveFab.isEnabled = true
                    if (response.isSuccessful && response.body() != null) {
                        val updated = response.body()!!
                        val shownName = (updated.name ?: newName)

                        // Update view state
                        textName.text = shownName
                        val finalEmail = updated.email ?: textEmail.text.toString()
                        setAvatarInitial(shownName, finalEmail)

                        // Back to view mode
                        textName.visibility = View.VISIBLE
                        inputNameLayout.visibility = View.GONE
                        textEmail.visibility = View.VISIBLE
                        inputEmailLayout.visibility = View.GONE

                        // Restore exact original card look
                        origCardColor?.let { infoCard.setCardBackgroundColor(it) }
                        infoCard.cardElevation = origCardElevation
                        infoCard.useCompatPadding = origUseCompatPadding

                        motion.transitionToStart()

                        parentFragmentManager.setFragmentResult(
                            RESULT_KEY,
                            Bundle().apply {
                                putString("action", ACTION_PROFILE_SAVED)
                                putString(NAME, shownName)
                                putString(EMAIL, finalEmail)
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

    @ColorInt
    private fun resolveAttrColor(attrRes: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attrRes, tv, true)
        return tv.data
    }
}