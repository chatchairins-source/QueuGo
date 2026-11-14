package com.queuego.merchant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgInk
import com.queuego.shared.QgMuted

/** One page-title scale for every Merchant top-level screen. */
internal val MerchantPageTitleSize = 19.sp

@Composable
internal fun MerchantPageTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            color = QgInk,
            fontSize = MerchantPageTitleSize,
            lineHeight = 23.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = QgMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}
