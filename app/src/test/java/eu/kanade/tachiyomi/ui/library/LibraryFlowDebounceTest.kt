package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LibraryFlowDebounceTest {

    @Test
    fun `leading value emits immediately and later values debounce`() = runTest {
        val input = MutableStateFlow("a")
        val output = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            input.leadingDebounce(250).toList(output)
        }

        // F3: the first value must not wait for the timeout (initial library render).
        runCurrent()
        output shouldBe listOf("a")

        input.value = "ab"
        input.value = "abc"
        advanceTimeBy(100)
        runCurrent()
        output shouldBe listOf("a")

        advanceTimeBy(200)
        runCurrent()
        output shouldBe listOf("a", "abc")

        job.cancel()
    }

    @Test
    fun `resubscription emits the current value immediately again`() = runTest {
        val input = MutableStateFlow("x")
        val output = mutableListOf<String>()

        val first = launch(UnconfinedTestDispatcher(testScheduler)) {
            input.leadingDebounce(250).toList(output)
        }
        runCurrent()
        first.cancel()

        val second = launch(UnconfinedTestDispatcher(testScheduler)) {
            input.leadingDebounce(250).toList(output)
        }
        runCurrent()
        output shouldBe listOf("x", "x")
        second.cancel()
    }
}
