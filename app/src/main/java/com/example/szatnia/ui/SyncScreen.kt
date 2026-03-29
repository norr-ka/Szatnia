package com.example.szatnia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.szatnia.domain.CostumeCategory
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(
    coordinator: SzatniaCoordinator,
    snackbars: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var webAppUrl by remember(coordinator.webAppUrl) { mutableStateOf(coordinator.webAppUrl) }
    var choirCsv by remember { mutableStateOf("") }
    var historyCsv by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CostumeCategory.RED_DRESSES) }
    val snapshot = coordinator.snapshot

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Google Sheets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = webAppUrl,
                        onValueChange = {
                            webAppUrl = it
                            coordinator.updateWebAppUrl(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Adres Web App Google Apps Script") },
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coordinator.persistWebAppUrl().showIn(snackbars, scope)
                        },
                    ) {
                        Text("ZAPISZ ADRES")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                coordinator.loadFromSheets().showIn(snackbars, this)
                            }
                        },
                    ) {
                        Text("POBIERZ Z GOOGLE SHEETS")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                coordinator.pushToSheets().showIn(snackbars, this)
                            }
                        },
                    ) {
                        Text("ZAPISZ DO GOOGLE SHEETS")
                    }
                }
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Aktualny stan lokalny",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Chórzyści: ${snapshot.choirMembers.size}")
                    Text("Stroje: ${snapshot.costumes.size}")
                    Text("Historia wpisów: ${snapshot.historyEntries.size}")
                }
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Import chórzystów z CSV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Wklej cały aktualny plik CSV. Brakujące albo zduplikowane numery ewidencyjne dostaną bezpieczne identyfikatory techniczne.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = choirCsv,
                        onValueChange = { choirCsv = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        label = { Text("CSV chórzystów") },
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                coordinator.importChoirMembersCsv(choirCsv).showIn(snackbars, this)
                            }
                        },
                    ) {
                        Text("IMPORTUJ CHÓRZYSTÓW")
                    }
                }
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Import historii kategorii",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Wklej historię jednej kategorii. Aplikacja przeliczy aktualny stan strojów wyłącznie z dat wypożyczeń i zwrotów.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CostumeCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = historyCsv,
                        onValueChange = { historyCsv = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        label = { Text("CSV historii wypożyczeń") },
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                coordinator.importHistoryCsv(historyCsv, selectedCategory)
                                    .showIn(snackbars, this)
                            }
                        },
                    ) {
                        Text("IMPORTUJ HISTORIĘ KATEGORII")
                    }
                }
            }
        }
    }
}
