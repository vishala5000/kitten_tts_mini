# Kitten TTS Android — Production Offline App

This repository builds a real Android APK with GitHub Actions. It uses the Kitten TTS Mini v0.8 ONNX model from the `kitten` release of `vishala5000/kitten_tts_mini`.

## Features

- Fully on-device inference after installation.
- No runtime model download.
- Unlimited-length text input at the UI level.
- Automatic long-text chunking is provided by the KittenTTS Rust engine.
- Jasper, Bella, Luna, Bruno, Rosie, Hugo, Kiki and Leo voice selection.
- 24 kHz WAV output.
- Saves generated files to `Music/Kitten TTS` using Android MediaStore.
- ARM64 (`arm64-v8a`) production APK.
- GitHub Actions builds everything, including the Rust native engine and ONNX Runtime.
- Release assets are downloaded from the user's `kitten` GitHub release during CI.

## Model source

The configured release is:
https://github.com/vishala5000/kitten_tts_mini/releases/tag/kitten

Expected release assets:
- `kitten_tts_mini_v0_8.onnx`
- `config.json`
- `voices.npz`

The v0.8 model is ONNX2 and exposes the eight named voices used by this app.

## Build

Push this repository to GitHub and run **Build APK** from Actions, or push to `main`.

The workflow creates:
`app/build/outputs/apk/release/KittenTTS-release.apk`

The APK is unsigned by design unless signing secrets are supplied. The workflow also publishes an unsigned artifact. For Play Store/Uptodown distribution, configure Android signing secrets.
