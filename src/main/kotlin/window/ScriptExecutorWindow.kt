package window

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.*
import common.LocalAppResources
import kotlinx.coroutines.launch
import util.FileDialog
import util.YesNoCancelDialog
import javax.swing.BoxLayout
import javax.swing.JPanel
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.regex.Pattern

var codeEditor: RTextScrollPane? = null
@Composable
fun ScriptExecutorWindow(state: ScriptExecutorWindowState) {
    val scope = rememberCoroutineScope()

    fun exit() = scope.launch { state.exit() }
    fun runOrStopScript() = scope.launch { state.runOrStopScript() }

    Window(
        state = state.window,
        title = titleOf(state),
        icon = LocalAppResources.current.icon,
        onCloseRequest = { exit() }
    ) {
        LaunchedEffect(Unit) { state.run() }

        WindowNotifications(state)
        WindowMenuBar(state)

        Column {
            Row(
                horizontalArrangement = Arrangement.End
            ) {
                val status by mutableStateOf(state.status)
                val iconPath by mutableStateOf(state.buttonIconPath)
                Button(
                    modifier = Modifier.size(50.dp, 50.dp),
                    onClick = { runOrStopScript() }
                ) {
                    Image(painterResource(iconPath.value), "")
                }
                Text(
                    text = status.value,
                    modifier = Modifier.height(50.dp).align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.button,
                    textAlign = TextAlign.Center
                )
            }
            CodeEditor(mutableStateOf(state.text), modifier = Modifier.fillMaxSize().padding(10.dp).weight(4f), state)
            Row (
                modifier = Modifier.fillMaxWidth().height(100.dp).padding(10.dp).weight(1f).border(width = 1.dp, color = Color.Gray),
            ) {
                val scriptOutput by mutableStateOf(state.scriptOutput)
                val isRunning by mutableStateOf(state.isRunning)
                val scroll = rememberScrollState(0)
                LaunchedEffect(scriptOutput.value) {
                    scroll.scrollTo(scroll.maxValue)
                }
                val filename = state.path?.toFile()?.name

                val textWithLinks = getTextWithLinks(scriptOutput.value, filename)
                ClickableText(
                    text = textWithLinks,
                    modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(scroll),
                    style = MaterialTheme.typography.body2,
                    onClick = { offset ->
                        textWithLinks.getStringAnnotations(
                            tag = "link_tag",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { stringAnnotation ->
                            println(stringAnnotation.item)
                            val lineStr = stringAnnotation.item.split(':')[1]
                            val charPos = stringAnnotation.item.split(':')[2]
                            println("line number: ${lineStr.toInt()}, pos: ${charPos.toInt()}")
                            val position = codeEditor?.textArea?.getLineStartOffset(lineStr.toInt() - 1)?.plus(charPos.toInt() - 1)
                            println("calculated position: $position")
                            if (position != null) {
                                codeEditor?.textArea?.caretPosition = position
                            }
                            codeEditor?.textArea?.requestFocus()
                        }
                    }
                )
            }
        }

        if (state.openDialog.isAwaiting) {
            FileDialog(
                title = "ScriptExecutor",
                isLoad = true,
                onResult = {
                    state.openDialog.onResult(it)
                }
            )
        }

        if (state.saveDialog.isAwaiting) {
            FileDialog(
                title = "ScriptExecutor",
                isLoad = false,
                onResult = { state.saveDialog.onResult(it) }
            )
        }

        if (state.exitDialog.isAwaiting) {
            YesNoCancelDialog(
                title = "Notepad",
                message = "Save changes?",
                onResult = { state.exitDialog.onResult(it) }
            )
        }
    }
}

@Composable
fun CodeEditor(code: MutableState<String>, modifier: Modifier = Modifier, state: ScriptExecutorWindowState) {
    if (codeEditor == null) {
        val textArea = RSyntaxTextArea(20, 60);
        textArea.syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_KOTLIN
        textArea.isCodeFoldingEnabled = true
        textArea.antiAliasingEnabled = true

        val sp = RTextScrollPane(textArea)
        sp.textArea.text = code.value
        sp.textArea.addCaretListener {
            state.text = sp.textArea.text
            code.value = sp.textArea.text
        }

        codeEditor = sp
    }
    Box(modifier = modifier) {
        SwingPanel(
            background = Color.White,
            factory = {
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(codeEditor)
                }
            }
        )
    }
}

private fun titleOf(state: ScriptExecutorWindowState): String {
    val changeMark = if (state.isChanged) "*" else ""
    val filePath = state.path ?: "Untitled"
    return "$changeMark$filePath - ScriptExecutor"
}

@Composable
private fun WindowNotifications(state: ScriptExecutorWindowState) {
    // Usually we take into account something like LocalLocale.current here
    fun ScriptExecutorWindowNotification.format() = when (this) {
        is ScriptExecutorWindowNotification.SaveSuccess -> Notification(
            "File is saved", path.toString(), Notification.Type.Info
        )
        is ScriptExecutorWindowNotification.SaveError -> Notification(
            "File isn't saved", path.toString(), Notification.Type.Error
        )
    }

    LaunchedEffect(Unit) {
        state.notifications.collect {
            state.sendNotification(it.format())
        }
    }
}

@Composable
private fun FrameWindowScope.WindowMenuBar(state: ScriptExecutorWindowState) = MenuBar {
    val scope = rememberCoroutineScope()

    fun save() = scope.launch { state.save() }
    fun open() = scope.launch { state.open() }
    fun exit() = scope.launch { state.exit() }

    Menu("File") {
        Item("New window", onClick = state::newWindow)
        Item("Open...", onClick = { open() })
        Item("Save", onClick = { save() }, enabled = state.isChanged || state.path == null)
        Separator()
        Item("Exit", onClick = { exit() })
    }

    Menu("Settings") {
        Item(
            if (state.settings.isTrayEnabled) "Hide tray" else "Show tray",
            onClick = state.settings::toggleTray
        )
        Item(
            if (state.window.placement == WindowPlacement.Fullscreen) "Exit fullscreen" else "Enter fullscreen",
            onClick = state::toggleFullscreen
        )
    }
}

fun getTextWithLinks(text: String, filename: String?) : AnnotatedString {
    return buildAnnotatedString {
    val codeLinksPattern = Pattern.compile(
        "($filename:(\\d+):(\\d))",
        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
    )
    val matcher = codeLinksPattern.matcher(text)
    var matchStart: Int
    var matchEnd = 0
    var previousMatchStart = 0
    while (matcher.find()) {
        matchStart = matcher.start(1)
        matchEnd = matcher.end()
        val beforeMatch = text.substring(
            startIndex = previousMatchStart,
            endIndex = matchStart
        )
        val tagMatch = text.substring(
            startIndex = matchStart,
            endIndex = matchEnd
        )
        append(
            beforeMatch
        )
        val annotation = text.substring(
            startIndex = matchStart,
            endIndex = matchEnd
        )
        pushStringAnnotation(tag = "link_tag", annotation = annotation)
        withStyle(
            SpanStyle(
                color = Color.Blue,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(
                tagMatch
            )
        }
        pop()
        previousMatchStart = matchEnd
    }
    if (text.length > matchEnd) {
        append(
            text.substring(
                startIndex = matchEnd,
                endIndex = text.length
            )
        )
    }
}
}