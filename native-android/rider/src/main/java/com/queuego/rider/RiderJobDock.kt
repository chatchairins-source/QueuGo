package com.queuego.rider

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal fun riderDockShouldExpand(height: Float, peek: Float, maximum: Float, velocity: Float, fling: Float): Boolean {
    if (maximum <= peek) return false
    return when {
        velocity < -fling -> true
        velocity > fling -> false
        else -> height > (peek + maximum) / 2f
    }
}

/** One retained job-card body; only the handle owns sheet drag gestures. */
@Composable
internal fun RiderJobDock(
    jobKey: String?,
    title: String,
    destination: String,
    maximumHeight: Dp,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    if (jobKey == null) {
        Column(modifier.heightIn(max = maximumHeight).verticalScroll(scroll).padding(horizontal = 8.dp), content = content)
        return
    }
    val density = LocalDensity.current
    var expanded by rememberSaveable(jobKey) { mutableStateOf(initiallyExpanded) }
    var headerHeight by remember(jobKey) { mutableIntStateOf(0) }
    val peek = maxOf(with(density) { 92.dp.toPx() }, headerHeight.toFloat())
    val maximum = maxOf(peek, with(density) { maximumHeight.toPx() })
    var height by remember(jobKey, peek, maximum) { mutableFloatStateOf(if (expanded) maximum else peek) }
    var dragging by remember(jobKey) { mutableStateOf(false) }
    val displayedHeight by animateFloatAsState(
        targetValue = height,
        animationSpec = if (dragging) snap() else tween(180),
        label = "Rider job dock height"
    )
    fun toggle() {
        expanded = !expanded
        height = if (expanded) maximum else peek
    }
    val drag = rememberDraggableState { delta -> height = (height - delta).coerceIn(peek, maximum) }
    Column(
        modifier.height(with(density) { displayedHeight.toDp() })
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(Color(0xFAFFFFFF))
    ) {
        Column(
            Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                .semantics {
                    contentDescription = "การ์ดไรเดอร์ กดหรือลากเพื่อเปิดและย่อ"
                    stateDescription = if (expanded) "เปิดรายละเอียด" else "ย่อรายละเอียด"
                }
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { height = displayedHeight; dragging = true },
                    onDragStopped = { velocity ->
                        expanded = riderDockShouldExpand(height, peek, maximum, velocity, with(density) { 600.dp.toPx() })
                        dragging = false
                        height = if (expanded) maximum else peek
                    }
                )
                .clickable(role = Role.Button, onClickLabel = if (expanded) "ย่อการ์ดงาน" else "เปิดรายละเอียดงาน", onClick = ::toggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(36.dp).height(4.dp).background(Color(0xFFD2CACD), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(destination, color = QgMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                Text(if (expanded) "ย่อ" else "เปิด", color = QgRed, fontSize = 12.sp)
                QgIcon("back", Modifier.size(20.dp).rotate(if (expanded) 270f else 90f), QgRed)
            }
        }
        // Keep content mounted at zero remaining height when collapsed, so card-local
        // state and existing workflow callbacks survive every open/close gesture.
        Column(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            content = content
        )
    }
}
