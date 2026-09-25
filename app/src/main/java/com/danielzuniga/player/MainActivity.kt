package com.danielzuniga.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.danielzuniga.player.ui.MusicViewModel
import com.danielzuniga.player.ui.PlayerApp
import com.danielzuniga.player.ui.theme.PlayerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerTheme {
                PlayerApp(viewModel)
            }
        }
    }
}
