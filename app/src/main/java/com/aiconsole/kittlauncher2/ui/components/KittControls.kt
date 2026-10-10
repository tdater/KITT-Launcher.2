package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ReadoutFont
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

/** Single-line amber monospace input in a black bezel. */
@Composable
fun KittTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(48.dp),
        singleLine = true,
        textStyle = TextStyle(fontFamily = ReadoutFont, fontSize = 16.sp, color = DashAmber),
        cursorBrush = SolidColor(DashAmber),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(KittBlack)
                    .border(1.dp, KittBezel, shape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = DashGrey, maxLines = 1)
                }
                inner()
            }
        }
    )
}

/** Labelled on/off switch drawn as an LED indicator plus KITT button. */
@Composable
fun KittToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(width = 14.dp, height = 22.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (checked) LedGreen else LedGreen.copy(alpha = 0.12f))
        )
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = DashWhite)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = DashGrey)
            }
        }
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.labelLarge,
            color = if (checked) LedGreen else DashGrey
        )
    }
}

/** Horizontally scrolling single-choice row of KITT buttons. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DashAmber,
    enabled: (T) -> Boolean = { true }
) {
    LazyRow(modifier = modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { option ->
            KittButton(
                label = label(option),
                onClick = { onSelect(option) },
                modifier = Modifier.widthIn(min = 72.dp).height(44.dp),
                color = color,
                active = option == selected,
                enabled = enabled(option)
            )
        }
    }
}

/** Section divider with a red accent tab, like the panel headers. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = ScannerRed) {
    Row(
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(width = 4.dp, height = 14.dp).background(color))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Small explanatory text under a setting. */
@Composable
fun HelpText(text: String, modifier: Modifier = Modifier, color: Color = DashGrey) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = color)
}
