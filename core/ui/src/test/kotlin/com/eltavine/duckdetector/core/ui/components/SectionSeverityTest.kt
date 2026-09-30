/*
 * Copyright 2026 Duck Apps Contributor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eltavine.duckdetector.core.ui.components

import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionSeverityTest {
    @Test
    fun `danger takes precedence even if warning comes first`() {
        assertEquals(
            DetectionSeverity.DANGER,
            highestSectionSeverity(listOf(DetectorStatus.warning(), DetectorStatus.danger())),
        )
    }

    @Test
    fun `warning is shown if no danger exists`() {
        assertEquals(
            DetectionSeverity.WARNING,
            highestSectionSeverity(listOf(DetectorStatus.allClear(), DetectorStatus.warning())),
        )
    }

    @Test
    fun `empty and informational sections have no warning label`() {
        assertEquals(null, highestSectionSeverity(emptyList()))
        assertEquals(
            null,
            highestSectionSeverity(listOf(DetectorStatus.info(InfoKind.ERROR), DetectorStatus.allClear())),
        )
    }
}
