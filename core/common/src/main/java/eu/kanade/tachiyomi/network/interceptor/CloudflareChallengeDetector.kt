package eu.kanade.tachiyomi.network.interceptor

import java.util.Locale

/**
 * Чистый (без Android/WebView-зависимостей) классификатор ответов Cloudflare.
 *
 * Вынесен отдельно от [CloudflareInterceptor], чтобы:
 *  - логика детектирования покрывалась модульными тестами без эмулятора;
 *  - один и тот же набор маркеров использовался везде (интерцептор + резолвер).
 *
 * Раньше интерцептор ловил всего два маркера (`challenge-error-title` /
 * `challenge-error-text`) — это маркеры СТРАНИЦЫ ОШИБКИ (challenge провален),
 * а не самого интерстишала «Just a moment…». Из-за этого классические
 * JS-челленджи без `cf-mitigated` могли не распознаться. Здесь набор
 * маркеров расширен и разделён по типам challenge.
 */
object CloudflareChallengeDetector {

    private val cloudflareServers = setOf("cloudflare", "cloudflare-nginx")

    /**
     * Интерактивный challenge (Turnstile / managed challenge с виджетом):
     * требует действия человека, автоматически не пройдётся.
     */
    val interactiveMarkers = listOf(
        "cf-turnstile",
        "challenges.cloudflare.com",
        "data-sitekey",
    )

    /**
     * Неинтерактивный интерстишал («Just a moment…», JS-челлендж):
     * проходится автоматически в WebView.
     */
    val interstitialMarkers = listOf(
        "_cf_chl_opt",
        "cf_chl_opt",
        "/cdn-cgi/challenge-platform/",
        "challenge-platform",
        "cf-browser-verification",
        "cf-challenge-running",
        "just a moment",
        "проверки безопасности",
        "проверка безопасности",
        "выполнение проверки",
    )

    /**
     * Страница ошибки challenge (челлендж провален/сломан).
     */
    val errorMarkers = listOf(
        "challenge-error-title",
        "challenge-error-text",
    )

    private val allMarkers = interactiveMarkers + interstitialMarkers + errorMarkers

    fun isCloudflareServer(server: String?): Boolean =
        server != null && server.lowercase(Locale.ROOT) in cloudflareServers

    /** `cf-mitigated: challenge` — авторитетный признак managed challenge. */
    fun isManagedChallenge(cfMitigated: String?): Boolean =
        cfMitigated?.trim()?.equals("challenge", ignoreCase = true) == true

    /** Есть ли в теле любой маркер challenge (интерактивный/интерстишал/ошибка). */
    fun hasChallengeMarkers(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return allMarkers.any { it in lower }
    }

    /** Есть ли в теле маркер именно интерактивного («человеческого») challenge. */
    fun hasInteractiveMarkers(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return interactiveMarkers.any { it in lower }
    }

    /**
     * P7: JS-проба наличия виджета человеческой верификации (Turnstile и т.п.) - общий
     * источник для фонового резолвера и ручного WebView-экрана.
     */
    val interactiveWidgetProbeJs: String = """
        (function() {
            try {
                return document.querySelector('.cf-turnstile, [data-sitekey], iframe[src*="challenges.cloudflare.com"]') != null;
            } catch (_) {
                return false;
            }
        })();
    """.trimIndent()

