package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryRowType

/** Missing, failed and incomplete rows are not evidence that an existing row should be deleted. */
internal fun DiscoveryFeed.isConclusiveEmpty(row: DiscoveryRowType): Boolean =
    rows[row]?.isEmpty() == true && row !in failedRows && row !in degradedRows
