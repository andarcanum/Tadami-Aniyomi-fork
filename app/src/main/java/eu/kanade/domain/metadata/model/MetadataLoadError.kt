package eu.kanade.domain.metadata.model

sealed interface MetadataLoadError {
    data object NetworkError : MetadataLoadError
    data object NotFound : MetadataLoadError
    data object NotAuthenticated : MetadataLoadError
    data object Disabled : MetadataLoadError
}
