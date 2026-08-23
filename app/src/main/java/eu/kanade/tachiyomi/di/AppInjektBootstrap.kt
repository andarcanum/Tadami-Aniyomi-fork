package eu.kanade.tachiyomi.di

import android.app.Application
import android.os.Build
import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.DomainModule
import eu.kanade.domain.SYDomainModule
import uy.kohesive.injekt.Injekt

/**
 * Registers the Injekt modules the app depends on. Called from [Application.attachBaseContext]
 * so the graph exists before any ContentProvider runs: WorkManager initializes in a provider
 * and can dispatch a pending library-update worker immediately after the previous process was
 * killed (e.g. by the OS under memory pressure), racing ahead of Application.onCreate().
 * Worker constructors resolve from Injekt, so a missing graph crashed them with
 * InjektionException and silently disabled auto-updates (crash log #bug 0.60).
 *
 * Returns whether this is the app's main process. Safe to call twice: onCreate() relies on the
 * attachBaseContext call having already run.
 *
 * [configure] runs right after the fresh DI scope is created and before any module import --
 * a test seam for overriding environment-bound bindings (e.g. the storage folder provider,
 * which needs localized string resources unavailable on the JVM).
 */
fun Application.bootstrapInjektModules(configure: () -> Unit = {}): Boolean {
    val isMainProcess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageName == Application.getProcessName()
    } else {
        true
    }

    patchInjekt()

    configure()

    Injekt.importModule(PreferenceModule(this))
    // Register domain interactors before app managers. Some app managers can be touched by
    // async platform callbacks during AppModule registration, so their domain dependencies
    // must already exist in Injekt.
    Injekt.importModule(DomainModule())
    // SY -->
    Injekt.importModule(SYDomainModule())
    // SY <--
    if (isMainProcess) {
        Injekt.importModule(AppModule(this))
    }

    return isMainProcess
}
