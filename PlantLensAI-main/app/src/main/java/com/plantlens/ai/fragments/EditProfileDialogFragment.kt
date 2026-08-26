package com.plantlens.ai.fragments

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.plantlens.ai.R
import com.plantlens.ai.databinding.DialogEditProfileBinding
import com.plantlens.ai.firebase.FirebaseManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EditProfileDialogFragment : DialogFragment() {

    @Inject
    lateinit var firebaseManager: FirebaseManager

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    private var onProfileUpdatedListener: (() -> Unit)? = null

    fun setOnProfileUpdatedListener(listener: () -> Unit) {
        onProfileUpdatedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditProfileBinding.inflate(layoutInflater)
        val view = binding.root

        val currentUser = firebaseManager.getCurrentUser()
        binding.editNameInput.setText(currentUser?.displayName ?: "")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_profile)
            .setView(view)
            .setPositiveButton(R.string.save, null) // Override click in onShow for validation
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val newName = binding.editNameInput.text?.toString()?.trim() ?: ""
                if (validateName(newName)) {
                    val uid = currentUser?.uid
                    if (uid != null) {
                        lifecycleScope.launch {
                            try {
                                firebaseManager.updateUserName(uid, newName)
                                Toast.makeText(requireContext(), R.string.profile_updated_success, Toast.LENGTH_SHORT).show()
                                onProfileUpdatedListener?.invoke()
                                dismiss()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), getString(R.string.profile_update_failed, e.message), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        dismiss()
                    }
                }
            }
        }

        binding.editNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newName = s?.toString()?.trim() ?: ""
                validateName(newName)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return dialog
    }

    private fun validateName(name: String): Boolean {
        return when {
            name.isEmpty() -> {
                binding.editNameLayout.error = getString(R.string.error_name_empty)
                false
            }
            name.length < 3 -> {
                binding.editNameLayout.error = getString(R.string.error_name_too_short)
                false
            }
            name.length > 30 -> {
                binding.editNameLayout.error = getString(R.string.error_name_too_long)
                false
            }
            else -> {
                binding.editNameLayout.error = null
                true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditProfileDialogFragment"
        fun newInstance() = EditProfileDialogFragment()
    }
}
