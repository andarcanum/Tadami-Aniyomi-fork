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
     * Возвращает false для страниц, уже содержащих содержательный текст/статью (где Turnstile
     * может присутствовать лишь как форма для комментариев в подвале).
     */
    val interactiveWidgetProbeJs: String = """
        (function() {
            try {
                var title = (document.title || '').toLowerCase();
                var isChallengeTitle = title.indexOf('just a moment') !== -1 ||
                    title.indexOf('bot verification') !== -1 ||
                    title.indexOf('attention required') !== -1 ||
                    title.indexOf('security verification') !== -1 ||
                    title.indexOf('security check') !== -1 ||
                    title.indexOf('verifying you are human') !== -1 ||
                    title.indexOf('проверк') !== -1;

                var textLength = (document.body ? (document.body.innerText || document.body.textContent || '') : '').trim().length;
                var pCount = document.querySelectorAll('p').length;

                // Если страница уже содержит статьи/главы и заголовок не челлендж - это форма комментариев, а не блокирующий Turnstile
                if (!isChallengeTitle && (textLength >= 300 || pCount >= 3)) {
                    return false;
                }

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
                    title.indexOf('attention required') !== -1 ||
                    title.indexOf('security verification') !== -1 ||
                    title.indexOf('security check') !== -1 ||
                    title.indexOf('verifying you are human') !== -1 ||
                    title.indexOf('проверк') !== -1) {
                    return true;
                }
                if (document.getElementById('challenge-running') !== null ||
                    document.getElementById('challenge-stage') !== null ||
                    document.getElementById('challenge-form') !== null ||
                    document.querySelector('.cf-browser-verification') !== null) {
                    return true;
                }
                var textLength = (document.body ? (document.body.innerText || document.body.textContent || '') : '').trim().length;
                var pCount = document.querySelectorAll('p').length;
                if (textLength >= 300 || pCount >= 3) {
                    return false;
                }
                var html = document.documentElement ? document.documentElement.outerHTML.slice(0, 8192).toLowerCase() : '';
                var markers = ['cf-turnstile', '_cf_chl_opt', 'cf_chl_opt', '/cdn-cgi/challenge-platform/',
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
     * - "ready" если документ готов (readyState complete/interactive), body не пустой и нет активного challenge;
     * - "waiting" если документ ещё рендерится/загружается.
     */
    val pageReadyProbeJs: String = """
        (function() {
            try {
                var title = (document.title || '').toLowerCase();
                var isChallengeTitle = title.indexOf('just a moment') !== -1 ||
                    title.indexOf('bot verification') !== -1 ||
                    title.indexOf('attention required') !== -1 ||
                    title.indexOf('security verification') !== -1 ||
                    title.indexOf('security check') !== -1 ||
                    title.indexOf('verifying you are human') !== -1 ||
                    title.indexOf('проверк') !== -1;

                if (isChallengeTitle) {
                    return 'challenge';
                }

                var hasActiveChallenge = document.getElementById('challenge-running') !== null ||
                    document.getElementById('challenge-stage') !== null ||
                    document.getElementById('challenge-form') !== null ||
                    document.querySelector('.cf-browser-verification') !== null;

                if (hasActiveChallenge) {
                    return 'challenge';
                }

                var isReady = document.readyState === 'complete' || document.readyState === 'interactive';
                var textLength = (document.body ? (document.body.innerText || document.body.textContent || '') : '').trim().length;
                var pCount = document.querySelectorAll('p').length;

                // Страница со статьёй/главой и параграфами готова, даже если в подвале есть форма с Turnstile
                if (isReady && (textLength >= 300 || pCount >= 3)) {
                    return 'ready';
                }

                // Виджет Turnstile на пустой странице означает активную проверку
                var hasTurnstile = document.querySelector('.cf-turnstile, iframe[src*="challenges.cloudflare.com"]') !== null;
                if (hasTurnstile && textLength < 300 && pCount < 2) {
                    return 'challenge';
                }

                if (isReady && textLength >= 50) {
                    return 'ready';
                }

                return 'waiting';
            } catch (_) {
                return 'waiting';
            }
        })();
    """.trimIndent()

    /**
     * Определяет, является ли [body] активной блокирующей страницей Cloudflare challenge,
     * а не легитимной страницей контента со встроенной формой комментариев (где есть data-sitekey / cf-turnstile).
     */
    fun isBlockingChallengePage(body: String, title: String? = null): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase(Locale.ROOT)

        val pageTitle = title?.lowercase(Locale.ROOT) ?: extractTitle(lower)
        if (pageTitle != null && isChallengeTitle(pageTitle)) {
            return true
        }

        val hasActiveChallengeContainer = lower.contains("id=\"challenge-running\"") ||
            lower.contains("id=\"challenge-stage\"") ||
            lower.contains("id=\"challenge-form\"") ||
            lower.contains("class=\"cf-browser-verification\"") ||
            lower.contains("id=\"challenge-error-title\"") ||
            lower.contains("id=\"challenge-error-text\"")
        if (hasActiveChallengeContainer) {
            return true
        }

        // Страница с абзацами текста или объёмным контентом без активного контейнера не является экраном блокировки CF
        val pCount = countParagraphTags(lower)
        val hasSubstantialContent = (lower.length >= 1000 && pCount >= 3) || lower.length >= 8192
        if (hasSubstantialContent && (pageTitle == null || !isChallengeTitle(pageTitle))) {
            return false
        }

        return hasChallengeMarkers(body)
    }

    fun countParagraphTags(html: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            idx = html.indexOf("<p", idx)
            if (idx == -1) break
            val next = html.getOrNull(idx + 2)
            if (next == ' ' || next == '>' || next == '/' || next == '\n' || next == '\r' || next == '\t') {
                count++
            }
            idx += 2
        }
        return count
    }

    private fun extractTitle(lowerHtml: String): String? {
        val start = lowerHtml.indexOf("<title")
        if (start == -1) return null
        val tagEnd = lowerHtml.indexOf('>', start)
        if (tagEnd == -1) return null
        val end = lowerHtml.indexOf("</title>", tagEnd)
        if (end == -1) return null
        return lowerHtml.substring(tagEnd + 1, end).trim()
    }

    fun isChallengeTitle(lowerTitle: String): Boolean {
        return lowerTitle.contains("just a moment") ||
            lowerTitle.contains("bot verification") ||
            lowerTitle.contains("attention required") ||
            lowerTitle.contains("security verification") ||
            lowerTitle.contains("security check") ||
            lowerTitle.contains("проверк") ||
            lowerTitle.contains("verifying you are human")
    }

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
