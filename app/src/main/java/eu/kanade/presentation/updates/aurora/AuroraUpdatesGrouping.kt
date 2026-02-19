package eu.kanade.presentation.updates.aurora

import java.time.LocalDate
import java.util.LinkedHashMap

data class AuroraUpdatesDateGroup<T>(
    val date: LocalDate,
    val groups: List<AuroraUpdatesTitleGroup<T>>,
)

data class AuroraUpdatesTitleGroup<T>(
    val key: String,
    val entryId: Long,
    val title: String,
    val itemCount: Int,
    val items: List<T>,
)

fun <T> buildAuroraUpdatesGroups(
    items: List<T>,
    dateSelector: (T) -> LocalDate,
    titleIdSelector: (T) -> Long,
    titleSelector: (T) -> String,
): List<AuroraUpdatesDateGroup<T>> {
    val groupedByDateAndTitle = LinkedHashMap<LocalDate, LinkedHashMap<Long, MutableGroup<T>>>()

    items.forEach { item ->
        val date = dateSelector(item)
        val entryId = titleIdSelector(item)
        val title = titleSelector(item)

        val byTitle = groupedByDateAndTitle.getOrPut(date) { LinkedHashMap() }
        val group = byTitle.getOrPut(entryId) {
            MutableGroup(entryId = entryId, title = title, items = mutableListOf())
        }
        group.items += item
    }

    return groupedByDateAndTitle.map { (date, byTitle) ->
        AuroraUpdatesDateGroup(
            date = date,
            groups = byTitle.values.map { group ->
                AuroraUpdatesTitleGroup(
                    key = "${date}_${
                        group.entryId
                    }",
                    entryId = group.entryId,
                    title = group.title,
                    itemCount = group.items.size,
                    items = group.items.toList(),
                )
            },
        )
    }
}

private data class MutableGroup<T>(
    val entryId: Long,
    val title: String,
    val items: MutableList<T>,
)
