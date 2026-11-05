package com.c99.komikureader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.c99.komikureader.ui.navigation.KomikuNavHost
import com.c99.komikureader.ui.theme.KomikuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only honour notification extras on a fresh launch, not when restoring after rotation.
        val openSlug = if (savedInstanceState == null) intent.getStringExtra(EXTRA_OPEN_SLUG) else null
        val openLibrary = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false)
        setContent {
            KomikuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KomikuNavHost(openSlug = openSlug, openLibrary = openLibrary)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SLUG = "open_slug"
        const val EXTRA_OPEN_LIBRARY = "open_library"
    }
}
