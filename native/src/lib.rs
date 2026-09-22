use std::collections::HashMap;
use std::ffi::CString;
use std::path::Path;
use std::sync::Mutex;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use kittentts::model::KittenTtsOnnx;
use once_cell::sync::OnceCell;

static MODEL: OnceCell<Mutex<Option<KittenTtsOnnx>>> = OnceCell::new();

fn jstring_to_string(env: &mut JNIEnv, s: JString) -> Result<String, String> {
    env.get_string(&s)
        .map(|x| x.to_string_lossy().into_owned())
        .map_err(|e| e.to_string())
}

fn to_jstring(env: &mut JNIEnv, text: &str) -> jstring {
    match env.new_string(text) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vishala_kitten_NativeBridge_init(
    mut env: JNIEnv,
    _class: JClass,
    model: JString,
    voices: JString,
    config: JString,
) -> jstring {
    let model_path = match jstring_to_string(&mut env, model) { Ok(v) => v, Err(e) => return to_jstring(&mut env, &e) };
    let voices_path = match jstring_to_string(&mut env, voices) { Ok(v) => v, Err(e) => return to_jstring(&mut env, &e) };
    let config_path = match jstring_to_string(&mut env, config) { Ok(v) => v, Err(e) => return to_jstring(&mut env, &e) };

    let result = (|| -> anyhow::Result<()> {
        let config_text = std::fs::read_to_string(&config_path)?;
        let cfg: serde_json::Value = serde_json::from_str(&config_text)?;
        let aliases = cfg.get("voice_aliases")
            .and_then(|v| v.as_object())
            .map(|obj| obj.iter().filter_map(|(k,v)| v.as_str().map(|s| (k.clone(), s.to_string()))).collect::<HashMap<_,_>>())
            .unwrap_or_default();

        let model_obj = KittenTtsOnnx::load(
            Path::new(&model_path),
            Path::new(&voices_path),
            HashMap::new(),
            aliases,
        )?;

        let cell = MODEL.get_or_init(|| Mutex::new(None));
        *cell.lock().map_err(|_| anyhow::anyhow!("model lock poisoned"))? = Some(model_obj);
        Ok(())
    })();

    match result {
        Ok(()) => to_jstring(&mut env, ""),
        Err(e) => to_jstring(&mut env, &format!("{e:#}")),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vishala_kitten_NativeBridge_synthesize(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
    voice: JString,
    speed: f32,
    output: JString,
) -> jstring {
    let text = match jstring_to_string(&mut env, text) { Ok(v) => v, Err(e) => return to_jstring(&mut env, &e) };
    let voice = match jstring_to_string(&mut env, voice) { Ok(v) => v, Err(e) => return to_jstring(&mut env, &e) };
    let output = match jstring_to_string(&mut env, output) { Ok(v) => v, Err(e) => return to_jstring(&mut env, &e) };

    let result = (|| -> anyhow::Result<()> {
        let cell = MODEL.get().ok_or_else(|| anyhow::anyhow!("Model is not initialized"))?;
        let guard = cell.lock().map_err(|_| anyhow::anyhow!("model lock poisoned"))?;
        let model = guard.as_ref().ok_or_else(|| anyhow::anyhow!("Model is not initialized"))?;
        model.generate_to_file(&text, Path::new(&output), &voice, speed, true)?;
        Ok(())
    })();

    match result {
        Ok(()) => std::ptr::null_mut(),
        Err(e) => to_jstring(&mut env, &format!("{e:#}")),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vishala_kitten_NativeBridge_release(
    _env: JNIEnv,
    _class: JClass,
) {
    if let Some(cell) = MODEL.get() {
        if let Ok(mut guard) = cell.lock() {
            *guard = None;
        }
    }
}
