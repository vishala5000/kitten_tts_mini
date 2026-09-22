package com.vishala.kitten

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var input: EditText
    private lateinit var generate: Button
    private lateinit var clear: Button
    private lateinit var spinner: Spinner
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private var modelDir: File? = null

    private val voices = arrayOf("Jasper", "Bella", "Luna", "Bruno", "Rosie", "Hugo", "Kiki", "Leo")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.textInput)
        generate = findViewById(R.id.generateButton)
        clear = findViewById(R.id.clearButton)
        spinner = findViewById(R.id.voiceSpinner)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        clear.setOnClickListener { input.text.clear() }
        generate.setOnClickListener { generateSpeech() }

        prepareModel()
    }

    private fun prepareModel() {
        setBusy(true, "Preparing Kitten TTS model…")
        executor.execute {
            try {
                val dir = File(filesDir, "kitten-model").apply { mkdirs() }
                copyAssetIfNeeded("model/kitten_tts_mini_v0_8.onnx", File(dir, "kitten_tts_mini_v0_8.onnx"), 78L * 1024 * 1024)
                copyAssetIfNeeded("model/voices.npz", File(dir, "voices.npz"), 3L * 1024 * 1024)
                copyAssetIfNeeded("model/config.json", File(dir, "config.json"), 256)
                val error = NativeBridge.init(
                    File(dir, "kitten_tts_mini_v0_8.onnx").absolutePath,
                    File(dir, "voices.npz").absolutePath,
                    File(dir, "config.json").absolutePath
                )
                if (error.isNotEmpty()) throw IllegalStateException(error)
                modelDir = dir
                runOnUiThread { setBusy(false, "Ready. Enter text and generate a WAV.") }
            } catch (t: Throwable) {
                runOnUiThread {
                    setBusy(false, "Model initialization failed: ${t.message ?: t::class.java.simpleName}")
                    generate.isEnabled = false
                }
            }
        }
    }

    private fun copyAssetIfNeeded(assetName: String, target: File, expectedMinBytes: Long) {
        if (target.exists() && target.length() >= expectedMinBytes) return
        target.parentFile?.mkdirs()
        assets.open(assetName).use { inputStream ->
            FileOutputStream(target).use { output ->
                inputStream.copyTo(output, 1024 * 1024)
                output.fd.sync()
            }
        }
        if (target.length() < expectedMinBytes) {
            throw IllegalStateException("Asset appears incomplete: $assetName")
        }
    }

    private fun generateSpeech() {
        val text = input.text.toString()
        if (text.isBlank()) {
            input.error = "Enter some text first."
            return
        }
        val voice = spinner.selectedItem?.toString() ?: "Jasper"
        setBusy(true, "Generating speech…")
        executor.execute {
            val temp = File(cacheDir, "kitten-${System.currentTimeMillis()}.wav")
            try {
                val error = NativeBridge.synthesize(text, voice, 1.0f, temp.absolutePath)
                if (error != null) throw IllegalStateException(error)
                val uri = saveToMusic(temp)
                temp.delete()
                runOnUiThread {
                    setBusy(false, "Saved to Music/Kitten TTS • $uri")
                    Toast.makeText(this, "WAV saved in Music/Kitten TTS", Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                temp.delete()
                runOnUiThread { setBusy(false, "Generation failed: ${t.message ?: t::class.java.simpleName}") }
            }
        }
    }

    private fun saveToMusic(source: File): String {
        val resolver = contentResolver
        val name = "kitten_tts_${System.currentTimeMillis()}.wav"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Kitten TTS")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create Music media entry")
        try {
            resolver.openOutputStream(uri, "w")!!.use { out ->
                FileInputStream(source).use { it.copyTo(out, 1024 * 1024) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun setBusy(busy: Boolean, message: String) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        generate.isEnabled = !busy
        clear.isEnabled = !busy
        spinner.isEnabled = !busy
        status.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
        try { NativeBridge.release() } catch (_: Throwable) {}
    }
}
