package com.example.notasflotantes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {
    private val overlayRequest = 4101
    private val audioRequest = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        continueSetup()
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            startFloatingNote()
        }
    }

    private fun continueSetup() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permite que Notas Flotantes aparezca sobre otras aplicaciones", Toast.LENGTH_LONG).show()
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                overlayRequest
            )
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioRequest)
            return
        }
        startFloatingNote()
    }

    @Deprecated("Deprecated by Android, retained for overlay permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayRequest) continueSetup()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startFloatingNote()
        } else if (requestCode == audioRequest) {
            Toast.makeText(this, "Activa el permiso de micrófono para abrir Notas Flotantes", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startFloatingNote() {
        val intent = Intent(this, FloatingNoteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        finish()
    }
}
