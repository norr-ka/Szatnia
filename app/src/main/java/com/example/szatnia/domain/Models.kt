package com.example.szatnia.domain

import java.time.LocalDate

enum class VoicePart(val label: String) {
    SOPRANO("Sopran"),
    ALTO("Alt"),
    TENOR("Tenor"),
    BASS("Bas");

    companion object {
        fun fromRaw(raw: String?): VoicePart? {
            return entries.firstOrNull { it.label.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}

enum class MemberStatus(val label: String) {
    ACTIVE("Czynny"),
    TRIAL("Próbny"),
    PASSIVE("Bierny"),
    REMOVED("Skreślony");

    companion object {
        fun fromRaw(raw: String?): MemberStatus? {
            return entries.firstOrNull { it.label.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}

enum class CostumeCategory(val label: String) {
    RED_DRESSES("Sukienki czerwone"),
    BURGUNDY_SET("Bordowy strój"),
    CAPES("Pelerynki"),
    JACKETS("Marynarki"),
    TROUSERS("Spodnie"),
    BLACK_SHIRTS("Czarne koszule"),
    WHITE_SHIRTS("Białe koszule"),
    CONCERT_FOLDERS("Teczki koncertowe");

    val requiresDeposit: Boolean
        get() = this == CONCERT_FOLDERS

    companion object {
        fun fromRaw(raw: String?): CostumeCategory? {
            return entries.firstOrNull { it.label.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}

enum class CostumeAvailability(val label: String) {
    AVAILABLE("Dostępny"),
    BORROWED("Wypożyczony");

    companion object {
        fun fromRaw(raw: String?): CostumeAvailability? {
            return entries.firstOrNull { it.label.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}

data class ChoirMember(
    val fullName: String,
    val registryNumber: String,
    val phone: String? = null,
    val email: String? = null,
    val size: String? = null,
    val voicePart: VoicePart? = null,
    val status: MemberStatus? = null,
)

data class Costume(
    val costumeId: String,
    val category: CostumeCategory,
    val costumeNumber: String,
    val size: String? = null,
    val availability: CostumeAvailability = CostumeAvailability.AVAILABLE,
    val currentBorrowerRegistryNumber: String? = null,
    val currentBorrowerNameOverride: String? = null,
) {
    val isBorrowed: Boolean
        get() = availability == CostumeAvailability.BORROWED
}

data class RentalHistoryEntry(
    val operationId: String,
    val borrowedAt: LocalDate,
    val returnedAt: LocalDate? = null,
    val category: CostumeCategory,
    val costumeId: String,
    val costumeNumber: String,
    val registryNumber: String,
    val fullName: String,
    val deposit: Double? = null,
) {
    val isOpen: Boolean
        get() = returnedAt == null
}

data class ChoirSnapshot(
    val choirMembers: List<ChoirMember> = emptyList(),
    val costumes: List<Costume> = emptyList(),
    val historyEntries: List<RentalHistoryEntry> = emptyList(),
)

fun Costume.borrowerDisplayName(members: List<ChoirMember>): String? {
    return currentBorrowerRegistryNumber?.let { registry ->
        members.firstOrNull { it.registryNumber == registry }?.fullName
    } ?: currentBorrowerNameOverride
}

fun Costume.displayNumber(costumes: List<Costume>): String {
    val similar = costumes
        .filter { it.category == category && it.costumeNumber == costumeNumber }
        .sortedBy { it.costumeId }
    if (similar.size <= 1) {
        return costumeNumber
    }
    val index = similar.indexOfFirst { it.costumeId == costumeId }
    return "$costumeNumber [${index + 1}/${similar.size}]"
}
