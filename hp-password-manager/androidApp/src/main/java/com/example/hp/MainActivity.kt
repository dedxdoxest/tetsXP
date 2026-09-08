package com.example.hp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.hp.data.Entry
import com.example.hp.ui.PasswordListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sampleEntries = listOf(
                        Entry(
                            id = "1",
                            name = "Google",
                            login = "user@gmail.com",
                            password = "********",
                            url = "https://google.com",
                            notes = null,
                            totpSecret = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        ),
                        Entry(
                            id = "2",
                            name = "GitHub",
                            login = "developer",
                            password = "********",
                            url = "https://github.com",
                            notes = null,
                            totpSecret = "JBSWY3DPEHPK3PXP",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    
                    PasswordListScreen(
                        entries = sampleEntries,
                        onEntryClick = { /* TODO: Navigate to details */ },
                        onAddEntry = { /* TODO: Navigate to create */ }
                    )
                }
            }
        }
    }
}
