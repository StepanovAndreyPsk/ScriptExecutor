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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

var codeEditor: RTextScrollPane? = null
@Composable
fun ScriptExecutorWindow(state: ScriptExecutorWindowState) {
    val scope = rememberCoroutineScope()

    fun exit() = scope.launch { state.exit() }
    fun runScript() = scope.launch { state.runScript() }

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
//                modifier = Modifier.fillMaxWidth().height(50.dp).padding(5.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    modifier = Modifier.size(50.dp, 50.dp),
                    onClick = { runScript() }
                ) {
                    Image(painterResource("run.png"), "")
                }
            }
            CodeEditor(mutableStateOf(state.text), modifier = Modifier.fillMaxSize().padding(10.dp).weight(4f))
            Row (
                modifier = Modifier.fillMaxWidth().height(100.dp).padding(10.dp).weight(1f).border(width = 1.dp, color = Color.Gray),
            ) {
                val scriptOutput by mutableStateOf(state.scriptOutput)
                val scroll = rememberScrollState(0)
                LaunchedEffect(scriptOutput.value) {
                    scroll.scrollTo(scroll.maxValue)
                }
                Text(
                    text = scriptOutput.value,
                    modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(scroll),
                    style = MaterialTheme.typography.body2
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
fun CodeEditor(code: MutableState<String>, modifier: Modifier = Modifier) {
    if (codeEditor == null) {
        // create the scrollpane only once. Otherwise when text area value is
        // changed, the compose function is called from addCaretListener,
        // and a new code editor is created, with invalid caret position.
        val textArea = RSyntaxTextArea(20, 60);
        textArea.syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_KOTLIN
        textArea.isCodeFoldingEnabled = true
        textArea.antiAliasingEnabled = true

        val sp = RTextScrollPane(textArea)
        sp.textArea.text = code.value
        sp.textArea.addCaretListener { code.value = sp.textArea.text }

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