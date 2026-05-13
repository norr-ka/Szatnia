package com.example.szatnia.data

import com.example.szatnia.domain.ChoirMember
import com.example.szatnia.domain.ChoirSnapshot
import com.example.szatnia.domain.Costume
import com.example.szatnia.domain.RentalHistoryEntry

interface ChoirRepository {
    fun snapshot(): ChoirSnapshot
    fun replaceSnapshot(snapshot: ChoirSnapshot)
    fun upsertChoirMember(member: ChoirMember)
    fun upsertCostume(costume: Costume)
    fun deleteCostume(costumeId: String)
    fun appendHistoryEntry(entry: RentalHistoryEntry)
    fun upsertHistoryEntry(entry: RentalHistoryEntry)
}
