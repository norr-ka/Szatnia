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
        costumeNumber: String,
        registryNumber: String,
        deposit: Double?,
    ): Result<Unit> {
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.firstOrNull { it.costumeNumber == costumeNumber }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono stroju $costumeNumber"))
        val member = snapshot.choirMembers.firstOrNull { it.registryNumber == registryNumber }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono chórzysty $registryNumber"))

        if (costume.availability == CostumeAvailability.BORROWED) {
            return Result.failure(IllegalStateException("Strój $costumeNumber jest już wypożyczony"))
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
                operationId = buildOperationId(costume.costumeNumber, member.registryNumber),
                borrowedAt = operationDate,
                category = costume.category,
                costumeNumber = costume.costumeNumber,
                registryNumber = member.registryNumber,
                fullName = member.fullName,
                deposit = deposit?.takeIf { costume.category.requiresDeposit },
            )
        )
        return Result.success(Unit)
    }

    fun returnCostume(operationDate: LocalDate, costumeNumber: String): Result<Unit> {
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.firstOrNull { it.costumeNumber == costumeNumber }
            ?: return Result.failure(IllegalArgumentException("Nie znaleziono stroju $costumeNumber"))

        if (costume.availability == CostumeAvailability.AVAILABLE) {
            return Result.failure(IllegalStateException("Strój $costumeNumber jest już dostępny"))
        }

        val openEntry = snapshot.historyEntries
            .lastOrNull { it.costumeNumber == costumeNumber && it.returnedAt == null }
            ?: return Result.failure(
                IllegalStateException("Brak otwartego wpisu historii dla stroju $costumeNumber")
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
        val normalizedNumber = costumeNumber.trim()
        if (normalizedNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Numer stroju nie może być pusty"))
        }
        val snapshot = repository.snapshot()
        if (snapshot.costumes.any { it.costumeNumber.equals(normalizedNumber, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Strój o numerze $normalizedNumber już istnieje"))
        }
        repository.upsertCostume(
            Costume(
                category = category,
                costumeNumber = normalizedNumber,
                size = size?.trim().orEmpty().ifBlank { null },
            )
        )
        return Result.success(Unit)
    }

    private fun buildOperationId(costumeNumber: String, registryNumber: String): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        return "$timestamp-$costumeNumber-$registryNumber"
    }
}
