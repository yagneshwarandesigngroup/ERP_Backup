package com.ydg.chaterpapp

import android.content.Context
import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GoogleSheetsUtils {
    private const val SHEETS_API_URL = "https://sheets.googleapis.com/v4/spreadsheets"
    private const val PREFS_NAME = "ChatERP_Prefs"

    /** Retrieves or creates the user’s “ChatERP DATA” spreadsheet. */
    suspend fun getUserSpreadsheet(
        context: Context,
        email: String,
        accessToken: String
    ): String {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "spreadsheetId_$email"
        val stored = prefs.getString(key, null)
        return if (stored != null) {
            stored
        } else {
            val newId = createSpreadsheet(accessToken)
            prefs.edit().putString(key, newId).apply()
            newId
        }
    }

    /** Creates “ChatERP DATA” with default tab “Project A.” */
    suspend fun createSpreadsheet(accessToken: String): String {
        val client = OkHttpClient()
        val url = SHEETS_API_URL

        val sheetProps = JSONObject().put("title", "Project A")
        val sheetObj = JSONObject().put("properties", sheetProps)
        val sheetsArray = JSONArray().put(sheetObj)
        val jsonBody = JSONObject()
            .put("properties", JSONObject().put("title", "ChatERP DATA"))
            .put("sheets", sheetsArray)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Failed to create spreadsheet: ${resp.code} ${resp.message}")
            }
            val respBody = resp.body?.string()
                ?: throw Exception("Empty response creating spreadsheet")
            return JSONObject(respBody).getString("spreadsheetId")
        }
    }

    /** Renames an existing sheet/tab in the spreadsheet. */
    suspend fun renameProjectSheet(
        spreadsheetId: String,
        oldName: String,
        newName: String,
        accessToken: String
    ) {
        val client = OkHttpClient()
        // 1) Fetch metadata to find sheetId
        val metaUrl = "$SHEETS_API_URL/$spreadsheetId?fields=sheets.properties"
        val metaReq = Request.Builder()
            .url(metaUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val sheetIdNum = client.newCall(metaReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Failed to fetch metadata: ${resp.code}")
            }
            val json = JSONObject(resp.body!!.string())
            val arr = json.getJSONArray("sheets")
            var foundId: Int? = null
            for (i in 0 until arr.length()) {
                val prop = arr.getJSONObject(i).getJSONObject("properties")
                if (prop.getString("title") == oldName) {
                    foundId = prop.getInt("sheetId")
                    break
                }
            }
            foundId ?: throw Exception("Sheet '$oldName' not found")
        }

        // 2) Send batchUpdate to rename
        val requestArray = JSONArray().put(
            JSONObject().put("updateSheetProperties", JSONObject().apply {
                put("properties", JSONObject().apply {
                    put("sheetId", sheetIdNum)
                    put("title", newName)
                })
                put("fields", "title")
            })
        )
        val bodyJson = JSONObject().put("requests", requestArray)
        val batchUrl = "$SHEETS_API_URL/$spreadsheetId:batchUpdate"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val batchBody = bodyJson.toString().toRequestBody(mediaType)

        val batchReq = Request.Builder()
            .url(batchUrl)
            .post(batchBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(batchReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Failed to rename sheet: ${resp.code} ${resp.message}")
            }
        }
    }

    /** Adds a new sheet/tab named [projectName]. */
    suspend fun createProjectSheet(
        spreadsheetId: String,
        projectName: String,
        accessToken: String
    ) {
        val client = OkHttpClient()
        val url = "$SHEETS_API_URL/$spreadsheetId:batchUpdate"
        val requestArray = JSONArray().put(
            JSONObject().put("addSheet", JSONObject().put("properties", JSONObject().put("title", projectName)))
        )
        val bodyJson = JSONObject().put("requests", requestArray)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = bodyJson.toString().toRequestBody(mediaType)

        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Failed to add sheet: ${resp.code} ${resp.message}")
            }
        }
    }

    /** Fetches all sheet/tab titles for the given spreadsheet. */
    suspend fun fetchSheetTabs(
        spreadsheetId: String,
        accessToken: String
    ): List<String> {
        val client = OkHttpClient()
        val url = "$SHEETS_API_URL/$spreadsheetId?fields=sheets.properties.title"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Error fetching tabs: ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            val arr = json.getJSONArray("sheets")
            return List(arr.length()) { i ->
                arr.getJSONObject(i)
                    .getJSONObject("properties")
                    .getString("title")
            }
        }
    }
}
