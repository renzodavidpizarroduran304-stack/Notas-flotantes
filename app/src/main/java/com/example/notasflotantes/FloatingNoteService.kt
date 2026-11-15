package com.example.notasflotantes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.pm.ServiceInfo
import java.util.Locale

class FloatingNoteService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var editor: EditText? = null
    private var speech: SpeechRecognizer? = null
    private var listening = false
    private var minimized = false
    private var expanded = false
    private val speechHandler = Handler(Looper.getMainLooper())
    private var restartSpeech = false
    private var speechIntent: Intent? = null
    private val prefs by lazy { getSharedPreferences("floating_note", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun createNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("floating_notes", "Notas Flotantes", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "floating_notes")
        } else Notification.Builder(this)
        notification.setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Notas Flotantes")
            .setContentText("Tu nota está disponible sobre otras aplicaciones")
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(71, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(71, notification.build())
        }
    }

    private fun showPanel() {
        if (panel != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = rounded("#17232B", 18)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        val title = TextView(this).apply {
            text = "  NOTAS"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val minimize = smallButton("—") { minimizePanel() }
        val expand = smallButton("□") { toggleExpanded() }
        val close = smallButton("×") { stopSelf() }
        header.addView(title, LinearLayout.LayoutParams(0, 42, 1f))
        header.addView(minimize)
        header.addView(expand)
        header.addView(close)
        header.setOnTouchListener { _, event -> dragPanel(event) }

        val input = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "Escribe tu nota..."
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(30, 43, 52))
            minLines = 5
            maxLines = 12
            setText(prefs.getString("text", "") ?: "")
            addTextChangedListener(SimpleTextWatcher { value -> prefs.edit().putString("text", value).apply() })
        }
        editor = input

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(smallButton("Copiar todo") { copyAll() }, LinearLayout.LayoutParams(0, 44, 1f))
        actions.addView(smallButton("Voz") { toggleSpeech() }, LinearLayout.LayoutParams(0, 44, 1f))
        root.addView(header)
        root.addView(input, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actions)
        panel = root

        val screen = resources.displayMetrics
        params = WindowManager.LayoutParams(
            (screen.widthPixels * 0.88f).toInt(),
            (screen.heightPixels * 0.42f).toInt(),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (screen.heightPixels * 0.12f).toInt()
        }
        windowManager.addView(root, params)
    }

    private fun smallButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            background = rounded("#087F8C", 12)
            setPadding(5, 0, 5, 0)
            setOnClickListener { action() }
        }

    private fun rounded(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = radius.toFloat()
    }

    private fun dragPanel(event: MotionEvent): Boolean {
        val p = params ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { p.x = (event.rawX - panel!!.width / 2).toInt(); p.y = event.rawY.toInt(); return true }
            MotionEvent.ACTION_MOVE -> { p.x = (event.rawX - panel!!.width / 2).toInt(); p.y = (event.rawY - 25).toInt(); windowManager.updateViewLayout(panel, p); return true }
        }
        return false
    }

    private fun minimizePanel() {
        val view = panel ?: return
        val p = params ?: return
        minimized = true
        view.removeAllViews()
        view.orientation = LinearLayout.VERTICAL
        view.addView(smallButton("N") { maximizePanel() }, LinearLayout.LayoutParams(58, 58))
        p.width = 64
        p.height = 64
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowManager.updateViewLayout(view, p)
    }

    private fun maximizePanel() {
        if (panel == null) { showPanel(); return }
        if (!minimized) return
        val old = panel
        windowManager.removeView(old)
        panel = null
        minimized = false
        expanded = false
        showPanel()
    }

    private fun toggleExpanded() {
        val view = panel ?: return
        val p = params ?: return
        if (minimized) { maximizePanel(); return }
        val screen = resources.displayMetrics
        expanded = !expanded
        p.width = (screen.widthPixels * if (expanded) 0.96f else 0.88f).toInt()
        p.height = (screen.heightPixels * if (expanded) 0.68f else 0.42f).toInt()
        windowManager.updateViewLayout(view, p)
    }

    private fun copyAll() {
        val text = editor?.text?.toString().orEmpty()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nota", text))
        Toast.makeText(this, "Nota copiada", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeech() {
        if (listening || restartSpeech) {
            restartSpeech = false
            speechHandler.removeCallbacksAndMessages(null)
            listening = false
            speech?.cancel()
            speech?.destroy()
            speech = null
            Toast.makeText(this, "Dictado detenido", Toast.LENGTH_SHORT).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible", Toast.LENGTH_SHORT).show()
            return
        }
        startSpeech()
    }

    private fun startSpeech() {
        restartSpeech = true
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        createSpeechRecognizerIfNeeded()
        launchSpeechSession()
    }

    private fun createSpeechRecognizerIfNeeded() {
        if (speech != null) return
        speech = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
                override fun onBeginningOfSpeech() { listening = true }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { }
                override fun onError(error: Int) {
                    if (!restartSpeech) return
                    listening = false
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        scheduleSpeechRestart()
                    } else {
                        restartSpeech = false
                        Toast.makeText(this@FloatingNoteService, "No se pudo continuar el dictado. Pulsa Voz para intentarlo otra vez.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    if (!restartSpeech) return
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        val current = editor?.text?.toString().orEmpty()
                        editor?.append(if (current.isBlank()) spoken else " " + spoken)
                        editor?.setSelection(editor?.text?.length ?: 0)
                    }
                    scheduleSpeechRestart()
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
    }

    private fun scheduleSpeechRestart() {
        if (!restartSpeech) return
        listening = false
        speechHandler.removeCallbacksAndMessages(null)
        speechHandler.postDelayed({
            if (restartSpeech) launchSpeechSession()
        }, 450L)
    }

    private fun launchSpeechSession() {
        if (!restartSpeech) return
        try {
            speech?.startListening(speechIntent)
            listening = true
        } catch (_: Exception) {
            scheduleSpeechRestart()
        }
    }

    override fun onDestroy() {
        restartSpeech = false
        speechHandler.removeCallbacksAndMessages(null)
        speech?.cancel()
        speech?.destroy()
        panel?.let { windowManager.removeView(it) }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
