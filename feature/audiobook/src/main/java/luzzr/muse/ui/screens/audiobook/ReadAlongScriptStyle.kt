package luzzr.muse.ui.screens.audiobook

import luzzr.muse.domain.model.ReadAlongTheme

internal data class ReadAlongCssPalette(
    val paper: String,
    val ink: String,
    val sentenceWash: String,
    val activeInk: String
)

internal fun ReadAlongTheme.cssPalette(): ReadAlongCssPalette =
    when (this) {
        ReadAlongTheme.PAPER -> ReadAlongCssPalette(
            paper = "#F4F1EA",
            ink = "#3E3A35",
            sentenceWash = "#D9D0BF",
            activeInk = "#A6805A"
        )
        ReadAlongTheme.SEPIA -> ReadAlongCssPalette(
            paper = "#F1E6D0",
            ink = "#4B4033",
            sentenceWash = "#D9C5A4",
            activeInk = "#9B6E42"
        )
        ReadAlongTheme.NIGHT -> ReadAlongCssPalette(
            paper = "#252321",
            ink = "#E9E0D3",
            sentenceWash = "#4E4841",
            activeInk = "#D8AF78"
        )
    }
