package com.example.szatnia.data

import com.example.szatnia.domain.ChoirSnapshot
import com.example.szatnia.domain.Costume
import com.example.szatnia.domain.CostumeAvailability
import com.example.szatnia.domain.CostumeCategory
import com.example.szatnia.domain.RentalHistoryEntry

data class HistoryImportSummary(
    val importedEntries: Int,
    val updatedCostumes: Int,
)

class SnapshotImportService(
    private val csvImportService: CsvImportService = CsvImportService(),
) {
    fun replaceChoirMembers(snapshot: ChoirSnapshot, csv: String): Pair<ChoirSnapshot, Int> {
        val members = csvImportService.importChoirMembers(csv)
        return snapshot.copy(choirMembers = members) to members.size
    }

    fun replaceHistoryForCategory(
        snapshot: ChoirSnapshot,
        csv: String,
        category: CostumeCategory,
    ): Pair<ChoirSnapshot, HistoryImportSummary> {
        val importedEntries = csvImportService.importLegacyCategoryHistory(csv, category)
        val historyWithoutCategory = snapshot.historyEntries.filterNot { it.category == category }
        val mergedHistory = (historyWithoutCategory + importedEntries).sortedBy { it.operationId }

        val rebuiltCategoryCostumes = rebuildCostumesForCategory(
            existingCostumes = snapshot.costumes,
            historyEntries = mergedHistory,
            category = category,
        )
        val nextSnapshot = snapshot.copy(
            costumes = snapshot.costumes.filterNot { it.category == category } + rebuiltCategoryCostumes,
            historyEntries = mergedHistory,
        )

        return nextSnapshot to HistoryImportSummary(
            importedEntries = importedEntries.size,
            updatedCostumes = rebuiltCategoryCostumes.size,
        )
    }

    private fun rebuildCostumesForCategory(
        existingCostumes: List<Costume>,
        historyEntries: List<RentalHistoryEntry>,
        category: CostumeCategory,
    ): List<Costume> {
        val existingByNumber = existingCostumes
            .filter { it.category == category }
            .associateBy { it.costumeNumber }
        val numbers = (existingByNumber.keys + historyEntries
            .filter { it.category == category }
            .map { it.costumeNumber })
            .sorted()

        return numbers.map { costumeNumber ->
            val existing = existingByNumber[costumeNumber]
            val openEntry = historyEntries.lastOrNull {
                it.category == category &&
                    it.costumeNumber == costumeNumber &&
                    it.returnedAt == null
            }

            if (openEntry == null) {
                Costume(
                    category = category,
                    costumeNumber = costumeNumber,
                    size = existing?.size,
                    availability = CostumeAvailability.AVAILABLE,
                )
            } else {
                Costume(
                    category = category,
                    costumeNumber = costumeNumber,
                    size = existing?.size,
                    availability = CostumeAvailability.BORROWED,
                    currentBorrowerRegistryNumber = openEntry.registryNumber,
                    currentBorrowerNameOverride = openEntry.fullName,
                )
            }
        }
    }
}
