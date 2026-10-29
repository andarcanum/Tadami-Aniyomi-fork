package eu.kanade.presentation.reader.novel

/**
 * Renderer-independent view layer for book mode.
 *
 * Book mode used to be hard-wired to the WebView because the reader talked to it with raw JavaScript.
 * The book itself (spine, sections, progress) knows nothing about pixels or WebViews, so the renderer
 * is reduced to this small surface: place the reader at a location, step a page, and switch flow. Any
 * renderer that can do those three things can present the book.
 */
internal enum class NovelBookFlow {
    /** One continuous scroll. */
    SCROLLED,

    /** Discrete pages, laid out as one column per viewport (the model foliate/readest use). */
    PAGINATED,
}

/** Renderer that presents the book, plus the flow it implies. */
internal enum class NovelBookRenderer(
    val flow: NovelBookFlow,
    val usesWebView: Boolean,
) {
    WEBVIEW_SCROLLED(flow = NovelBookFlow.SCROLLED, usesWebView = true),
    WEBVIEW_PAGINATED(flow = NovelBookFlow.PAGINATED, usesWebView = true),
    RICH_NATIVE_SCROLLED(flow = NovelBookFlow.SCROLLED, usesWebView = false),
}

/**
 * Whether the native book renderer is mounted in the reader.
 *
 * The native list consumes the same book command stream as the WebView document, so book mode works
 * on both renderers. The flag stays as an explicit kill switch: if the native list ever regresses,
 * book mode falls back to the WebView renderer without touching the policy or the reader.
 */
internal const val NOVEL_BOOK_NATIVE_RENDERER_READY = true

/**
 * Why book mode is presented by the WebView instead of the native renderer.
 *
 * The fallback used to be silent, so a reader that had enabled the native renderer simply saw a
 * different presentation with no explanation. The decision now carries its reason so the reader can
 * show it.
 */
internal enum class NovelBookRendererReason {
    /** The reader is configured for pages, which the paged WebView flow presents. */
    PAGE_MODE,

    /** The native renderer was not requested. */
    NATIVE_NOT_REQUESTED,

    /** The native renderer is not mounted in this build. */
    NATIVE_UNAVAILABLE,

    /** Bionic reading is applied to the WebView document only. */
    BIONIC_READING,

    /** Custom reader CSS or JavaScript only has meaning inside the WebView document. */
    CUSTOM_STYLES,

    /** The content uses features the native renderer cannot express. */
    UNSUPPORTED_CONTENT,

    /** The user explicitly opted into the WebView reader in preferences. */
    USER_WEBVIEW,

    /** The native renderer presents the book; nothing was given up. */
    NONE,
}

/** The chosen renderer together with the reason it was chosen. */
internal data class NovelBookRendererDecision(
    val renderer: NovelBookRenderer,
    val reason: NovelBookRendererReason,
) {
    /**
     * True when the reader asked for the native renderer and got the WebView anyway, which is the
     * only case worth telling the reader about.
     */
    val isSilentFallback: Boolean
        get() = reason == NovelBookRendererReason.BIONIC_READING ||
            reason == NovelBookRendererReason.CUSTOM_STYLES ||
            reason == NovelBookRendererReason.UNSUPPORTED_CONTENT
}

/**
 * Picks the renderer for book mode from the reader settings.
 *
 * Book mode is no longer WebView-only: "pages" maps to the paginated book flow, and the experimental
 * native renderer presents the same book as a native scroll. Bionic reading and content the native
 * renderer cannot express still fall back to the WebView, exactly like in chapter-by-chapter mode.
 */
internal fun resolveNovelBookRenderer(
    pageReaderEnabled: Boolean,
    richNativeRendererExperimentalEnabled: Boolean,
    bionicReadingEnabled: Boolean,
    richContentUnsupportedFeaturesDetected: Boolean,
    customStylesPresent: Boolean = false,
    nativeBookRendererAvailable: Boolean = NOVEL_BOOK_NATIVE_RENDERER_READY,
    preferWebViewRenderer: Boolean = false,
): NovelBookRenderer = resolveNovelBookRendererDecision(
    pageReaderEnabled = pageReaderEnabled,
    richNativeRendererExperimentalEnabled = richNativeRendererExperimentalEnabled,
    bionicReadingEnabled = bionicReadingEnabled,
    richContentUnsupportedFeaturesDetected = richContentUnsupportedFeaturesDetected,
    customStylesPresent = customStylesPresent,
    nativeBookRendererAvailable = nativeBookRendererAvailable,
    preferWebViewRenderer = preferWebViewRenderer,
).renderer

/**
 * The single rule that picks the book renderer, stated once as an ordered list of reasons.
 *
 * Every branch names why it was taken, so the reader can explain a fallback instead of quietly
 * changing the presentation under the user.
 */
internal fun resolveNovelBookRendererDecision(
    pageReaderEnabled: Boolean,
    richNativeRendererExperimentalEnabled: Boolean,
    bionicReadingEnabled: Boolean,
    richContentUnsupportedFeaturesDetected: Boolean,
    customStylesPresent: Boolean = false,
    nativeBookRendererAvailable: Boolean = NOVEL_BOOK_NATIVE_RENDERER_READY,
    preferWebViewRenderer: Boolean = false,
): NovelBookRendererDecision {
    // Pages are a presentation choice, not a fallback: the paged flow is a first-class book flow and
    // now stitches sections just like the scrolled one.
    if (pageReaderEnabled) {
        return NovelBookRendererDecision(
            renderer = NovelBookRenderer.WEBVIEW_PAGINATED,
            reason = NovelBookRendererReason.PAGE_MODE,
        )
    }
    if (preferWebViewRenderer) {
        return NovelBookRendererDecision(
            renderer = NovelBookRenderer.WEBVIEW_SCROLLED,
            reason = NovelBookRendererReason.USER_WEBVIEW,
        )
    }
    val reason = when {
        !richNativeRendererExperimentalEnabled -> NovelBookRendererReason.NATIVE_NOT_REQUESTED
        !nativeBookRendererAvailable -> NovelBookRendererReason.NATIVE_UNAVAILABLE
        bionicReadingEnabled -> NovelBookRendererReason.BIONIC_READING
        customStylesPresent -> NovelBookRendererReason.CUSTOM_STYLES
        richContentUnsupportedFeaturesDetected -> NovelBookRendererReason.UNSUPPORTED_CONTENT
        else -> NovelBookRendererReason.NONE
    }
    return NovelBookRendererDecision(
        renderer = if (reason == NovelBookRendererReason.NONE) {
            NovelBookRenderer.RICH_NATIVE_SCROLLED
        } else {
            NovelBookRenderer.WEBVIEW_SCROLLED
        },
        reason = reason,
    )
}

/** A position inside the book, expressed without any renderer units. */
internal data class NovelBookViewLocation(
    val sectionIndex: Int,
    val sectionFraction: Float,
)

/**
 * Document identity used while book mode is active. The reader keeps a single document alive for
 * the whole novel, so it must not be reloaded when the current chapter changes.
 */
internal const val BOOK_MODE_DOCUMENT_TAG = "an-book-document"
