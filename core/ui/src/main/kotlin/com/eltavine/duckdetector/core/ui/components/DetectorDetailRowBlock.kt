/*
 * Copyright 2026 Duck Apps Contributor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.eltavine.duckdetector.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.ui.presentation.rememberStatusAppearance
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveDetailValueRow

@Composable
public fun DetectorDetailRowBlock(
    label: String,
    value: String,
    status: DetectorStatus,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
    detail: String? = null,
    detailMonospace: Boolean = false,
    statusIcon: ImageVector? = null,
    verticalPadding: Dp = 12.dp,
) {
    val appearance = rememberStatusAppearance(status)
    AdaptiveDetailValueRow(
        label = label,
        value = value,
        icon = statusIcon ?: appearance.icon,
        iconTint = appearance.iconTint,
        modifier = modifier,
        valueModifier = valueModifier,
        detail = detail,
        detailMonospace = detailMonospace,
        materialVerticalPadding = verticalPadding,
        materialLabelStyle = DuckTypography.Callout,
        materialValueStyle = DuckTypography.CalloutEmphasized,
        materialDetailStyle = DuckTypography.PanelSupporting,
    )
}
