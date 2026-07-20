package eu.kanade.tachiyomi.extension.novel.runtime

import app.cash.quickjs.QuickJs

private const val NATIVE_OBJECT_NAME = "__native"
private const val NATIVE_BRIDGE_NAME = "__nativeBridge"

/**
 * Binds the flat [NovelJsRuntime.NativeApi] bridge into a QuickJS runtime.
 *
 * The whole interface is bound once under [NATIVE_BRIDGE_NAME] (QuickJS marshals
 * String/Int/Boolean/void natively). A generated JS shim then exposes
 * [NATIVE_OBJECT_NAME] with the lenient argument coercion the previous J2V8 binder
 * provided: missing/null required strings become "", nullable strings stay null,
 * and numeric handles are truncated to integers.
 */
fun bindNativeApi(
    runtime: QuickJs,
    nativeApi: NovelJsRuntime.NativeApi,
    compatibilityLogger: CompatibilityLogger,
) {
    runtime.set(
        NATIVE_BRIDGE_NAME,
        NovelJsRuntime.NativeApi::class.java,
        LoggingNativeApi(nativeApi, compatibilityLogger),
    )
    runtime.evaluate(buildNativeBridgeShim(), "novel-js-native-bridge.js")
}

internal enum class BridgeArg(val coercion: String) {
    RequiredString("__s"),
    NullableString("__sn"),
    IntHandle("__i"),
}

internal data class BridgeMethod(val name: String, val args: List<BridgeArg>)

private val S = BridgeArg.RequiredString
private val SN = BridgeArg.NullableString
private val I = BridgeArg.IntHandle

/**
 * One entry per [NovelJsRuntime.NativeApi] method. Arities and coercions mirror the
 * interface signatures; NovelJsRuntimeBinderTest verifies the table stays in sync
 * via reflection when methods are added or removed.
 */
internal val nativeBridgeMethods: List<BridgeMethod> = listOf(
    BridgeMethod("fetch", listOf(S, SN)),
    BridgeMethod("fetchBinary", listOf(S, SN)),
    BridgeMethod("fetchProto", listOf(S, S, SN)),
    BridgeMethod("storageGet", listOf(S)),
    BridgeMethod("storageSet", listOf(S, S)),
    BridgeMethod("storageRemove", listOf(S)),
    BridgeMethod("storageClear", emptyList()),
    BridgeMethod("storageKeys", emptyList()),
    BridgeMethod("localStorageGet", listOf(S)),
    BridgeMethod("localStorageSet", listOf(S, S)),
    BridgeMethod("localStorageRemove", listOf(S)),
    BridgeMethod("localStorageClear", emptyList()),
    BridgeMethod("localStorageKeys", emptyList()),
    BridgeMethod("sessionStorageGet", listOf(S)),
    BridgeMethod("sessionStorageSet", listOf(S, S)),
    BridgeMethod("sessionStorageRemove", listOf(S)),
    BridgeMethod("sessionStorageClear", emptyList()),
    BridgeMethod("sessionStorageKeys", emptyList()),
    BridgeMethod("resolveUrl", listOf(S, SN)),
    BridgeMethod("getPathname", listOf(S)),
    BridgeMethod("select", listOf(S, S)),
    BridgeMethod("aesGcmDecrypt", listOf(S, S, S)),
    BridgeMethod("urlEncode", listOf(S, SN)),
    BridgeMethod("urlDecode", listOf(S, SN)),
    BridgeMethod("domLoad", listOf(S)),
    BridgeMethod("domSelect", listOf(I, S)),
    BridgeMethod("domParent", listOf(I)),
    BridgeMethod("domChildren", listOf(I, SN)),
    BridgeMethod("domNext", listOf(I, SN)),
    BridgeMethod("domPrev", listOf(I, SN)),
    BridgeMethod("domNextAll", listOf(I, SN)),
    BridgeMethod("domPrevAll", listOf(I, SN)),
    BridgeMethod("domSiblings", listOf(I, SN)),
    BridgeMethod("domClosest", listOf(I, S)),
    BridgeMethod("domContents", listOf(I)),
    BridgeMethod("domIs", listOf(I, S)),
    BridgeMethod("domHas", listOf(I, S)),
    BridgeMethod("domNot", listOf(I, S)),
    BridgeMethod("domHtml", listOf(I)),
    BridgeMethod("domOuterHtml", listOf(I)),
    BridgeMethod("domXml", listOf(I)),
    BridgeMethod("domText", listOf(I)),
    BridgeMethod("domAttr", listOf(I, S)),
    BridgeMethod("domSetAttr", listOf(I, S, S)),
    BridgeMethod("domRemoveAttr", listOf(I, S)),
    BridgeMethod("domAttrs", listOf(I)),
    BridgeMethod("domHasClass", listOf(I, S)),
    BridgeMethod("domData", listOf(I, S)),
    BridgeMethod("domVal", listOf(I)),
    BridgeMethod("domTagName", listOf(I)),
    BridgeMethod("domIsTextNode", listOf(I)),
    BridgeMethod("domReplaceWith", listOf(I, S)),
    BridgeMethod("domBefore", listOf(I, S)),
    BridgeMethod("domAfter", listOf(I, S)),
    BridgeMethod("domAppend", listOf(I, S)),
    BridgeMethod("domPrepend", listOf(I, S)),
    BridgeMethod("domEmpty", listOf(I)),
    BridgeMethod("domRemove", listOf(I)),
    BridgeMethod("domAddClass", listOf(I, S)),
    BridgeMethod("domRemoveClass", listOf(I, S)),
    BridgeMethod("domRelease", listOf(I)),
    BridgeMethod("domReleaseAll", emptyList()),
    BridgeMethod("consoleLog", listOf(S)),
    BridgeMethod("consoleError", listOf(S)),
    BridgeMethod("consoleWarn", listOf(S)),
)

