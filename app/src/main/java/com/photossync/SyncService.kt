package com.photossync
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
class SyncService : Service() {
    private val CHANNEL_ID = "PhotosSyncService"
    private val extensions = setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".mp4", ".mov", ".avi", ".mkv")
    private val dossiers = listOf("/sdcard/DCIM", "/sdcard/Pictures", "/sdcard/Movies", "/sdcard/Download")
    private val observers = mutableListOf<FileObserver>()
    override fun onCreate() {
        super.onCreate()
        creerNotification()
        surveillerTout()
    }
    private fun creerNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PhotosSync", NotificationManager.IMPORTANCE_LOW)
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        startForeground(1, Notification.Builder(this, CHANNEL_ID).setContentTitle("PhotosSync").setContentText("Surveillance...").setSmallIcon(android.R.drawable.ic_menu_upload).build())
    }
    private fun surveillerTout() { dossiers.forEach { if (File(it).exists()) observerDossier(File(it)) } }
    private fun observerDossier(dossier: File) {
        val obs = object : FileObserver(dossier.absolutePath, CLOSE_WRITE or CREATE) {
            override fun onEvent(event: Int, chemin: String?) {
                chemin ?: return
                val f = File(dossier.absolutePath, chemin)
                if (f.isDirectory) observerDossier(f)
                else if (estMedia(f.name)) CoroutineScope(Dispatchers.IO).launch { uploader(f) }
            }
        }
        obs.startWatching()
        observers.add(obs)
    }
    private fun estMedia(nom: String) = extensions.any { nom.lowercase().endsWith(it) }
    private fun uploader(f: File) { Log.d("PhotosSync", "📤 ${f.name}") }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); observers.forEach { it.stopWatching() } }
}
