package com.queuego.shared

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Test identity lives in Account, so ordinary operational screens stay focused. */
@Composable
fun QgTestBuildInfo(role: String, version: String, versionCode: Int,
    gitSha: String, builtUtc: String, buildType: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("ข้อมูลเวอร์ชันทดสอบ") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("QueueGo $role TEST") },
        text = {
            SelectionContainer {
                Text("App role: $role\nVersion: $version\nversionCode: $versionCode\nGit SHA: $gitSha\nBuilt UTC: $builtUtc\nBuild type: $buildType")
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("ปิด") } }
    )
}
