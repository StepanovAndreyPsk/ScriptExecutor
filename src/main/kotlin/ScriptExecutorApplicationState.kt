import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import common.Settings
import window.ScriptExecutorWindowState

@Composable
fun rememberApplicationState() = remember {
    ScriptExecutorApplicationState().apply {
        newWindow()
    }
}

class ScriptExecutorApplicationState {
    val settings = Settings()
    val tray = TrayState()

    private val _windows = mutableStateListOf<ScriptExecutorWindowState>()
    val windows: List<ScriptExecutorWindowState> get() = _windows

    fun newWindow() {
        _windows.add(
            ScriptExecutorWindowState(
                application = this,
                path = null,
                exit = _windows::remove
            )
        )
    }

    fun sendNotification(notification: Notification) {
        tray.sendNotification(notification)
    }

    suspend fun exit() {
        val windowsCopy = windows.reversed()
        for (window in windowsCopy) {
            if (!window.exit()) {
                break
            }
        }
    }
}