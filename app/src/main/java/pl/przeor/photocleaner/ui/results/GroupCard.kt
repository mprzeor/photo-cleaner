package pl.przeor.photocleaner.ui.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.przeor.photocleaner.analysis.DuplicateGroup
import pl.przeor.photocleaner.analysis.ImageFeatures
import pl.przeor.photocleaner.ui.common.formatBytes
import pl.przeor.photocleaner.ui.common.formatDateTime

private val TILE = 124.dp

@Composable
fun GroupCard(
    group: DuplicateGroup,
    onOpen: (ImageFeatures) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${group.items.size} similar photos",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${formatBytes(group.totalBytes)} total · ${formatBytes(group.reclaimableBytes)} reclaimable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.items, key = { it.key }) { photo ->
                    PhotoTile(
                        photo = photo,
                        onClick = { onOpen(photo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(
    photo: ImageFeatures,
    onClick: () -> Unit,
) {
    Column(Modifier.width(TILE)) {
        Box(
            Modifier
                .size(TILE)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (photo.hasLocation) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = "Has GPS location",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            photo.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${photo.width}×${photo.height} · ${formatBytes(photo.size)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatDateTime(photo.effectiveTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
