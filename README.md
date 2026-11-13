# ALIC AI — THE POWER OF INTELLIGENCE

Native Android assistant (Kotlin, Jetpack Compose, Material 3, MVVM): dotted 3D orb UI with themes (Dark / Light / Sunset) and accent colours (Aurora, Red, Cyan, Blue, Purple), male assistant
personality, text chat, **Gemini Live** real-time voice, Android app control, YouTube assistant,
Coding Studio and AI Vision. No login; everything stays on the device.

## Voice
* The only voice input is **Gemini Live** (tap the big LIVE button). There is no speech-recognition feature.
* Replies in text chat can be read aloud with the speaker button (Android text-to-speech).

## Permissions (asked on first launch, and under Tools > Permissions)
Microphone, Camera, Notifications, and the **Accessibility service** (lets ALIC press Back / Home / Recents /
Notifications by command, e.g. "go back", "home jao". It does not read your screen).
Android cannot grant accessibility automatically: the app opens the Accessibility settings and you switch ALIC AI on.
On Android 13+ for sideloaded apps, first use: Settings > Apps > ALIC AI > ⋮ > *Allow restricted settings*.

## Build with GitHub Actions
Push to `main` → **Actions → Android Build → Artifacts → ALIC-AI-debug-apk**.
Optional signed release: add secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Gemini setup
Settings → Gemini API configuration → paste key → Save → Test → *List models* and pick chat + live models.
The key is stored encrypted on the device and is never in the code. For production, use a backend proxy
(base URL field) so no secret ships in the APK.

## Local build
JDK 17 + Android SDK + Gradle 8.7: `gradle assembleDebug`

## What ALIC can do (function calling, in Live voice and in chat)
Open any app, call a contact/number, prepare SMS or WhatsApp messages (you tap Send), YouTube / YouTube Music search,
Google search, Maps navigation, alarms, timers, flashlight, settings pages, volume, Back/Home/Recents/notifications/screenshot
(needs the Accessibility service). Calls need Contacts + Phone permissions; "Display over other apps" lets ALIC keep opening
apps while another app is on screen during a Live conversation.

## "Hey Alic" offline wake word (Vosk)
1. Extract the Vosk model zip and copy its CONTENTS (am, conf, graph, ivector, README) into
   `app/src/main/assets/model-hi/` (Hindi small model) or `app/src/main/assets/model-en-us/` (English small model, preferred when present).
2. On first run `WakeWordService` copies the model to `filesDir/vosk-model` (once) and loads it on a background thread.
3. Settings -> Wake Up Detection: enable the wake word (do this from the app so Android allows the microphone service), set the popup,
   overlay permission, default assistant, battery optimization.
The Hindi model is Devanagari-only and has no "alic" word, so it listens for the nearest real words ("हे एलेक" / "हे एलिस");
the app checks the model vocabulary at load time and shows the phrases it actually listens for. Test accuracy on your phone.
Limitations: no guaranteed lock-screen listening/popup, Android 14+ blocks starting a mic service from the background,
force-stop disables background services, continuous listening uses battery.

## Design (emerald glass)
Black + emerald palette (#050807 background, #00D98B primary, #00FF9D glow, #244C38 borders).
Bottom bar: Home, Chat, Voice, Tools, Profile. Home has Chat, a microphone button and Quick Actions.
Quick Actions and Tools open the existing features only.
