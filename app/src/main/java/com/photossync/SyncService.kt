package com.photossync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class SyncService : Service() {

    private val extensions = setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".mp4", ".mov", ".avi", ".mkv")
    private val dossiers = listOf("/sdcard/DCIM", "/sdcard/Pictures", "/sdcard/Movies", "/sdcard/Download")
    private val observers = mutableListOf<FileObserver>()
    private lateinit var prefs: SharedPreferences
    private val client = okhttp3.OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("PhotosSync", Context.MODE_PRIVATE)
        Log.d("PhotosSync", "Service WebDAV démarré")
        surveillerTout()
    }

    private fun surveillerTout() {
        dossiers.forEach { chemin ->
            val dossier = File(chemin)
            if (dossier.exists()) observerDossier(dossier)
        }
    }

    private fun observerDossier(dossier: File) {
        val observer = object : FileObserver(dossier.absolutePath, CLOSE_WRITE or CREATE) {
            override fun onEvent(event: Int, chemin: String?) {
                chemin ?: return
                val fichier = File(dossier.absolutePath, chemin)
                if (fichier.isDirectory) observerDossier(fichier)
                else if (estMedia(fichier.name)) {
                    CoroutineScope(Dispatchers.IO).launch { uploaderFichier(fichier) }
                }
            }
        }
        observer.startWatching()
        observers.add(observer)
    }

    private fun estMedia(nom: String): Boolean {
        val ext = nom.lowercase().substringAfterLast(".", "")
        return extensions.contains(".$ext")
    }

    private fun uploaderFichier(fichier: File) {
        val urlBase = prefs.getString("dav_url", "") ?: return
        val user = prefs.getString("dav_user", "") ?: return
        val pass = prefs.getString("dav_pass", "") ?: return

        if (urlBase.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Log.d("PhotosSync", "⚠️ WebDAV non configuré : ${fichier.name}")
            return
        }

        try {
            val urlDest = if (urlBase.endsWith("/")) "$urlBase${fichier.name}" else "$urlBase/${fichier.name}"
            val mediaType = when {
                fichier.name.endsWith(".jpg", true) -> "image/jpeg"
                fichier.name.endsWith(".png", true) -> "image/png"
                fichier.name.endsWith(".mp4", true) -> "video/mp4"
                else -> "application/octet-stream"
            }.toMediaType()

            val request = Request.Builder()
                .url(urlDest)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .put(fichier.asRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d("PhotosSync", "✅ Envoyé : ${fichier.name}")
            } else {
                Log.e("PhotosSync", "⚠️ ${response.code} : ${fichier.name}")
            }
        } catch (e: Exception) {
            Log.e("PhotosSync", "❌ Échec ${fichier.name} : ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        Log.d("PhotosSync", "Service arrêté")
    }
}
