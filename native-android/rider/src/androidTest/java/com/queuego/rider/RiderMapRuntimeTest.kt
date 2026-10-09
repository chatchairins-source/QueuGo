package com.queuego.rider

import android.os.Process
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RiderMapRuntimeTest {
    @Test fun homeMapMountsBackgroundsAndRecreatesWithoutMissingSdkClasses() {
        ActivityScenario.launch(RiderMapRuntimeActivity::class.java).use { scenario ->
            fun awaitRealMap() {
                var ready: CountDownLatch? = null
                scenario.onActivity { ready = it.mapReady }
                assertTrue("Longdo did not create the actual native map", ready!!.await(30, TimeUnit.SECONDS))
            }
            awaitRealMap()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitRealMap()
            scenario.recreate()
            awaitRealMap()
        }
        Log.i("QueueGoMapRuntime", "completed_pid=${Process.myPid()}")
    }
}
