package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.DocHubAppUi
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.OfficeViewModel
import com.example.ui.viewmodel.OfficeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as DocHubApplication
            val viewModel: OfficeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = OfficeViewModelFactory(app.repository)
            )
            val themeMode by viewModel.appTheme.collectAsState()

            // Handle incoming document intents dynamically
            androidx.compose.runtime.LaunchedEffect(intent) {
                intent?.let { incomingIntent ->
                    if (incomingIntent.action == android.content.Intent.ACTION_VIEW ||
                        incomingIntent.action == android.content.Intent.ACTION_SEND
                    ) {
                        val uri = if (incomingIntent.action == android.content.Intent.ACTION_SEND) {
                            incomingIntent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                        } else {
                            incomingIntent.data
                        }
                        uri?.let { fileUri ->
                            viewModel.loadDocumentFromUri(contentResolver, fileUri)
                        }
                    }
                }
            }

            MyApplicationTheme(themeMode = themeMode) {
                DocHubAppUi(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
