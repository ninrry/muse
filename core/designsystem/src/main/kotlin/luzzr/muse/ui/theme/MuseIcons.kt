package luzzr.muse.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 满足【应用 UI 设计规则与规范】第 2 条：
 * "严禁使用现成的 Material Icons (Icons.Default.* 等)。
 * 所有图标必须是基于 Path/SVG 绘制的抽象、简约、线条风格。"
 *
 * 极简线条风格 Path 图标定义集合。
 */
object MuseIcons {

    private fun buildIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Home: ImageVector by lazy {
        buildIcon("MuseIcons.Home") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 10.5f)
                lineTo(12f, 3.5f)
                lineTo(20f, 10.5f)
                lineTo(20f, 20f)
                curveTo(20f, 20.6f, 19.4f, 21f, 18.8f, 21f)
                lineTo(14.5f, 21f)
                lineTo(14.5f, 14.5f)
                lineTo(9.5f, 14.5f)
                lineTo(9.5f, 21f)
                lineTo(5.2f, 21f)
                curveTo(4.6f, 21f, 4f, 20.6f, 4f, 20f)
                close()
            }
        }
    }

    val HomeOutlined: ImageVector get() = Home

    val Library: ImageVector by lazy {
        buildIcon("MuseIcons.Library") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 6f)
                lineTo(4f, 20f)
                moveTo(8f, 3f)
                lineTo(8f, 20f)
                moveTo(12f, 8f)
                curveTo(12f, 5.8f, 13.8f, 4f, 16f, 4f)
                curveTo(18.2f, 4f, 20f, 5.8f, 20f, 8f)
                curveTo(20f, 10.2f, 18.2f, 12f, 16f, 12f)
                curveTo(13.8f, 12f, 12f, 10.2f, 12f, 8f)
                close()
                moveTo(16f, 12f)
                lineTo(16f, 20f)
            }
        }
    }

    val LibraryOutlined: ImageVector get() = Library

    val Audiobook: ImageVector by lazy {
        buildIcon("MuseIcons.Audiobook") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 19.5f)
                curveTo(4f, 18.1f, 5.1f, 17f, 6.5f, 17f)
                lineTo(20f, 17f)
                moveTo(4f, 19.5f)
                curveTo(4f, 20.9f, 5.1f, 22f, 6.5f, 22f)
                lineTo(20f, 22f)
                moveTo(4f, 19.5f)
                lineTo(4f, 4.5f)
                curveTo(4f, 3.1f, 5.1f, 2f, 6.5f, 2f)
                lineTo(20f, 2f)
                lineTo(20f, 22f)
                moveTo(12f, 7f)
                curveTo(10.3f, 7f, 9f, 8.3f, 9f, 10f)
                curveTo(9f, 11.7f, 10.3f, 13f, 12f, 13f)
                curveTo(13.7f, 13f, 15f, 11.7f, 15f, 10f)
            }
        }
    }

    val AudiobookOutlined: ImageVector get() = Audiobook

    val Settings: ImageVector by lazy {
        buildIcon("MuseIcons.Settings") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
                moveTo(9f, 4f)
                lineTo(9f, 10f)
                moveTo(15f, 14f)
                lineTo(15f, 20f)
            }
        }
    }

    val SettingsOutlined: ImageVector get() = Settings

    val Moon: ImageVector get() = Settings

    val Sun: ImageVector get() = Settings

    val Book: ImageVector get() = Audiobook

    val Play: ImageVector by lazy {
        buildIcon("MuseIcons.Play") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 4.5f)
                lineTo(19f, 12f)
                lineTo(7f, 19.5f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        buildIcon("MuseIcons.Pause") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(8f, 5f)
                lineTo(8f, 19f)
                moveTo(16f, 5f)
                lineTo(16f, 19f)
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        buildIcon("MuseIcons.SkipNext") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 5f)
                lineTo(15f, 12f)
                lineTo(5f, 19f)
                close()
                moveTo(19f, 5f)
                lineTo(19f, 19f)
            }
        }
    }

    val SkipPrevious: ImageVector by lazy {
        buildIcon("MuseIcons.SkipPrevious") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 5f)
                lineTo(9f, 12f)
                lineTo(19f, 19f)
                close()
                moveTo(5f, 5f)
                lineTo(5f, 19f)
            }
        }
    }

    val Shuffle: ImageVector by lazy {
        buildIcon("MuseIcons.Shuffle") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 8f)
                moveTo(4f, 20f)
                lineTo(21f, 3f)
                moveTo(21f, 16f)
                lineTo(21f, 21f)
                lineTo(16f, 21f)
                moveTo(15f, 15f)
                lineTo(21f, 21f)
                moveTo(4f, 4f)
                lineTo(9f, 9f)
            }
        }
    }

    val Repeat: ImageVector by lazy {
        buildIcon("MuseIcons.Repeat") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 2f)
                lineTo(21f, 6f)
                lineTo(17f, 10f)
                moveTo(3f, 11f)
                lineTo(3f, 8f)
                curveTo(3f, 6.9f, 3.9f, 6f, 5f, 6f)
                lineTo(21f, 6f)
                moveTo(7f, 22f)
                lineTo(3f, 18f)
                lineTo(7f, 14f)
                moveTo(21f, 13f)
                lineTo(21f, 16f)
                curveTo(21f, 17.1f, 20.1f, 18f, 19f, 18f)
                lineTo(3f, 18f)
            }
        }
    }

    val RepeatOne: ImageVector by lazy {
        buildIcon("MuseIcons.RepeatOne") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 2f)
                lineTo(21f, 6f)
                lineTo(17f, 10f)
                moveTo(3f, 11f)
                lineTo(3f, 8f)
                curveTo(3f, 6.9f, 3.9f, 6f, 5f, 6f)
                lineTo(21f, 6f)
                moveTo(7f, 22f)
                lineTo(3f, 18f)
                lineTo(7f, 14f)
                moveTo(21f, 13f)
                lineTo(21f, 16f)
                curveTo(21f, 17.1f, 20.1f, 18f, 19f, 18f)
                lineTo(3f, 18f)
                moveTo(11f, 10f)
                lineTo(12f, 10f)
                lineTo(12f, 14f)
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        buildIcon("MuseIcons.ArrowBack") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }
    }

    val MoreVert: ImageVector by lazy {
        buildIcon("MuseIcons.MoreVert") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 6f)
                curveTo(12.83f, 6f, 13.5f, 5.33f, 13.5f, 4.5f)
                curveTo(13.5f, 3.67f, 12.83f, 3f, 12f, 3f)
                curveTo(11.17f, 3f, 10.5f, 3.67f, 10.5f, 4.5f)
                curveTo(10.5f, 5.33f, 11.17f, 6f, 12f, 6f)
                close()
                moveTo(12f, 13.5f)
                curveTo(12.83f, 13.5f, 13.5f, 12.83f, 13.5f, 12f)
                curveTo(13.5f, 11.17f, 12.83f, 10.5f, 12f, 10.5f)
                curveTo(11.17f, 10.5f, 10.5f, 11.17f, 10.5f, 12f)
                curveTo(10.5f, 12.83f, 11.17f, 13.5f, 12f, 13.5f)
                close()
                moveTo(12f, 21f)
                curveTo(12.83f, 21f, 13.5f, 20.33f, 13.5f, 19.5f)
                curveTo(13.5f, 18.67f, 12.83f, 18f, 12f, 18f)
                curveTo(11.17f, 18f, 10.5f, 18.67f, 10.5f, 19.5f)
                curveTo(10.5f, 20.33f, 11.17f, 21f, 12f, 21f)
                close()
            }
        }
    }

    val Search: ImageVector by lazy {
        buildIcon("MuseIcons.Search") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.5f, 18f)
                curveTo(14.64f, 18f, 18f, 14.64f, 18f, 10.5f)
                curveTo(18f, 6.36f, 14.64f, 3f, 10.5f, 3f)
                curveTo(6.36f, 3f, 3f, 6.36f, 3f, 10.5f)
                curveTo(3f, 14.64f, 6.36f, 18f, 10.5f, 18f)
                close()
                moveTo(16f, 16f)
                lineTo(21f, 21f)
            }
        }
    }

    val Add: ImageVector by lazy {
        buildIcon("MuseIcons.Add") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Delete: ImageVector by lazy {
        buildIcon("MuseIcons.Delete") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                moveTo(8f, 6f)
                lineTo(8f, 4f)
                curveTo(8f, 3.4f, 8.4f, 3f, 9f, 3f)
                lineTo(15f, 3f)
                curveTo(15.6f, 3f, 16f, 3.4f, 16f, 4f)
                lineTo(16f, 6f)
                moveTo(19f, 6f)
                lineTo(19f, 20f)
                curveTo(19f, 20.6f, 18.6f, 21f, 18f, 21f)
                lineTo(6f, 21f)
                curveTo(5.4f, 21f, 5f, 20.6f, 5f, 20f)
                lineTo(5f, 6f)
            }
        }
    }

    val DeleteOutline: ImageVector get() = Delete

    val Edit: ImageVector by lazy {
        buildIcon("MuseIcons.Edit") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 3f)
                curveTo(17.8f, 2.2f, 19f, 2.2f, 19.8f, 3f)
                curveTo(20.6f, 3.8f, 20.6f, 5f, 19.8f, 5.8f)
                lineTo(7.5f, 18.1f)
                lineTo(3f, 19.5f)
                lineTo(4.4f, 15f)
                lineTo(17f, 3f)
                close()
            }
        }
    }

    val Queue: ImageVector by lazy {
        buildIcon("MuseIcons.Queue") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f)
                lineTo(15f, 6f)
                moveTo(3f, 12f)
                lineTo(15f, 12f)
                moveTo(3f, 18f)
                lineTo(11f, 18f)
                moveTo(16f, 16f)
                lineTo(21f, 16f)
                moveTo(18.5f, 13.5f)
                lineTo(18.5f, 18.5f)
            }
        }
    }

    val Sort: ImageVector by lazy {
        buildIcon("MuseIcons.Sort") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                moveTo(4f, 12f)
                lineTo(14f, 12f)
                moveTo(4f, 18f)
                lineTo(8f, 18f)
            }
        }
    }

    val GraphicEq: ImageVector by lazy {
        buildIcon("MuseIcons.GraphicEq") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 9f)
                lineTo(4f, 15f)
                moveTo(8f, 4f)
                lineTo(8f, 20f)
                moveTo(12f, 7f)
                lineTo(12f, 17f)
                moveTo(16f, 3f)
                lineTo(16f, 21f)
                moveTo(20f, 10f)
                lineTo(20f, 14f)
            }
        }
    }

    val Check: ImageVector by lazy {
        buildIcon("MuseIcons.Check") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        buildIcon("MuseIcons.CheckCircle") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                close()
                moveTo(8.5f, 12f)
                lineTo(11f, 14.5f)
                lineTo(15.5f, 9.5f)
            }
        }
    }

    val RadioButtonUnchecked: ImageVector by lazy {
        buildIcon("MuseIcons.RadioButtonUnchecked") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                close()
            }
        }
    }

    val Folder: ImageVector by lazy {
        buildIcon("MuseIcons.Folder") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f)
                curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f)
                lineTo(10f, 4f)
                lineTo(12f, 6f)
                lineTo(19f, 6f)
                curveTo(20.1f, 6f, 21f, 6.9f, 21f, 8f)
                lineTo(21f, 18f)
                curveTo(21f, 19.1f, 20.1f, 20f, 19f, 20f)
                lineTo(5f, 20f)
                curveTo(3.9f, 20f, 3f, 19.1f, 3f, 18f)
                close()
            }
        }
    }

    val FolderOpen: ImageVector get() = Folder

    val BookmarkAdd: ImageVector by lazy {
        buildIcon("MuseIcons.BookmarkAdd") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 4f)
                curveTo(5f, 2.9f, 5.9f, 2f, 7f, 2f)
                lineTo(17f, 2f)
                curveTo(18.1f, 2f, 19f, 2.9f, 19f, 4f)
                lineTo(19f, 21f)
                lineTo(12f, 17f)
                lineTo(5f, 21f)
                close()
            }
        }
    }

    val Speed: ImageVector by lazy {
        buildIcon("MuseIcons.Speed") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                close()
                moveTo(12f, 12f)
                lineTo(16f, 8f)
            }
        }
    }

    val MusicNote: ImageVector by lazy {
        buildIcon("MuseIcons.MusicNote") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 18f)
                curveTo(7.34f, 18f, 6f, 16.66f, 6f, 15f)
                curveTo(6f, 13.34f, 7.34f, 12f, 9f, 12f)
                curveTo(10.66f, 12f, 12f, 13.34f, 12f, 15f)
                curveTo(12f, 16.66f, 10.66f, 18f, 9f, 18f)
                close()
                moveTo(12f, 15f)
                lineTo(12f, 3f)
                lineTo(18f, 3f)
            }
        }
    }

    val Radar: ImageVector by lazy {
        buildIcon("MuseIcons.Radar") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 12f)
                moveTo(12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                close()
                moveTo(12f, 16f)
                curveTo(14.2f, 16f, 16f, 14.2f, 16f, 12f)
                curveTo(16f, 9.8f, 14.2f, 8f, 12f, 8f)
                curveTo(9.8f, 8f, 8f, 9.8f, 8f, 12f)
                curveTo(8f, 14.2f, 9.8f, 16f, 12f, 16f)
                close()
            }
        }
    }

    val Image: ImageVector by lazy {
        buildIcon("MuseIcons.Image") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                lineTo(19f, 3f)
                curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
                lineTo(21f, 19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                close()
                moveTo(8.5f, 10f)
                curveTo(9.3f, 10f, 10f, 9.3f, 10f, 8.5f)
                curveTo(10f, 7.7f, 9.3f, 7f, 8.5f, 7f)
                curveTo(7.7f, 7f, 7f, 7.7f, 7f, 8.5f)
                curveTo(7f, 9.3f, 7.7f, 10f, 8.5f, 10f)
                close()
                moveTo(21f, 15f)
                lineTo(16f, 10f)
                lineTo(5f, 21f)
            }
        }
    }

    val Tune: ImageVector by lazy {
        buildIcon("MuseIcons.Tune") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 8f)
                lineTo(20f, 8f)
                moveTo(4f, 16f)
                lineTo(20f, 16f)
                moveTo(8f, 5f)
                lineTo(8f, 11f)
                moveTo(16f, 13f)
                lineTo(16f, 19f)
            }
        }
    }

    val Forward10: ImageVector by lazy {
        buildIcon("MuseIcons.Forward10") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                curveTo(16.42f, 5f, 20f, 8.58f, 20f, 13f)
                curveTo(20f, 17.42f, 16.42f, 21f, 12f, 21f)
                curveTo(7.58f, 21f, 4f, 17.42f, 4f, 13f)
                moveTo(12f, 2f)
                lineTo(12f, 8f)
                lineTo(17f, 5f)
            }
        }
    }

    val Replay10: ImageVector by lazy {
        buildIcon("MuseIcons.Replay10") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                curveTo(7.58f, 5f, 4f, 8.58f, 4f, 13f)
                curveTo(4f, 17.42f, 7.58f, 21f, 12f, 21f)
                curveTo(16.42f, 21f, 20f, 17.42f, 20f, 13f)
                moveTo(12f, 2f)
                lineTo(12f, 8f)
                lineTo(7f, 5f)
            }
        }
    }

    val Toc: ImageVector by lazy {
        buildIcon("MuseIcons.Toc") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                moveTo(3f, 12f)
                lineTo(21f, 12f)
                moveTo(3f, 18f)
                lineTo(21f, 18f)
            }
        }
    }

    val HourglassEmpty: ImageVector by lazy {
        buildIcon("MuseIcons.HourglassEmpty") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 2f)
                lineTo(18f, 2f)
                moveTo(6f, 22f)
                lineTo(18f, 22f)
                moveTo(6f, 2f)
                lineTo(12f, 12f)
                lineTo(6f, 22f)
                moveTo(18f, 2f)
                lineTo(12f, 12f)
                lineTo(18f, 22f)
            }
        }
    }

    val AutoAwesomeMotion: ImageVector by lazy {
        buildIcon("MuseIcons.AutoAwesomeMotion") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(22f, 7f)
                lineTo(12f, 12f)
                lineTo(2f, 7f)
                close()
                moveTo(2f, 12f)
                lineTo(12f, 17f)
                lineTo(22f, 12f)
                moveTo(2f, 17f)
                lineTo(12f, 22f)
                lineTo(22f, 17f)
            }
        }
    }
}
