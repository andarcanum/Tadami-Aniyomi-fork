package tachiyomi.core.common.util.lang

import java.text.CollationKey
import java.text.Collator
import java.util.Locale

// java.text.Collator is not thread-safe (compare() is not synchronized), so give
// each thread its own instance. Sorting can run on Dispatchers.Default (library pipeline).
private val collators = ThreadLocal.withInitial {
    val locale = Locale.getDefault()
    Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
    }
}

fun String.compareToWithCollator(other: String): Int {
    return collators.get()?.compare(this, other) ?: this.compareTo(other, ignoreCase = true)
}

/**
 * Precomputes an ICU [CollationKey] using the thread-local [Collator].
 * Converting strings once in O(N) before sorting avoids O(N log N) ICU collation parses
 * on large libraries.
 */
fun String.toCollationKey(): CollationKey? {
    return collators.get()?.getCollationKey(this)
}

fun compareCollationKeys(
    leftKey: CollationKey?,
    leftFallback: String,
    rightKey: CollationKey?,
    rightFallback: String,
): Int {
    if (leftKey != null && rightKey != null) {
        return leftKey.compareTo(rightKey)
    }
    return leftFallback.compareToWithCollator(rightFallback)
}
