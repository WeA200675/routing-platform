package org.routingplatform.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.VoicePreferences

internal data class NavigationDeviceVoice(
    val id: String,
    val languageTag: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
    val features: Set<String>,
)

/*
 * Android output adapter downstream from G6.5.
 *
 * It receives speech text/configuration only and has no dependency on routing,
 * route-progress, positioning or reroute controllers.
 */
internal class AndroidNavigationVoiceRuntime(
    context: Context,
) : AutoCloseable {
    private val speaker =
        AndroidTextToSpeechNavigationSpeaker(
            context.applicationContext
        )

    private val playback =
        NavigationVoicePlaybackRuntime(
            speaker
        )

    fun present(
        snapshot: NavigationUiSnapshot,
        voice: VoicePreferences,
    ) {
        playback.present(
            snapshot = snapshot,
            voice = voice,
        )
    }

    fun availableVoices(): List<NavigationDeviceVoice> =
        speaker.availableVoices()

    override fun close() {
        playback.close()
    }
}

@Composable
internal fun NavigationVoiceRuntimeEffect(
    snapshot: NavigationUiSnapshot,
    baseVoice: VoicePreferences,
    personality: NavigationPersonalityPreferences,
) {
    val context =
        LocalContext.current.applicationContext

    val runtime =
        remember(context) {
            AndroidNavigationVoiceRuntime(
                context
            )
        }

    val effectiveVoice =
        ExperiencePackRuntimeResolver.resolveVoicePreferences(
            base = baseVoice,
            personality = personality,
        )

    LaunchedEffect(
        runtime,
        snapshot,
        effectiveVoice,
    ) {
        runtime.present(
            snapshot = snapshot,
            voice = effectiveVoice,
        )
    }

    DisposableEffect(runtime) {
        onDispose {
            runtime.close()
        }
    }
}

private class AndroidTextToSpeechNavigationSpeaker(
    context: Context,
) : NavigationVoiceSpeaker {
    private enum class State {
        Initializing,
        Ready,
        Unavailable,
        Closed,
    }

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = State.Initializing
    private var pendingCue: NavigationVoiceCue? = null
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech =
            TextToSpeech(
                context.applicationContext
            ) { status ->
                val deferred =
                    synchronized(lock) {
                        if (state == State.Closed) {
                            null
                        } else if (status == TextToSpeech.SUCCESS) {
                            state = State.Ready
                            pendingCue.also {
                                pendingCue = null
                            }
                        } else {
                            state = State.Unavailable
                            pendingCue = null
                            null
                        }
                    }

                deferred?.let { cue ->
                    mainHandler.post {
                        speakReady(cue)
                    }
                }
            }
    }

    override fun submit(
        cue: NavigationVoiceCue,
    ): NavigationSpeechSubmission {
        val currentState =
            synchronized(lock) {
                if (state == State.Initializing) {
                    pendingCue = cue
                }
                state
            }

        return when (currentState) {
            State.Initializing ->
                NavigationSpeechSubmission.Queued

            State.Ready ->
                if (speakReady(cue)) {
                    NavigationSpeechSubmission.Spoken
                } else {
                    NavigationSpeechSubmission.Rejected
                }

            State.Unavailable,
            State.Closed ->
                NavigationSpeechSubmission.Rejected
        }
    }

    override fun clearPending() {
        synchronized(lock) {
            pendingCue = null
        }
    }

    override fun stop() {
        val engine =
            synchronized(lock) {
                textToSpeech
            }

        runCatching {
            engine?.stop()
        }
    }

    fun availableVoices(): List<NavigationDeviceVoice> {
        val engine =
            synchronized(lock) {
                if (state == State.Ready) {
                    textToSpeech
                } else {
                    null
                }
            } ?: return emptyList()

        return runCatching {
            engine.voices
                .orEmpty()
                .map { voice ->
                    NavigationDeviceVoice(
                        id = voice.name,
                        languageTag = voice.locale.toLanguageTag(),
                        quality = voice.quality,
                        latency = voice.latency,
                        requiresNetwork = voice.isNetworkConnectionRequired,
                        features = voice.features.orEmpty().toSet(),
                    )
                }
                .sortedWith(
                    compareBy<NavigationDeviceVoice>(
                        { it.languageTag },
                        { it.requiresNetwork },
                        { it.id },
                    )
                )
        }.getOrDefault(
            emptyList()
        )
    }

    private fun speakReady(
        cue: NavigationVoiceCue,
    ): Boolean {
        val engine =
            synchronized(lock) {
                if (state == State.Ready) {
                    textToSpeech
                } else {
                    null
                }
            } ?: return false

        if (!configure(engine, cue)) {
            return false
        }

        val utteranceId =
            "navigation-" +
                cue.key.hashCode().toString(16)

        return runCatching {
            engine.speak(
                cue.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId,
            ) == TextToSpeech.SUCCESS
        }.getOrDefault(
            false
        )
    }

    private fun configure(
        engine: TextToSpeech,
        cue: NavigationVoiceCue,
    ): Boolean {
        val locale =
            Locale.forLanguageTag(
                cue.languageTag
            )

        if (locale.language.isBlank()) {
            return false
        }

        val languageResult =
            runCatching {
                engine.setLanguage(locale)
            }.getOrDefault(
                TextToSpeech.ERROR
            )

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return false
        }

        cue.voiceId?.let { requestedId ->
            val requestedVoice =
                runCatching {
                    engine.voices
                        .orEmpty()
                        .firstOrNull {
                            it.name == requestedId &&
                                it.locale.language.equals(
                                    locale.language,
                                    ignoreCase = true,
                                )
                        }
                }.getOrNull()

            if (requestedVoice != null) {
                val voiceResult =
                    runCatching {
                        engine.setVoice(
                            requestedVoice
                        )
                    }.getOrDefault(
                        TextToSpeech.ERROR
                    )

                if (voiceResult == TextToSpeech.ERROR) {
                    runCatching {
                        engine.setLanguage(locale)
                    }
                }
            }
        }

        return runCatching {
            engine.setSpeechRate(
                cue.speechRate.toFloat()
            ) != TextToSpeech.ERROR
        }.getOrDefault(
            false
        )
    }

    override fun close() {
        val engine =
            synchronized(lock) {
                if (state == State.Closed) {
                    null
                } else {
                    state = State.Closed
                    pendingCue = null
                    textToSpeech.also {
                        textToSpeech = null
                    }
                }
            }

        mainHandler.removeCallbacksAndMessages(null)

        runCatching {
            engine?.stop()
        }

        runCatching {
            engine?.shutdown()
        }
    }
}