package com.deploydulupulangnanti.qrcodescanner

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScanRecord(val id: String = UUID.randomUUID().toString(), val value: String, val format: String, val time: Long = System.currentTimeMillis(), val favorite: Boolean = false, val created: Boolean = false)

class ScannerModel(application: Application) : AndroidViewModel(application) {
    private val writeLock = Mutex()
    private val prefs = application.getSharedPreferences("preferences", 0)
    var records by mutableStateOf<List<ScanRecord>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var result by mutableStateOf<ScanRecord?>(null)
    var saveHistory by mutableStateOf(prefs.getBoolean("history", true)); private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    var dark by mutableStateOf(prefs.getBoolean("dark", false)); private set
    init {
        viewModelScope.launch {
            records = withContext(Dispatchers.IO) {
                runCatching {
                    val file = getApplication<Application>().getFileStreamPath("history.json")
                    val array = JSONArray(if (file.exists()) file.readText() else "[]")
                    (0 until array.length()).map { index -> array.getJSONObject(index).let {
                        ScanRecord(it.getString("id"), it.getString("value"), it.getString("format"), it.getLong("time"), it.optBoolean("favorite"), it.optBoolean("created"))
                    } }
                }.getOrDefault(emptyList())
            }; loaded = true
        }
    }
    fun setting(key: String, enabled: Boolean) {
        when (key) { "history" -> saveHistory = enabled; "haptics" -> haptics = enabled; "dark" -> dark = enabled }
        prefs.edit().putBoolean(key, enabled).apply()
    }
    fun accept(value: String, format: String, created: Boolean = false) {
        if (!loaded || value.isBlank()) return
        val record = ScanRecord(value = value, format = format, created = created)
        if (saveHistory) { records = (listOf(record) + records).take(1000); persist() }
        result = record
    }
    fun favorite(record: ScanRecord) {
        val updated = record.copy(favorite = !record.favorite)
        records = if (records.any { it.id == record.id }) records.map { if (it.id == record.id) updated else it } else (listOf(updated) + records).take(1000)
        result = result?.let { if (it.id == record.id) updated else it }; persist()
    }
    fun delete(record: ScanRecord) { records = records.filterNot { it.id == record.id }; if (result?.id == record.id) result = null; persist() }
    fun clear() { records = emptyList(); result = null; persist() }
    fun csv(): String = "Created at,Format,Content,Favorite,Source\r\n" + records.joinToString("\r\n") {
        listOf(java.time.Instant.ofEpochMilli(it.time).toString(), it.format, it.value, it.favorite.toString(), if (it.created) "Created" else "Scanned").joinToString(",", transform = CodeContent::csvCell)
    }
    private fun persist() {
        val snapshot = records
        viewModelScope.launch {
          writeLock.withLock { withContext(Dispatchers.IO) {
            val array = JSONArray()
            snapshot.forEach { array.put(JSONObject().put("id", it.id).put("value", it.value).put("format", it.format).put("time", it.time).put("favorite", it.favorite).put("created", it.created)) }
            val file = android.util.AtomicFile(getApplication<Application>().getFileStreamPath("history.json"))
            var stream: java.io.FileOutputStream? = null
            try { stream = file.startWrite(); stream.write(array.toString().toByteArray()); file.finishWrite(stream) }
            catch (_: Exception) { stream?.let { file.failWrite(it) } }
          } }
        }
    }
}
