package mihon.data.extension.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializer for proto3 JSON-style 64-bit integers.
 *
 * google.protobuf.json_format serializes int64 as JSON *strings* ("4", "62897..."),
 * which kotlinx Json cannot decode into [Long] without coercion. This serializer
 * accepts both the proto3-JSON string form and the plain numeric form when decoding
 * from JSON, while delegating to regular long handling everywhere else (protobuf).
 *
 * Used by [NetworkExtensionStore] so the new-format index.json published alongside
 * index.pb (e.g. keiyoushi/extensions) can be parsed directly.
 */
internal object Proto3JsonInt64Serializer : KSerializer<Long> {

    override val descriptor = PrimitiveSerialDescriptor("proto3JsonInt64", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        return when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                if (element !is JsonPrimitive) {
                    throw SerializationException(
                        "Expected 64-bit integer as JSON number or string, got: " + element,
                    )
                }
                element.content.trim().toLongOrNull()
                    ?: throw SerializationException(
                        "Expected 64-bit integer as JSON number or string, got: '" + element.content + "'",
                    )
            }
            else -> decoder.decodeLong()
        }
    }

    override fun serialize(encoder: Encoder, value: Long) {
        when (encoder) {
            is JsonEncoder -> encoder.encodeJsonElement(JsonPrimitive(value))
            else -> encoder.encodeLong(value)
        }
    }
}
