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

/** Only actionable evidence gets a section badge: Danger takes precedence over Warning. */
public fun highestSectionSeverity(statuses: Iterable<DetectorStatus>): DetectionSeverity? {
    var warning = false
    for (status in statuses) {
        when (status.severity) {
            DetectionSeverity.DANGER -> return DetectionSeverity.DANGER
            DetectionSeverity.WARNING -> warning = true
            DetectionSeverity.INFO, DetectionSeverity.ALL_CLEAR -> Unit
        }
    }
    return if (warning) DetectionSeverity.WARNING else null
}
