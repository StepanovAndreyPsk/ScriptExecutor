package window

import ScriptExecutorApplicationState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import common.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import util.AlertDialogResult
import java.nio.file.Path
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class ScriptExecutorWindowState(
    private val application: ScriptExecutorApplicationState,
    path: Path?,
    private val exit: (ScriptExecutorWindowState) -> Unit
) {
    val settings: Settings get() = application.settings

    val window = WindowState()

    var path by mutableStateOf(path)
        private set

    var isChanged by mutableStateOf(false)
        private set

    var isRunning by mutableStateOf(false)

    val openDialog = DialogState<Path?>()
    val saveDialog = DialogState<Path?>()
    val exitDialog = DialogState<AlertDialogResult>()

    private var _notifications = Channel<ScriptExecutorWindowNotification>(0)
    val notifications: Flow<ScriptExecutorWindowNotification> get() = _notifications.receiveAsFlow()

    private var _text by mutableStateOf("")
    private var _scriptOutput = mutableStateOf("")
    private var _status = mutableStateOf("")
    private var _iconPath = mutableStateOf("run.png")

    private lateinit var process: Process

    var text: String
        get() = _text
        set(value) {
            check(isInit)
            _text = value
            isChanged = true
        }

    var scriptOutput: MutableState<String>
        get() = _scriptOutput
        set(value) {
            check(isInit)
            _scriptOutput = value
        }

    var status: MutableState<String>
        get() = _status
        set(value) {
            check(isInit)
            _status = value
        }

    var buttonIconPath: MutableState<String>
        get() = _iconPath
        set(value) {
            check(isInit)
            _iconPath = value
        }

    var isInit by mutableStateOf(false)
        private set

    fun toggleFullscreen() {
        window.placement = if (window.placement == WindowPlacement.Fullscreen) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Fullscreen
        }
    }
    suspend fun runOrStopScript() {
        if (isRunning) {
            stopScript()
        }
        else {
            runScript()
        }
    }

    suspend fun runScript() {
        println("running script...")
        scriptOutput.value = ""
        if (path == null) {
            withContext(Dispatchers.Default) {
                launch {
                    save()
                }
            }
        }

        if (path == null) {
            return
        }

        println("script successfully ran, printing result...")
        println("path = $path")
        val file = path?.toFile()
        assert(file != null)

        if (file != null) {
            assert(file.exists())
        }

        val p = ProcessBuilder("kotlinc",  "-script", path.toString())
        var output = ""
        var error = ""
        withContext(Dispatchers.IO) {
            process = p.start()
            isRunning = true
            status.value = "Running script..."
            buttonIconPath.value = "stop.png"
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))
            try {
                while (inputStream.readLine()?.also { output = it } != null || errorStream.readLine()
                        ?.also { error = it } != null) {
                    if (output.isNotEmpty()) {
                        scriptOutput.value += "\n$output"
                    }
                    if (error.isNotEmpty()) {
                        scriptOutput.value += "\n$error"
                    }
//                println("Debug: " + output)
                }
            } catch(e: IOException) {
                isRunning = false
                status.value = "Script stopped"
                buttonIconPath.value = "run.png"
            }
            finally {
                inputStream.close()
                errorStream.close()
            }

            if (isRunning) {
                process.waitFor()
                isRunning = false
                val exitCode = process.exitValue()
                status.value = "Script Executed, exit code: $exitCode"
                buttonIconPath.value = "run.png"
            }
        }
    }

    fun stopScript() {
        process.destroy()
        buttonIconPath.value = "run.png"
    }

    suspend fun run() {
        if (path != null) {
            open(path!!)
        } else {
            initNew()
        }
    }

    private suspend fun open(path: Path) {
        isInit = false
        this.path = path
        try {
            _text = path.readTextAsync()
            isInit = true
            codeEditor!!.textArea.text = path.readTextAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            text = "Cannot read $path"
        }
        isChanged = false
    }

    private fun initNew() {
        _text = ""
        isInit = true
        isChanged = false
    }

    fun newWindow() {
        application.newWindow()
    }

    suspend fun open() {
        if (askToSave()) {
            val path = openDialog.awaitResult()
            if (path != null) {
                open(path)
            }
        }
    }

    suspend fun save(): Boolean {
        check(isInit)
        if (path == null) {
            val path = saveDialog.awaitResult()
            if (path != null) {
                save(path)
                return true
            }
        } else {
            save(path!!)
            return true
        }
        return false
    }

    private var saveJob: Job? = null

    private suspend fun save(path: Path) {
        isChanged = false
        this.path = path

        saveJob?.cancel()
        saveJob = path.launchSaving(text)

        try {
            saveJob?.join()
            _notifications.trySend(ScriptExecutorWindowNotification.SaveSuccess(path))
        } catch (e: Exception) {
            isChanged = true
            e.printStackTrace()
            _notifications.trySend(ScriptExecutorWindowNotification.SaveError(path))
        }
    }

    suspend fun exit(): Boolean {
        return if (askToSave()) {
            exit(this)
            true
        } else {
            false
        }
    }

    private suspend fun askToSave(): Boolean {
        if (isChanged) {
            when (exitDialog.awaitResult()) {
                AlertDialogResult.Yes -> {
                    if (save()) {
                        return true
                    }
                }
                AlertDialogResult.No -> {
                    return true
                }
                AlertDialogResult.Cancel -> return false
            }
        } else {
            return true
        }

        return false
    }

    fun sendNotification(notification: Notification) {
        application.sendNotification(notification)
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun Path.launchSaving(text: String) = GlobalScope.launch {
    writeTextAsync(text)
}

private suspend fun Path.writeTextAsync(text: String) = withContext(Dispatchers.IO) {
    toFile().writeText(text)
}

private suspend fun Path.readTextAsync() = withContext(Dispatchers.IO) {
    toFile().readText()
}

sealed class ScriptExecutorWindowNotification {
    class SaveSuccess(val path: Path) : ScriptExecutorWindowNotification()
    class SaveError(val path: Path) : ScriptExecutorWindowNotification()
}

class DialogState<T> {
    private var onResult: CompletableDeferred<T>? by mutableStateOf(null)

    val isAwaiting get() = onResult != null

    suspend fun awaitResult(): T {
        onResult = CompletableDeferred()
        val result = onResult!!.await()
        onResult = null
        return result
    }

    fun onResult(result: T) = onResult!!.complete(result)
}