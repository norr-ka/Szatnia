package com.example.szatnia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.szatnia.data.ChoirRepository
import com.example.szatnia.data.GoogleSheetsSyncService
import com.example.szatnia.data.HistoryImportSummary
import com.example.szatnia.data.SnapshotImportService
import com.example.szatnia.data.SyncPreferences
import com.example.szatnia.domain.ChoirMember
import com.example.szatnia.domain.ChoirSnapshot
import com.example.szatnia.domain.Costume
import com.example.szatnia.domain.CostumeAvailability
import com.example.szatnia.domain.CostumeCategory
import com.example.szatnia.domain.MemberStatus
import com.example.szatnia.domain.RentalService
import com.example.szatnia.domain.VoicePart
import com.example.szatnia.domain.borrowerDisplayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

sealed interface Screen {
    data object Home : Screen
    data object Sync : Screen
    data object CostumeCategories : Screen
    data class CostumeList(val category: CostumeCategory) : Screen
    data object Members : Screen
    data class MemberProfile(val registryNumber: String) : Screen
}

class SzatniaCoordinator(
    private val repository: ChoirRepository,
    private val syncPreferences: SyncPreferences,
    private val syncService: GoogleSheetsSyncService,
    private val snapshotImportService: SnapshotImportService = SnapshotImportService(),
) {
    private val rentalService = RentalService(repository)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var sessionDate by mutableStateOf(LocalDate.now())
        private set

    var snapshot by mutableStateOf(repository.snapshot())
        private set

    var webAppUrl by mutableStateOf(syncPreferences.getWebAppUrl().orEmpty())
        private set

    fun goTo(screen: Screen) {
        this.screen = screen
    }

    fun goBack() {
        screen = when (screen) {
            Screen.Home -> Screen.Home
            Screen.Sync -> Screen.Home
            Screen.CostumeCategories -> Screen.Home
            is Screen.CostumeList -> Screen.CostumeCategories
            Screen.Members -> Screen.Home
            is Screen.MemberProfile -> Screen.Members
        }
    }

    fun updateSessionDate(date: LocalDate) {
        sessionDate = date
    }

    fun autoResetDateIfNeeded() {
        val today = LocalDate.now()
        if (sessionDate != today) {
            sessionDate = today
        }
    }

    fun borrowCostume(costumeNumber: String, registryNumber: String, deposit: Double?): Result<Unit> {
        return rentalService.borrowCostume(
            operationDate = sessionDate,
            costumeNumber = costumeNumber,
            registryNumber = registryNumber,
            deposit = deposit,
        ).also { refresh() }
    }

    fun returnCostume(costumeNumber: String): Result<Unit> {
        return rentalService.returnCostume(sessionDate, costumeNumber).also { refresh() }
    }

    fun addCostume(category: CostumeCategory, costumeNumber: String, size: String?): Result<Unit> {
        return rentalService.addCostume(category, costumeNumber, size).also { refresh() }
    }

    fun updateWebAppUrl(url: String) {
        webAppUrl = url
    }

    fun persistWebAppUrl(): Result<String> {
        return runCatching {
            val normalized = webAppUrl.trim()
            require(normalized.isNotBlank()) { "Wklej adres wdrożonej aplikacji Google Apps Script" }
            syncPreferences.saveWebAppUrl(normalized)
            webAppUrl = normalized
            "Zapisano adres integracji Google Sheets"
        }
    }

    suspend fun loadFromSheets(): Result<String> {
        return runCatching {
            val snapshotFromSheets = syncService.loadSnapshot(requireWebAppUrl()).getOrThrow()
            repository.replaceSnapshot(snapshotFromSheets)
            refresh()
            "Pobrano dane z Google Sheets"
        }
    }

    suspend fun pushToSheets(): Result<String> {
        return runCatching {
            syncService.replaceSnapshot(requireWebAppUrl(), snapshot).getOrThrow()
            "Zapisano pełny stan do Google Sheets"
        }
    }

    suspend fun importChoirMembersCsv(csv: String): Result<String> {
        return runCatching {
            require(csv.isNotBlank()) { "Wklej CSV chórzystów" }
            val (updatedSnapshot, importedCount) = snapshotImportService.replaceChoirMembers(snapshot, csv)
            repository.replaceSnapshot(updatedSnapshot)
            refresh()
            pushIfConfigured()
            "Zaimportowano $importedCount chórzystów"
        }
    }

    suspend fun importHistoryCsv(csv: String, category: CostumeCategory): Result<String> {
        return runCatching {
            require(csv.isNotBlank()) { "Wklej CSV historii wypożyczeń" }
            val (updatedSnapshot, summary) = snapshotImportService.replaceHistoryForCategory(
                snapshot = snapshot,
                csv = csv,
                category = category,
            )
            repository.replaceSnapshot(updatedSnapshot)
            refresh()
            pushIfConfigured()
            summary.toMessage(category)
        }
    }

    suspend fun borrowCostumeAndSync(
        costumeNumber: String,
        registryNumber: String,
        deposit: Double?,
    ): Result<String> {
        return runCatching {
            borrowCostume(costumeNumber, registryNumber, deposit).getOrThrow()
            pushIfConfigured()
            "Zapisano wypożyczenie"
        }
    }

    suspend fun returnCostumeAndSync(costumeNumber: String): Result<String> {
        return runCatching {
            returnCostume(costumeNumber).getOrThrow()
            pushIfConfigured()
            "Zapisano zwrot"
        }
    }

    suspend fun addCostumeAndSync(
        category: CostumeCategory,
        costumeNumber: String,
        size: String?,
    ): Result<String> {
        return runCatching {
            addCostume(category, costumeNumber, size).getOrThrow()
            pushIfConfigured()
            "Dodano strój"
        }
    }

    private fun refresh() {
        snapshot = repository.snapshot()
    }

    private suspend fun pushIfConfigured() {
        if (webAppUrl.isBlank()) return
        syncService.replaceSnapshot(webAppUrl, snapshot).getOrThrow()
    }

    private fun requireWebAppUrl(): String {
        return webAppUrl.trim().ifBlank {
            throw IllegalArgumentException("Najpierw zapisz adres aplikacji Google Apps Script")
        }
    }

    private fun HistoryImportSummary.toMessage(category: CostumeCategory): String {
        return "Zaimportowano $importedEntries wpisów dla ${category.label.lowercase()} i zaktualizowano $updatedCostumes strojów"
    }
}

