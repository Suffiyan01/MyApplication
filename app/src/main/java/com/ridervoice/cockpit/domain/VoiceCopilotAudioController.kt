package com.ridervoice.cockpit.domain

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceCopilotAudioController(
    private val context: Context,
    private val onFinalCommand: (String) -> Unit
) : TextToSpeech.OnInitListener, RecognitionListener {

    companion object {
        private const val TAG = "VoiceCopilotAudio"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsEngine: TextToSpeech? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var isTtsInitialized = false
    private val pendingSpeechQueue = mutableListOf<Pair<String, Float>>()

    private var isContinuousHotwordActive = false
    private var isProcessingWakeWord = false

    init {
        mainHandler.post {
            recreateSpeechRecognizer()
            ttsEngine = TextToSpeech(context.applicationContext, this)
        }
    }

    fun isSpeechAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun recreateSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Defensive
        }
        speechRecognizer = null

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceCopilotAudioController)
                }
            } catch (e: Exception) {
                speechRecognizer = null
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsEngine?.let { tts ->
                val langResult = tts.setLanguage(Locale.US)
                if (langResult != TextToSpeech.LANG_MISSING_DATA && langResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsInitialized = true
                    // Drain pending speech queue
                    pendingSpeechQueue.forEach { (text, volume) ->
                        speak(text, volume)
                    }
                    pendingSpeechQueue.clear()
                }
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        abandonAudioFocus()
                    }
                    @Deprecated("Deprecated in API 21")
                    override fun onError(utteranceId: String?) {
                        abandonAudioFocus()
                    }
                })
            }
        }
    }

    fun startContinuousHotword() {
        isContinuousHotwordActive = true
        startListening()
    }

    fun stopContinuousHotword() {
        isContinuousHotwordActive = false
        stopListening()
    }

    fun startListening() {
        mainHandler.post {
            if (!isSpeechAvailable()) {
                _isListening.value = false
                _partialTranscript.value = "Speech recognition unavailable on device"
                return@post
            }

            recreateSpeechRecognizer()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            try {
                _isListening.value = true
                _partialTranscript.value = "Listening for 'Hey Rider'..."
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                _partialTranscript.value = "Error starting mic"
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            _isListening.value = false
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Defensive
            }
        }
    }

    fun speak(text: String, volumeScale: Float = 0.85f) {
        if (text.isBlank()) return

        if (!isTtsInitialized) {
            pendingSpeechQueue.add(Pair(text, volumeScale))
            return
        }

        requestAudioFocusDucking()

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeScale.coerceIn(0f, 1f))
        }

        ttsEngine?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "RIDERVOICE_UTTERANCE_${System.currentTimeMillis()}"
        )
    }

    private fun requestAudioFocusDucking() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = focusRequest
                am.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    fun destroy() {
        isContinuousHotwordActive = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                ttsEngine?.stop()
                ttsEngine?.shutdown()
                ttsEngine = null
            } catch (e: Exception) {
                // Handled
            }
        }
    }

    // --- RecognitionListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        _partialTranscript.value = "Listening for 'Hey Rider'..."
    }

    override fun onBeginningOfSpeech() {
        _isListening.value = true
        _partialTranscript.value = "Listening..."
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Listening for 'Hey Rider'..."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening for 'Hey Rider'..."
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_CLIENT -> "Client error."
            SpeechRecognizer.ERROR_NETWORK -> "Network required for speech."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied."
            else -> "Voice recognition error ($error)"
        }
        _partialTranscript.value = errorMsg

        if (isContinuousHotwordActive) {
            mainHandler.postDelayed({
                if (isContinuousHotwordActive && !_isListening.value) {
                    startListening()
                }
            }, 600)
        } else {
            recreateSpeechRecognizer()
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val finalResult = matches?.firstOrNull() ?: ""
        if (finalResult.isNotBlank()) {
            _partialTranscript.value = finalResult
            val lower = finalResult.lowercase()
            if (lower.contains("hey rider") || lower.contains("hi rider") || lower.contains("hello rider") || lower.contains("rider")) {
                onWakeWordDetected()
            } else {
                onFinalCommand(finalResult)
                if (isContinuousHotwordActive) {
                    mainHandler.postDelayed({
                        if (isContinuousHotwordActive && !_isListening.value) {
                            startListening()
                        }
                    }, 2000)
                }
            }
        } else {
            _partialTranscript.value = "Listening for 'Hey Rider'..."
            if (isContinuousHotwordActive) {
                mainHandler.postDelayed({
                    if (isContinuousHotwordActive && !_isListening.value) {
                        startListening()
                    }
                }, 600)
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull() ?: ""
        if (partial.isNotBlank()) {
            _partialTranscript.value = partial
            val lower = partial.lowercase()
            if (lower.contains("hey rider") || lower.contains("hi rider") || lower.contains("hello rider") || lower.contains("rider")) {
                onWakeWordDetected()
            }
        }
    }

    private fun onWakeWordDetected() {
        if (isProcessingWakeWord) return
        isProcessingWakeWord = true
        Log.d(TAG, "Wake word 'Hey Rider' detected!")
        _partialTranscript.value = "Wake Word 'Hey Rider' Active!"
        speak("Yes, rider? What's your task?", 0.90f)
        mainHandler.postDelayed({
            isProcessingWakeWord = false
            startListening()
        }, 1800)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
