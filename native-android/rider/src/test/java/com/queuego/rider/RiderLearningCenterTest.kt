package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderLearningCenterTest {
    @Test fun learningCenterCoversCurrentQueueGoOperationalFlow() {
        val lessons = riderLearningLessons()
        assertEquals(8, lessons.size)
        assertEquals(lessons.size, lessons.map { it.id }.distinct().size)
        assertTrue(lessons.all { it.title.isNotBlank() && it.summary.isNotBlank() && it.steps.isNotEmpty() })

        val all = lessons.flatMap { listOf(it.title, it.summary) + it.steps }.joinToString("\n")
        assertTrue(all.contains("30 วินาที"))
        assertTrue(all.contains("Google Maps"))
        assertTrue(all.contains("1 รูป"))
        assertTrue(all.contains("200 เมตร"))
        assertTrue(all.contains("Market"))
        assertTrue(all.contains("ภารกิจ"))
    }

    @Test fun learningCenterDoesNotRestoreRetiredVerificationFlow() {
        val all = riderLearningLessons().flatMap { it.steps }.joinToString("\n").lowercase()
        assertTrue(all.contains("ไม่มีขั้นตอน pin หรือ otp"))
        assertTrue(all.contains("ไม่ใช่การบังคับ geofence 250 เมตร"))
        assertFalse(all.contains("ต้องอยู่ในระยะ 250 เมตร"))
        assertFalse(all.contains("สไลด์จบงาน"))
    }
}
