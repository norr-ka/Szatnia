package com.example.szatnia.data

import com.example.szatnia.domain.ChoirSnapshot
import com.example.szatnia.domain.Costume
import com.example.szatnia.domain.CostumeAvailability
import com.example.szatnia.domain.CostumeCategory
import com.example.szatnia.domain.RentalHistoryEntry
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

class CostumeIdentityResolver {
    fun normalizeSnapshot(snapshot: ChoirSnapshot): ChoirSnapshot {
        val normalizedCostumes = normalizeCostumes(snapshot.costumes)
        val normalizedHistory = normalizeHistory(snapshot.historyEntries, normalizedCostumes)
        val mergedCostumes = mergeHistoryCostumes(normalizedCostumes, normalizedHistory)
        return snapshot.copy(
            costumes = applyCurrentBorrowers(mergedCostumes, normalizedHistory),
            historyEntries = normalizedHistory,
        )
    }

    fun normalizeCostumes(costumes: List<Costume>): List<Costume> {
        val seenIds = mutableSetOf<String>()
        val sequenceByGroup = mutableMapOf<Pair<CostumeCategory, String>, Int>()

        return costumes.map { costume ->
            val normalizedNumber = normalizeCostumeNumber(costume.costumeNumber)
            val key = costume.category to normalizedNumber
            val preferredId = costume.costumeId.trim().takeIf { it.isNotEmpty() && seenIds.add(it) }
            val resolvedId = preferredId ?: nextCostumeId(
                category = costume.category,
                costumeNumber = normalizedNumber,
                sequenceByGroup = sequenceByGroup,
                reservedIds = seenIds,
            )
            Costume(
                costumeId = resolvedId,
                category = costume.category,
                costumeNumber = normalizedNumber,
                size = costume.size,
                availability = costume.availability,
                currentBorrowerRegistryNumber = costume.currentBorrowerRegistryNumber,
                currentBorrowerNameOverride = costume.currentBorrowerNameOverride,
            )
        }
    }

    fun normalizeHistory(
        entries: List<RentalHistoryEntry>,
        costumes: List<Costume> = emptyList(),
    ): List<RentalHistoryEntry> {
        val normalizedEntries = entries.map { entry ->
            entry.copy(costumeNumber = normalizeCostumeNumber(entry.costumeNumber))
        }
        val result = mutableListOf<RentalHistoryEntry>()
        val groupedCostumeIds = costumes
            .groupBy { it.category to it.costumeNumber }
            .mapValues { (_, group) -> group.map { it.costumeId }.sorted() }

        normalizedEntries
            .groupBy { it.category to it.costumeNumber }
            .toSortedMap(compareBy<Pair<CostumeCategory, String>>({ it.first.label }, { it.second }))
            .forEach { (key, groupEntries) ->
                val assigned = assignGroupIds(
                    category = key.first,
                    costumeNumber = key.second,
                    entries = groupEntries,
                    existingIds = groupedCostumeIds[key].orEmpty(),
                )
                result += assigned
            }
        return result.sortedWith(
            compareBy<RentalHistoryEntry>({ it.borrowedAt }, { it.returnedAt ?: LocalDate.MAX }, { it.operationId })
        )
    }

    private fun assignGroupIds(
        category: CostumeCategory,
        costumeNumber: String,
        entries: List<RentalHistoryEntry>,
        existingIds: List<String>,
    ): List<RentalHistoryEntry> {
        data class InstanceState(
            val id: String,
            var open: Boolean = false,
            var availableFrom: LocalDate? = null,
        )

        val instances = linkedMapOf<String, InstanceState>()
        val reservedIds = existingIds.toMutableSet()
        val sequenceByGroup = mutableMapOf<Pair<CostumeCategory, String>, Int>()
        val sortedEntries = entries.sortedWith(
            compareBy<RentalHistoryEntry>({ it.borrowedAt }, { it.returnedAt ?: LocalDate.MAX }, { it.operationId })
        )

        fun claimInstance(id: String): InstanceState {
            return instances.getOrPut(id) { InstanceState(id = id) }
        }

        fun nextFreshId(): String {
            return nextCostumeId(
                category = category,
                costumeNumber = costumeNumber,
                sequenceByGroup = sequenceByGroup,
                reservedIds = reservedIds,
            )
        }

        return sortedEntries.map { entry ->
            val providedId = entry.costumeId.trim().takeIf { it.isNotEmpty() }
            val assignedId = when {
                providedId != null -> {
                    reservedIds += providedId
                    claimInstance(providedId).id
                }
                else -> {
                    instances.values
                        .filter { !it.open && (it.availableFrom == null || !it.availableFrom!!.isAfter(entry.borrowedAt)) }
                        .maxByOrNull { it.availableFrom ?: LocalDate.MIN }
                        ?.id
                        ?: existingIds.firstOrNull { it !in instances }
                        ?: nextFreshId()
                }
            }
            val instance = claimInstance(assignedId)
            instance.open = entry.returnedAt == null
            instance.availableFrom = entry.returnedAt
            entry.copy(costumeId = assignedId)
        }
    }

