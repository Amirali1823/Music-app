package com.example.androidmusicapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.example.androidmusicapp.data.Song
import com.example.androidmusicapp.ui.AppTheme
import com.example.androidmusicapp.ui.PlayerViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // handle result
        }

        // Request READ permission on launch (for pre-Android 13)
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    // Placeholder ViewModel integration
    val songs = listOf<Song>() // TODO: collect from ViewModel

    Scaffold(topBar = { TopAppBar(title = { Text("AndroidMusicApp") }) }) {
        Column {
            SongList(songs = songs) { /* onClick play */ }
        }
    }
}

@Composable
fun SongList(songs: List<Song>, onClick: (Song) -> Unit) {
    LazyColumn {
        items(songs) { song ->
            Row(modifier = Modifier.clickable { onClick(song) }) {
                Text(text = song.title)
                Text(text = " — ${'$'}{song.artist ?: "Unknown"}")
                Text(text = "  plays: ${'$'}{song.playCount}")
            }
            Divider()
        }
    }
}
