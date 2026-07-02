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

@file:Suppress("ktlint:standard:function-naming")

package com.eltavine.duckdetector.core.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

private const val LONG_TOKEN_BREAK_INTERVAL = 12
private const val ZERO_WIDTH_SPACE = '\u200B'

@Composable
fun WrapSafeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val safeText = remember(text) { text.withWrapOpportunities() }

    Text(
        text = safeText,
        modifier = modifier,
        style = if (textAlign != null) style.copy(textAlign = textAlign) else style,
        color = color,
    )
}

private fun String.withWrapOpportunities(): String {
    if (length <= LONG_TOKEN_BREAK_INTERVAL) return this

    val builder = StringBuilder(length + (length / LONG_TOKEN_BREAK_INTERVAL))
    var uninterruptedCount = 0

    for (character in this) {
        builder.append(character)

        if (character.createsNaturalBreak()) {
            if (!character.isWhitespace()) {
                builder.append(ZERO_WIDTH_SPACE)
            }
            uninterruptedCount = 0
            continue
        }

        uninterruptedCount += 1
        if (uninterruptedCount >= LONG_TOKEN_BREAK_INTERVAL) {
            builder.append(ZERO_WIDTH_SPACE)
            uninterruptedCount = 0
        }
    }

    return builder.toString()
}

private fun Char.createsNaturalBreak(): Boolean = isWhitespace() || this in setOf(
    '-',
    '_',
    '/',
    '\\',
    '.',
    ':',
    ',',
    ';',
    '|',
    '+',
    '@',
    '#',
)
