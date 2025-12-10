package com.example.kindboxmobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kindboxmobile.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnRegister.setOnClickListener {
            // 1. Ambil data dari Input Layout Baru
            val email = binding.etEmail.text.toString().trim()
            val name = binding.etName.text.toString().trim()      // ID dari layout baru
            val username = binding.etUsername.text.toString().trim() // ID dari layout baru
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim() // ID dari layout baru

            // 2. Validasi Input
            if (email.isEmpty() || name.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Konfirmasi password tidak cocok!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. Proses Registrasi
            // Langkah A: Buat Akun di Authentication (Email & Pass)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val userId = authResult.user?.uid

                    // Langkah B: Simpan Nama & Username ke Firestore
                    if (userId != null) {
                        val userMap = hashMapOf(
                            "id" to userId,
                            "name" to name,
                            "username" to username,
                            "email" to email
                        )

                        firestore.collection("users").document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registrasi Berhasil!", Toast.LENGTH_SHORT).show()

                                // Pindah ke MainActivity (Hapus history agar tidak bisa back ke register)
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Gagal menyimpan data user: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal Daftar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Tombol kembali ke Login
        binding.tvLogin.setOnClickListener {
            finish()
        }
    }
}