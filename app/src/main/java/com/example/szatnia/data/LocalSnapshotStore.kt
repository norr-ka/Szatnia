package com.example.szatnia.data

import android.content.Context
import com.example.szatnia.domain.ChoirSnapshot
import org.json.JSONArray
import org.json.JSONObject

class LocalSnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences("szatnia_local_snapshot", Context.MODE_PRIVATE)
    private val mapper = GoogleSheetsSnapshotMapper()

    fun loadSnapshot(): ChoirSnapshot? {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            mapper.toSnapshot(
                choirMemberRows = jsonArrayToRows(root.optJSONArray("choirMembers")),
                costumeRows = jsonArrayToRows(root.optJSONArray("costumes")),
                historyRows = jsonArrayToRows(root.optJSONArray("historyEntries")),
            )
        }.getOrNull()
    }

    fun saveSnapshot(snapshot: ChoirSnapshot) {
        val root = JSONObject().apply {
            put("choirMembers", rowsToJsonArray(snapshot.choirMembers.map(mapper::choirMemberToRow)))
            put("costumes", rowsToJsonArray(snapshot.costumes.map(mapper::costumeToRow)))
            put("historyEntries", rowsToJsonArray(snapshot.historyEntries.map(mapper::historyToRow)))
        }
        preferences.edit().putString(KEY_SNAPSHOT, root.toString()).apply()
    }

    fun isPendingSync(): Boolean {
        return preferences.getBoolean(KEY_PENDING_SYNC, false)
    }

    fun setPendingSync(value: Boolean) {
        preferences.edit().putBoolean(KEY_PENDING_SYNC, value).apply()
    }

    private fun rowsToJsonArray(rows: List<SheetRow>): JSONArray {
        return JSONArray().apply {
            rows.forEach { row ->
                put(JSONObject().apply {
                    row.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
                })
            }
        }
    }

    private fun jsonArrayToRows(array: JSONArray?): List<SheetRow> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val row = linkedMapOf<String, String?>()
                val keys = item.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    row[key] = if (item.isNull(key)) null else item.opt(key)?.toString()
                }
                add(row)
            }
        }
    }

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_PENDING_SYNC = "pending_sync"
    }
}
