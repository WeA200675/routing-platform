package org.routingplatform.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.VoicePreferences

internal data class NavigationVoiceRuntimePresentation(
    val catalogState:
        NavigationVoiceCatalogState,

    val preview:
        (VoicePreferences) ->
        NavigationVoicePreviewResult,

    val refreshCatalog:
        () -> Unit,
)

/*
 * Android output adapter downstream from G6.5/G6.6.
 *
 * G6.9 adds an observable device capability catalog and preview output.
 * The runtime still receives speech text/configuration only and has no
 * dependency on routing, route-progress, positioning, reroute or safety
 * controllers.
 */
internal class AndroidNavigationVoiceRuntime(
    context:
        Context,

    onCatalogStateChanged:
        (NavigationVoiceCatalogState) ->
        Unit =
        {},
) :
    AutoCloseable {
    private val speaker =
        AndroidTextToSpeechNavigationSpeaker(
            context =
                context
                    .applicationContext,

            onCatalogStateChanged =
                onCatalogStateChanged,
        )

    private val playback =
        NavigationVoicePlaybackRuntime(
            speaker
        )

    fun present(
        snapshot:
            NavigationUiSnapshot,

        voice:
            VoicePreferences,
    ) {
        playback.present(
            snapshot =
                snapshot,

            voice =
                voice,
        )
    }

    fun preview(
        voice:
            VoicePreferences,
    ): NavigationVoicePreviewResult =
        speaker.preview(
            voice
        )

    fun refreshCatalog() {
        speaker.refreshCatalog()
    }

    override fun close() {
        playback.close()
    }
}

@Composable
internal fun rememberNavigationVoiceRuntime(
    snapshot:
        NavigationUiSnapshot,

    baseVoice:
        VoicePreferences,

    personality:
        NavigationPersonalityPreferences,
): NavigationVoiceRuntimePresentation {
    val context =
        LocalContext
            .current
            .applicationContext

    val catalogState =
        remember(
            context
        ) {
            mutableStateOf<
                NavigationVoiceCatalogState
            >(
                NavigationVoiceCatalogState
                    .Initializing
            )
        }

    val runtime =
        remember(
            context
        ) {
            AndroidNavigationVoiceRuntime(
                context =
                    context,

                onCatalogStateChanged = {
                        updatedState ->

                    catalogState.value =
                        updatedState
                },
            )
        }

    val effectiveVoice =
        ExperiencePackRuntimeResolver
            .resolveVoicePreferences(
                base =
                    baseVoice,

                personality =
                    personality,
            )

    LaunchedEffect(
        runtime,
        snapshot,
        effectiveVoice,
    ) {
        runtime.present(
            snapshot =
                snapshot,

            voice =
                effectiveVoice,
        )
    }

    DisposableEffect(
        runtime
    ) {
        onDispose {
            runtime.close()
        }
    }

    return NavigationVoiceRuntimePresentation(
        catalogState =
            catalogState
                .value,

        preview = {
                voice ->

            runtime.preview(
                voice
            )
        },

        refreshCatalog = {
            runtime.refreshCatalog()
        },
    )
}

