package com.eltavine.duckdetector.features.selinux.data.repository

import com.eltavine.duckdetector.capability.selinuxpolicy.data.AppZygoteMountPath
import com.eltavine.duckdetector.capability.selinuxpolicy.data.AppZygoteMountSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppZygoteMountMethodTest {
    @Test fun `verified carrier provides supporting evidence without root verdict`() {
        val snapshot = AppZygoteMountSnapshot(
            attempted = true, available = true, carrierVerified = true, mountInfoAvailable = true,
            paths = listOf(AppZygoteMountPath(
                "/mnt", true, true, label = "u:object_r:tmpfs:s0",
                directoryFirstEntry = true, mountId = 15, mountFs = "tmpfs",
            )),
        )
        val result = buildAppZygoteMountMethod(
            snapshot,
            "16 1 0:12 / /mnt rw - tmpfs tmpfs rw\n",
        )
        assertEquals("Info", result.status)
        assertNull(result.isSecure)
        assertTrue(result.details.orEmpty().contains("equal=false"))
    }

    @Test fun `denial, missing preload and carrier crash never mean clean`() {
        val denied = buildAppZygoteMountMethod(
            AppZygoteMountSnapshot(
                attempted = true, available = false, carrierVerified = false,
                attrErrno = 13, failureReason = "SELinux preload crashed",
            ),
            "",
        )
        assertEquals("Unavailable", denied.status)
        assertNull(denied.isSecure)
        assertTrue(denied.details.orEmpty().contains("SELinux preload crashed"))
        val notStarted = buildAppZygoteMountMethod(AppZygoteMountSnapshot(), null)
        assertEquals("Unavailable", notStarted.status)
        assertFalse(notStarted.details.orEmpty().contains("equal=true"))
    }
}
