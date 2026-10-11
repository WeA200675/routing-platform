package org.routingplatform.app.ai

/**
 * Fail-closed registry for optional local generators. No backend is selected
 * implicitly and no network fallback exists.
 */
class SocialAiBackendRegistry(
    backends: List<LocalSocialAiTextGenerationBackend>,
) {
    private val byId =
        backends.associateBy { it.backendId }

    init {
        require(byId.size == backends.size) {
            "Local Social AI backend ids must be unique."
        }
        require(byId.keys.none { it.isBlank() }) {
            "Local Social AI backend ids must not be blank."
        }
    }

    fun find(backendId: String): LocalSocialAiTextGenerationBackend? =
        byId[backendId]

    fun require(backendId: String): LocalSocialAiTextGenerationBackend =
        checkNotNull(find(backendId)) {
            "Requested local Social AI backend is not registered."
        }

    fun availableBackendIds(): List<String> =
        byId.keys.sorted()
}
