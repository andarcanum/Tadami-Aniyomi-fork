@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package kotlinx.serialization.internal

import kotlinx.serialization.KSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * Guards the ABI shim that satisfies the linkage of extensions compiled with D8 interface
 * desugaring against kotlinx-serialization 1.8.x (KT-84952). If the GeneratedSerializer$-CC
 * class or its static methods change shape, extensions fail at runtime with
 * NoClassDefFoundError "Failed resolution of: Lkotlinx/serialization/internal/GeneratedSerializer$-CC"
 * even though the app itself compiles fine - these reflections make the contract explicit.
 */
class GeneratedSerializerCcAbiTest {

    private val ccClass = Class.forName("kotlinx.serialization.internal.GeneratedSerializer$-CC")

    private val serializerArrayType = Class.forName("[Lkotlinx.serialization.KSerializer;")

    // A non-null receiver whose abstract members are never touched by the shim body.
    private fun sampleSerializer(): GeneratedSerializer<*> {
        val handler = InvocationHandler { _, _, _ -> null }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            GeneratedSerializer::class.java.classLoader,
            arrayOf(GeneratedSerializer::class.java),
            handler,
        ) as GeneratedSerializer<*>
    }

    @Test
    fun typeParametersSerializersIsStaticDesugaredEntryPoint() {
        val method = ccClass.getMethod(
            "typeParametersSerializers",
            GeneratedSerializer::class.java,
        )
        assertTrue(Modifier.isStatic(method.modifiers), "extension DEX calls it via invokestatic")
        assertEquals(serializerArrayType, method.returnType)
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(null, sampleSerializer()) as Array<KSerializer<*>>
        assertEquals(0, result.size, "default behavior returns an empty serializer array")
    }

    @Test
    fun dollarDefaultBridgeIsStaticDesugaredEntryPoint() {
        val method = ccClass.getMethod(
            "\$default\$typeParametersSerializers",
            GeneratedSerializer::class.java,
        )
        assertTrue(Modifier.isStatic(method.modifiers))
        assertEquals(serializerArrayType, method.returnType)
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(null, sampleSerializer()) as Array<KSerializer<*>>
        assertEquals(0, result.size)
    }
}
