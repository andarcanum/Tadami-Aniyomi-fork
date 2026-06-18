package eu.kanade.tachiyomi.extension.novel.runtime

import com.eclipsesource.v8.JavaCallback
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object

private const val NATIVE_OBJECT_NAME = "__native"

@Suppress("DEPRECATION")
fun bindNativeApi(
    runtime: V8,
    nativeApi: NovelJsRuntime.NativeApi,
    compatibilityLogger: CompatibilityLogger,
) {
    val nativeObject = V8Object(runtime)

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return run {
                    val url = parameters.stringArg(0)
                    compatibilityLogger.logOperation("fetch", "network", "url=$url")
                    try {
                        nativeApi.fetch(url, parameters.stringArgOrNull(1))
                    } catch (e: Exception) {
                        compatibilityLogger.logFailure("fetch", "network", "request_failed", e)
                        throw e
                    }
                }
            }
        },
        "fetch",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val url = parameters.stringArg(0)
                compatibilityLogger.logOperation("fetchBinary", "network", "url=$url")
                return nativeApi.fetchBinary(url, parameters.stringArgOrNull(1))
            }
        },
        "fetchBinary",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.fetchProto(
                    parameters.stringArg(0),
                    parameters.stringArg(1),
                    parameters.stringArgOrNull(2),
                )
            }
        },
        "fetchProto",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("storageGet", "storage", "key=$key")
                return nativeApi.storageGet(key)
            }
        },
        "storageGet",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("storageSet", "storage", "key=$key")
                nativeApi.storageSet(key, parameters.stringArg(1))
                return null
            }
        },
        "storageSet",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("storageRemove", "storage", "key=$key")
                nativeApi.storageRemove(key)
                return null
            }
        },
        "storageRemove",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                compatibilityLogger.logOperation("storageClear", "storage")
                nativeApi.storageClear()
                return null
            }
        },
        "storageClear",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                compatibilityLogger.logOperation("storageKeys", "storage")
                return nativeApi.storageKeys()
            }
        },
        "storageKeys",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("localStorageGet", "storage", "key=$key")
                return nativeApi.localStorageGet(key)
            }
        },
        "localStorageGet",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("localStorageSet", "storage", "key=$key")
                nativeApi.localStorageSet(key, parameters.stringArg(1))
                return null
            }
        },
        "localStorageSet",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("localStorageRemove", "storage", "key=$key")
                nativeApi.localStorageRemove(key)
                return null
            }
        },
        "localStorageRemove",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                compatibilityLogger.logOperation("localStorageClear", "storage")
                nativeApi.localStorageClear()
                return null
            }
        },
        "localStorageClear",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                compatibilityLogger.logOperation("localStorageKeys", "storage")
                return nativeApi.localStorageKeys()
            }
        },
        "localStorageKeys",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("sessionStorageGet", "storage", "key=$key")
                return nativeApi.sessionStorageGet(key)
            }
        },
        "sessionStorageGet",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("sessionStorageSet", "storage", "key=$key")
                nativeApi.sessionStorageSet(key, parameters.stringArg(1))
                return null
            }
        },
        "sessionStorageSet",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                val key = parameters.stringArg(0)
                compatibilityLogger.logOperation("sessionStorageRemove", "storage", "key=$key")
                nativeApi.sessionStorageRemove(key)
                return null
            }
        },
        "sessionStorageRemove",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                compatibilityLogger.logOperation("sessionStorageClear", "storage")
                nativeApi.sessionStorageClear()
                return null
            }
        },
        "sessionStorageClear",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                compatibilityLogger.logOperation("sessionStorageKeys", "storage")
                return nativeApi.sessionStorageKeys()
            }
        },
        "sessionStorageKeys",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.resolveUrl(
                    parameters.stringArg(0),
                    parameters.stringArgOrNull(1),
                )
            }
        },
        "resolveUrl",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.getPathname(parameters.stringArg(0))
            }
        },
        "getPathname",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.select(
                    parameters.stringArg(0),
                    parameters.stringArg(1),
                )
            }
        },
        "select",
    )

    // DOM Store methods
    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                compatibilityLogger.logOperation("domLoad", "dom")
                return nativeApi.domLoad(parameters.stringArg(0))
            }
        },
        "domLoad",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domSelect(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domSelect",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domParent(parameters.intArg(0))
            }
        },
        "domParent",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domChildren(parameters.intArg(0), parameters.stringArgOrNull(1))
            }
        },
        "domChildren",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domNext(parameters.intArg(0), parameters.stringArgOrNull(1))
            }
        },
        "domNext",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domPrev(parameters.intArg(0), parameters.stringArgOrNull(1))
            }
        },
        "domPrev",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domNextAll(parameters.intArg(0), parameters.stringArgOrNull(1))
            }
        },
        "domNextAll",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domPrevAll(parameters.intArg(0), parameters.stringArgOrNull(1))
            }
        },
        "domPrevAll",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domSiblings(parameters.intArg(0), parameters.stringArgOrNull(1))
            }
        },
        "domSiblings",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domClosest(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domClosest",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domContents(parameters.intArg(0))
            }
        },
        "domContents",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domIs(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domIs",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domHas(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domHas",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domNot(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domNot",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domHtml(parameters.intArg(0))
            }
        },
        "domHtml",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domOuterHtml(parameters.intArg(0))
            }
        },
        "domOuterHtml",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domXml(parameters.intArg(0))
            }
        },
        "domXml",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domText(parameters.intArg(0))
            }
        },
        "domText",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domAttr(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domAttr",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domSetAttr(parameters.intArg(0), parameters.stringArg(1), parameters.stringArg(2))
                return receiver
            }
        },
        "domSetAttr",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domAttrs(parameters.intArg(0))
            }
        },
        "domAttrs",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domHasClass(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domHasClass",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domData(parameters.intArg(0), parameters.stringArg(1))
            }
        },
        "domData",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domVal(parameters.intArg(0))
            }
        },
        "domVal",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domTagName(parameters.intArg(0))
            }
        },
        "domTagName",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.domIsTextNode(parameters.intArg(0))
            }
        },
        "domIsTextNode",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domReplaceWith(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domReplaceWith",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domBefore(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domBefore",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domAfter(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domAfter",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domAppend(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domAppend",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domPrepend(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domPrepend",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domEmpty(parameters.intArg(0))
                return null
            }
        },
        "domEmpty",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domRemove(parameters.intArg(0))
                return null
            }
        },
        "domRemove",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domAddClass(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domAddClass",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domRemoveAttr(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domRemoveAttr",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domRemoveClass(parameters.intArg(0), parameters.stringArg(1))
                return null
            }
        },
        "domRemoveClass",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.domRelease(parameters.intArg(0))
                return null
            }
        },
        "domRelease",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, @Suppress("UNUSED_PARAMETER") parameters: V8Array): Any? {
                nativeApi.domReleaseAll()
                return null
            }
        },
        "domReleaseAll",
    )

    // Crypto
    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                return nativeApi.aesGcmDecrypt(
                    parameters.stringArg(0),
                    parameters.stringArg(1),
                    parameters.stringArg(2),
                )
            }
        },
        "__aesGcmDecrypt",
    )

    // Console methods
    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.consoleLog(parameters.stringArg(0))
                return null
            }
        },
        "consoleLog",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.consoleError(parameters.stringArg(0))
                return null
            }
        },
        "consoleError",
    )

    nativeObject.registerJavaMethod(
        object : JavaCallback {
            override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                nativeApi.consoleWarn(parameters.stringArg(0))
                return null
            }
        },
        "consoleWarn",
    )

    runtime.add(NATIVE_OBJECT_NAME, nativeObject)
    nativeObject.release()
}