internal fun buildNativeBridgeShim(): String {
    val bindings = nativeBridgeMethods.joinToString("\n") { method ->
        val params = List(method.args.size) { index -> "a$index" }.joinToString(", ")
        val coerced = method.args
            .mapIndexed { index, arg -> "${arg.coercion}(a$index)" }
            .joinToString(", ")
        "  native[\"${method.name}\"] = function($params) " +
            "{ return bridge[\"${method.name}\"]($coerced); };"
    }
    return buildString {
        appendLine("(function(global) {")
        appendLine("  var bridge = global.$NATIVE_BRIDGE_NAME;")
        appendLine("  function __s(v) { return v === null || v === undefined ? \"\" : String(v); }")
        appendLine("  function __sn(v) { return v === null || v === undefined ? null : String(v); }")
        appendLine(
            "  function __i(v) { var n = Number(v); " +
                "return isNaN(n) ? 0 : (n < 0 ? Math.ceil(n) : Math.floor(n)); }",
        )
        appendLine("  var native = {};")
        appendLine(bindings)
        appendLine("  global.$NATIVE_OBJECT_NAME = native;")
        append("})(this);")
    }
}

/**
 * Engine-agnostic logging decorator that reproduces the per-operation compatibility
 * logging the previous J2V8 binder performed inline.
 */
internal class LoggingNativeApi(
    private val delegate: NovelJsRuntime.NativeApi,
    private val logger: CompatibilityLogger,
) : NovelJsRuntime.NativeApi by delegate {

    override fun fetch(url: String, optionsJson: String?): String {
        logger.logOperation("fetch", "network", "url=$url")
        return try {
            delegate.fetch(url, optionsJson)
        } catch (e: Exception) {
            logger.logFailure("fetch", "network", "request_failed", e)
            throw e
        }
    }

    override fun fetchBinary(url: String, optionsJson: String?): String {
        logger.logOperation("fetchBinary", "network", "url=$url")
        return delegate.fetchBinary(url, optionsJson)
    }

    override fun storageGet(key: String): String? {
        logger.logOperation("storageGet", "storage", "key=$key")
        return delegate.storageGet(key)
    }

    override fun storageSet(key: String, value: String) {
        logger.logOperation("storageSet", "storage", "key=$key")
        delegate.storageSet(key, value)
    }

    override fun storageRemove(key: String) {
        logger.logOperation("storageRemove", "storage", "key=$key")
        delegate.storageRemove(key)
    }

    override fun storageClear() {
        logger.logOperation("storageClear", "storage")
        delegate.storageClear()
    }

    override fun storageKeys(): String {
        logger.logOperation("storageKeys", "storage")
        return delegate.storageKeys()
    }

    override fun localStorageGet(key: String): String? {
        logger.logOperation("localStorageGet", "storage", "key=$key")
        return delegate.localStorageGet(key)
    }

    override fun localStorageSet(key: String, value: String) {
        logger.logOperation("localStorageSet", "storage", "key=$key")
        delegate.localStorageSet(key, value)
    }

    override fun localStorageRemove(key: String) {
        logger.logOperation("localStorageRemove", "storage", "key=$key")
        delegate.localStorageRemove(key)
    }

    override fun localStorageClear() {
        logger.logOperation("localStorageClear", "storage")
        delegate.localStorageClear()
    }

    override fun localStorageKeys(): String {
        logger.logOperation("localStorageKeys", "storage")
        return delegate.localStorageKeys()
    }

    override fun sessionStorageGet(key: String): String? {
        logger.logOperation("sessionStorageGet", "storage", "key=$key")
        return delegate.sessionStorageGet(key)
    }

    override fun sessionStorageSet(key: String, value: String) {
        logger.logOperation("sessionStorageSet", "storage", "key=$key")
        delegate.sessionStorageSet(key, value)
    }

    override fun sessionStorageRemove(key: String) {
        logger.logOperation("sessionStorageRemove", "storage", "key=$key")
        delegate.sessionStorageRemove(key)
    }

    override fun sessionStorageClear() {
        logger.logOperation("sessionStorageClear", "storage")
        delegate.sessionStorageClear()
    }

    override fun sessionStorageKeys(): String {
        logger.logOperation("sessionStorageKeys", "storage")
        return delegate.sessionStorageKeys()
    }

    override fun domLoad(html: String): Int {
        logger.logOperation("domLoad", "dom")
        return delegate.domLoad(html)
    }
}
