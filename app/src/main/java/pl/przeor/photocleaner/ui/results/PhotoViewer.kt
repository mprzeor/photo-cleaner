package pl.przeor.photocleaner.ui.results

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.przeor.photocleaner.analysis.DuplicateGroup
import pl.przeor.photocleaner.analysis.ImageFeatures
import pl.przeor.photocleaner.ui.common.formatBytes
import pl.przeor.photocleaner.ui.common.formatDateTime

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Fullscreen photo viewer for one duplicate group. Swipe left/right to move
 * between the group's photos, pinch or double-tap to zoom; the bin button
 * moves the shown photo to the system Trash (after the system confirmation
 * dialog).
 */
@Composable
fun PhotoViewer(
    group: DuplicateGroup,
    initialKey: String,
    snackbarHostState: SnackbarHostState,
    onMoveToTrash: (ImageFeatures) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialPage = remember(group.id, initialKey) {
        group.items.indexOfFirst { it.key == initialKey }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { group.items.size }

    // After a photo is trashed the group shrinks: the photo that took its place
    // shows automatically, and when the *last* page was deleted, snap back to
    // the previous photo instead of an empty page.
    LaunchedEffect(group.items.size) {
        if (pagerState.currentPage >= group.items.size) {
            pagerState.scrollToPage(group.items.size - 1)
        }
    }

    // Rendered as an overlay in the activity window rather than a Dialog:
    // dialog windows do not reliably receive window insets, which pushed the
    // bottom bar underneath the system navigation controls.
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomablePhoto(
                    photo = group.items[page],
                    isCurrent = pagerState.currentPage == page,
                )
            }

            val current = group.items[pagerState.currentPage.coerceIn(group.items.indices)]

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
                Text(
                    "${pagerState.currentPage + 1} / ${group.items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${current.width}×${current.height} · ${formatBytes(current.size)} · " +
                        formatDateTime(current.effectiveTime),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onMoveToTrash(current) },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Move to Trash",
                        tint = Color.White,
                    )
                }
            }

            // The results screen's snackbar sits behind this fullscreen dialog,
            // so messages must also be rendered here to be visible.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
            )
    }
}

/**
 * One pager page: pinch to zoom, drag to pan while zoomed, double-tap to
 * toggle zoom. Panning is only consumed while zoomed in, so at 1x a
 * horizontal drag still swipes the pager to the next photo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePhoto(photo: ImageFeatures, isCurrent: Boolean) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, atScale: Float): Offset {
        val maxX = (atScale - 1f) * containerSize.width / 2f
        val maxY = (atScale - 1f) * containerSize.height / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = clampOffset(offset + panChange, scale)
    }

    // Reset the zoom once the user has swiped away from this photo.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = DOUBLE_TAP_ZOOM
                        val center = Offset(size.width / 2f, size.height / 2f)
                        offset = clampOffset((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                    }
                })
            }
            .transformable(state = transformState, canPan = { scale > 1f }),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
