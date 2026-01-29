package com.wonderback.sudoku

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

private const val TAG = "SudokuTestApp"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.i(TAG, "║   SUDOKU TEST APP - TALKBACK AGENT TESTING           ║")
        Log.i(TAG, "╚═══════════════════════════════════════════════════════╝")
        Log.i(TAG, "MainActivity.onCreate() called")
        Log.i(TAG, "Setting up Compose UI with accessibility support")

        setContent {
            SudokuTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    SudokuScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "MainActivity.onStart() - App visible to user")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "MainActivity.onResume() - App ready for interaction")
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "MainActivity.onPause() - App losing focus")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "MainActivity.onStop() - App no longer visible")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MainActivity.onDestroy() - App shutting down")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
    }
}

@Composable
fun SudokuTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = lightColors(
            primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
            primaryVariant = androidx.compose.ui.graphics.Color(0xFF3700B3),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC5)
        ),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SudokuTestTheme {
        SudokuScreen()
    }
}
