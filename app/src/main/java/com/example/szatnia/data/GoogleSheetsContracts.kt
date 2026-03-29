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
import java.time.format.DateTimeFormatter

object GoogleSheetsSchema {
    object ChoirMembersSheet {
        const val name = "Chorzysci"
        const val fullName = "Imię i nazwisko"
        const val registryNumber = "Nr ewidencyjny osoby"
        const val phone = "Telefon"
        const val email = "Mail"
        const val size = "Rozmiar"
        const val voice = "Głos"
        const val status = "Status"
    }

    object CostumesSheet {
        const val name = "Stroje"
        const val category = "Kategoria stroju"
        const val costumeNumber = "Numer stroju"
        const val size = "Rozmiar"
        const val status = "Status"
        const val currentBorrower = "Aktualnie_Wypozyczajacy"
    }

    object HistorySheet {
        const val name = "Historia_Wypozyczen"
        const val operationId = "ID_Operacji"
        const val borrowedAt = "Data_wypozyczenia"
        const val returnedAt = "Data_zwrotu"
        const val category = "Kategoria stroju"
        const val costumeNumber = "Numer stroju"
        const val registryNumber = "Nr ewidencyjny osoby"
        const val fullName = "Imię i nazwisko"
        const val deposit = "Kaucja"
    }
}

typealias SheetRow = Map<String, String?>

interface GoogleSheetsGateway {
    suspend fun readRows(sheetName: String): List<SheetRow>
    suspend fun overwriteRows(sheetName: String, rows: List<SheetRow>)
    suspend fun appendRow(sheetName: String, row: SheetRow)
}

class GoogleSheetsSnapshotMapper {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun toSnapshot(
        choirMemberRows: List<SheetRow>,
        costumeRows: List<SheetRow>,
        historyRows: List<SheetRow>,
    ): ChoirSnapshot {
        return ChoirSnapshot(
            choirMembers = choirMemberRows.mapNotNull(::rowToChoirMember),
            costumes = costumeRows.mapNotNull(::rowToCostume),
            historyEntries = historyRows.mapNotNull(::rowToHistoryEntry),
        )
    }

    fun choirMemberToRow(member: ChoirMember): SheetRow {
        return mapOf(
            GoogleSheetsSchema.ChoirMembersSheet.fullName to member.fullName,
            GoogleSheetsSchema.ChoirMembersSheet.registryNumber to member.registryNumber,
            GoogleSheetsSchema.ChoirMembersSheet.phone to member.phone,
            GoogleSheetsSchema.ChoirMembersSheet.email to member.email,
            GoogleSheetsSchema.ChoirMembersSheet.size to member.size,
            GoogleSheetsSchema.ChoirMembersSheet.voice to member.voicePart?.label,
            GoogleSheetsSchema.ChoirMembersSheet.status to member.status?.label,
        )
    }

    fun costumeToRow(costume: Costume): SheetRow {
        return mapOf(
            GoogleSheetsSchema.CostumesSheet.category to costume.category.label,
            GoogleSheetsSchema.CostumesSheet.costumeNumber to costume.costumeNumber,
            GoogleSheetsSchema.CostumesSheet.size to costume.size,
            GoogleSheetsSchema.CostumesSheet.status to costume.availability.label,
            GoogleSheetsSchema.CostumesSheet.currentBorrower to costume.currentBorrowerRegistryNumber,
        )
    }

    fun historyToRow(entry: RentalHistoryEntry): SheetRow {
        return mapOf(
            GoogleSheetsSchema.HistorySheet.operationId to entry.operationId,
            GoogleSheetsSchema.HistorySheet.borrowedAt to entry.borrowedAt.format(dateFormatter),
            GoogleSheetsSchema.HistorySheet.returnedAt to entry.returnedAt?.format(dateFormatter),
            GoogleSheetsSchema.HistorySheet.category to entry.category.label,
            GoogleSheetsSchema.HistorySheet.costumeNumber to entry.costumeNumber,
            GoogleSheetsSchema.HistorySheet.registryNumber to entry.registryNumber,
            GoogleSheetsSchema.HistorySheet.fullName to entry.fullName,
            GoogleSheetsSchema.HistorySheet.deposit to entry.deposit?.toString(),
        )
    }

    private fun rowToChoirMember(row: SheetRow): ChoirMember? {
        val registryNumber = row[GoogleSheetsSchema.ChoirMembersSheet.registryNumber].cleanValue()
            ?: return null
        val fullName = row[GoogleSheetsSchema.ChoirMembersSheet.fullName].cleanValue()
            ?: return null
        return ChoirMember(
            fullName = fullName,
            registryNumber = registryNumber,
            phone = row[GoogleSheetsSchema.ChoirMembersSheet.phone].cleanValue(),
            email = row[GoogleSheetsSchema.ChoirMembersSheet.email].cleanValue(),
            size = row[GoogleSheetsSchema.ChoirMembersSheet.size].cleanValue(),
            voicePart = VoicePart.fromRaw(row[GoogleSheetsSchema.ChoirMembersSheet.voice]),
            status = MemberStatus.fromRaw(row[GoogleSheetsSchema.ChoirMembersSheet.status]),
        )
    }

    private fun rowToCostume(row: SheetRow): Costume? {
        val category = CostumeCategory.fromRaw(row[GoogleSheetsSchema.CostumesSheet.category])
            ?: return null
        val costumeNumber = row[GoogleSheetsSchema.CostumesSheet.costumeNumber].cleanValue()
            ?: return null
        return Costume(
            category = category,
            costumeNumber = costumeNumber,
            size = row[GoogleSheetsSchema.CostumesSheet.size].cleanValue(),
            availability = CostumeAvailability.fromRaw(row[GoogleSheetsSchema.CostumesSheet.status])
                ?: CostumeAvailability.AVAILABLE,
            currentBorrowerRegistryNumber = row[GoogleSheetsSchema.CostumesSheet.currentBorrower].cleanValue(),
        )
    }

    private fun rowToHistoryEntry(row: SheetRow): RentalHistoryEntry? {
        val operationId = row[GoogleSheetsSchema.HistorySheet.operationId].cleanValue() ?: return null
        val category = CostumeCategory.fromRaw(row[GoogleSheetsSchema.HistorySheet.category]) ?: return null
        val costumeNumber = row[GoogleSheetsSchema.HistorySheet.costumeNumber].cleanValue() ?: return null
        val registryNumber = row[GoogleSheetsSchema.HistorySheet.registryNumber].cleanValue() ?: return null
        val fullName = row[GoogleSheetsSchema.HistorySheet.fullName].cleanValue() ?: return null
        val borrowedAt = row[GoogleSheetsSchema.HistorySheet.borrowedAt].toFlexibleDate() ?: return null

        return RentalHistoryEntry(
            operationId = operationId,
            borrowedAt = borrowedAt,
            returnedAt = row[GoogleSheetsSchema.HistorySheet.returnedAt].toFlexibleDate(),
            category = category,
            costumeNumber = costumeNumber,
            registryNumber = registryNumber,
            fullName = fullName,
            deposit = row[GoogleSheetsSchema.HistorySheet.deposit].cleanValue()
                ?.replace(',', '.')
                ?.toDoubleOrNull(),
        )
    }

    private fun String?.toFlexibleDate(): LocalDate? {
        val clean = cleanValue() ?: return null
        return runCatching { LocalDate.parse(clean, dateFormatter) }
            .recoverCatching { LocalDate.parse(clean) }
            .getOrNull()
    }

    private fun String?.cleanValue(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