    /**
     * JS-проба наличия маркеров Cloudflare challenge (интерстишал / Turnstile / ошибка)
     * в текущем DOM WebView. Единый источник истины для резолвера и WebView fetch bridge.
     */
    val challengeMarkersProbeJs: String = """
        (function() {
            try {
                var title = (document.title || '').toLowerCase();
                if (title.indexOf('just a moment') !== -1 ||
                    title.indexOf('bot verification') !== -1 ||
                    title.indexOf('redirecting') !== -1 ||
                    title.indexOf('you are being redirected') !== -1 ||
                    title.indexOf('проверк') !== -1 ||
                    title.indexOf('security verification') !== -1) {
                    return true;
                }
                var html = document.documentElement ? document.documentElement.outerHTML.slice(0, 8192).toLowerCase() : '';
                var markers = ['cf-turnstile', 'challenges.cloudflare.com', 'data-sitekey',
                    '_cf_chl_opt', 'cf_chl_opt', '/cdn-cgi/challenge-platform/',
                    'challenge-platform', 'cf-browser-verification', 'cf-challenge-running',
                    'challenge-error-title', 'challenge-error-text', 'just a moment', 'проверк'];
                for (var i = 0; i < markers.length; i++) {
                    if (html.indexOf(markers[i]) !== -1) return true;
                }
                return false;
            } catch (_) {
                return true;
            }
        })();
    """.trimIndent()

    /**
     * JS-проба готовности контентной страницы в WebView:
     * - "challenge" если на странице есть активный Cloudflare challenge / Turnstile;
     * - "ready" если документ готов (readyState complete/interactive), body не пустой и нет маркеров challenge;
     * - "waiting" если документ ещё рендерится/загружается.
     */
    val pageReadyProbeJs: String = """
        (function() {
            try {
                var title = (document.title || '').toLowerCase();
                if (title.indexOf('just a moment') !== -1 ||
                    title.indexOf('bot verification') !== -1 ||
                    title.indexOf('redirecting') !== -1 ||
                    title.indexOf('you are being redirected') !== -1 ||
                    title.indexOf('проверк') !== -1 ||
                    title.indexOf('security verification') !== -1) {
                    return 'challenge';
                }
                var html = document.documentElement ? document.documentElement.outerHTML.slice(0, 8192).toLowerCase() : '';
                var markers = ['cf-turnstile', 'challenges.cloudflare.com', 'data-sitekey',
                    '_cf_chl_opt', 'cf_chl_opt', '/cdn-cgi/challenge-platform/',
                    'challenge-platform', 'cf-browser-verification', 'cf-challenge-running',
                    'challenge-error-title', 'challenge-error-text', 'just a moment', 'проверк'];
                for (var i = 0; i < markers.length; i++) {
                    if (html.indexOf(markers[i]) !== -1) return 'challenge';
                }
                var isReady = document.readyState === 'complete' || document.readyState === 'interactive';
                var bodyLength = document.body ? document.body.innerHTML.length : 0;
                if (isReady && bodyLength >= 50) {
                    return 'ready';
                }
                return 'waiting';
            } catch (_) {
                return 'challenge';
            }
        })();
    """.trimIndent()

    /**
     * Полная классификация ответа. `bodyPeek` — первые килобайты тела
     * (маркеры challenge всегда вверху документа).
     */
    fun classify(
        code: Int,
        server: String?,
        cfMitigated: String?,
        bodyPeek: String,
    ): CloudflareChallengeType {
        if (code !in ERROR_CODES || !isCloudflareServer(server)) {
            return CloudflareChallengeType.NONE
        }
        val lower = bodyPeek.lowercase(Locale.ROOT)
        if (interactiveMarkers.any { it in lower }) {
            return CloudflareChallengeType.INTERACTIVE
        }
        if (isManagedChallenge(cfMitigated)) {
            return CloudflareChallengeType.MANAGED
        }
        if (interstitialMarkers.any { it in lower }) {
            return CloudflareChallengeType.INTERSTITIAL
        }
        if (errorMarkers.any { it in lower }) {
            return CloudflareChallengeType.ERROR
        }
        return CloudflareChallengeType.NONE
    }
}

enum class CloudflareChallengeType {
    /** Не challenge (обычный ответ либо не-Cloudflare). */
    NONE,

    /** JS-интерстишал «Just a moment…» — проходится автоматически. */
    INTERSTITIAL,

    /** Managed challenge (`cf-mitigated: challenge`) без явного виджета. */
    MANAGED,

    /** Turnstile / интерактивный виджет — требует действия человека. */
    INTERACTIVE,

    /** Страница ошибки challenge. */
    ERROR,
}
