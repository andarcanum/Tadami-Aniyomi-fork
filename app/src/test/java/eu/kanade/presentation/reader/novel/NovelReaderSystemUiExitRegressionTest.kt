package eu.kanade.presentation.reader.novel

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NovelReaderStatusBarExitRegressionTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `system back exit restores status bars before showing them again`() {
        assertExitRouteRestoresStatusBars(
            installExitRoute = { activity, _ ->
                activity.setContent {
                    BackHandler {
                        activity.setContent { }
                    }

                    showNovelReaderSystemUi()
                }
            },
            triggerExit = { activity, _ ->
                activity.onBackPressedDispatcher.onBackPressed()
            },
        )
    }

    @Test
    fun `in app back affordance exit restores status bars before showing them again`() {
        var invokeBackAffordance: (() -> Unit)? = null

        assertExitRouteRestoresStatusBars(
            installExitRoute = { activity, _ ->
                activity.setContent {
                    invokeBackAffordance = { activity.setContent { } }
                    showNovelReaderSystemUi()
                }
            },
            triggerExit = { _, _ ->
                invokeBackAffordance?.invoke()
            },
        )
    }

    @Test
    fun `navigator pop finish path exit restores status bars before showing them again`() {
        assertExitRouteRestoresStatusBars(
            installExitRoute = { activity, _ ->
                activity.setContent {
                    showNovelReaderSystemUi()
                }
            },
            triggerExit = { _, controller ->
                controller.pause().stop().destroy()
            },
        )
    }

    private fun assertExitRouteRestoresStatusBars(
        installExitRoute: (ComponentActivity, ActivityController<ComponentActivity>) -> Unit,
        triggerExit: (ComponentActivity, ActivityController<ComponentActivity>) -> Unit,
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val insetsController = mockk<androidx.core.view.WindowInsetsControllerCompat>(relaxed = true)

        mockkStatic(WindowCompat::class)
        every { WindowCompat.getInsetsController(activity.window, any()) } returns insetsController
        every { insetsController.isAppearanceLightStatusBars } returns false
        every { insetsController.isAppearanceLightNavigationBars } returns false
        every { insetsController.systemBarsBehavior } returns
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        justRun { insetsController.isAppearanceLightStatusBars = any() }
        justRun { insetsController.isAppearanceLightNavigationBars = any() }
        justRun { insetsController.systemBarsBehavior = any() }

        installExitRoute(activity, controller)

        shadowOf(Looper.getMainLooper()).idle()
        clearMocks(insetsController, answers = false, recordedCalls = true)

        triggerExit(activity, controller)
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.verifyOrder {
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    @Composable
    private fun showNovelReaderSystemUi() {
        SystemUIController(
            fullScreenMode = true,
            keepScreenOn = false,
            showReaderUi = false,
        )
    }
}
