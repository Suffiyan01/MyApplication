# Implementation Plan: "Hey Rider" Wake Word Voice Trigger

Implement hotword wake word detection in `VoiceCopilotAudioController.kt` so that whenever the rider says **"Hey Rider"**, the AI Copilot instantly wakes up, responds via TTS (*"Yes, rider? What's your task?"*), and listens for the task command.

## User Review Required

> [!NOTE]
> The app will continuously analyze speech recognition results for the wake phrase `"hey rider"` (case-insensitive). When detected, it transitions into command-listening mode hands-free.

## Proposed Changes

### Component 1: Wake Word Hotword Interception

#### [MODIFY] [VoiceCopilotAudioController.kt](file:///C:/Users/web/AndroidStudioProjects/MyApplication/app/src/main/java/com/ridervoice/cockpit/domain/VoiceCopilotAudioController.kt)
- Monitor partial and final speech results in `onPartialResults` and `onResults`.
- Detect `"hey rider"` keyword.
- Upon detection:
  - Play TTS confirmation: *"Yes, rider? What's your task?"*
  - Automatically re-trigger `startListening()` after a short delay so the rider can speak their task command.

## Verification Plan

### Automated Tests
- Run Gradle unit tests: `gradle_build(commandLine="app:testDebugUnitTest")`

### Manual Verification
- Assemble debug APK: `gradle_build(commandLine="app:assembleDebug")`
- Deploy app to connected Android device: `deploy`
