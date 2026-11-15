package com.example.notasflotantes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {
    private val audioRequest = 4102
    private var waitingForOverlay = false
    private var overlayDialogShown = false
    private var serviceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        continueSetup()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForOverlay) {
            waitingForOverlay = false
            if (Settings.canDrawOverlays(this)) {
                overlayDialogShown = false
                continueAfterOverlay()
            } else {
                showOverlayHelp()
            }
        }
    }

    private fun continueSetup() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        continueAfterOverlay()
    }

    private fun continueAfterOverlay() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioRequest)
        } else {
            startFloatingNote()
        }
    }

    private fun openOverlaySettings() {
        waitingForOverlay = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                waitingForOverlay = false
                Toast.makeText(this, "Abre Ajustes > Aplicaciones > Notas Flotantes > Mostrar sobre otras aplicaciones", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showOverlayHelp() {
        if (overlayDialogShown || isFinishing) return
        overlayDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("Falta activar un permiso")
            .setMessage("En Configuración, busca Notas Flotantes y activa «Mostrar sobre otras aplicaciones». Después vuelve aquí y pulsa «Ya lo activé».")
            .setCancelable(false)
            .setPositiveButton("Ya lo activé") { _, _ ->
                overlayDialogShown = false
                if (Settings.canDrawOverlays(this)) {
                    continueAfterOverlay()
                } else {
                    openOverlaySettings()
                }
            }
            .setNegativeButton("Cerrar") { _, _ ->
                overlayDialogShown = false
                finish()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startFloatingNote()
        } else if (requestCode == audioRequest) {
            Toast.makeText(this, "Activa el permiso de micrófono para usar el dictado por voz", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startFloatingNote() {
        if (serviceStarted) return
        serviceStarted = true
        val intent = Intent(this, FloatingNoteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        finish()
    }
}
