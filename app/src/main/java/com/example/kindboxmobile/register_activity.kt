package com.example.kindboxmobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
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
            // 1. Ambil data dari Input
            val name = binding.etName.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            // 2. Validasi Input (Satu per satu agar user tahu salahnya dimana)

            // Cek Nama
            if (name.isEmpty()) {
                binding.etName.error = "Nama lengkap harus diisi"
                binding.etName.requestFocus()
                return@setOnClickListener
            }

            // Cek Username
            if (username.isEmpty()) {
                binding.etUsername.error = "Username harus diisi"
                binding.etUsername.requestFocus()
                return@setOnClickListener
            }

            // Cek Email Kosong
            if (email.isEmpty()) {
                binding.etEmail.error = "Email harus diisi"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            // Cek Format Email (misal: user@gmail.com)
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Format email tidak valid"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            // Cek Password Kosong
            if (password.isEmpty()) {
                binding.etPassword.error = "Password harus diisi"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Cek Panjang Password
            if (password.length < 6) {
                binding.etPassword.error = "Password minimal 6 karakter"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Cek Konfirmasi Password
            if (confirmPassword.isEmpty()) {
                binding.etConfirmPassword.error = "Konfirmasi password harus diisi"
                binding.etConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            // Cek Kecocokan Password
            if (password != confirmPassword) {
                binding.etConfirmPassword.error = "Password tidak cocok"
                binding.etConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            // 3. Jika semua validasi lolos, jalankan proses Registrasi
            registerUser(name, username, email, password)
        }

        // Tombol kembali ke Login (Sudah punya akun?)
        binding.tvLogin.setOnClickListener {
            finish() // Menutup activity ini dan kembali ke activity sebelumnya (Login)
        }
    }

    private fun registerUser(name: String, username: String, email: String, pass: String) {
        // Tampilkan loading (opsional, bisa ditambahkan ProgressBar di XML nanti)

        // Langkah A: Buat Akun di Authentication
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid

                // Langkah B: Simpan data tambahan ke Firestore
                if (userId != null) {
                    val userMap = hashMapOf(
                        "id" to userId,
                        "name" to name,
                        "username" to username,
                        "email" to email,
                        "createdAt" to System.currentTimeMillis() // Info tambahan kapan akun dibuat
                    )

                    firestore.collection("users").document(userId)
                        .set(userMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Registrasi Berhasil! Silakan Login", Toast.LENGTH_LONG).show()

                            // Pindah ke MainActivity dan hapus history agar tidak bisa tombol back ke Register
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            // Jika simpan ke Firestore gagal
                            Toast.makeText(this, "Gagal menyimpan data profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                // Jika proses Auth gagal (misal email sudah terpakai)
                Toast.makeText(this, "Registrasi Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}