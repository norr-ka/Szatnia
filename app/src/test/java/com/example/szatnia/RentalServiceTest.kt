package com.example.szatnia

import com.example.szatnia.data.CsvImportService
import com.example.szatnia.data.InMemoryChoirRepository
import com.example.szatnia.data.SnapshotImportService
import com.example.szatnia.domain.CostumeAvailability
import com.example.szatnia.domain.CostumeCategory
import com.example.szatnia.domain.RentalService
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RentalServiceTest {
    @Test
    fun borrowingUpdatesSpecificCostumeAndAppendsHistory() {
        val repository = InMemoryChoirRepository()
        val service = RentalService(repository)

        val result = service.borrowCostume(
            operationDate = LocalDate.of(2026, 3, 27),
            costumeId = "CONCERT_FOLDERS-T7-1",
            registryNumber = "001",
            deposit = 50.0,
        )

        assertTrue(result.isSuccess)
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.first { it.costumeId == "CONCERT_FOLDERS-T7-1" }
        assertEquals(CostumeAvailability.BORROWED, costume.availability)
        assertEquals("001", costume.currentBorrowerRegistryNumber)

        val history = snapshot.historyEntries.last { it.costumeId == "CONCERT_FOLDERS-T7-1" }
        assertEquals(LocalDate.of(2026, 3, 27), history.borrowedAt)
        assertEquals(50.0, history.deposit)
    }

    @Test
    fun returningClosesOpenHistoryEntryForExactCostumeId() {
        val repository = InMemoryChoirRepository()
        val service = RentalService(repository)

        val result = service.returnCostume(
            operationDate = LocalDate.of(2026, 3, 27),
            costumeId = "JACKETS-M3-1",
        )

        assertTrue(result.isSuccess)
        val snapshot = repository.snapshot()
        val costume = snapshot.costumes.first { it.costumeId == "JACKETS-M3-1" }
        assertEquals(CostumeAvailability.AVAILABLE, costume.availability)
        assertNull(costume.currentBorrowerRegistryNumber)

        val history = snapshot.historyEntries.first { it.costumeId == "JACKETS-M3-1" }
        assertEquals(LocalDate.of(2026, 3, 27), history.returnedAt)
    }

    @Test
    fun csvImportHandlesPolishDatesAndLegacyNameColumn() {
        val csv = """
            ID_Operacji;Data_wypozyczenia;Data_zwrotu;Kategoria stroju;Numer stroju;Nr ewidencyjny osoby;ImieJesliZwrocone;Kaucja
            HIST-1;27.03.2026;;Teczki koncertowe;T1;001;Anna Kowalska;40,5
        """.trimIndent()

        val entries = CsvImportService().importHistory(csv)

        assertEquals(1, entries.size)
        assertEquals(LocalDate.of(2026, 3, 27), entries.first().borrowedAt)
        assertEquals(CostumeCategory.CONCERT_FOLDERS, entries.first().category)
        assertEquals("Anna Kowalska", entries.first().fullName)
        assertEquals(40.5, entries.first().deposit)
        assertTrue(entries.first().costumeId.isNotBlank())
    }

    @Test
    fun choirImportGeneratesUniqueIdsForMissingRegistryNumbers() {
        val csv = """
            imie i nazwisko,Nr ewidencyjny osoby,Telefon,Mail,Rozmiar,Głos,Status
            Antonik Anita,-,,,,Alt,Czynny
            Balcerzak Łukasz,-,,,,Tenor,Czynny
        """.trimIndent()

        val members = CsvImportService().importChoirMembers(csv)

        assertEquals(2, members.size)
        assertTrue(members.all { it.registryNumber.startsWith("AUTO-") })
        assertNotEquals(members[0].registryNumber, members[1].registryNumber)
    }

    @Test
    fun historyImportRebuildsCurrentBorrowerFromOpenEntries() {
        val csv = """
            ImieJesliZwrocone,nr sukienki,data wypożyczenia,data zwrotu,imię i nazwisko,nr ewidencyjny osoby
            ,A6,3.12.2024,17.12.2024,Szukała Kamila,A033SZUKAM
            Brodzińska Milena,A6,9.01.2025,,Brodzińska Milena,A036BROMIL
        """.trimIndent()

        val repository = InMemoryChoirRepository()
        val service = SnapshotImportService()

        val (snapshot, summary) = service.replaceHistoryForCategory(
            snapshot = repository.snapshot(),
            csv = csv,
            category = CostumeCategory.RED_DRESSES,
        )

        assertEquals(2, summary.importedEntries)
        val costume = snapshot.costumes.first {
            it.category == CostumeCategory.RED_DRESSES && it.costumeNumber == "A6"
        }
        assertEquals(CostumeAvailability.BORROWED, costume.availability)
        assertEquals("A036BROMIL", costume.currentBorrowerRegistryNumber)
    }

    @Test
    fun legacyImportCreatesSeparateIdsForDuplicateNumbers() {
        val csv = """
            ImieJesliZwrocone,nr teczki,data wypożyczenia,data zwrotu,imię i nazwisko,nr ewidencyjny osoby
            ,bez nr,1.01.2026,,Anna Kowalska,001
            ,bez nr,2.01.2026,,Marta Nowak,002
        """.trimIndent()

        val entries = CsvImportService().importLegacyCategoryHistory(
            csv = csv,
            category = CostumeCategory.CONCERT_FOLDERS,
        )

        assertEquals(2, entries.size)
        assertNotEquals(entries[0].costumeId, entries[1].costumeId)
    }

    @Test
    fun legacyCapeImportReadsNrPelerynkiColumn() {
        val csv = """
            nr pelerynki,data wypożyczenia,data zwrotu,imię i nazwisko,nr ewidencyjny osoby
            P-20,,3.07.2025,Adamczak Aleksandra,S001ADAALE
            P-41,16.01.2025,,Brodzińska Milena,A036BROMIL
        """.trimIndent()

        val entries = CsvImportService().importLegacyCategoryHistory(
            csv = csv,
            category = CostumeCategory.CAPES,
        )

        assertEquals(2, entries.size)
        assertEquals(setOf("P-20", "P-41"), entries.map { it.costumeNumber }.toSet())
    }
}
