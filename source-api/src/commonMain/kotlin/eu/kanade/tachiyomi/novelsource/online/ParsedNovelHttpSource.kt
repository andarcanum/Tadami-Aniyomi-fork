package eu.kanade.tachiyomi.novelsource.online

import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A simple implementation for novel sources from a website using Jsoup, an HTML parser.
 */
@Suppress("unused")
abstract class ParsedNovelHttpSource : HttpNovelSource() {

    /**
     * Parses the response from the site and returns a [NovelsPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularNovelsParse(response: Response): NovelsPage {
        val document = response.asJsoup()

        val novels = document.select(popularNovelSelector()).map { element ->
            popularNovelFromElement(element)
        }

        val hasNextPage = popularNovelNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return NovelsPage(novels, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each novel.
     */
    protected abstract fun popularNovelSelector(): String

    /**
     * Returns a novel from the given [element]. Most sites only show the title and the url, it's
     * totally fine to fill only those two values.
     *
     * @param element an element obtained from [popularNovelSelector].
     */
    protected abstract fun popularNovelFromElement(element: Element): SNovel

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun popularNovelNextPageSelector(): String?

    /**
     * Parses the response from the site and returns a [NovelsPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchNovelsParse(response: Response): NovelsPage {
        val document = response.asJsoup()

        val novels = document.select(searchNovelSelector()).map { element ->
            searchNovelFromElement(element)
        }

        val hasNextPage = searchNovelNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return NovelsPage(novels, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each novel.
     */
    protected abstract fun searchNovelSelector(): String

    /**
     * Returns a novel from the given [element]. Most sites only show the title and the url, it's
     * totally fine to fill only those two values.
     *
     * @param element an element obtained from [searchNovelSelector].
     */
    protected abstract fun searchNovelFromElement(element: Element): SNovel

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun searchNovelNextPageSelector(): String?

    /**
     * Parses the response from the site and returns a [NovelsPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response): NovelsPage {
        val document = response.asJsoup()

        val novels = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return NovelsPage(novels, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each novel.
     */
    protected abstract fun latestUpdatesSelector(): String

    /**
     * Returns a novel from the given [element]. Most sites only show the title and the url, it's
     * totally fine to fill only those two values.
     *
     * @param element an element obtained from [latestUpdatesSelector].
     */
    protected abstract fun latestUpdatesFromElement(element: Element): SNovel

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun latestUpdatesNextPageSelector(): String?

    /**
     * Parses the response from the site and returns the details of a novel.
     *
     * @param response the response from the site.
     */
    override fun novelDetailsParse(response: Response): SNovel {
        return novelDetailsParse(response.asJsoup())
    }

    /**
     * Returns the details of the novel from the given [document].
     *
     * @param document the parsed document.
     */
    protected abstract fun novelDetailsParse(document: Document): SNovel

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SNovelChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    protected abstract fun chapterListSelector(): String

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    protected abstract fun chapterFromElement(element: Element): SNovelChapter

    /**
     * Parses the response from the site and returns the chapter HTML/text.
     *
     * @param response the response from the site.
     */
    override fun chapterTextParse(response: Response): String {
        return chapterTextParse(response.asJsoup())
    }

    /**
     * Returns the chapter text/HTML from the given [document].
     *
     * @param document the parsed document.
     */
    protected abstract fun chapterTextParse(document: Document): String
}
