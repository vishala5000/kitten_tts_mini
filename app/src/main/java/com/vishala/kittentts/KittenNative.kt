package com.vishala.kittentts

data class KittenInitResult(
    val success: Boolean,
    val error: String? = null,
    val voices: List<String> = emptyList()
)

object KittenNative {

    private var loaded = false

    init {
        // FIXED: Changed from "kittentts_jni" to "kitten_jni" to match the actual .so file name
        System.loadLibrary("kitten_jni")
    }

    fun initialize(
        modelPath: String,
        voicesPath: String,
        configPath: String
    ): KittenInitResult {

        return try {

            val result = nativeInitialize(
                modelPath,
                voicesPath,
                configPath
            )

            if (!result.isNullOrBlank()) {
                loaded = false

                KittenInitResult(
                    success = false,
                    error = result
                )
            } else {

                loaded = true

                KittenInitResult(
                    success = true,
                    voices = listOf(
                        "Bella",
                        "Jasper",
                        "Luna",
                        "Bruno",
                        "Rosie",
                        "Hugo",
                        "Kiki",
                        "Leo"
                    )
                )
            }

        } catch (t: Throwable) {

            loaded = false

            KittenInitResult(
                success = false,
                error = t.message ?: t.javaClass.simpleName
            )
        }
    }

    fun synthesize(
        text: String,
        voice: String,
        speed: Float,
        outputPath: String
    ): String? {

        if (!loaded) {
            return "Kitten TTS engine is not initialized."
        }

        if (text.isBlank()) {
            return "Text is empty."
        }

        return try {

            nativeSynthesize(
                text,
                voice,
                speed,
                outputPath
            )

        } catch (t: Throwable) {

            t.message ?: t.javaClass.simpleName
        }
    }

    fun release() {

        if (!loaded) {
            return
        }

        try {
            nativeRelease()
        } finally {
            loaded = false
        }
    }

    private external fun nativeInitialize(
        modelPath: String,
        voicesPath: String,
        configPath: String
    ): String?

    private external fun nativeSynthesize(
        text: String,
        voice: String,
        speed: Float,
        outputPath: String
    ): String?

    private external fun nativeRelease()
}
