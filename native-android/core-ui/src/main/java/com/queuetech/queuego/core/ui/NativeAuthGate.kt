package com.queuetech.queuego.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queuetech.queuego.core.model.QueueGoUser

@Composable
fun NativeAuthGate(
    appName: String,
    viewModel: NativeAuthViewModel,
    content: @Composable (QueueGoUser, () -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        NativeAuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is NativeAuthState.SignedOut -> NativeLoginScreen(appName, current.error, viewModel::login)
        is NativeAuthState.SignedIn -> content(current.user, viewModel::logout)
    }
}

@Composable
private fun NativeLoginScreen(appName: String, error: String?, onLogin: (String, String) -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Q", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text(appName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("เข้าสู่ระบบด้วยบัญชี QueueGo Production", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("เบอร์โทรศัพท์ / อีเมล") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("รหัสผ่าน") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onLogin(identifier, password) },
                enabled = identifier.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("เข้าสู่ระบบ") }
        }
    }
}

@Composable
fun NativeAccountHome(appName: String, user: QueueGoUser, statusText: String, onLogout: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(statusText, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onLogout) { Text("ออกจากระบบ") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("บัญชี Production", fontWeight = FontWeight.Bold)
                    Text(user.name.ifBlank { user.role.displayName })
                    if (user.phone.isNotBlank()) Text(user.phone)
                    Text("ประเภท: ${user.role.displayName}")
                    Text("สถานะ: ${user.status}")
                }
            }
        }
    }
}
