# ScreenLens

Point your phone camera at any screen and get an instant AI explanation, shown as an overlay on the live camera view.

**Live app:** https://zai-labs.github.io/screenlens/

## Features

- **Live camera overlay**: the answer appears at the top of the camera view and streams in word by word.
- **Two modes**
  - **Describe**: explains what's on the screen (errors, code, UI, products) in 2–4 sentences.
  - **MCQ**: answers multiple-choice questions only, e.g. `Q4: C) … — short reason`.
- **Two AI providers**
  - **Google Gemini**, with optional Google Search grounding (sources are shown).
  - **Anthropic Claude** (Haiku or Sonnet).
- **Auto mode**: re-checks every 3 seconds and skips frames that haven't changed (image hashing).
- **Camera lens picker and zoom**: choose between the main, wide and telephoto lenses where the phone allows.
- **Full-screen installable app (PWA)**: works in portrait and landscape.
- **No backend**: a single HTML file. API keys are stored only in your browser.

## Use it

1. Open the live link in **Chrome on Android**. The camera needs HTTPS.
2. Allow camera access.
3. Tap **⚙** and paste an API key:
   - Gemini: https://aistudio.google.com/apikey
   - Claude: https://console.anthropic.com/settings/keys (needs API credit; a Claude.ai subscription doesn't include API access)
4. Point at a screen and tap the round **Describe** button (it reads **Answer** in MCQ mode).
5. Optional: **⋮ → Add to Home screen → Install** to run it full screen like a native app.

### Settings (⚙)

| Setting | Notes |
|---|---|
| Mode | Describe / MCQ (answer only) |
| Auto | Re-check every 3 s |
| Camera lens / Zoom | Lists all the phone's cameras; zoom if supported |
| Provider | Gemini or Claude |
| Gemini model | Default `gemini-flash-latest` (e.g. `gemini-flash-lite-latest`, `gemini-2.5-flash-lite`) |
| Claude model | Default `claude-haiku-5-5` (or `claude-sonnet-5-5`) |
| Google Search | Gemini only; better for real-world lookups, slightly slower |

If a Gemini model name is wrong, the error message lists the models your key can use.

## Tips

- Fill the frame with the screen and hold steady. Use zoom for small text.
- Turn **Google Search off** for the fastest answers (about 1–2 s).
- HTTP 429 means quota exceeded: switch to a lighter model, turn off Search, or enable billing.

## Project structure

```
index.html      Web app (camera, overlay, Gemini/Claude streaming, settings)
manifest.json   PWA manifest (full screen, any orientation)
sw.js           Minimal service worker (enables "Install app")
icon-*.png      App icons
android/        Native Android version (Kotlin + CameraX + Gemini)
.github/workflows/build-apk.yml   Builds the Android APK on every push
```

### Android APK (optional)

Every push runs **Actions → Build APK**. Download the `ScreenLens-apk` artifact, unzip it and install `app-debug.apk` on your phone (allow "Install unknown apps").

## How it works

1. The `getUserMedia` API opens the back camera in a `<video>` element.
2. On a tap, a frame is drawn to a canvas, resized to 1024 px and JPEG-encoded.
3. The frame is sent to:
   - Gemini `streamGenerateContent?alt=sse`, optionally with the `google_search` tool, or
   - Claude `/v1/messages` with `stream: true`.
4. The server-sent event (SSE) chunks are appended to the overlay as they arrive.

## Security note

API keys are stored in the browser's `localStorage` and sent directly from your phone to Google or Anthropic. That's fine for personal use. For a public release, put the API calls behind a small backend proxy so keys aren't exposed.

## Fair use

Use the MCQ mode for learning and practice. Don't use it in exams or proctored assessments where outside help isn't allowed.
