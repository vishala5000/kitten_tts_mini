package com.vishala.kitten

object NativeBridge {
    init { System.loadLibrary("kitten_android") }

    external fun init(modelPath: String, voicesPath: String, configPath: String): String
    external fun synthesize(
        text: String,
        voice: String,
        speed: Float,
        outputPath: String
    ): String?
    external fun release()
}
