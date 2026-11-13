package com.zain.screenlens

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_KEY = "api_key"
        private const val PREF_MODEL = "model"
        private const val PREF_SEARCH = "use_search"
        private const val DEFAULT_MODEL = "gemini-flash-latest"
        private const val AUTO_INTERVAL_MS = 3000L
        private const val PROMPT =
            "This is a phone-camera photo of a screen (monitor, laptop, TV or phone). " +
            "Ignore glare, moiré and the screen bezel. In 2-4 short sentences, say what is shown " +
            "and explain it. If it is an error or code, explain the cause and the fix. " +
            "If it is a product, place or object, identify it. Plain text, no markdown."
    }

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var autoButton: Button

    private var imageCapture: ImageCapture? = null
    private val gemini = GeminiClient()
    private var busy = false
    private var autoMode = false
    private var lastHash: Long? = null

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val autoTick = object : Runnable {
        override fun run() {
            if (!autoMode) return
            capture(auto = true)
            handler.postDelayed(this, AUTO_INTERVAL_MS)
        }
    }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else statusText.text = "Camera permission is required."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        autoButton = findViewById(R.id.autoButton)
        resultText.movementMethod = ScrollingMovementMethod()

        findViewById<Button>(R.id.describeButton).setOnClickListener { capture(auto = false) }
        findViewById<Button>(R.id.settingsButton).setOnClickListener { showSettings() }
        previewView.setOnClickListener { capture(auto = false) }
        autoButton.setOnClickListener { toggleAuto() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }

        if (apiKey().isBlank()) showSettings()
    }

    override fun onPause() {
        super.onPause()
        if (autoMode) toggleAuto()
    }

    private fun apiKey() = prefs.getString(PREF_KEY, "").orEmpty().trim()

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (e: Exception) {
                statusText.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleAuto() {
        autoMode = !autoMode
        autoButton.text = if (autoMode) "Auto: ON" else "Auto: OFF"
        handler.removeCallbacks(autoTick)
        if (autoMode) {
            lastHash = null
            handler.post(autoTick)
        }
    }

    private fun capture(auto: Boolean) {
        if (busy) return
        val ic = imageCapture ?: return
        val key = apiKey()
        if (key.isBlank()) { showSettings(); return }

        busy = true
        if (!auto) statusText.text = "Capturing…"
        ic.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap: Bitmap = try {
                    ImageUtils.rotate(image.toBitmap(), image.imageInfo.rotationDegrees)
                } catch (e: Exception) {
                    busy = false
                    statusText.text = "Could not read frame: ${e.message}"
                    return
                } finally {
                    image.close()
                }

                val hash = ImageUtils.averageHash(bitmap)
                val prev = lastHash
                if (auto && prev != null && java.lang.Long.bitCount(hash xor prev) <= 6) {
                    busy = false // scene unchanged, skip
                    return
                }
                lastHash = hash
                describe(bitmap, key)
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                statusText.text = "Capture failed: ${exception.message}"
            }
        })
    }

    private fun describe(bitmap: Bitmap, key: String) {
        val model = prefs.getString(PREF_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        val useSearch = prefs.getBoolean(PREF_SEARCH, true)
        statusText.text = "Thinking… ($model${if (useSearch) " + Google Search" else ""})"
        resultText.text = ""
        val started = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { ImageUtils.toJpegBase64(bitmap) }
                val sources = gemini.streamDescribe(key, model, PROMPT, jpeg, useSearch) { chunk ->
                    resultText.append(chunk)
                }
                if (sources.isNotEmpty()) {
                    resultText.append("\n\nSources: " + sources.take(3).joinToString(" · "))
                }
                val secs = (System.currentTimeMillis() - started) / 1000.0
                statusText.text = "Done in ${"%.1f".format(secs)}s — tap to describe again"
            } catch (e: Exception) {
                statusText.text = "Error"
                resultText.text = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    private fun showSettings() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val keyInput = EditText(this).apply {
            hint = "Gemini API key (aistudio.google.com)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(apiKey())
        }
        val modelInput = EditText(this).apply {
            hint = "Model (default $DEFAULT_MODEL)"
            setText(prefs.getString(PREF_MODEL, DEFAULT_MODEL))
        }
        val searchBox = CheckBox(this).apply {
            text = "Use Google Search grounding"
            isChecked = prefs.getBoolean(PREF_SEARCH, true)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(keyInput)
            addView(modelInput)
            addView(searchBox)
        }
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putString(PREF_KEY, keyInput.text.toString().trim())
                    .putString(PREF_MODEL, modelInput.text.toString().trim().ifBlank { DEFAULT_MODEL })
                    .putBoolean(PREF_SEARCH, searchBox.isChecked)
                    .apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
