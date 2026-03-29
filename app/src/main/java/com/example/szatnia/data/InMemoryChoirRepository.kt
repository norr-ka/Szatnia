package com.example.szatnia.data

import com.example.szatnia.domain.ChoirMember
import com.example.szatnia.domain.ChoirSnapshot
import com.example.szatnia.domain.Costume
import com.example.szatnia.domain.CostumeAvailability
import com.example.szatnia.domain.CostumeCategory
import com.example.szatnia.domain.MemberStatus
import com.example.szatnia.domain.RentalHistoryEntry
import com.example.szatnia.domain.VoicePart
import java.time.LocalDate

class InMemoryChoirRepository(
    initialSnapshot: ChoirSnapshot = sampleSnapshot(),
) : ChoirRepository {
    private val choirMembers = initialSnapshot.choirMembers.toMutableList()
    private val costumes = initialSnapshot.costumes.toMutableList()
    private val historyEntries = initialSnapshot.historyEntries.toMutableList()

    override fun snapshot(): ChoirSnapshot {
        return ChoirSnapshot(
            choirMembers = choirMembers.toList(),
            costumes = costumes.toList(),
            historyEntries = historyEntries.toList(),
        )
    }

    override fun replaceSnapshot(snapshot: ChoirSnapshot) {
        choirMembers.clear()
        choirMembers += snapshot.choirMembers
        costumes.clear()
        costumes += snapshot.costumes
        historyEntries.clear()
        historyEntries += snapshot.historyEntries
    }

    override fun upsertChoirMember(member: ChoirMember) {
        val existingIndex = choirMembers.indexOfFirst { it.registryNumber == member.registryNumber }
        if (existingIndex == -1) {
            choirMembers += member
        } else {
            choirMembers[existingIndex] = member
        }
    }

    override fun upsertCostume(costume: Costume) {
        val existingIndex = costumes.indexOfFirst { it.costumeNumber == costume.costumeNumber }
        if (existingIndex == -1) {
            costumes += costume
        } else {
            costumes[existingIndex] = costume
        }
    }

    override fun appendHistoryEntry(entry: RentalHistoryEntry) {
        historyEntries += entry
    }

    override fun upsertHistoryEntry(entry: RentalHistoryEntry) {
        val existingIndex = historyEntries.indexOfFirst { it.operationId == entry.operationId }
        if (existingIndex == -1) {
            historyEntries += entry
        } else {
            historyEntries[existingIndex] = entry
        }
    }

    companion object {
        fun sampleSnapshot(): ChoirSnapshot {
            return ChoirSnapshot(
                choirMembers = listOf(
                    ChoirMember(
                        fullName = "Anna Kowalska",
                        registryNumber = "001",
                        phone = "600100200",
                        email = "anna@chor.pl",
                        size = "M",
                        voicePart = VoicePart.SOPRANO,
                        status = MemberStatus.ACTIVE,
                    ),
                    ChoirMember(
                        fullName = "Marta Nowak",
                        registryNumber = "002",
                        phone = null,
                        email = null,
                        size = "S",
                        voicePart = VoicePart.ALTO,
                        status = MemberStatus.TRIAL,
                    ),
                    ChoirMember(
                        fullName = "Piotr Zielinski",
                        registryNumber = "010",
                        phone = "500222333",
                        email = "piotr@chor.pl",
                        size = "L",
                        voicePart = VoicePart.TENOR,
                        status = MemberStatus.ACTIVE,
                    ),
                    ChoirMember(
                        fullName = "Jan Malec",
                        registryNumber = "020",
                        phone = "500888999",
                        email = "jan@chor.pl",
                        size = "XL",
                        voicePart = VoicePart.BASS,
                        status = MemberStatus.PASSIVE,
                    ),
                ),
                costumes = listOf(
                    Costume(
                        category = CostumeCategory.RED_DRESSES,
                        costumeNumber = "A19",
                        size = "M",
                    ),
                    Costume(
                        category = CostumeCategory.RED_DRESSES,
                        costumeNumber = "A20",
                        size = "S",
                        availability = CostumeAvailability.BORROWED,
                        currentBorrowerRegistryNumber = "002",
                    ),
                    Costume(
                        category = CostumeCategory.JACKETS,
                        costumeNumber = "M3",
                        size = "L",
                        availability = CostumeAvailability.BORROWED,
                        currentBorrowerRegistryNumber = "010",
                    ),
                    Costume(
                        category = CostumeCategory.CONCERT_FOLDERS,
                        costumeNumber = "T7",
                        size = null,
                    ),
                    Costume(
                        category = CostumeCategory.WHITE_SHIRTS,
                        costumeNumber = "K11",
                        size = "XL",
                    ),
                ),
                historyEntries = listOf(
                    RentalHistoryEntry(
                        operationId = "20260301081500-A20-002",
                        borrowedAt = LocalDate.of(2026, 3, 1),
                        category = CostumeCategory.RED_DRESSES,
                        costumeNumber = "A20",
                        registryNumber = "002",
                        fullName = "Marta Nowak",
                        deposit = null,
                    ),
                    RentalHistoryEntry(
                        operationId = "20260315100000-M3-010",
                        borrowedAt = LocalDate.of(2026, 3, 15),
                        category = CostumeCategory.JACKETS,
                        costumeNumber = "M3",
                        registryNumber = "010",
                        fullName = "Piotr Zielinski",
                        deposit = null,
                    ),
                ),
            )
        }
    }
}
