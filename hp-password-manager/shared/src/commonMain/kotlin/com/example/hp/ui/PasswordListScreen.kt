package com.example.hp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hp.data.Entry

@Composable
fun PasswordListScreen(
    entries: List<Entry>,
    onEntryClick: (Entry) -> Unit,
    onAddEntry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Пароли",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries.size) { index ->
                val entry = entries[index]
                EntryCard(entry = entry, onClick = { onEntryClick(entry) })
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(onClick = onAddEntry) {
                Text("+")
            }
        }
    }
}

@Composable
fun EntryCard(entry: Entry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = entry.login,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
