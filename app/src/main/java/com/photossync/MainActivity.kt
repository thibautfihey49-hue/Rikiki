package com.photossync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statut: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statut = findViewById(R.id.statut)
        btnConnect = findViewById(R.id.btnConnect)
        btnStart = findViewById(R.id.btnStart)

        demanderPermissions()

        btnConnect.setOnClickListener {
            statut.text = "🔐 Connexion Google à configurer\n\n→ Il faut créer un projet sur Google Cloud Console\n→ Activer l'API Google Photos\n→ Ajouter votre empreinte SHA-1\n→ Déposer le Client ID dans l'appli"
        }

        btnStart.setOnClickListener {
            val intent = Intent(this, SyncService::class.java)
            startService(intent)
            statut.text = "🔄 Surveillance active !\n\n📂 Surveille : DCIM, Pictures, Movies, Download\n\n⚠️ En attente de connexion Google pour l'envoi"
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
