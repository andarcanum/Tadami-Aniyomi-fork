package eu.kanade.tachiyomi.test

/**
 * Fails when [pattern] has a literal `}` outside a character class that is not escaped.
 *
 * The desktop JVM regex engine accepts such a brace, but Android's ICU engine rejects it with a
 * PatternSyntaxException at compile time, so JVM-only tests cannot catch this device-only failure.
 */
fun assertIcuSafeRegexPattern(pattern: String) {
    var inCharClass = false
    var escaped = false
    pattern.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            char == '[' -> inCharClass = true
            char == ']' -> inCharClass = false
            char == '}' && !inCharClass ->
                throw AssertionError("Unescaped '}' at index $index in $pattern")
        }
    }
}
