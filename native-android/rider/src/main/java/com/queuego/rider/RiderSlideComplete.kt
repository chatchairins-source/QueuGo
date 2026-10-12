package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgRed
import kotlin.math.roundToInt

@Composable
internal fun RiderSlideComplete(
    enabled: Boolean,
    busy: Boolean,
    label: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var completing by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(enabled, busy) {
        if (!busy) {
            progress = 0f
            completing = false
        }
        if (!enabled) progress = 0f
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Color(0xFFF4EFF1) else Color(0xFFF4F1F2))
    ) {
        val thumbWidth = 48.dp
        val horizontalPadding = 4.dp
        val travelPx = with(density) { (maxWidth - thumbWidth - horizontalPadding * 2).toPx().coerceAtLeast(1f) }
        val leftPadPx = with(density) { horizontalPadding.toPx() }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(56.dp)
                .background(
                    Color(0xFFF04455).copy(alpha = if (enabled) 1f else 0f),
                    RoundedCornerShape(20.dp)
                )
        )

        Text(
            if (busy) "กำลังบันทึก..." else label,
            color = if (enabled) Color.White else Color(0xFF91878B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center).padding(start = 54.dp, end = 12.dp)
        )

        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = (leftPadPx + travelPx * progress).roundToInt(),
                        y = with(density) { 4.dp.toPx().roundToInt() }
                    )
                }
                .size(thumbWidth)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White)
                .pointerInput(enabled, busy, completing, travelPx) {
                    if (!enabled || busy || completing) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            progress = (progress + dragAmount / travelPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (progress >= 0.92f) {
                                progress = 1f
                                completing = true
                                onComplete()
                            } else {
                                progress = 0f
                            }
                        },
                        onDragCancel = { progress = 0f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = QgRed)
            } else {
                Text("›", color = QgRed, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
