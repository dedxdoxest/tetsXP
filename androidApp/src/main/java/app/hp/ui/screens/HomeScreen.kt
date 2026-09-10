package app.hp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Главный экран приложения
 * Секции: Пароли, Коды подтверждения, Wi-Fi, Заметки
 * Поиск - внизу экрана (как в Apple Passwords)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit = {},
    onCreateEntry: () -> Unit = {},
    onEntryClick: (String) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пароли") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateEntry,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Секции
            SectionCard(
                title = "Пароли",
                count = 42,
                onClick = { }
            )
            
            SectionCard(
                title = "Коды подтверждения",
                count = 5,
                onClick = { }
            )
            
            SectionCard(
                title = "Wi-Fi",
                count = 3,
                onClick = { }
            )
            
            SectionCard(
                title = "Заметки",
                count = 8,
                onClick = { }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Поиск внизу
            SearchBar(
                onClick = onSearchClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Badge {
                Text(count.toString())
            }
        }
    }
}

@Composable
private fun SearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = "",
        onValueChange = { },
        modifier = modifier
            .padding(bottom = 16.dp),
        placeholder = { Text("Поиск") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Поиск")
        },
        readOnly = true,
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}
