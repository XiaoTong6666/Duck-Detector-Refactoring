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

package com.eltavine.duckdetector.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveCardSurface

/** The dashboard and detail cards share one surface without changing their content or state. */
@Composable
public fun DuckPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClick: (() -> Unit)? = null,
    materialShape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val interactiveContentModifier = Modifier
        .fillMaxWidth()
        // Keep the indication node alive when the click target is disabled after an action.
        // Press/Release can therefore finish its native ripple/highlight without delaying the
        // state change that expands the card.
        .indication(interactionSource, indication)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
        .padding(contentPadding)

    // Keep one stable native Card composition while clickability changes. The click target stays
    // on the inner content so expanding/collapsing children retain their composition identity.
    AdaptiveCardSurface(
        modifier = modifier.fillMaxWidth(),
        materialShape = materialShape,
        materialContainerColor = DuckTheme.palette.groupedSurface,
    ) {
        Column(
            modifier = interactiveContentModifier,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
