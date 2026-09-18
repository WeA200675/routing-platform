package org.routingplatform.app.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

object SocialAiMemoryCodec {

    fun encode(
        knowledge: List<SocialAiKnowledge>,
    ): String {
        require(knowledge.size <= MAX_ENTRIES) {
            "Social AI memory entry limit exceeded."
        }
        require(knowledge.map { it.id }.distinct().size == knowledge.size) {
            "Social AI memory ids must be unique."
        }

        val buffer = ByteArrayOutputStream()

        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(knowledge.size)

            knowledge.forEach { entry ->
                output.writeUTF(entry.id)
                output.writeUTF(entry.kind.name)
                output.writeUTF(entry.key)
                output.writeUTF(entry.value)
                output.writeDouble(entry.confidence)
                output.writeUTF(entry.scope.name)
                output.writeOptionalString(entry.contextKey)
                output.writeUTF(entry.source)
                output.writeLong(entry.observedAtEpochMillis)
                output.writeBoolean(entry.userLocked)
            }
        }

        val bytes = buffer.toByteArray()

        require(bytes.size <= MAX_BYTES) {
            "Encoded Social AI memory exceeds size limit."
        }

        return Base64
            .getEncoder()
            .encodeToString(bytes)
    }

    fun decode(
        encoded: String,
    ): List<SocialAiKnowledge> {
        require(encoded.isNotBlank()) {
            "Encoded Social AI memory must not be blank."
        }
        require(encoded.length <= MAX_TEXT_LENGTH) {
            "Encoded Social AI memory text exceeds size limit."
        }

        val bytes =
            try {
                Base64.getDecoder().decode(encoded)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Encoded Social AI memory is not valid Base64.",
                    error,
                )
            }

        require(bytes.size <= MAX_BYTES) {
            "Decoded Social AI memory exceeds size limit."
        }

        return DataInputStream(
            ByteArrayInputStream(bytes)
        ).use { input ->
            require(input.readInt() == MAGIC) {
                "Social AI memory magic mismatch."
            }
            require(input.readInt() == VERSION) {
                "Unsupported Social AI memory version."
            }

            val count = input.readInt()

            require(count in 0..MAX_ENTRIES) {
                "Invalid Social AI memory entry count."
            }

            val result = ArrayList<SocialAiKnowledge>(count)
            val ids = HashSet<String>(count)

            repeat(count) {
                val entry =
                    SocialAiKnowledge(
                        id = input.readUTF(),
                        kind = input.readEnumValue("knowledge kind"),
                        key = input.readUTF(),
                        value = input.readUTF(),
                        confidence = input.readDouble(),
                        scope = input.readEnumValue("memory scope"),
                        contextKey = input.readOptionalString(),
                        source = input.readUTF(),
                        observedAtEpochMillis = input.readLong(),
                        userLocked = input.readBoolean(),
                    )

                require(ids.add(entry.id)) {
                    "Duplicate Social AI memory id: ${entry.id}"
                }

                result += entry
            }

            require(input.available() == 0) {
                "Social AI memory payload contains trailing data."
            }

            result
        }
    }
}

private fun DataOutputStream.writeOptionalString(
    value: String?,
) {
    writeBoolean(value != null)
    value?.let(::writeUTF)
}

private fun DataInputStream.readOptionalString(): String? =
    if (readBoolean()) {
        readUTF()
    } else {
        null
    }

private inline fun <reified T : Enum<T>> DataInputStream.readEnumValue(
    fieldName: String,
): T {
    val raw = readUTF()

    return enumValues<T>()
        .firstOrNull { it.name == raw }
        ?: throw IllegalArgumentException(
            "Unknown $fieldName value: $raw"
        )
}

private const val MAGIC =
    0x52504149

private const val VERSION =
    1

private const val MAX_ENTRIES =
    512

private const val MAX_BYTES =
    512 * 1024

private const val MAX_TEXT_LENGTH =
    1024 * 1024
