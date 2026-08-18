package tachiyomi.core.common.util.lang

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
