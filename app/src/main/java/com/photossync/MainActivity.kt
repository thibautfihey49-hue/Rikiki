package com.photossync

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var statut: TextView
    private lateinit var davUrl: EditText
    private lateinit var davUser: EditText
    private lateinit var davPass: EditText
    private lateinit var btnTest: Button
    private lateinit var btnStart: Button
    private lateinit var prefs: SharedPreferences
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("PhotosSync", MODE_PRIVATE)
        statut = findViewById(R.id.statut)
        davUrl = findViewById(R.id.davUrl)
        davUser = findViewById(R.id.davUser)
        davPass = findViewById(R.id.davPass)
        btnTest = findViewById(R.id.btnTest)
        btnStart = findViewById(R.id.btnStart)

        davUrl.setText(prefs.getString("dav_url", ""))
        davUser.setText(prefs.getString("dav_user", ""))
        davPass.setText(prefs.getString("dav_pass", ""))

        demanderPermissions()

        btnTest.setOnClickListener { testerConnexion() }
        btnStart.setOnClickListener {
            sauvegarderConfig()
            startService(Intent(this, SyncService::class.java))
            statut.text = "🔄 Surveillance active !\n\n📂 DCIM, Pictures, Movies, Download\n📤 Envoi WebDAV automatique"
        }
    }

    private fun sauvegarderConfig() {
        prefs.edit()
            .putString("dav_url", davUrl.text.toString().trim())
            .putString("dav_user", davUser.text.toString().trim())
            .putString("dav_pass", davPass.text.toString().trim())
            .apply()
    }

    private fun testerConnexion() {
        sauvegarderConfig()
        val url = davUrl.text.toString().trim()
        val user = davUser.text.toString().trim()
        val pass = davPass.text.toString().trim()

        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            statut.text = "⚠️ Remplissez tous les champs"
            return
        }

        statut.text = "🔄 Test de la connexion..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", Credentials.basic(user, pass))
                    .head()
                    .build()

                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    statut.text = if (response.isSuccessful) {
                        "✅ Connexion RÉUSSIE !\n\nServeur WebDAV opérationnel"
                    } else {
                        "⚠️ Réponse : ${response.code} ${response.message}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statut.text = "❌ Erreur : ${e.message}"
                }
            }
        }
    }

    private fun demanderPermissions() {
        val permissions = mutableListOf(Manifest.permission.INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val aDemander = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (aDemander.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, aDemander, 100)
        }
    }
}
