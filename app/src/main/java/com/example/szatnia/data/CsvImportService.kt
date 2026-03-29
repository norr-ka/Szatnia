package com.example.szatnia.data

import com.example.szatnia.domain.ChoirMember
import com.example.szatnia.domain.ChoirSnapshot
import com.example.szatnia.domain.Costume
import com.example.szatnia.domain.CostumeAvailability
import com.example.szatnia.domain.CostumeCategory
import com.example.szatnia.domain.MemberStatus
import com.example.szatnia.domain.RentalHistoryEntry
import com.example.szatnia.domain.VoicePart
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CsvImportService {
    fun importSnapshot(
        choirMembersCsv: String,
        costumesCsv: String,
        historyCsv: String,
    ): ChoirSnapshot {
        return ChoirSnapshot(
            choirMembers = importChoirMembers(choirMembersCsv),
            costumes = importCostumes(costumesCsv),
            historyEntries = importHistory(historyCsv),
        )
    }

    fun importChoirMembers(csv: String): List<ChoirMember> {
        val importedMembers = parseCsv(csv).mapNotNull { row ->
            val registryNumber = row.findValue(
                "Nr ewidencyjny osoby",
                "nr ewidencyjny osoby",
                "nr_ewidencyjny_osoby",
                "id",
            )
            val fullName = row.findValue("Imię i nazwisko", "Imie i nazwisko", "ImieNazwisko")
                ?: return@mapNotNull null

            ChoirMember(
                fullName = fullName,
                registryNumber = registryNumber ?: "",
                phone = row.findValue("Telefon", "Phone"),
                email = row.findValue("Mail", "Email", "E-mail"),
                size = row.findValue("Rozmiar", "Size"),
                voicePart = VoicePart.fromRaw(row.findValue("Głos", "Glos", "Voice")),
                status = MemberStatus.fromRaw(row.findValue("Status")),
            )
        }
        return ensureUniqueRegistryNumbers(importedMembers)
    }

    fun importCostumes(csv: String): List<Costume> {
        return parseCsv(csv).mapNotNull { row ->
            val category = CostumeCategory.fromRaw(
                row.findValue("Kategoria stroju", "Kategoria", "Category")
            ) ?: return@mapNotNull null
            val costumeNumber = row.findValue("Numer stroju", "Numer", "CostumeNumber")
                ?: return@mapNotNull null

            val rawBorrower = row.findValue(
                "Aktualnie_Wypozyczajacy",
                "AktualnieWypozyczajacy",
                "Wypozyczajacy",
            )

            Costume(
                category = category,
                costumeNumber = costumeNumber,
                size = row.findValue("Rozmiar", "Size"),
                availability = CostumeAvailability.fromRaw(row.findValue("Status"))
                    ?: if (rawBorrower.isNullOrBlank()) {
                        CostumeAvailability.AVAILABLE
                    } else {
                        CostumeAvailability.BORROWED
                    },
                currentBorrowerRegistryNumber = rawBorrower?.takeIf { it.any(Char::isDigit) },
                currentBorrowerNameOverride = rawBorrower?.takeIf {
                    it.any(Char::isLetter) && !it.any(Char::isDigit)
                },
            )
        }
    }

    fun importHistory(csv: String): List<RentalHistoryEntry> {
        return parseCsv(csv).mapNotNull { row ->
            val category = CostumeCategory.fromRaw(
                row.findValue("Kategoria stroju", "Kategoria", "Category")
            ) ?: return@mapNotNull null
            val borrowedAt = row.findValue("Data_wypozyczenia", "Data wypożyczenia", "BorrowedAt")
                .toFlexibleDate() ?: return@mapNotNull null
            val costumeNumber = row.findValue("Numer stroju", "Numer", "CostumeNumber")
                ?: return@mapNotNull null
            val registryNumber = row.findValue(
                "Nr ewidencyjny osoby",
                "nr ewidencyjny osoby",
                "nr_ewidencyjny_osoby",
                "IdChorzysty",
            ) ?: return@mapNotNull null
            val fullName = row.findValue(
                "Imię i nazwisko",
                "Imie i nazwisko",
                "ImieJesliZwrocone",
                "ImięJeśliZwrocone",
                "Nazwisko historyczne",
            ) ?: return@mapNotNull null

            RentalHistoryEntry(
                operationId = row.findValue("ID_Operacji", "OperationId")
                    ?: buildFallbackOperationId(costumeNumber, registryNumber, borrowedAt),
                borrowedAt = borrowedAt,
                returnedAt = row.findValue("Data_zwrotu", "Data zwrotu", "ReturnedAt").toFlexibleDate(),
                category = category,
                costumeNumber = costumeNumber,
                registryNumber = registryNumber,
                fullName = fullName,
                deposit = row.findValue("Kaucja", "Deposit")
                    ?.replace(" ", "")
                    ?.replace(',', '.')
                    ?.toDoubleOrNull(),
            )
        }
    }

    fun importLegacyCategoryHistory(
        csv: String,
        category: CostumeCategory,
    ): List<RentalHistoryEntry> {
        return parseCsv(csv).mapIndexedNotNull { index, row ->
            val costumeNumber = row.findValue(
                "Numer stroju",
                "Numer",
                "CostumeNumber",
                "nr sukienki",
                "nr stroju",
                "nr marynarki",
                "nr spodni",
                "nr koszuli",
                "nr teczki",
            ) ?: return@mapIndexedNotNull null

            val fullName = row.findValue(
                "imię i nazwisko",
                "imie i nazwisko",
                "ImieJesliZwrocone",
                "ImięJeśliZwrocone",
            ) ?: return@mapIndexedNotNull null

            val registryNumber = row.findValue(
                "Nr ewidencyjny osoby",
                "nr ewidencyjny osoby",
                "nr_ewidencyjny_osoby",
            ).normalizeRegistryNumber(fullName)

            val borrowedAt = row.findValue(
                "Data_wypozyczenia",
                "data wypożyczenia",
                "data wypozyczenia",
            ).toFlexibleDate()
            val returnedAt = row.findValue(
                "Data_zwrotu",
                "data zwrotu",
            ).toFlexibleDate()

            val effectiveBorrowedAt = borrowedAt
                ?: returnedAt
                ?: LocalDate.of(1970, 1, 1)

            RentalHistoryEntry(
                operationId = row.findValue("ID_Operacji", "OperationId")
                    ?: buildFallbackOperationId(costumeNumber, registryNumber, effectiveBorrowedAt, index),
                borrowedAt = effectiveBorrowedAt,
                returnedAt = returnedAt,
                category = category,
                costumeNumber = costumeNumber,
                registryNumber = registryNumber,
                fullName = fullName,
                deposit = null,
            )
        }
    }

    private fun buildFallbackOperationId(
        costumeNumber: String,
        registryNumber: String,
        borrowedAt: LocalDate,
    ): String {
        return "${borrowedAt.format(DateTimeFormatter.BASIC_ISO_DATE)}-$costumeNumber-$registryNumber"
    }

    private fun buildFallbackOperationId(
        costumeNumber: String,
        registryNumber: String,
        borrowedAt: LocalDate,
        rowIndex: Int,
    ): String {
        return "${borrowedAt.format(DateTimeFormatter.BASIC_ISO_DATE)}-$costumeNumber-$registryNumber-${rowIndex + 1}"
    }

    private fun parseCsv(csv: String): List<Map<String, String>> {
        val normalized = csv.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) {
            return emptyList()
        }

        val lines = normalized.lines().filterNot { it.isBlank() }
        if (lines.isEmpty()) {
            return emptyList()
        }

        val delimiter = if (lines.first().count { it == ';' } >= lines.first().count { it == ',' }) {
            ';'
        } else {
            ','
        }

        val rows = lines.map { parseRow(it, delimiter) }
        val headers = rows.first().map { it.normalizeHeader() }
        return rows.drop(1).map { row ->
            headers.mapIndexedNotNull { index, header ->
                header.takeIf { it.isNotBlank() }?.let { validHeader ->
                    validHeader to row.getOrElse(index) { "" }.trim()
                }
            }.toMap()
        }
    }

    private fun parseRow(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                character == '"' -> inQuotes = !inQuotes
                character == delimiter && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index += 1
        }
        cells += current.toString()
        return cells
    }

    private fun Map<String, String>.findValue(vararg candidates: String): String? {
        return candidates
            .map { it.normalizeHeader() }
            .firstNotNullOfOrNull { candidate -> this[candidate]?.trim()?.takeIf { it.isNotEmpty() } }
    }

    private fun String?.toFlexibleDate(): LocalDate? {
        val clean = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val supportedFormats = listOf(
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )
        return supportedFormats.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(clean, formatter) }.getOrNull()
        }
    }

    private fun String.normalizeHeader(): String {
        return lowercase(Locale.ROOT)
            .replace("ą", "a")
            .replace("ć", "c")
            .replace("ę", "e")
            .replace("ł", "l")
            .replace("ń", "n")
            .replace("ó", "o")
            .replace("ś", "s")
            .replace("ż", "z")
            .replace("ź", "z")
            .replace(" ", "")
            .replace("_", "")
    }

    private fun ensureUniqueRegistryNumbers(members: List<ChoirMember>): List<ChoirMember> {
        val seen = mutableSetOf<String>()
        return members.map { member ->
            val baseRegistryNumber = member.registryNumber.normalizeRegistryNumber(member.fullName)
            var registryNumber = baseRegistryNumber
            var suffix = 2
            while (!seen.add(registryNumber)) {
                registryNumber = "$baseRegistryNumber-$suffix"
                suffix += 1
            }
            member.copy(registryNumber = registryNumber)
        }
    }

    private fun String?.normalizeRegistryNumber(fullName: String): String {
        val clean = this?.trim()
        if (!clean.isNullOrEmpty() && clean != "-") {
            return clean
        }
        return "AUTO-${slugify(fullName)}"
    }

    private fun slugify(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return normalized
            .uppercase(Locale.ROOT)
            .replace("[^A-Z0-9]".toRegex(), "")
            .ifBlank { "CHORZYSTA" }
    }
}
