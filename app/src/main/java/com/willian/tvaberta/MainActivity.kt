package com.willian.tvaberta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.willian.tvaberta.ui.TvRoot

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TvRoot(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.player.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        vm.player.onActivityStop(false)
    }
}
