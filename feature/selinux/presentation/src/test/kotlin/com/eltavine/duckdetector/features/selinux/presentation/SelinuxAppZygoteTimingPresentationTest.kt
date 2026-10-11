package com.eltavine.duckdetector.features.selinux.presentation

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.features.selinux.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelinuxAppZygoteTimingPresentationTest {
    @Test fun `positive timing raises method card and impact to danger without claiming root proof`() {
        val model = SelinuxCardModelMapper().map(report(timingResult(157, 14, 2, 400)))
        assertEquals(DetectorStatus.danger(), model.status)
        assertTrue(model.verdict.contains("App Zygote"))
        assertEquals(DetectorStatus.danger(), model.methodRows.single().status)
        assertTrue(model.methodRows.single().detail.orEmpty().contains("not proof"))
        assertTrue(model.impactItems.any { it.status == DetectorStatus.danger() &&
            it.text.contains("App Zygote invalid-context") })
    }

    @Test fun `weak sign and legacy sample never raise danger`() {
        for (result in listOf(timingResult(1, 11, 5, 400), timingResult(-104, 2, 14, 120),
            timingResult(157, 14, 2, 400, pairs = 128))) {
            val model = SelinuxCardModelMapper().map(report(result))
            assertEquals(DetectorStatus.allClear(), model.status)
            assertTrue(model.impactItems.none { it.text.contains("App Zygote invalid-context") })
        }
    }

    private fun timingResult(delta: Long, positive: Int, negative: Int, slower: Int, pairs: Int = 512): SelinuxCheckResult {
        val reading = SelinuxAvcLookupReading(
            collection = SelinuxAvcLookupCollection.TIMING_ONLY, writesPerBatch = 4096,
            batchesA = emptyList(), batchesB = emptyList(), completedRounds = 0,
            pairs = pairs, medianANs = 1000, medianBNs = 900, medianDeltaNs = delta,
            positiveBlocks = positive, negativeBlocks = negative, aSlower = slower,
            details = SelinuxAvcLookupCollectionDetails(null, 2, null, false, "EXITED", 0, 0, 0,
                3, -1, 0, 0, "No cache_stats"),
        )
        return SelinuxCheckResult(
            method = SelinuxOracle.APP_ZYGOTE_ATTR_TIMING.label,
            status = reading.timingVerdict.label,
            isSecure = if (reading.timingVerdict == SelinuxAppZygoteTimingVerdict.POSITIVE) false else null,
            permissionDenied = false, oracle = SelinuxOracle.APP_ZYGOTE_ATTR_TIMING, avcLookup = reading,
        )
    }

    private fun report(method: SelinuxCheckResult) = SelinuxReport(
        stage = SelinuxStage.READY, mode = SelinuxMode.ENFORCING,
        resolvedStatusLabel = "Enforcing", filesystemMounted = true, paradoxDetected = false,
        methods = listOf(method), processContext = null, contextType = null, policyAnalysis = null,
        auditIntegrity = null, androidVersion = "14", apiLevel = 34,
    )
}
