# Keep the native TTS class and its methods from being renamed or removed
-keep class com.vishala.kittentts.KittenNative {
    private native <methods>;
}
