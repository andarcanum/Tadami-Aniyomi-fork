package eu.kanade.tachiyomi.network

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class HttpExceptionAbiContractTest {

    @Test
    fun `HttpException preserves extensions-lib ABI contract`() {
        val exception = HttpException(404)

        // Must inherit from IllegalStateException for extension exception catching
        exception.shouldBeInstanceOf<IllegalStateException>()
        exception.message shouldBe "HTTP error 404"

        // Bytecode ABI verification: getCode() must exist as a virtual method with int return
        val getCodeMethod = HttpException::class.java.getMethod("getCode")
        getCodeMethod.returnType shouldBe Int::class.javaPrimitiveType

        val codeValue = getCodeMethod.invoke(exception) as Int
        codeValue shouldBe 404
    }
}
