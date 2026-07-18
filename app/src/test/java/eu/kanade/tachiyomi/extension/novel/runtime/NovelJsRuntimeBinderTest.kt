package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelJsRuntimeBinderTest {

    private val interfaceMethods: Map<String, Int> =
        NovelJsRuntime.NativeApi::class.java.declaredMethods
            .associate { it.name to it.parameterCount }

    @Test
    fun `bridge table covers every native api method exactly once`() {
        val tableNames = nativeBridgeMethods.map { it.name }

        tableNames.toSet() shouldBe interfaceMethods.keys
        tableNames.size shouldBe interfaceMethods.size
    }

    @Test
    fun `bridge table arities match native api signatures`() {
        nativeBridgeMethods.forEach { method ->
            method.args.size shouldBe interfaceMethods.getValue(method.name)
        }
    }

    @Test
    fun `shim exposes a coercing native object backed by the bridge`() {
        val shim = buildNativeBridgeShim()

        shim shouldContain "var bridge = global.__nativeBridge;"
        shim shouldContain "global.__native = native;"
        shim shouldContain "function __s(v)"
        shim shouldContain "function __sn(v)"
        shim shouldContain "function __i(v)"
        nativeBridgeMethods.forEach { method ->
            shim shouldContain "native[\"${method.name}\"]"
        }
    }

    @Test
    fun `shim keeps legacy argument coercion semantics`() {
        val shim = buildNativeBridgeShim()

        // Required strings default to "", nullable strings stay null, handles become ints.
        shim shouldContain
            "native[\"fetch\"] = function(a0, a1) { return bridge[\"fetch\"](__s(a0), __sn(a1)); };"
        shim shouldContain
            "native[\"domSelect\"] = function(a0, a1) { return bridge[\"domSelect\"](__i(a0), __s(a1)); };"
        shim shouldContain
            "native[\"fetchProto\"] = function(a0, a1, a2) " +
            "{ return bridge[\"fetchProto\"](__s(a0), __s(a1), __sn(a2)); };"
        shim shouldContain
            "native[\"domReleaseAll\"] = function() { return bridge[\"domReleaseAll\"](); };"
        shim shouldContain "return v === null || v === undefined ? \"\" : String(v);"
        shim shouldContain "return v === null || v === undefined ? null : String(v);"
    }
}
