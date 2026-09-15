package org.routingplatform.app.ai

import android.content.Context

class AndroidSocialAiMemoryStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun load(
        storeId: String,
    ): List<SocialAiKnowledge> {
        val encoded =
            preferences.getString(
                key(storeId),
                null,
            ) ?: return emptyList()

        return SocialAiMemoryCodec.decode(encoded)
    }

    fun save(
        storeId: String,
        knowledge: List<SocialAiKnowledge>,
    ): Boolean =
        preferences
            .edit()
            .putString(
                key(storeId),
                SocialAiMemoryCodec.encode(knowledge),
            )
            .commit()

    fun clear(
        storeId: String,
    ): Boolean =
        preferences
            .edit()
            .remove(
                key(storeId)
            )
            .commit()

    fun exists(
        storeId: String,
    ): Boolean =
        preferences.contains(
            key(storeId)
        )

    private fun key(
        storeId: String,
    ): String {
        require(STORE_ID_PATTERN.matches(storeId)) {
            "storeId must contain 1-64 safe identifier characters."
        }

        return STORE_KEY_PREFIX +
            storeId
    }

    private companion object {
        const val PREFERENCES_NAME =
            "routing-platform-social-ai-memory-v1"

        const val STORE_KEY_PREFIX =
            "memory:"

        val STORE_ID_PATTERN =
            Regex("[A-Za-z0-9._-]{1,64}")
    }
}
