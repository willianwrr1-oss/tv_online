package com.willian.tvaberta

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import com.willian.tvaberta.data.SettingsStore
import com.willian.tvaberta.ui.AppRoot
import com.willian.tvaberta.ui.enterPip

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.player.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        vm.player.onActivityStop(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.player.current != null && SettingsStore.value.pipOnLeave && vm.player.isPlayingNow()) {
            enterPip(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPip = isInPictureInPictureMode
        // Fechou a janelinha pelo X: a tela já não está visível, então para o vídeo.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            vm.player.pauseNow()
        }
    }
}
