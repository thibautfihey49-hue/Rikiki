package com.photossync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SyncService : Service() {

    private val extensions = setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".mp4", ".mov", ".avi", ".mkv")
    private val dossiers = listOf("/sdcard/DCIM", "/sdcard/Pictures", "/sdcard/Movies", "/sdcard/Download")
    private val observers = mutableListOf<FileObserver>()

    override fun onCreate() {
        super.onCreate()
        Log.d("PhotosSync", "Service de synchro démarré")
        surveillerTout()
    }

    private fun surveillerTout() {
        dossiers.forEach { chemin ->
            val dossier = File(chemin)
            if (dossier.exists()) {
                observerDossier(dossier)
            }
        }
    }

    private fun observerDossier(dossier: File) {
        val observer = object : FileObserver(dossier.absolutePath, CLOSE_WRITE or CREATE) {
            override fun onEvent(event: Int, chemin: String?) {
                chemin ?: return
                val fichier = File(dossier.absolutePath, chemin)
                if (fichier.isDirectory) {
                    observerDossier(fichier)
                } else if (estMedia(fichier.name)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        uploaderFichier(fichier)
                    }
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
        Log.d("PhotosSync", "📤 À envoyer : ${fichier.name} — Connexion Google à configurer")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        Log.d("PhotosSync", "Service arrêté")
    }
}
