package com.example.szatnia.data

import com.example.szatnia.domain.ChoirSnapshot
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GoogleSheetsSyncService(
    private val mapper: GoogleSheetsSnapshotMapper = GoogleSheetsSnapshotMapper(),
) {
    suspend fun loadSnapshot(webAppUrl: String): Result<ChoirSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(
                method = "GET",
                targetUrl = buildSnapshotUrl(webAppUrl),
            )
            val root = JSONObject(response)
            require(root.optBoolean("ok", true)) {
                root.optString("error", "Nie udało się pobrać danych z Google Sheets")
            }
            mapper.toSnapshot(
                choirMemberRows = jsonArrayToRows(root.optJSONArray("choirMembers")),
                costumeRows = jsonArrayToRows(root.optJSONArray("costumes")),
                historyRows = jsonArrayToRows(root.optJSONArray("historyEntries")),
            )
        }
    }

    suspend fun replaceSnapshot(webAppUrl: String, snapshot: ChoirSnapshot): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("action", "replaceSnapshot")
                    put("choirMembers", rowsToJsonArray(snapshot.choirMembers.map(mapper::choirMemberToRow)))
                    put("costumes", rowsToJsonArray(snapshot.costumes.map(mapper::costumeToRow)))
                    put("historyEntries", rowsToJsonArray(snapshot.historyEntries.map(mapper::historyToRow)))
                }
                val response = request(
                    method = "POST",
                    targetUrl = webAppUrl,
                    body = payload.toString(),
                )
                val root = JSONObject(response)
                require(root.optBoolean("ok", false)) {
                    root.optString("error", "Nie udało się zapisać danych do Google Sheets")
                }
            }
        }

    private fun buildSnapshotUrl(webAppUrl: String): String {
        val separator = if (webAppUrl.contains("?")) "&" else "?"
        return "$webAppUrl${separator}action=snapshot"
    }

    private fun request(method: String, targetUrl: String, body: String? = null): String {
        val connection = URL(targetUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
            if (statusCode !in 200..299) {
                error("Błąd Google Sheets ($statusCode): $response")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun rowsToJsonArray(rows: List<SheetRow>): JSONArray {
        return JSONArray().apply {
            rows.forEach { row ->
                put(JSONObject().apply {
                    row.forEach { (key, value) ->
                        put(key, value ?: JSONObject.NULL)
                    }
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
                item.keys().forEach { key ->
                    row[key] = if (item.isNull(key)) null else item.opt(key)?.toString()
                }
                add(row)
            }
        }
    }
}
