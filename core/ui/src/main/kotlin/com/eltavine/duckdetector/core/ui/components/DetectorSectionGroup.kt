/*
 * Copyright 2026 Duck Apps Contributor
 * If you have any questions, suggestions, or other inquiries, please email Eltavine <me@eltavine.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.eltavine.duckdetector.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveContent

internal data class DetectorMaterialSectionShape(
    val topRadius: Dp,
    val bottomRadius: Dp,
)

internal val LocalDetectorMaterialSectionShape = compositionLocalOf {
    DetectorMaterialSectionShape(topRadius = 16.dp, bottomRadius = 16.dp)
}

@DslMarker
public annotation class DetectorSectionGroupDsl

@DetectorSectionGroupDsl
public class DetectorSectionGroupScope internal constructor() {
    internal data class Entry(
        val content: @Composable () -> Unit,
    )

    internal val entries = mutableListOf<Entry>()

    public fun item(
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        if (visible) entries += Entry(content)
    }
}

/**
 * Material uses connected segmented-list geometry:
 * 16dp outer corners, 4dp connected inner corners, and a 2dp gap.
 * MIUIX keeps its native grouped rows and dividers.
 */
@Composable
public fun DetectorSectionGroup(
    modifier: Modifier = Modifier,
    content: DetectorSectionGroupScope.() -> Unit,
) {
    val entries = DetectorSectionGroupScope().apply(content).entries
    if (entries.isEmpty()) return

    AdaptiveContent(
        material = {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                entries.forEachIndexed { index, entry ->
                    CompositionLocalProvider(
                        LocalDetectorMaterialSectionShape provides DetectorMaterialSectionShape(
                            topRadius = if (index == 0) 16.dp else 4.dp,
                            bottomRadius = if (index == entries.lastIndex) 16.dp else 4.dp,
                        ),
                    ) {
                        entry.content()
                    }
                }
            }
        },
        miuix = {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                entries.forEach { entry -> entry.content() }
            }
        },
    )
}