    private fun mergeHistoryCostumes(
        costumes: List<Costume>,
        historyEntries: List<RentalHistoryEntry>,
    ): List<Costume> {
        val existingIds = costumes.map { it.costumeId }.toSet()
        val missing = historyEntries
            .groupBy { it.costumeId }
            .values
            .mapNotNull { entries ->
                val latest = entries.maxWithOrNull(
                    compareBy<RentalHistoryEntry>({ it.borrowedAt }, { it.returnedAt ?: LocalDate.MAX }, { it.operationId })
                ) ?: return@mapNotNull null
                latest.takeIf { it.costumeId !in existingIds }?.let {
                    Costume(
                        costumeId = it.costumeId,
                        category = it.category,
                        costumeNumber = it.costumeNumber,
                    )
                }
            }
        return (costumes + missing).sortedWith(compareBy({ it.category.label }, { it.costumeNumber }, { it.costumeId }))
    }

    private fun applyCurrentBorrowers(
        costumes: List<Costume>,
        historyEntries: List<RentalHistoryEntry>,
    ): List<Costume> {
        val latestOpenByCostumeId = historyEntries
            .filter { it.returnedAt == null }
            .groupBy { it.costumeId }
            .mapValues { (_, entries) ->
                entries.maxWithOrNull(
                    compareBy<RentalHistoryEntry>({ it.borrowedAt }, { it.operationId })
                )
            }
        val historyByCostumeId = historyEntries.groupBy { it.costumeId }

        return costumes.map { costume ->
            val openEntry = latestOpenByCostumeId[costume.costumeId]
            when {
                openEntry != null -> costume.copy(
                    availability = CostumeAvailability.BORROWED,
                    currentBorrowerRegistryNumber = openEntry.registryNumber,
                    currentBorrowerNameOverride = openEntry.fullName,
                )
                historyByCostumeId.containsKey(costume.costumeId) -> costume.copy(
                    availability = CostumeAvailability.AVAILABLE,
                    currentBorrowerRegistryNumber = null,
                    currentBorrowerNameOverride = null,
                )
                else -> costume
            }
        }
    }

    fun normalizeCostumeNumber(raw: String?): String {
        return raw?.trim().takeUnless { it.isNullOrEmpty() } ?: "bez nr"
    }

    private fun nextCostumeId(
        category: CostumeCategory,
        costumeNumber: String,
        sequenceByGroup: MutableMap<Pair<CostumeCategory, String>, Int>,
        reservedIds: MutableSet<String>,
    ): String {
        val key = category to costumeNumber
        while (true) {
            val next = (sequenceByGroup[key] ?: 0) + 1
            sequenceByGroup[key] = next
            val candidate = buildCostumeId(category, costumeNumber, next)
            if (reservedIds.add(candidate)) {
                return candidate
            }
        }
    }

    private fun buildCostumeId(category: CostumeCategory, costumeNumber: String, sequence: Int): String {
        val normalized = Normalizer.normalize(costumeNumber, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase(Locale.ROOT)
            .replace("[^A-Z0-9]+".toRegex(), "-")
            .trim('-')
            .ifBlank { "BEZ-NR" }
        return "${category.name}-$normalized-$sequence"
    }
}
