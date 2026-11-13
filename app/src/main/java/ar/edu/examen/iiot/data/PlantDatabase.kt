package ar.edu.examen.iiot.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ar.edu.examen.iiot.model.Reading

class PlantDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                machine_id TEXT NOT NULL,
                machine_name TEXT NOT NULL,
                temperature REAL NOT NULL,
                humidity REAL NOT NULL,
                vibration REAL NOT NULL,
                captured_at INTEGER NOT NULL,
                source TEXT NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            "CREATE TABLE configuration (config_key TEXT PRIMARY KEY, config_value TEXT NOT NULL)"
        )
        createHistoryIndex(db)
    }

    /**
     * Migraciones incrementales. Antes se hacía DROP TABLE + onCreate, lo que borraba
     * todas las lecturas y la configuración del usuario en cada cambio de versión.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createHistoryIndex(db)
        }
    }

    /** v2: índice para la consulta de historial por equipo (crece con cada sincronización). */
    private fun createHistoryIndex(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_readings_machine_time ON readings (machine_id, captured_at)"
        )
    }

    fun insert(reading: Reading) {
        val values = ContentValues().apply {
            put("machine_id", reading.machineId)
            put("machine_name", reading.machineName)
            put("temperature", reading.temperature)
            put("humidity", reading.humidity)
            put("vibration", reading.vibration)
            put("captured_at", reading.timestamp)
            put("source", reading.source)
        }
        writableDatabase.insertOrThrow("readings", null, values)
    }

    fun latest(): List<Reading> {
        val sql = """SELECT r.id, r.machine_id, r.machine_name,
                   r.temperature, r.humidity, r.vibration, r.captured_at, r.source
                   FROM readings r
                   INNER JOIN (
                       SELECT machine_id, MAX(id) AS latest_id
                       FROM readings GROUP BY machine_id
                   ) x ON r.id = x.latest_id
                   ORDER BY r.machine_name""".trimIndent()
        return readableDatabase.rawQuery(sql, null).use(::readAll)
    }

    /**
     * Antes concatenaba el texto del usuario en el SQL ('%$filter%'): SQL injection y crash con
     * un apóstrofo. Ahora el valor viaja como parámetro y se escapan los comodines de LIKE.
     */
    fun findLatestByName(filter: String): List<Reading> {
        val sql = """SELECT r.id, r.machine_id, r.machine_name,
                   r.temperature, r.humidity, r.vibration, r.captured_at, r.source
                   FROM readings r
                   INNER JOIN (
                       SELECT machine_id, MAX(id) AS latest_id
                       FROM readings GROUP BY machine_id
                   ) x ON r.id = x.latest_id
                   WHERE r.machine_name LIKE ? ESCAPE '\'
                   ORDER BY r.machine_name""".trimIndent()
        val pattern = "%" + escapeLike(filter) + "%"
        return readableDatabase.rawQuery(sql, arrayOf(pattern)).use(::readAll)
    }

    /** Últimas lecturas de un equipo, de la más reciente a la más antigua. */
    fun history(machineId: String, limit: Int): List<Reading> {
        // limit es un Int de la app (no texto del usuario); machineId sí va como parámetro.
        val sql = """SELECT id, machine_id, machine_name,
                   temperature, humidity, vibration, captured_at, source
                   FROM readings
                   WHERE machine_id = ?
                   ORDER BY captured_at DESC, id DESC
                   LIMIT $limit""".trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(machineId)).use(::readAll)
    }

    fun readConfig(key: String): String? =
        readableDatabase.rawQuery(
            "SELECT config_value FROM configuration WHERE config_key = ?",
            arrayOf(key)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** La tabla configuration no tiene claves foráneas: reemplazar la fila es seguro. */
    fun writeConfig(key: String, value: String) {
        val values = ContentValues().apply {
            put("config_key", key)
            put("config_value", value)
        }
        writableDatabase.insertWithOnConflict(
            "configuration", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun readAll(cursor: Cursor): List<Reading> {
        val items = mutableListOf<Reading>()
        while (cursor.moveToNext()) {
            items += Reading(
                id = cursor.getLong(0),
                machineId = cursor.getString(1),
                machineName = cursor.getString(2),
                temperature = cursor.getDouble(3),
                humidity = cursor.getDouble(4),
                vibration = cursor.getDouble(5),
                timestamp = cursor.getLong(6),
                source = cursor.getString(7)
            )
        }
        return items
    }

    private companion object {
        const val DATABASE_NAME = "planta_iiot.db"
        const val DATABASE_VERSION = 2
    }
}
