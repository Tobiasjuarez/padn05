# Cambios realizados

## Funcionalidad 1: umbral de alarma

**Validación.** `PlantRules.parseTemperatureLimit(texto)` acepta solo números entre 30 y 120 °C (inclusive), con punto o coma decimal; cualquier otro valor devuelve `null`. La validación vive en el modelo (testeable sin Android) y la usa el `PlantViewModel`; la pantalla solo muestra el error que le llega en el estado ("Ingresá un número entre 30 y 120 °C").

**Persistencia.** El umbral se guarda en SQLite, en la tabla `configuration` que ya existía (clave `temperature_limit_celsius`), mediante `PlantRepository.saveTemperatureLimit()` → `PlantDatabase.writeConfig()`. Al iniciar, el ViewModel lo lee con `temperatureLimit()`; si no hay valor (instalación nueva) o es inválido, se usa 65 °C.

**Actualización de la pantalla.** Tras persistir el valor, el ViewModel actualiza `threshold` y recalcula `alertCount` con `PlantRules.countAlerts(lecturas, umbral)`. Las tarjetas calculan NORMAL/ALERTA con `PlantRules.requiresAttention(lectura, umbral)` (temperatura **mayor o igual** al umbral). No hace falta sincronizar.

**Interacción (Compose).** El botón "Umbral" abre un `AlertDialog` con un campo numérico, mensaje de error, Guardar (se deshabilita mientras guarda) y Cancelar.

## Funcionalidad 2: historial

**Consulta.** `PlantDatabase.history(machineId, 10)`: `SELECT … WHERE machine_id = ? ORDER BY captured_at DESC, id DESC LIMIT 10` (el id del equipo viaja como parámetro). Se agregó el índice `idx_readings_machine_time (machine_id, captured_at)` porque la tabla crece con cada sincronización.

**Presentación.** "Ver historial" abre un `AlertDialog` con una `LazyColumn` que muestra fecha/hora (dd/MM/yyyy HH:mm:ss), temperatura y origen de cada lectura. Tiene estados de carga, de error y vacío ("Todavía no hay lecturas guardadas para este equipo…"). El historial vive en `DashboardState.history`; al cerrarlo se pone en `null` y **el filtro de búsqueda no se modifica**. Si se sincroniza con el historial abierto, se recarga.

## Refactorizaciones

- **Problema 1: SQL injection en la búsqueda.** `findLatestByName` concatenaba el texto del usuario (`LIKE '%$filter%'`). Con `' OR 1=1 --` se altera la consulta y con un apóstrofo (`O'Brien`) la app crasheaba. **Corrección:** `LIKE ? ESCAPE '\'` con el valor como parámetro y escape de los comodines `%` y `_`. **Beneficio:** el texto siempre se trata como dato; la búsqueda no se puede romper desde la UI.

- **Problema 2: acceso a disco en el hilo principal.** El `init` del ViewModel, `filter()` (en cada tecla) y las inserciones de `sync()` ejecutaban SQLite en el hilo de la UI. **Corrección:** el repositorio expone funciones `suspend` que corren en `Dispatchers.IO`; el ViewModel las llama desde `viewModelScope` y cancela la búsqueda anterior para que un resultado viejo no pise uno nuevo. **Beneficio:** la UI no se congela al crecer la base (riesgo de ANR).

- **Problema 3: regla de alerta duplicada.** El 65 estaba escrito en `PlantRules`, en el ViewModel (contador) y en `EquipmentCard` (estado de la tarjeta). Cambiar el umbral en un lugar dejaba los otros desactualizados. **Corrección:** `PlantRules` es la única fuente de la regla y recibe el umbral vigente. **Beneficio:** contador y tarjetas siempre coinciden; la regla se testea en un solo lugar.

- **Problema 4: migración destructiva.** `onUpgrade` hacía `DROP TABLE` de lecturas y configuración. Cualquier cambio de versión borraba el historial y el umbral del usuario. **Corrección:** migraciones incrementales (`if (oldVersion < 2) …`); la v2 solo agrega el índice. **Beneficio:** actualizar la app conserva los datos.

- **Problema 5: fuga de memoria de la Activity.** `PlantRepository(this)` guardaba la Activity dentro del repositorio que vive en el ViewModel, que sobrevive a la rotación. **Corrección:** se usa `applicationContext`. **Beneficio:** las Activities destruidas se liberan.

- **Problema 6: errores y cancelación.** `catch (Exception)` también atrapaba la `CancellationException` de las corrutinas, y el estado de sincronización viajaba en un campo mutable del repositorio (`lastSyncMessage`). **Corrección:** se relanza la cancelación y `sync()` devuelve un `SyncResult` con el mensaje. Además, la UI usa `collectAsStateWithLifecycle()` y la parte XML `repeatOnLifecycle(STARTED)`, así no se recolecta estado con la app en segundo plano.

## Evidencia de validación

**Pruebas automatizadas** (`app/src/test/.../PlantRulesTest.kt`, 7 tests, se mantienen los 2 originales):
- Umbral inclusivo: 80 con umbral 80 es ALERTA; 79,9 es NORMAL.
- Contador según el umbral: con [47,3; 56,2; 69,5] → 1 alerta con 65, 2 con 50, 0 con 70.
- Rango válido: "30", "120", "72,5" y "72.5" se aceptan; "29.9", "120.1", "abc", "", "NaN" y "-50" se rechazan.
- Formato: 65.0 → "65"; 72.5 → "72.5".

Ejecutar con `./gradlew testDebugUnitTest`. Las consultas nuevas (historial, búsqueda con parámetros, índice, configuración) se verificaron contra SQLite: el historial devuelve 10 filas de la más reciente a la más antigua y usa el índice; buscar `' OR 1=1 --` u `O'Brien` no devuelve filas ni falla.

**Prueba manual** (emulador):
1. Instalación nueva → el botón muestra "Umbral: 65 °C"; Horno C (~70 °C) en ALERTA, contador 1.
2. Umbral → "150" → Guardar → mensaje de error, el diálogo sigue abierto. "abc" → mismo error.
3. Umbral → "50" → Guardar → Compresor A y Horno C pasan a ALERTA y el contador a 2, sin sincronizar.
4. Cerrar la app desde recientes y volver a abrirla → sigue "Umbral: 50 °C".
5. Escribir "horno" en la búsqueda → Ver historial → diálogo con 1 lectura. Cerrar → la búsqueda sigue en "horno".
6. Sincronizar dos veces → Ver historial → 3 lecturas, la más reciente primero, con fecha/hora, temperatura y origen.
7. Modo avión → Sincronizar → mensaje "Sin conexión a API: usando datos simulados", sin crash.
8. Buscar `O'Brien` → "No hay equipos para mostrar", sin crash.
