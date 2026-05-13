package com.example.szatnia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.szatnia.data.GoogleSheetsSyncService
import com.example.szatnia.data.InMemoryChoirRepository
import com.example.szatnia.data.LocalSnapshotStore
import com.example.szatnia.data.SyncPreferences
import com.example.szatnia.ui.SzatniaApp
import com.example.szatnia.ui.SzatniaCoordinator
import com.example.szatnia.ui.theme.SzatniaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val localSnapshotStore = LocalSnapshotStore(this)
        val coordinator = SzatniaCoordinator(
            repository = InMemoryChoirRepository(
                initialSnapshot = localSnapshotStore.loadSnapshot() ?: InMemoryChoirRepository.sampleSnapshot()
            ),
            syncPreferences = SyncPreferences(this),
            syncService = GoogleSheetsSyncService(),
            localSnapshotStore = localSnapshotStore,
        )

        setContent {
            SzatniaTheme {
                SzatniaApp(coordinator = coordinator)
            }
        }
    }
}
