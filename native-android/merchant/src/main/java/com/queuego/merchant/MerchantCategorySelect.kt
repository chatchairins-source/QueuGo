package com.queuego.merchant

import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.AdapterView
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

/** A single tap opens the platform category list, without a reveal-button step. */
@Composable
internal fun MerchantCategorySelect(
    categories: List<String>, category: String, enabled: Boolean,
    onCategory: (String) -> Unit
) {
    val values = listOf("") + categories.distinct().filter { it.isNotBlank() }
    val labels = listOf("ทุกหมวด") + values.drop(1)
    val currentCategory by rememberUpdatedState(category)
    val currentValues by rememberUpdatedState(values)
    val currentCallback by rememberUpdatedState(onCategory)
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        factory = { context -> Spinner(context).apply {
            contentDescription = "หมวดหมู่สินค้า"
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentValues.getOrNull(position)?.let { if (it != currentCategory && parent?.isEnabled == true && position == parent.selectedItemPosition) currentCallback(it) }
                }
            }
        } },
        update = { spinner ->
            spinner.isEnabled = enabled
            if (spinner.tag != labels) {
                spinner.tag = labels
                spinner.adapter = ArrayAdapter(spinner.context, android.R.layout.simple_spinner_dropdown_item, labels)
            }
            val index = values.indexOf(category).coerceAtLeast(0)
            if (spinner.selectedItemPosition != index) spinner.setSelection(index, false)
        }
    )
}
