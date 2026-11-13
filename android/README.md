# ScreenLens

Point your Android phone camera at any screen → Gemini (with Google Search grounding) describes it in an overlay.

## Get the APK

**Option A – GitHub (no Android Studio needed)**
1. Create a new GitHub repo and push this folder.
2. Open the repo → **Actions** → "Build APK" runs automatically (~4 min).
3. Download the **ScreenLens-apk** artifact, unzip, copy `app-debug.apk` to your phone and install (allow "Install unknown apps").

**Option B – Android Studio**
Open the folder → let Gradle sync → Build ▸ Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`.

## First run
1. Get a free API key at https://aistudio.google.com/apikey
2. App opens Settings → paste key → Save.
3. Point at a screen → tap **Describe** (or tap the preview). **Auto** re-describes every 3 s, skipping unchanged frames.

Model defaults to `gemini-flash-latest` (change it in ⚙ Settings).

> The key is stored only on the phone. For a public release, move the call behind a backend or Firebase AI Logic so the key isn't in the app.
