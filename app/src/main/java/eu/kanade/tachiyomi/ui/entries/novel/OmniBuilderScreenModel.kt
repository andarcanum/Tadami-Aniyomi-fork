package eu.kanade.tachiyomi.ui.entries.novel

import android.webkit.JavascriptInterface
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.novel.resolver.model.OmniRule
import tachiyomi.domain.source.novel.resolver.model.PaginationType
import tachiyomi.domain.source.novel.resolver.repository.OmniRuleRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URL

class OmniBuilderScreenModel(
    val url: String,
    private val ruleRepository: OmniRuleRepository = Injekt.get()
) : StateScreenModel<OmniBuilderScreenModel.State>(State.Init) {

    private val domain = runCatching { URL(url).host }.getOrDefault(url)
    private val ruleBuilder = OmniRuleBuilder(domain)

    val bridge = object : Any() {
        @JavascriptInterface
        fun onElementSelected(selector: String, text: String) {
            screenModelScope.launch {
                handleSelection(selector, text)
            }
        }
    }

    private fun handleSelection(selector: String, text: String) {
        val current = state.value
        if (current is State.Active && current.isInteractionMode) return // Don't select in interaction mode
        
        when (current) {
            is State.StepTitle -> {
                ruleBuilder.titleSelector = selector
                mutableState.update { State.StepCover(current.isInteractionMode) }
            }
            is State.StepCover -> {
                ruleBuilder.coverSelector = selector
                mutableState.update { State.StepChapters(current.isInteractionMode) }
            }
            is State.StepChapters -> {
                // Remove ALL :nth-of-type(...) from the selector path to ensure we match all items
                val cleanSelector = selector.replace(Regex(":nth-of-type\\(\\d+\\)"), "")
                
                val parts = cleanSelector.split(" > ")
                val aIndex = parts.indexOfLast { it == "a" || it.startsWith("a:") || it.startsWith("a.") || it.startsWith("a#") }
                
                if (aIndex != -1) {
                    // We found an <a> tag in the hierarchy (even if they clicked a span inside it)
                    // Drop the <a> tag and its parent <li> (if present) to find the list container
                    var dropIndex = aIndex
                    if (aIndex > 0 && parts[aIndex - 1].startsWith("li")) {
                        dropIndex = aIndex - 1
                    }
                    
                    ruleBuilder.chapterListSelector = parts.subList(0, dropIndex).joinToString(" > ")
                    ruleBuilder.chapterUrlSelector = "a"
                    ruleBuilder.chapterNameSelector = "a"
                } else {
                    // Fallback if no <a> tag is in the path
                    ruleBuilder.chapterListSelector = cleanSelector
                    ruleBuilder.chapterUrlSelector = null
                    ruleBuilder.chapterNameSelector = null
                }
                
                mutableState.update { State.StepPagination(current.isInteractionMode) }
            }
            is State.StepPagination -> {
                ruleBuilder.paginationSelector = selector
                mutableState.update { State.PaginationTypeSelection }
            }
            else -> {}
        }
    }

    fun start() {
        mutableState.update { State.StepTitle() }
    }

    fun skipCurrentStep() {
        val current = state.value
        val isInteract = (current as? State.Active)?.isInteractionMode ?: false
        when (current) {
            is State.StepTitle -> mutableState.update { State.StepCover(isInteract) }
            is State.StepCover -> mutableState.update { State.StepChapters(isInteract) }
            is State.StepPagination -> mutableState.update { State.Review }
            else -> {} // StepChapters cannot be skipped
        }
    }

    fun goBack() {
        val current = state.value
        val isInteract = (current as? State.Active)?.isInteractionMode ?: false
        when (current) {
            is State.StepCover -> mutableState.update { State.StepTitle(isInteract) }
            is State.StepChapters -> mutableState.update { State.StepCover(isInteract) }
            is State.StepPagination -> mutableState.update { State.StepChapters(isInteract) }
            is State.PaginationTypeSelection -> mutableState.update { State.StepPagination(isInteract) }
            is State.Review -> mutableState.update { State.StepPagination(isInteract) }
            else -> {} // Init and StepTitle cannot go back
        }
    }

    fun setPaginationType(type: PaginationType) {
        ruleBuilder.paginationType = type
        mutableState.update { State.Review }
    }

    fun saveRule(onSaved: () -> Unit) {
        screenModelScope.launch {
            ruleRepository.insertRule(ruleBuilder.build())
            onSaved()
        }
    }

    fun toggleInteractionMode() {
        val current = state.value
        if (current is State.Active) {
            val nextInteract = !current.isInteractionMode
            mutableState.update { 
                when (current) {
                    is State.StepTitle -> current.copy(isInteractionMode = nextInteract)
                    is State.StepCover -> current.copy(isInteractionMode = nextInteract)
                    is State.StepChapters -> current.copy(isInteractionMode = nextInteract)
                    is State.StepPagination -> current.copy(isInteractionMode = nextInteract)
                }
            }
        }
    }

    sealed interface State {
        data object Init : State
        
        sealed interface Active : State {
            val isInteractionMode: Boolean
        }
        
        data class StepTitle(override val isInteractionMode: Boolean = false) : Active
        data class StepCover(override val isInteractionMode: Boolean = false) : Active
        data class StepChapters(override val isInteractionMode: Boolean = false) : Active
        data class StepPagination(override val isInteractionMode: Boolean = false) : Active
        
        data object PaginationTypeSelection : State
        data object Review : State
    }

    private class OmniRuleBuilder(val domain: String) {
        var titleSelector: String? = null
        var coverSelector: String? = null
        var chapterListSelector: String = ""
        var chapterNameSelector: String? = null
        var chapterUrlSelector: String? = null
        var paginationSelector: String? = null
        var paginationType: PaginationType = PaginationType.NONE

        fun build() = OmniRule(
            domain = domain,
            titleSelector = titleSelector,
            authorSelector = null,
            coverSelector = coverSelector,
            descriptionSelector = null,
            chapterListSelector = chapterListSelector.ifBlank { "a" }, // Fallback
            chapterNameSelector = chapterNameSelector,
            chapterUrlSelector = chapterUrlSelector,
            paginationSelector = paginationSelector,
            paginationType = paginationType,
            contentSelector = "div.entry-content, div.reading-content, div.text-left", // Default content area
            removeSelectors = ".code-block, .adsbygoogle, .social-share, .comments-area"
        )
    }
}
