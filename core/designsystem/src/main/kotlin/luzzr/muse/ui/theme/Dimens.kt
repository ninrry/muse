package luzzr.muse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MuseDimens {
    val MiniPlayerClearance = 96.dp
    val ScreenPaddingH = 16.dp
    val TouchTarget = 48.dp
    val ListItemMinHeight = 64.dp
    val AlbumGridMinCellWidth = 160.dp
    val CollectionCardHeight = 112.dp

    val MiniPlayerHeight = 64.dp
    val PlayerArtworkSmall = 48.dp
    val PlayerArtworkMax = 320.dp
    val QueueSheetEmptyHeight = 200.dp
    val MetadataSheetEmptyHeight = 200.dp
    val LyricsChipWidth = 120.dp
    val NavigationBarHeight = 80.dp
    val TopBarHeight = 56.dp
    val CardCornerRadius = 28.dp
    val SmallCardCornerRadius = 20.dp
    val ButtonCornerRadius = 18.dp
    val ProgressBarHeight = 6.dp
    val SliderTrackPadding = 8.dp
    val TimeBubbleWidth = 48.dp
    val TimeBubbleHeight = 24.dp
    val TimeBubbleCornerRadius = 12.dp
    val ProgressBarCornerRadius = 4.dp
    val SliderThumbRadius = 10.dp
    val SliderTrackHeight = 4.dp
    val SliderTrackActiveHeight = 6.dp
    val SliderTrackInactiveHeight = 4.dp
    val SliderTrackActiveCornerRadius = 2.dp
    val SliderTrackInactiveCornerRadius = 2.dp
    val SliderThumbCornerRadius = 5.dp
    val SliderThumbElevation = 4.dp
    val SliderThumbPressedSize = 12.dp
    val SliderThumbSize = 10.dp
    val SliderThumbInactiveSize = 8.dp
    val SliderThumbInactiveCornerRadius = 4.dp
    val SliderThumbInactiveElevation = 2.dp
    val SliderThumbInactivePressedSize = 10.dp
    val SliderThumbInactivePressedCornerRadius = 5.dp
    val SliderThumbInactivePressedElevation = 4.dp

    val ButtonHeight = 48.dp
    val ButtonHeightSmall = 36.dp
    val ButtonHeightMedium = 44.dp
    val ButtonHeightLarge = 56.dp

    val DividerThickness = 1.dp

    val ArtworkSizeSmall = 36.dp
    val ArtworkSizeMedium = 48.dp
    val ArtworkSizeLarge = 72.dp
    val ArtworkSizePlayer = 80.dp

    val IconSizeTiny = 16.dp
    val IconSizeSmall = 18.dp
    val IconSizeNormal = 20.dp
    val IconSizeMedium = 24.dp
    val IconSizeLarge = 32.dp

    val CornerRadiusSmall = 10.dp
    val CornerRadiusMedium = 18.dp
    val CornerRadiusLarge = 22.dp
    val CornerRadiusPill = 28.dp

    val SpacingTiny = 3.dp
    val SpacingSmall = 6.dp
    val SpacingMedium = 10.dp
    val SpacingLarge = 14.dp

    val Sm = AppSpacing.xs
    val Md = AppSpacing.md
    val Lg = AppSpacing.lg
    val Xlg = AppSpacing.xlg
    val Xxlg = AppSpacing.xxlg

    val ListSpacing = 10.dp
    val SectionSpacing = 12.dp
    val ContentSpacing = 14.dp
    val CardSpacing = 20.dp

    @Composable
    fun adaptivePlayerArtworkSize(): Dp {
        return when (currentWindowSize()) {
            WindowSize.Compact -> 280.dp
            WindowSize.Medium -> 320.dp
            WindowSize.Expanded -> 400.dp
        }
    }

    @Composable
    fun adaptiveMiniPlayerHeight(): Dp {
        return when (currentWindowSize()) {
            WindowSize.Compact -> 64.dp
            WindowSize.Medium -> 72.dp
            WindowSize.Expanded -> 80.dp
        }
    }
}
