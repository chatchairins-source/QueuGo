package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderMissionsTest {
    @Test fun missionParserKeepsOnlyValidServerMissionsAndBoundsProgress() {
        val parsed = riderMissionsFromRpc(
            JSONArray()
                .put(
                    JSONObject()
                        .put("id","m1")
                        .put("title","ส่งครบ 10 เที่ยว")
                        .put("target_jobs",10)
                        .put("progress_jobs",12)
                        .put("state","completed")
                        .put("completed",true)
                )
                .put(JSONObject().put("id","").put("title","bad").put("target_jobs",2))
        )
        assertEquals(1, parsed.size)
        assertEquals(10, parsed.single().progressJobs)
        assertTrue(parsed.single().completed)
    }

    @Test fun missionParserNeverInventsMissionFromEmptyBackend() {
        val parsed = riderMissionsFromRpc(JSONArray())
        assertTrue(parsed.isEmpty())
    }

    @Test fun activeMissionProgressRemainsServerOwned() {
        val parsed = riderMissionsFromRpc(
            JSONObject()
                .put("id","m2")
                .put("title","ภารกิจจริง")
                .put("target_jobs",5)
                .put("progress_jobs",2)
                .put("state","active")
                .put("completed",false)
        ).single()
        assertEquals(2, parsed.progressJobs)
        assertFalse(parsed.completed)
    }
}
