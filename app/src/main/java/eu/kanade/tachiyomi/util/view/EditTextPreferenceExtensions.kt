@file:Suppress("PackageDirectoryMismatch", "EXTENSION_SHADOWED_BY_MEMBER")

package androidx.preference

/**
 * Returns package-private [EditTextPreference.getOnBindEditTextListener]
 */
fun EditTextPreference.getOnBindEditTextListener(): EditTextPreference.OnBindEditTextListener? {
    return onBindEditTextListener
}
