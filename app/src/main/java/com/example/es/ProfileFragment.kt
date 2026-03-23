package com.example.es

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    private lateinit var imgProfile: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvHandle: TextView
    private lateinit var btnEditPhoto: ImageButton
    private lateinit var btnLogout: Button

    private lateinit var userProfileDao: UserProfileDao
    private val auth = FirebaseAuth.getInstance()
    // We still keep auth for user identity, but skip Firebase Storage/DB for the image

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            saveProfileImageLocally(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val db = AppDatabase.getDatabase(requireContext())
        userProfileDao = db.userProfileDao()

        imgProfile = view.findViewById(R.id.imgProfile)
        tvName = view.findViewById(R.id.tvProfileName)
        tvHandle = view.findViewById(R.id.tvProfileHandle)
        btnEditPhoto = view.findViewById(R.id.btnEditPhoto)
        btnLogout = view.findViewById(R.id.btnLogout)

        loadUserInfo()

        btnEditPhoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnLogout.setOnClickListener {
            logoutUser()
        }

        return view
    }

    private fun loadUserInfo() {
        val user = auth.currentUser ?: return
        
        val name = user.displayName ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"
        val handle = "@" + (user.email?.substringBefore("@") ?: "user")
        
        tvName.text = name
        tvHandle.text = handle

        val uid = user.uid

        // Load from Room (Single source of truth now for the image)
        Thread {
            val localProfile = userProfileDao.getProfile(uid)
            activity?.runOnUiThread {
                if (localProfile != null && localProfile.profileImageUrl != null) {
                    loadImage(localProfile.profileImageUrl)
                } else if (user.photoUrl != null) {
                    loadImage(user.photoUrl.toString())
                }
            }
        }.start()
    }

    private fun loadImage(pathOrUrl: String) {
        imgProfile.load(pathOrUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            transformations(CircleCropTransformation())
        }
    }

    private fun saveProfileImageLocally(uri: Uri) {
        val user = auth.currentUser ?: return
        val uid = user.uid

        Thread {
            try {
                // 1. Copy file to internal storage
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val file = java.io.File(requireContext().filesDir, "profile_$uid.jpg")
                val outputStream = java.io.FileOutputStream(file)
                
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                val localPath = file.absolutePath
                
                // 2. Save path to Room
                val currentProfile = userProfileDao.getProfile(uid)
                if (currentProfile != null) {
                    userProfileDao.updateProfileImage(uid, localPath)
                } else {
                    userProfileDao.insertProfile(UserProfile(uid, user.displayName ?: "User", user.email!!, localPath))
                }

                activity?.runOnUiThread {
                    loadImage(localPath)
                    Toast.makeText(requireContext(), "Profile photo updated locally", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun logoutUser() {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(requireActivity(), SignUpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }
}
