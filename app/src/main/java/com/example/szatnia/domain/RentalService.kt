package com.example.szatnia.domain

import com.example.szatnia.data.ChoirRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RentalService(
    private val repository: ChoirRepository,
) {
    fun borrowCostume(
        operationDate: LocalDate,
        costumeId: String,
        registryNumber: String,
        deposit: Double?,
    ): Result<Unit> {
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.firstOrNull { it.costumeId == costumeId }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono stroju"))
        val member = snapshot.choirMembers.firstOrNull { it.registryNumber == registryNumber }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono chórzysty $registryNumber"))

        if (costume.availability == CostumeAvailability.BORROWED) {
            return Result.failure(
                IllegalStateException("Strój ${costume.costumeNumber} jest już wypożyczony")
            )
        }
        if (costume.category.requiresDeposit && deposit == null) {
            return Result.failure(IllegalArgumentException("Kaucja jest wymagana dla teczek koncertowych"))
        }

        repository.upsertCostume(
            costume.copy(
                availability = CostumeAvailability.BORROWED,
                currentBorrowerRegistryNumber = member.registryNumber,
                currentBorrowerNameOverride = null,
            )
        )
        repository.appendHistoryEntry(
            RentalHistoryEntry(
                operationId = buildOperationId(costume.costumeId, member.registryNumber),
                borrowedAt = operationDate,
                category = costume.category,
                costumeId = costume.costumeId,
                costumeNumber = costume.costumeNumber,
                registryNumber = member.registryNumber,
                fullName = member.fullName,
                deposit = deposit?.takeIf { costume.category.requiresDeposit },
            )
        )
        return Result.success(Unit)
    }

    fun returnCostume(operationDate: LocalDate, costumeId: String): Result<Unit> {
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.firstOrNull { it.costumeId == costumeId }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono stroju"))

        if (costume.availability == CostumeAvailability.AVAILABLE) {
            return Result.failure(
                IllegalStateException("Strój ${costume.costumeNumber} jest już dostępny")
            )
        }

        val openEntry = snapshot.historyEntries
            .lastOrNull { it.costumeId == costumeId && it.returnedAt == null }
            ?: return Result.failure(
                IllegalStateException("Brak otwartego wpisu historii dla stroju ${costume.costumeNumber}")
            )

        repository.upsertCostume(
            costume.copy(
                availability = CostumeAvailability.AVAILABLE,
                currentBorrowerRegistryNumber = null,
                currentBorrowerNameOverride = null,
            )
        )
        repository.upsertHistoryEntry(openEntry.copy(returnedAt = operationDate))
        return Result.success(Unit)
    }

    fun addCostume(category: CostumeCategory, costumeNumber: String, size: String?): Result<Unit> {
        val normalizedNumber = costumeNumber.trim().ifBlank { "bez nr" }
        val costumeId = buildCostumeId(
            existingCostumes = repository.snapshot().costumes,
            category = category,
            costumeNumber = normalizedNumber,
        )
        repository.upsertCostume(
            Costume(
                costumeId = costumeId,
                category = category,
                costumeNumber = normalizedNumber,
                size = size?.trim().orEmpty().ifBlank { null },
            )
        )
        return Result.success(Unit)
    }

    fun updateCostume(costumeId: String, costumeNumber: String, size: String?): Result<Unit> {
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.firstOrNull { it.costumeId == costumeId }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono stroju"))

        repository.upsertCostume(
            costume.copy(
                costumeNumber = costumeNumber.trim().ifBlank { "bez nr" },
                size = size?.trim().orEmpty().ifBlank { null },
            )
        )
        return Result.success(Unit)
    }

    fun deleteCostume(costumeId: String): Result<Unit> {
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.firstOrNull { it.costumeId == costumeId }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono stroju"))

        if (costume.isBorrowed) {
            return Result.failure(
                IllegalStateException("Nie można usunąć wypożyczonego stroju. Najpierw go zwróć.")
            )
        }

        repository.deleteCostume(costumeId)
        return Result.success(Unit)
    }

    private fun buildOperationId(costumeId: String, registryNumber: String): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        return "$timestamp-$costumeId-$registryNumber"
    }

    private fun buildCostumeId(
        existingCostumes: List<Costume>,
        category: CostumeCategory,
        costumeNumber: String,
    ): String {
        val base = "${category.name}-${costumeNumber.trim()}"
        var sequence = 1
        while (true) {
            val candidate = "$base-$sequence"
            if (existingCostumes.none { it.costumeId == candidate }) {
                return candidate
            }
            sequence += 1
        }
    }
}
