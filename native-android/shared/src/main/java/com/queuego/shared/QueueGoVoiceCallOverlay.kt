package com.queuego.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal fun NativeVoiceCall.counterpartName(currentUserId: String): String {
    val value = if (callerUserId == currentUserId) calleeName else callerName
    if (!value.isNullOrBlank()) return value
    val role = if (callerUserId == currentUserId) calleeRole else callerRole
    return when (role) {
        "shop" -> "ร้านค้า"
        "rider" -> "Rider"
        "customer" -> "ลูกค้า"
        else -> "ผู้ใช้งาน QueueGo"
    }
}

@Composable
fun QueueGoVoiceCallOverlay(
    state: NativeVoiceControllerState,
    currentUserId: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onDismissEnded: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit
) {
    if (state.phase == NativeVoicePhase.IDLE) return
    val call = state.call
    val name = call?.counterpartName(currentUserId) ?: "QueueGo"
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Q"

    Dialog(
        onDismissRequest = {
            if (state.phase in setOf(NativeVoicePhase.ENDED, NativeVoicePhase.ERROR)) {
                onDismissEnded()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state.phase in setOf(NativeVoicePhase.ENDED, NativeVoicePhase.ERROR),
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                tonalElevation = 6.dp,
                shadowElevation = 14.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(82.dp)
                            .background(QgRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initial,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF221E20),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        voicePhaseText(state),
                        color = if (state.phase == NativeVoicePhase.ERROR) Color(0xFFB00020) else QgMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    state.error?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            it,
                            color = Color(0xFFB00020),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    when (state.phase) {
                        NativeVoicePhase.INCOMING -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDecline,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("ปฏิเสธ", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onAnswer,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                                ) {
                                    Text("รับสาย", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        NativeVoicePhase.RINGING_OUT,
                        NativeVoicePhase.CONNECTING -> {
                            Button(
                                onClick = onHangUp,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD1E2D))
                            ) {
                                Text("วางสาย", fontWeight = FontWeight.Bold)
                            }
                        }

                        NativeVoicePhase.CONNECTED -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onToggleMute(!state.muted) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(15.dp)
                                ) {
                                    Text(
                                        if (state.muted) "เปิดไมค์" else "ปิดไมค์",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onToggleSpeaker(!state.speaker) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(15.dp)
                                ) {
                                    Text(
                                        if (state.speaker) "ปิดลำโพง" else "ลำโพง",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onHangUp,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD1E2D))
                            ) {
                                Text("วางสาย", fontWeight = FontWeight.Bold)
                            }
                        }

                        NativeVoicePhase.ENDED,
                        NativeVoicePhase.ERROR -> {
                            Button(
                                onClick = onDismissEnded,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                            ) {
                                Text("ปิด", fontWeight = FontWeight.Bold)
                            }
                        }

                        NativeVoicePhase.IDLE -> Unit
                    }
                }
            }
        }
    }
}

private fun voicePhaseText(state: NativeVoiceControllerState): String = when (state.phase) {
    NativeVoicePhase.IDLE -> ""
    NativeVoicePhase.RINGING_OUT -> "กำลังโทรผ่าน QueueGo"
    NativeVoicePhase.INCOMING -> "สายเรียกเข้า QueueGo"
    NativeVoicePhase.CONNECTING -> "กำลังเชื่อมต่อเสียง"
    NativeVoicePhase.CONNECTED -> "กำลังสนทนา"
    NativeVoicePhase.ENDED -> state.error ?: "สายสิ้นสุดแล้ว"
    NativeVoicePhase.ERROR -> "ไม่สามารถเชื่อมต่อสายได้"
}