data class BorrowDialogState(
    val presetCostumeNumber: String? = null,
    val presetRegistryNumber: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SzatniaApp(
    coordinator: SzatniaCoordinator,
    modifier: Modifier = Modifier,
) {
    val snapshot = coordinator.snapshot
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var addCostumeDialogOpen by rememberSaveable { mutableStateOf(false) }
    var borrowDialogState by remember { mutableStateOf<BorrowDialogState?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            coordinator.autoResetDateIfNeeded()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle(coordinator.screen),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (coordinator.screen != Screen.Home) {
                        TextButton(onClick = coordinator::goBack) {
                            Text("Wstecz")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(coordinator.sessionDate.format(dateFormatter))
                    }
                },
            )
        },
        floatingActionButton = {
            if (coordinator.screen == Screen.CostumeCategories) {
                FloatingActionButton(onClick = { addCostumeDialogOpen = true }) {
                    Text("+")
                }
            }
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val screen = coordinator.screen) {
                Screen.Home -> HomeScreen(
                    onMembersClick = { coordinator.goTo(Screen.Members) },
                    onCostumesClick = { coordinator.goTo(Screen.CostumeCategories) },
                    onSyncClick = { coordinator.goTo(Screen.Sync) },
                )

                Screen.Sync -> SyncScreen(coordinator = coordinator, snackbars = snackbars)

                Screen.CostumeCategories -> CostumeCategoriesScreen(
                    snapshot = snapshot,
                    onCategoryClick = { coordinator.goTo(Screen.CostumeList(it)) },
                )

                is Screen.CostumeList -> CostumeListScreen(
                    category = screen.category,
                    snapshot = snapshot,
                    onBorrowClick = { costume ->
                        borrowDialogState = BorrowDialogState(presetCostumeNumber = costume.costumeNumber)
                    },
                    onReturnClick = { costume ->
                        scope.launch {
                            coordinator.returnCostumeAndSync(costume.costumeNumber).showIn(snackbars, this)
                        }
                    },
                )

                Screen.Members -> MembersScreen(
                    snapshot = snapshot,
                    onMemberClick = { coordinator.goTo(Screen.MemberProfile(it.registryNumber)) },
                )

                is Screen.MemberProfile -> {
                    val member = snapshot.choirMembers.firstOrNull {
                        it.registryNumber == screen.registryNumber
                    }
                    if (member == null) {
                        EmptyState(
                            title = "Nie znaleziono chórzysty",
                            subtitle = "Lista mogła się zmienić po imporcie danych.",
                        )
                    } else {
                        MemberProfileScreen(
                            member = member,
                            snapshot = snapshot,
                            onReturnClick = { costume ->
                                scope.launch {
                                    coordinator.returnCostumeAndSync(costume.costumeNumber).showIn(snackbars, this)
                                }
                            },
                            onBorrowNewClick = {
                                borrowDialogState = BorrowDialogState(presetRegistryNumber = member.registryNumber)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        SessionDateDialog(
            selectedDate = coordinator.sessionDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                coordinator.updateSessionDate(it)
                showDatePicker = false
            },
        )
    }

    if (addCostumeDialogOpen) {
        AddCostumeDialog(
            onDismiss = { addCostumeDialogOpen = false },
            onConfirm = { category, number, size ->
                scope.launch {
                    coordinator.addCostumeAndSync(category, number, size).showIn(snackbars, this)
                    addCostumeDialogOpen = false
                }
            },
        )
    }

    borrowDialogState?.let { dialogState ->
        BorrowCostumeDialog(
            snapshot = snapshot,
            state = dialogState,
            onDismiss = { borrowDialogState = null },
            onConfirm = { costumeNumber, registryNumber, deposit ->
                scope.launch {
                    coordinator.borrowCostumeAndSync(costumeNumber, registryNumber, deposit)
                        .showIn(snackbars, this)
                    borrowDialogState = null
                }
            },
        )
    }
}

@Composable
private fun HomeScreen(
    onMembersClick: () -> Unit,
    onCostumesClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        LargeHomeTile(
            title = "CHÓRZYŚCI",
            subtitle = "Szybkie wyszukiwanie, profil i podgląd aktywnych strojów",
            onClick = onMembersClick,
        )
        LargeHomeTile(
            title = "STROJE",
            subtitle = "Kategorie, lista numerów i błyskawiczne wypożyczenia",
            onClick = onCostumesClick,
        )
        LargeHomeTile(
            title = "SYNC",
            subtitle = "Wklej CSV, pobierz stan i zapisz wszystko do Google Sheets",
            onClick = onSyncClick,
        )
    }
}

@Composable
private fun LargeHomeTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CostumeCategoriesScreen(
    snapshot: ChoirSnapshot,
    onCategoryClick: (CostumeCategory) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(CostumeCategory.entries) { category ->
            val categoryCostumes = snapshot.costumes.filter { it.category == category }
            val availableCount = categoryCostumes.count { it.availability == CostumeAvailability.AVAILABLE }
            val borrowedCount = categoryCostumes.size - availableCount
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category) },
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Dostępne: $availableCount  •  Wypożyczone: $borrowedCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CostumeListScreen(
    category: CostumeCategory,
    snapshot: ChoirSnapshot,
    onBorrowClick: (Costume) -> Unit,
    onReturnClick: (Costume) -> Unit,
) {
    val costumes = remember(snapshot, category) {
        snapshot.costumes
            .filter { it.category == category }
            .sortedBy { it.costumeNumber }
    }

    if (costumes.isEmpty()) {
        EmptyState(
            title = "Brak strojów w tej kategorii",
            subtitle = "Dodaj pierwszy egzemplarz z listy kategorii.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(costumes) { costume ->
            val borrowerName = costume.borrowerDisplayName(snapshot.choirMembers)
            CostumeCard(
                costume = costume,
                borrowerName = borrowerName,
                onBorrowClick = { onBorrowClick(costume) },
                onReturnClick = { onReturnClick(costume) },
            )
        }
    }
}

@Composable
private fun CostumeCard(
    costume: Costume,
    borrowerName: String?,
    onBorrowClick: () -> Unit,
    onReturnClick: () -> Unit,
) {
    val statusColor = if (costume.availability == CostumeAvailability.AVAILABLE) {
        Color(0xFF3C8C54)
    } else {
        Color(0xFFB94646)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(statusColor),
            )
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = costume.costumeNumber,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Rozmiar: ${costume.size ?: "brak"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (costume.availability == CostumeAvailability.AVAILABLE) {
                        "Status: dostępny"
                    } else {
                        "Status: wypożyczony przez ${borrowerName ?: "nieznaną osobę"}"
                    },
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (costume.availability == CostumeAvailability.AVAILABLE) {
                        onBorrowClick
                    } else {
                        onReturnClick
                    },
                ) {
                    Text(
                        if (costume.availability == CostumeAvailability.AVAILABLE) {
                            "WYPOŻYCZ"
                        } else {
                            "ZWRÓĆ"
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MembersScreen(
    snapshot: ChoirSnapshot,
    onMemberClick: (ChoirMember) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStatuses by remember { mutableStateOf(setOf<MemberStatus>()) }
    var selectedVoices by remember { mutableStateOf(setOf<VoicePart>()) }

    val filteredMembers = remember(snapshot, query, selectedStatuses, selectedVoices) {
        snapshot.choirMembers.filter { member ->
            val matchesQuery = query.isBlank() || listOf(
                member.fullName,
                member.registryNumber,
                member.phone.orEmpty(),
                member.email.orEmpty(),
            ).any { it.contains(query, ignoreCase = true) }
            val matchesStatus = selectedStatuses.isEmpty() || member.status in selectedStatuses
            val matchesVoice = selectedVoices.isEmpty() || member.voicePart in selectedVoices
            matchesQuery && matchesStatus && matchesVoice
        }.sortedBy { it.fullName }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Szukaj chórzysty") },
                singleLine = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedStatuses.isEmpty(),
                    onClick = { selectedStatuses = emptySet() },
                    label = { Text("Wszyscy") },
                )
                MemberStatus.entries.forEach { status ->
                    FilterChip(
                        selected = status in selectedStatuses,
                        onClick = { selectedStatuses = selectedStatuses.toggle(status) },
                        label = { Text(status.label) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedVoices.isEmpty(),
                    onClick = { selectedVoices = emptySet() },
                    label = { Text("Wszystkie głosy") },
                )
                VoicePart.entries.forEach { voice ->
                    FilterChip(
                        selected = voice in selectedVoices,
                        onClick = { selectedVoices = selectedVoices.toggle(voice) },
                        label = { Text(voice.label) },
                    )
                }
            }
        }

        if (filteredMembers.isEmpty()) {
            EmptyState(
                title = "Brak wyników",
                subtitle = "Zmień wyszukiwanie albo usuń filtry.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredMembers) { member ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMemberClick(member) },
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = member.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Nr ewidencyjny: ${member.registryNumber}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = listOfNotNull(
                                    member.voicePart?.label,
                                    member.status?.label,
                                    member.size?.let { "Rozmiar $it" },
                                ).joinToString("  •  ").ifBlank { "Brak dodatkowych danych" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberProfileScreen(
    member: ChoirMember,
    snapshot: ChoirSnapshot,
    onReturnClick: (Costume) -> Unit,
    onBorrowNewClick: () -> Unit,
) {
    val currentCostumes = remember(snapshot, member.registryNumber) {
        snapshot.costumes
            .filter { it.currentBorrowerRegistryNumber == member.registryNumber }
            .sortedBy { it.costumeNumber }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    ProfileMetaLine("Nr ewidencyjny", member.registryNumber)
                    ProfileMetaLine("Telefon", member.phone ?: "brak")
                    ProfileMetaLine("Mail", member.email ?: "brak")
                    ProfileMetaLine("Rozmiar", member.size ?: "brak")
                    ProfileMetaLine("Głos", member.voicePart?.label ?: "brak")
                    ProfileMetaLine("Status", member.status?.label ?: "brak")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onBorrowNewClick,
                    ) {
                        Text("WYPOŻYCZ NOWY")
                    }
                }
            }
        }
        item {
            Text(
                text = "Aktualnie posiada",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (currentCostumes.isEmpty()) {
            item {
                EmptyState(
                    title = "Brak aktywnych wypożyczeń",
                    subtitle = "Ten chórzysta nie ma obecnie przypisanego stroju.",
                )
            }
        } else {
            items(currentCostumes) { costume ->
                Card {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${costume.category.label} • ${costume.costumeNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Rozmiar: ${costume.size ?: "brak"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onReturnClick(costume) },
                        ) {
                            Text("ZWRÓĆ")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetaLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDateDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialDateMillis = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val datePickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis ?: initialDateMillis
                    onConfirm(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
                },
            ) {
                Text("Ustaw")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun AddCostumeDialog(
    onDismiss: () -> Unit,
    onConfirm: (CostumeCategory, String, String?) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(CostumeCategory.RED_DRESSES) }
    var costumeNumber by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj nowy strój") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CostumeCategory.entries.forEach { category ->
                        AssistChip(
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) },
                        )
                    }
                }
                Text(text = "Wybrano: ${selectedCategory.label}")
                OutlinedTextField(
                    value = costumeNumber,
                    onValueChange = { costumeNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Numer stroju") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rozmiar") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedCategory, costumeNumber, size.ifBlank { null }) }) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        },
    )
}

@Composable
private fun BorrowCostumeDialog(
    snapshot: ChoirSnapshot,
    state: BorrowDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double?) -> Unit,
) {
    var memberQuery by rememberSaveable { mutableStateOf("") }
    var costumeQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategories by remember { mutableStateOf(setOf<CostumeCategory>()) }
    var selectedRegistryNumber by remember(state.presetRegistryNumber) {
        mutableStateOf(state.presetRegistryNumber)
    }
    var selectedCostumeNumber by remember(state.presetCostumeNumber) {
        mutableStateOf(state.presetCostumeNumber)
    }
    var depositInput by rememberSaveable { mutableStateOf("") }

    val availableCostumes = remember(snapshot) {
        snapshot.costumes
            .filter { it.availability == CostumeAvailability.AVAILABLE }
            .sortedWith(compareBy({ it.category.label }, { it.costumeNumber }))
    }
    val filteredCostumes = remember(availableCostumes, costumeQuery, selectedCategories) {
        availableCostumes.filter { costume ->
            val matchesQuery = costumeQuery.isBlank() || listOf(
                costume.costumeNumber,
                costume.category.label,
                costume.size.orEmpty(),
            ).any { it.contains(costumeQuery, ignoreCase = true) }
            val matchesCategory = selectedCategories.isEmpty() || costume.category in selectedCategories
            matchesQuery && matchesCategory
        }
    }
    val filteredMembers = remember(snapshot, memberQuery) {
        snapshot.choirMembers
            .filter { member ->
                memberQuery.isBlank() || listOf(
                    member.fullName,
                    member.registryNumber,
                    member.phone.orEmpty(),
                ).any { it.contains(memberQuery, ignoreCase = true) }
            }
            .sortedBy { it.fullName }
    }

    val selectedCostume = availableCostumes.firstOrNull { it.costumeNumber == selectedCostumeNumber }
    val selectedMember = snapshot.choirMembers.firstOrNull { it.registryNumber == selectedRegistryNumber }
    val requiresDeposit = selectedCostume?.category?.requiresDeposit == true
    val isConfirmEnabled = selectedCostumeNumber != null &&
        selectedRegistryNumber != null &&
        (!requiresDeposit || depositInput.replace(',', '.').toDoubleOrNull() != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wypożycz strój") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.presetRegistryNumber == null) {
                    OutlinedTextField(
                        value = memberQuery,
                        onValueChange = { memberQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Wyszukaj chórzystę") },
                        singleLine = true,
                    )
                    SelectionList(
                        items = filteredMembers.take(6),
                        selectedItem = selectedMember,
                        itemLabel = { "${it.fullName} • ${it.registryNumber}" },
                        onSelect = { selectedRegistryNumber = it.registryNumber },
                    )
                } else {
                    Text(
                        text = "Chórzysta: ${selectedMember?.fullName ?: state.presetRegistryNumber}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HorizontalDivider()

                if (state.presetCostumeNumber == null) {
                    OutlinedTextField(
                        value = costumeQuery,
                        onValueChange = { costumeQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Wyszukaj strój") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategories.isEmpty(),
                            onClick = { selectedCategories = emptySet() },
                            label = { Text("Wszystkie kategorie") },
                        )
                        CostumeCategory.entries.forEach { category ->
                            FilterChip(
                                selected = category in selectedCategories,
                                onClick = { selectedCategories = selectedCategories.toggle(category) },
                                label = { Text(category.label) },
                            )
                        }
                    }
                    SelectionList(
                        items = filteredCostumes.take(6),
                        selectedItem = selectedCostume,
                        itemLabel = {
                            "${it.category.label} • ${it.costumeNumber}${it.size?.let { size -> " • $size" } ?: ""}"
                        },
                        onSelect = { selectedCostumeNumber = it.costumeNumber },
                    )
                } else {
                    Text(
                        text = "Strój: ${selectedCostume?.category?.label ?: ""} ${state.presetCostumeNumber}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (requiresDeposit) {
                    OutlinedTextField(
                        value = depositInput,
                        onValueChange = { depositInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Kaucja") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isConfirmEnabled,
                onClick = {
                    onConfirm(
                        selectedCostumeNumber.orEmpty(),
                        selectedRegistryNumber.orEmpty(),
                        depositInput.replace(',', '.').toDoubleOrNull(),
                    )
                },
            ) {
                Text("Wypożycz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        },
    )
}

@Composable
private fun <T> SelectionList(
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak wyników")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (item == selectedItem) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Text(
                            text = itemLabel(item),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> {
    return if (item in this) this - item else this + item
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun <T> Result<T>.showIn(
    snackbars: SnackbarHostState,
    scope: CoroutineScope,
    onSuccess: (T) -> String = { "Operacja zakończona powodzeniem" },
) {
    val message = fold(
        onSuccess = onSuccess,
        onFailure = { it.message ?: "Operacja nie powiodła się" },
    )
    scope.launch {
        snackbars.showSnackbar(message)
    }
}

private fun screenTitle(screen: Screen): String {
    return when (screen) {
        Screen.Home -> "Szatnia chóru"
        Screen.Sync -> "Import i Google Sheets"
        Screen.CostumeCategories -> "Kategorie strojów"
        is Screen.CostumeList -> screen.category.label
        Screen.Members -> "Chórzyści"
        is Screen.MemberProfile -> "Profil chórzysty"
    }
}
