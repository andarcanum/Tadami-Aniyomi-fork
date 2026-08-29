@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package kotlinx.serialization.internal

import kotlinx.serialization.KSerializer

/**
 * ABI compatibility shim for extensions compiled with D8 interface desugaring (minSdk < 24)
 * against kotlinx-serialization 1.8.0+ as a compileOnly dependency (JetBrains KT-84952).
 *
 * Those extension builds desugar Java 8 default interface methods into static calls on a
 * synthetic companion class `kotlinx.serialization.internal.GeneratedSerializer$-CC`.
 * Tadami ships with minSdk 26, so the app DEX never contains that class (ART handles interface
 * default methods natively) and extensions crash on load with
 * `NoClassDefFoundError: Failed resolution of: Lkotlinx/serialization/internal/GeneratedSerializer$-CC`
 * wrapped in IncompatibleExtensionException.
 *
 * The static methods below satisfy that linkage. Both names are provided because desugaring
 * layouts emit either `typeParametersSerializers` or its `$default$` bridge depending on the
 * D8/interface-embodied shape. Ordinary serializers have no type parameters, so returning an
 * empty array matches what the real default method does (PluginHelperInterfacesKt.EMPTY_SERIALIZER_ARRAY,
 * which is `internal` and therefore not referenced from here on purpose).
 *
 * Kept from R8 shrinking by app/proguard-rules.pro (kotlinx.serialization.** plus an explicit rule);
 * resolving through ChildFirstPathClassLoader -> app parent at runtime.
 */
@Suppress("ClassName", "FunctionName", "UNUSED_PARAMETER")
public class `GeneratedSerializer$-CC` {
    public companion object {
        /** D8 emits this for `GeneratedSerializer.typeParametersSerializers()` default-method call sites. */
        @JvmStatic
        public fun typeParametersSerializers(_this: GeneratedSerializer<*>): Array<KSerializer<*>> {
            return arrayOf()
        }

        /** Synthetic `$default` bridge some desugaring layouts also link against. */
        @JvmStatic
        public fun `$default$typeParametersSerializers`(_this: GeneratedSerializer<*>): Array<KSerializer<*>> {
            return arrayOf()
        }
    }
}
