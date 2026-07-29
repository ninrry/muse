package luzzr.muse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MuseDimens {
    val MiniPlayerClearance = 0.dp
    val ScreenPaddingH = 20.dp
    val ScreenPaddingWide = 24.dp
    val TouchTarget = 48.dp
    val ListItemMinHeight = 72.dp
    val PlaylistCardWidth = 132.dp
    // The card reserves one stable line for the name and one for the count.
    // 104.dp was not enough once the placeholder icon and accessibility-safe
    // padding were included, which caused the second line to be clipped.
    val PlaylistCardHeight = 128.dp
    val AlbumGridMinCellWidth = 144.dp
    val CollectionCardHeight = 104.dp

    val MiniPlayerHeight = 72.dp
    val PlayerArtworkSmall = 48.dp
    val PlayerArtworkMax = 320.dp
    val QueueSheetEmptyHeight = 200.dp
    val MetadataSheetEmptyHeight = 200.dp
    val LyricsChipWidth = 120.dp
    val NavigationBarHeight = 80.dp
    val TopBarHeight = 56.dp
    val CardCornerRadius = 16.dp
    val SmallCardCornerRadius = 12.dp
    val ButtonCornerRadius = 16.dp
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
    val ArtworkSizeMedium = 52.dp
    val ArtworkSizeLarge = 72.dp
    val ArtworkSizePlayer = 80.dp

    val IconSizeTiny = 16.dp
    val IconSizeSmall = 18.dp
    val IconSizeNormal = 20.dp
    val IconSizeMedium = 24.dp
    val IconSizeLarge = 32.dp

    val CornerRadiusSmall = 10.dp
    val CornerRadiusMedium = 16.dp
    val CornerRadiusLarge = 20.dp
    val CornerRadiusPill = 999.dp

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
            WindowSize.Compact -> 248.dp
            WindowSize.Medium -> 288.dp
            WindowSize.Expanded -> 352.dp
        }
    }

    @Composable
    fun adaptiveMiniPlayerHeight(): Dp {
        return when (currentWindowSize()) {
            WindowSize.Compact -> 72.dp
            WindowSize.Medium -> 76.dp
            WindowSize.Expanded -> 80.dp
        }
    }
}
