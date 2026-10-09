package com.eltavine.duckdetector.capability.selinuxpolicy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppZygoteMountSnapshotTest {
    @Test fun `structured carrier payload retains errno and denied entry`() {
        val snapshot = AppZygoteMountSnapshot(
            attempted = true, available = true, carrierVerified = true,
            attrAttempted = true, attrErrno = 13, mountInfoAvailable = true,
            paths = listOf(
                AppZygoteMountPath(
                    "/mnt/asec", attempted = true, available = true, errno = 13,
                    label = "u:object_r:tmpfs:s0", directoryAttempted = true,
                    directoryResultAvailable = true, directoryFirstEntry = null,
                    directoryErrno = 13, mountId = 44, mountFs = "tmpfs", mountSource = "tmpfs",
                ),
            ),
        )
        val output = AppZygoteMountPayloadCodec.decode(AppZygoteMountPayloadCodec.encode(snapshot))
        assertEquals(snapshot, output)
    }

    @Test fun `unstarted carrier and unknown payload are never clean`() {
        val empty = AppZygoteMountPayloadCodec.decode("")
        assertFalse(empty.attempted)
        assertFalse(empty.available)
        assertFalse(empty.carrierVerified)
        val invalid = AppZygoteMountPayloadCodec.decode("VERSION=99\nATTEMPTED=1")
        assertFalse(invalid.attempted)
        assertTrue(invalid.failureReason.orEmpty().contains("Unsupported"))
    }

    @Test fun `cross process mount id comparison never invents missing data`() {
        val zygote = AppZygoteMountSnapshot(
            attempted = true,
            paths = listOf(
                AppZygoteMountPath("/mnt", true, true, mountId = 12),
                AppZygoteMountPath("/mnt/obb", true, false),
            ),
        )
        val main = AppZygoteMountInfo.parse("13 1 0:13 / /mnt rw - tmpfs tmpfs rw\n")
        val comparison = AppZygoteMountInfo.compare(zygote, main)
        assertEquals(false, comparison[0].matches)
        assertNull(comparison[1].matches)
        assertEquals(13, comparison[0].mainId)
    }

    @Test fun `abnormal post collection failure can preserve successful app zygote observations`() {
        val observed = AppZygoteMountSnapshot(
            attempted = true,
            available = true,
            paths = listOf(AppZygoteMountPath("/mnt", true, true, label = "u:object_r:tmpfs:s0")),
        )
        val laterFailure = SelinuxContextValiditySnapshot(
            available = false, failureReason = "Later SELinux stage crashed",
            appZygoteMount = observed,
        )
        val decoded = SelinuxContextValidityBridge().parse(
            SelinuxContextValidityPayloadCodec.encode(laterFailure),
        )
        assertFalse(decoded.available)
        assertTrue(decoded.appZygoteMount.attempted)
        assertEquals(observed, decoded.appZygoteMount)
    }
}
