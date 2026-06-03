package luzzr.muse.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import luzzr.muse.data.model.Artist
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
fun ArtistListTab(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无艺术家数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = AppSpacing.md,
            bottom = MuseDimens.MiniPlayerClearance + AppSpacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        itemsIndexed(artists, key = { _, artist -> artist.name }) { _, artist ->
            ElevatedCard(
                onClick = { onArtistClick(artist) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MuseDimens.SmallCardCornerRadius)
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.sm).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(AppSpacing.xxlg),
                        shape = RoundedCornerShape(AppSpacing.lg),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(AppSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${artist.songCount}首 · ${artist.albumCount}张专辑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
