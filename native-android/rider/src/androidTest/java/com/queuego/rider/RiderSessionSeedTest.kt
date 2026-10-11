package com.queuego.rider

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class RiderSessionSeedTest {
    @Test
    fun seedAuthenticatedSessionFromInstrumentationArguments() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext

        fun required(name: String): String =
            args.getString(name)?.takeIf { it.isNotBlank() }
                ?: error("Missing instrumentation argument: $name")

        val name = String(
            Base64.decode(required("user_name_b64"), Base64.NO_WRAP),
            Charsets.UTF_8
        )

        val auth = QueueGoAuth(
            session = QueueGoSession(
                authUserId = required("auth_user_id"),
                accessToken = required("access_token"),
                refreshToken = args.getString("refresh_token")?.takeIf { it.isNotBlank() },
                expiresAtMs = required("expires_at_ms").toLong(),
                sessionId = required("session_id")
            ),
            user = QueueGoUser(
                id = required("user_id"),
                name = name,
                role = "rider",
                status = "active"
            )
        )
        SessionStore(context).save(auth)
        val restored = SessionStore(context).load() ?: error("Seeded session was not readable")
        assertEquals(auth.user.id, restored.user.id)
        assertEquals(auth.session.sessionId, restored.session.sessionId)
    }
}
