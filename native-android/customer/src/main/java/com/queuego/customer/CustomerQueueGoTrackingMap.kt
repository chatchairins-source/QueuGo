package com.queuego.customer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgQueueGoTrackingMap

@Composable
internal fun CustomerQueueGoTrackingMap(
    points: List<QgMapPoint>,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {}
) = QgQueueGoTrackingMap(points = points, modifier = modifier, onReady = onReady)
