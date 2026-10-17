package com.queuego.customer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgQueueGoLocationPickerMap

@Composable
internal fun CustomerQueueGoMapPicker(
    initialPoint: QgMapPoint?,
    recenterPoint: QgMapPoint?,
    recenterToken: Int,
    modifier: Modifier = Modifier,
    selectInitialPoint: Boolean = true,
    onCenterChanged: (QgMapPoint) -> Unit,
    onCenterObserved: (QgMapPoint) -> Unit = {},
    onReady: (Boolean) -> Unit = {}
) = QgQueueGoLocationPickerMap(
    initialPoint = initialPoint,
    recenterPoint = recenterPoint,
    recenterToken = recenterToken,
    modifier = modifier,
    selectInitialPoint = selectInitialPoint,
    onCenterChanged = onCenterChanged,
    onCenterObserved = onCenterObserved,
    onReady = onReady
)
