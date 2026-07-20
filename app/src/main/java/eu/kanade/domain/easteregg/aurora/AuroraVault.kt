package eu.kanade.domain.easteregg.aurora

import android.util.Base64
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Одна ступень квеста. В коде НЕТ ни ответа, ни награды:
 * только соль, контрольный хеш ключа и AES-GCM-шифртекст.
 * Подобрать ответ анализом кодовой базы невозможно — его можно только знать.
 */
data class AuroraStage(
    val salt: String,
    val iv: String,
    val check: String,
    val data: String,
)

object AuroraVault {

    private const val ITERATIONS = 120_000

    /**
     * Нормализация ввода. ДОЛЖНА бит в бит совпадать с normalize()
     * в tools/aurora_forge.mjs: NFC, lowercase, ё→е, схлопывание пробелов.
     */
    fun normalize(input: String): String {
        val base = Normalizer.normalize(input, Normalizer.Form.NFC)
            .lowercase()
            .replace('ё', 'е')
            .trim()
            .replace(Regex("\\s+"), " ")

        if (base in listOf(
                "hour of the wolf",
                "the hour of the wolf",
                "wolf hour",
                "the wolf hour",
                "wolfs hour",
                "wolf's hour",
                "3 am",
                "3:00 am",
                "3:00",
                "3.00 am",
                "3.00",
                "three am",
                "three o'clock",
            )
        ) {
            return "час волка"
        }
        return base
    }

    /**
     * Пробует открыть ступень фразой. Возвращает расшифрованный payload
     * или null, если фраза неверна. Ложное срабатывание исключено:
     * даже при коллизии контрольного хеша GCM-тег не сойдётся.
     */
    fun tryOpen(phrase: String, stage: AuroraStage): ByteArray? {
        val normalized = normalize(phrase)
        if (normalized.isEmpty()) return null
        val salt = Base64.decode(stage.salt, Base64.DEFAULT)
        val key = pbkdf2(normalized.encodeToByteArray(), salt, ITERATIONS, 32)
        val check = MessageDigest.getInstance("SHA-256").digest(key)
        if (!MessageDigest.isEqual(check, Base64.decode(stage.check, Base64.DEFAULT))) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, Base64.decode(stage.iv, Base64.DEFAULT)),
            )
            cipher.doFinal(Base64.decode(stage.data, Base64.DEFAULT))
        }.getOrNull()
    }

    /**
     * PBKDF2-HMAC-SHA256 вручную поверх javax.crypto.Mac — чтобы поведение
     * с UTF-8 (кириллицей) было идентичным на всех версиях Android
     * и совпадало с crypto.pbkdf2Sync в генераторе.
     */
    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val blockCount = (keyLen + 31) / 32
        val out = ByteArray(blockCount * 32)
        for (block in 1..blockCount) {
            mac.reset()
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte(),
                ),
            )
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * 32, 32)
        }
        return out.copyOf(keyLen)
    }
}
