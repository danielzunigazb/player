package com.danielzuniga.player.ui.terminal

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.danielzuniga.player.R
import com.danielzuniga.player.ui.components.BlockCursor
import com.danielzuniga.player.ui.components.DzControlShape
import com.danielzuniga.player.ui.components.DzIconButton
import com.danielzuniga.player.ui.components.DzMark
import com.danielzuniga.player.ui.components.Hairline
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType

/**
 * The player as a shell: an obsidian console with a gold `$` prompt, the block cursor, a
 * scrollback of what you ran and live completions from your library above the keyboard.
 */
@Composable
fun TerminalScreen(
    shell: Shell,
    lines: SnapshotStateList<ShellLine>,
    onClose: () -> Unit,
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var inputLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val suggestions = remember(input.text, lines.size) { shell.suggest(input.text) }

    LaunchedEffect(Unit) {
        if (lines.isEmpty()) lines += shell.banner()
        focus.requestFocus()
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun submit() {
        val text = input.text
        input = TextFieldValue("")
        when (shell.uiAction(text)) {
            Shell.CLEAR -> lines.clear()
            Shell.EXIT -> onClose()
            else -> lines += shell.run(text)
        }
    }

    Surface(color = Dz.colors.bg, contentColor = Dz.colors.ink, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                DzMark(size = 20.dp)
                Text(
                    text = "daniel@player:~/música",
                    style = DzType.small,
                    color = Dz.colors.inkMuted,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                )
                DzIconButton(DzIcons.Close, stringResource(R.string.terminal_close), onClose, bordered = false)
            }
            Hairline()

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = null) { focus.requestFocus() }
                    .testTag("terminal_output"),
            ) {
                itemsIndexed(lines) { index, line ->
                    // A little air before each new command keeps the scrollback readable.
                    if (line.kind == LineKind.INPUT && index > 0) Spacer(Modifier.padding(top = 8.dp))
                    OutputLine(line)
                }
            }

            if (suggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(suggestions) { suggestion ->
                        Text(
                            text = suggestion.trimEnd(),
                            style = DzType.small,
                            color = Dz.colors.inkMuted,
                            modifier = Modifier
                                .border(1.dp, Dz.colors.line, DzControlShape)
                                .clickable {
                                    input = TextFieldValue(suggestion, selection = TextRange(suggestion.length))
                                    focus.requestFocus()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Hairline()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text("$", style = DzType.body, color = Dz.colors.gold)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = DzType.body.copy(color = Dz.colors.ink),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    onTextLayout = { inputLayout = it },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            inner()
                            // The block cursor sits where the caret really is.
                            // The layout can lag one frame behind the text, so clamp to what it measured.
                            val caretX = inputLayout?.let { layout ->
                                val measured = layout.layoutInput.text.length
                                layout.getCursorRect(input.selection.start.coerceIn(0, measured)).left
                            } ?: 0f
                            BlockCursor(
                                height = 16.dp,
                                modifier = Modifier.offset { IntOffset(caretX.roundToInt(), 0) },
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .testTag("terminal_input"),
                )
                DzIconButton(DzIcons.ArrowRight, stringResource(R.string.terminal_run), { submit() }, bordered = false, tint = Dz.colors.gold)
            }
        }
    }
}

@Composable
private fun OutputLine(line: ShellLine) {
    val c = Dz.colors
    if (line.kind == LineKind.INPUT) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.gold)) { append("$ ") }
                append(line.text)
            },
            style = DzType.small,
            color = c.ink,
        )
        return
    }
    Text(
        text = line.text,
        style = DzType.small,
        color = when (line.kind) {
            LineKind.OK -> c.verdigris
            LineKind.ERR -> c.ember
            LineKind.DIM -> c.inkMuted
            else -> c.ink
        },
    )
}
