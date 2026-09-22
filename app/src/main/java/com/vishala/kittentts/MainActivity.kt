package com.vishala.kittentts

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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

    private var engineReady = false

    private val fallbackVoices = arrayOf(
        "Bella",
        "Jasper",
        "Luna",
        "Bruno",
        "Rosie",
        "Hugo",
        "Kiki",
        "Leo"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        input = findViewById(R.id.textInput)
        generate = findViewById(R.id.generateButton)
        clear = findViewById(R.id.clearButton)
        spinner = findViewById(R.id.voiceSpinner)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            fallbackVoices
        )

        clear.setOnClickListener {
            input.text.clear()
            input.requestFocus()
        }

        generate.setOnClickListener {
            generateSpeech()
        }

        prepareModel()
    }

    private fun prepareModel() {
        setBusy(true, "Loading Kitten TTS model...")

        executor.execute {
            try {
                val modelDir = File(filesDir, "kitten-model")
                modelDir.mkdirs()

                val modelFile = File(
                    modelDir,
                    "kitten_tts_mini_v0_8.onnx"
                )

                val voicesFile = File(
                    modelDir,
                    "voices.npz"
                )

                val configFile = File(
                    modelDir,
                    "config.json"
                )

                copyAssetIfNeeded(
                    "model/kitten_tts_mini_v0_8.onnx",
                    modelFile,
                    70L * 1024L * 1024L
                )

                copyAssetIfNeeded(
                    "model/voices.npz",
                    voicesFile,
                    1024L * 1024L
                )

                copyAssetIfNeeded(
                    "model/config.json",
                    configFile,
                    100L
                )

                val result = KittenNative.initialize(
                    modelFile.absolutePath,
                    voicesFile.absolutePath,
                    configFile.absolutePath
                )

                if (!result.success) {
                    throw IllegalStateException(
                        result.error ?: "Kitten TTS initialization failed."
                    )
                }

                val nativeVoices = result.voices

                runOnUiThread {
                    if (nativeVoices.isNotEmpty()) {
                        spinner.adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_spinner_dropdown_item,
                            nativeVoices.toTypedArray()
                        )
                    }

                    engineReady = true

                    setBusy(
                        false,
                        "Ready. Enter text and tap Generate WAV."
                    )
                }

            } catch (t: Throwable) {

                runOnUiThread {
                    engineReady = false

                    setBusy(
                        false,
                        "Model initialization failed: ${
                            t.message ?: t.javaClass.simpleName
                        }"
                    )

                    generate.isEnabled = false
                }
            }
        }
    }

    private fun copyAssetIfNeeded(
        assetName: String,
        target: File,
        expectedMinBytes: Long
    ) {
        if (target.exists() && target.length() >= expectedMinBytes) {
            return
        }

        target.parentFile?.mkdirs()

        assets.open(assetName).use { inputStream ->
            FileOutputStream(target).use { output ->
                inputStream.copyTo(
                    output,
                    1024 * 1024
                )

                output.fd.sync()
            }
        }

        if (!target.exists() || target.length() < expectedMinBytes) {
            throw IllegalStateException(
                "Asset appears incomplete: $assetName"
            )
        }
    }

    private fun generateSpeech() {

        if (!engineReady) {
            Toast.makeText(
                this,
                "TTS engine is not ready.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val text = input.text.toString()

        if (text.isBlank()) {
            input.error = "Enter some text first."
            input.requestFocus()
            return
        }

        val voice =
            spinner.selectedItem?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: "Jasper"

        setBusy(
            true,
            "Generating speech..."
        )

        executor.execute {

            val outputFile = File(
                cacheDir,
                "kitten_${System.currentTimeMillis()}.wav"
            )

            try {

                val error = KittenNative.synthesize(
                    text = text,
                    voice = voice,
                    speed = 1.0f,
                    outputPath = outputFile.absolutePath
                )

                if (!error.isNullOrBlank()) {
                    throw IllegalStateException(error)
                }

                if (!outputFile.exists() || outputFile.length() < 44L) {
                    throw IllegalStateException(
                        "TTS engine did not create a valid WAV file."
                    )
                }

                val uri = saveToMusic(outputFile)

                outputFile.delete()

                runOnUiThread {

                    setBusy(
                        false,
                        "Speech generated successfully."
                    )

                    Toast.makeText(
                        this,
                        "WAV saved to Music/Kitten TTS",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (t: Throwable) {

                outputFile.delete()

                runOnUiThread {

                    setBusy(
                        false,
                        "Generation failed: ${
                            t.message ?: t.javaClass.simpleName
                        }"
                    )
                }
            }
        }
    }

    private fun saveToMusic(source: File): String {

        val resolver = contentResolver

        val fileName =
            "kitten_tts_${System.currentTimeMillis()}.wav"

        val values = ContentValues().apply {

            put(
                MediaStore.Audio.Media.DISPLAY_NAME,
                fileName
            )

            put(
                MediaStore.Audio.Media.MIME_TYPE,
                "audio/wav"
            )

            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC +
                        "/Kitten TTS"
            )

            put(
                MediaStore.Audio.Media.IS_PENDING,
                1
            )
        }

        val uri = resolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException(
            "Unable to create Music media entry."
        )

        try {

            resolver.openOutputStream(
                uri,
                "w"
            )!!.use { output ->

                FileInputStream(source).use { input ->

                    input.copyTo(
                        output,
                        1024 * 1024
                    )
                }
            }

            val completed = ContentValues().apply {
                put(
                    MediaStore.Audio.Media.IS_PENDING,
                    0
                )
            }

            resolver.update(
                uri,
                completed,
                null,
                null
            )

            return uri.toString()

        } catch (t: Throwable) {

            resolver.delete(
                uri,
                null,
                null
            )

            throw t
        }
    }

    private fun setBusy(
        busy: Boolean,
        message: String
    ) {

        progress.visibility =
            if (busy) View.VISIBLE else View.GONE

        generate.isEnabled =
            !busy && engineReady

        clear.isEnabled =
            !busy

        spinner.isEnabled =
            !busy

        status.text = message
    }

    override fun onDestroy() {

        try {
            KittenNative.release()
        } catch (_: Throwable) {
        }

        executor.shutdownNow()

        super.onDestroy()
    }
}