private class AndroidTextToSpeechNavigationSpeaker(
    context:
        Context,

    private val onCatalogStateChanged:
        (NavigationVoiceCatalogState) ->
        Unit,
) :
    NavigationVoiceSpeaker {
    private enum class State {
        Initializing,
        Ready,
        Unavailable,
        Closed,
    }

    private companion object {
        val NAVIGATION_AUDIO_ATTRIBUTES:
            AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(
                    AudioAttributes
                        .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                )
                .setContentType(
                    AudioAttributes
                        .CONTENT_TYPE_SPEECH
                )
                .build()
    }

    private val lock =
        Any()

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var state =
        State.Initializing

    private var pendingCue:
        NavigationVoiceCue? =
        null

    private var textToSpeech:
        TextToSpeech? =
        null

    init {
        textToSpeech =
            TextToSpeech(
                context
                    .applicationContext
            ) {
                    status ->

                val initialization =
                    synchronized(
                        lock
                    ) {
                        if (
                            state ==
                            State.Closed
                        ) {
                            State.Closed to
                                null
                        } else if (
                            status ==
                            TextToSpeech
                                .SUCCESS
                        ) {
                            state =
                                State.Ready

                            State.Ready to
                                pendingCue
                                    .also {
                                        pendingCue =
                                            null
                                    }
                        } else {
                            state =
                                State.Unavailable

                            pendingCue =
                                null

                            State.Unavailable to
                                null
                        }
                    }

                when (
                    initialization
                        .first
                ) {
                    State.Ready -> {
                        mainHandler.post {
                            publishCatalog()

                            initialization
                                .second
                                ?.let {
                                        cue ->

                                    speakReady(
                                        cue
                                    )
                                }
                        }
                    }

                    State.Unavailable -> {
                        mainHandler.post {
                            onCatalogStateChanged(
                                NavigationVoiceCatalogState
                                    .Unavailable
                            )
                        }
                    }

                    State.Initializing,
                    State.Closed ->
                        Unit
                }
            }
    }

    override fun submit(
        cue:
            NavigationVoiceCue,
    ): NavigationSpeechSubmission {
        val currentState =
            synchronized(
                lock
            ) {
                if (
                    state ==
                    State.Initializing
                ) {
                    pendingCue =
                        cue
                }

                state
            }

        return when (
            currentState
        ) {
            State.Initializing ->
                NavigationSpeechSubmission
                    .Queued

            State.Ready ->
                if (
                    speakReady(
                        cue
                    )
                ) {
                    NavigationSpeechSubmission
                        .Spoken
                } else {
                    NavigationSpeechSubmission
                        .Rejected
                }

            State.Unavailable,
            State.Closed ->
                NavigationSpeechSubmission
                    .Rejected
        }
    }

    fun preview(
        voice:
            VoicePreferences,
    ): NavigationVoicePreviewResult {
        val engine =
            synchronized(
                lock
            ) {
                when (
                    state
                ) {
                    State.Ready ->
                        textToSpeech

                    State.Initializing ->
                        return NavigationVoicePreviewResult
                            .NotReady

                    State.Unavailable,
                    State.Closed ->
                        return NavigationVoicePreviewResult
                            .Rejected
                }
            }
                ?: return NavigationVoicePreviewResult
                    .Rejected

        if (
            !configure(
                engine =
                    engine,

                languageTag =
                    voice
                        .languageTag,

                voiceId =
                    voice
                        .voiceId,

                speechRate =
                    voice
                        .speechRate,
            )
        ) {
            return NavigationVoicePreviewResult
                .Rejected
        }

        val previewText =
            NavigationVoiceCatalog
                .previewText(
                    voice
                        .languageTag
                )

        val utteranceId =
            "voice-preview-" +
                System.nanoTime()
                    .toString(
                        16
                    )

        return if (
            runCatching {
                engine.speak(
                    previewText,
                    TextToSpeech
                        .QUEUE_FLUSH,
                    null,
                    utteranceId,
                ) ==
                    TextToSpeech
                        .SUCCESS
            }.getOrDefault(
                false
            )
        ) {
            NavigationVoicePreviewResult
                .Spoken
        } else {
            NavigationVoicePreviewResult
                .Rejected
        }
    }

    fun refreshCatalog() {
        mainHandler.post {
            publishCatalog()
        }
    }

    override fun clearPending() {
        synchronized(
            lock
        ) {
            pendingCue =
                null
        }
    }

    override fun stop() {
        val engine =
            synchronized(
                lock
            ) {
                textToSpeech
            }

        runCatching {
            engine
                ?.stop()
        }
    }

    private fun publishCatalog() {
        val engine =
            synchronized(
                lock
            ) {
                if (
                    state ==
                    State.Ready
                ) {
                    textToSpeech
                } else {
                    null
                }
            }
                ?: return

        val supportedLanguageTags =
            NavigationVoiceCatalog
                .languageOptions
                .mapNotNull {
                        option ->

                    val result =
                        runCatching {
                            engine
                                .isLanguageAvailable(
                                    Locale
                                        .forLanguageTag(
                                            option
                                                .languageTag
                                        )
                                )
                        }.getOrDefault(
                            TextToSpeech
                                .LANG_NOT_SUPPORTED
                        )

                    if (
                        result >=
                        TextToSpeech
                            .LANG_AVAILABLE
                    ) {
                        option
                            .languageTag
                    } else {
                        null
                    }
                }
                .toSet()

        val voices =
            runCatching {
                engine
                    .voices
                    .orEmpty()
                    .map {
                            voice ->

                        NavigationDeviceVoice(
                            id =
                                voice
                                    .name,

                            languageTag =
                                voice
                                    .locale
                                    .toLanguageTag(),

                            quality =
                                voice
                                    .quality,

                            latency =
                                voice
                                    .latency,

                            requiresNetwork =
                                voice
                                    .isNetworkConnectionRequired,

                            features =
                                voice
                                    .features
                                    .orEmpty()
                                    .toSet(),
                        )
                    }
                    .sortedWith(
                        compareBy<
                            NavigationDeviceVoice
                        > {
                            it.languageTag
                        }
                            .thenBy {
                                it.requiresNetwork
                            }
                            .thenByDescending {
                                it.quality
                            }
                            .thenBy {
                                it.latency
                            }
                            .thenBy {
                                it.id
                            }
                    )
            }.getOrDefault(
                emptyList()
            )

        onCatalogStateChanged(
            NavigationVoiceCatalogState
                .Ready(
                    NavigationVoiceCatalogSnapshot(
                        supportedLanguageTags =
                            supportedLanguageTags,

                        voices =
                            voices,
                    )
                )
        )
    }

    private fun speakReady(
        cue:
            NavigationVoiceCue,
    ): Boolean {
        val engine =
            synchronized(
                lock
            ) {
                if (
                    state ==
                    State.Ready
                ) {
                    textToSpeech
                } else {
                    null
                }
            }
                ?: return false

        if (
            !configure(
                engine =
                    engine,

                languageTag =
                    cue
                        .languageTag,

                voiceId =
                    cue
                        .voiceId,

                speechRate =
                    cue
                        .speechRate,
            )
        ) {
            return false
        }

        val utteranceId =
            "navigation-" +
                cue
                    .key
                    .hashCode()
                    .toString(
                        16
                    )

        return runCatching {
            engine.speak(
                cue.text,
                TextToSpeech
                    .QUEUE_FLUSH,
                null,
                utteranceId,
            ) ==
                TextToSpeech
                    .SUCCESS
        }.getOrDefault(
            false
        )
    }

    private fun configure(
        engine:
            TextToSpeech,

        languageTag:
            String,

        voiceId:
            String?,

        speechRate:
            Double,
    ): Boolean {
        val locale =
            Locale.forLanguageTag(
                languageTag
            )

        if (
            locale
                .language
                .isBlank()
        ) {
            return false
        }

        val languageResult =
            runCatching {
                engine.setLanguage(
                    locale
                )
            }.getOrDefault(
                TextToSpeech
                    .ERROR
            )

        if (
            languageResult <
            TextToSpeech
                .LANG_AVAILABLE
        ) {
            return false
        }

        voiceId
            ?.let {
                    requestedId ->

                val requestedVoice =
                    runCatching {
                        engine
                            .voices
                            .orEmpty()
                            .firstOrNull {
                                it.name ==
                                    requestedId &&
                                    NavigationVoiceCatalog
                                        .voiceMatchesLanguageTag(
                                            voiceLanguageTag =
                                                it.locale
                                                    .toLanguageTag(),

                                            targetLanguageTag =
                                                languageTag,
                                        )
                            }
                    }.getOrNull()

                if (
                    requestedVoice !=
                    null
                ) {
                    val voiceResult =
                        runCatching {
                            engine.setVoice(
                                requestedVoice
                            )
                        }.getOrDefault(
                            TextToSpeech
                                .ERROR
                        )

                    if (
                        voiceResult ==
                        TextToSpeech
                            .ERROR
                    ) {
                        runCatching {
                            engine.setLanguage(
                                locale
                            )
                        }
                    }
                }
            }

        /*
         * Navigation guidance should be routed as navigation assistance,
         * not as generic media. Failure to apply attributes is non-fatal
         * because some vendor engines may still synthesize correctly.
         */
        runCatching {
            engine.setAudioAttributes(
                NAVIGATION_AUDIO_ATTRIBUTES
            )
        }

        return runCatching {
            engine.setSpeechRate(
                speechRate
                    .toFloat()
            ) !=
                TextToSpeech
                    .ERROR
        }.getOrDefault(
            false
        )
    }

    override fun close() {
        val engine =
            synchronized(
                lock
            ) {
                if (
                    state ==
                    State.Closed
                ) {
                    null
                } else {
                    state =
                        State.Closed

                    pendingCue =
                        null

                    textToSpeech
                        .also {
                            textToSpeech =
                                null
                        }
                }
            }

        mainHandler
            .removeCallbacksAndMessages(
                null
            )

        runCatching {
            engine
                ?.stop()
        }

        runCatching {
            engine
                ?.shutdown()
        }
    }
}