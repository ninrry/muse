package luzzr.muse.ui.screens.audiobook

import luzzr.muse.domain.model.ReadAlongBook

/** State for importing the workflow's manifest + EPUB + audio package. */
data class ReadAlongImportState(
    val isImporting: Boolean = false,
    val importedBook: ReadAlongBook? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)
