# Verificación adicional del motor — 22 de octubre de 2026

Ampliación de portales al End completada el **23 de octubre de 2026**.

Pruebas solicitadas después de P7. Se ejecutaron en partidas descartables bajo
`work/engine-audit/`, sin modificar código productivo, partidas del usuario,
`mod_version`, `biospheres.json` ni la corrección de satélites grandes.
Los mods externos quedan para la comprobación posterior del usuario.

## Entorno y método

- Java 21.0.6, Minecraft 1.21.1, NeoForge 21.1.216, semilla **81723**, estructuras activadas.
- Mod auxiliar independiente `auditprobe`: no importa clases de Biospheres para
  generar o comparar mundos. Una comprobación de memoria usa reflexión únicamente
  para verificar la marca del NoiseChunk producido por el generador clásico real.
- Referencia: Minecraft + NeoForge + auditprobe. Variante: los mismos componentes
  y el JAR de Biospheres construido desde el estado actual del proyecto.
- SHA-256 del JAR probado:
  `8312d0a2b6aaca682d78840039f08a8b3fc6c14bf888c897a11249a96dc11645`.
- Solicitudes mediante `ServerChunkCache.getChunkFuture(..., FULL, true)`;
  se esperan también dos chunks alrededor de cada muestra, para que termine la
  decoración vecina capaz de escribir en ella. Se atienden las colas del servidor
  antes de cerrar los almacenamientos temporales.
- Los fixtures usan `ServerLevel` reales con generadores decodificados de los
  presets. El Overworld usa `minecraft:normal`, no el mundo plano de GameTest.
  El controlador temporal fija `WorldOptions(seed, true, false)` y restaura el
  estado del servidor en `finally`.
- Banco local: [EngineAudit.java](../../work/engine-audit/src/main/java/audit/EngineAudit.java),
  [build.gradle](../../work/engine-audit/build.gradle),
  [compare.py](../../work/engine-audit/compare.py). `work/` está ignorado por Git;
  estos auxiliares no se distribuyen dentro del mod.

## Resultados

| Comprobación | Resultado | Alcance |
|---|---|---|
| Vanilla con/sin Biospheres | **18/18 chunks idénticos** con generación ordenada | Overworld, Nether y End; 1.376.256 estados de bloque comparados individualmente |
| Guardado y reapertura | **Aprobado** | Los 18 chunks conservan bloques, biomas, cuatro heightmaps, estructuras y block entities |
| Persistencia P7 entre procesos | **Aprobado** | Dos chunks guardados y tres nuevos conservan la receta después de editar el TOML; un mundo nuevo adopta los valores nuevos |
| Retención de acuíferos | **16/16 objetos liberados** | Ocho NoiseChunks y ocho NoiseBasedAquifers reales, observados mediante referencias débiles |
| Portales clásico ↔ Nether / End | **Aprobado** | Traslado real de entidad en ambos sentidos; portal del Nether enlazado y plataforma de obsidiana del End comprobada |
| Eventos de jugadores | **Aprobado** | Dos ServerPlayers en un mundo guardado, otro en uno nuevo y control vanilla; entrada/reaparición sin mezclar destinos |

### Comparación vanilla

Muestras por dimensión: `(0,0)`, `(-8,0)`, `(8,8)`, `(-24,-24)`, `(32,-16)` y
`(1,0)`. Se comparan los estados expandidos de todos los bloques, no IDs de
paleta. También se comparan biomas, heightmaps, inicios/referencias de estructuras
y NBT de block entities. Solo se ordenan claves de compuestos y el conjunto de
block entities; se conserva el orden de las listas internas. No se comparan
tiempos de juego, iluminación ni entidades vivas.

La primera comparación concurrente mostró diferencias en tres muestras. Un
control adicional **sin Biospheres contra sí mismo** mostró diferencias en
cuatro muestras, en decoración de sculk/rocas. Por ello no se atribuyeron esas
diferencias al mod ni se relajó la comparación. Con solicitudes en orden fijo y
`ActiveProcessorCount=2` (un trabajador de generación), las 18 muestras con/sin
Biospheres coincidieron, incluidos los archivos de estados expandidos.
Las ejecuciones concurrentes sí completaron generación, guardado y reapertura;
no certifican determinismo bit a bit entre procesos.

Evidencia: [comparación ordenada](../../work/engine-audit/runs/comparison-with-serial.json),
[control concurrente A/A](../../work/engine-audit/runs/comparison-baseline-repeat.json),
[log sin mod](../../work/engine-baseline-serial.log),
[log con mod](../../work/engine-with-serial.log).

### Persistencia y jugadores

El proceso inicial creó un mundo de referencia completo y otro parcial con
radio 128, vidrio normal, puentes activos y océano apagado. Tras terminar ese
proceso se editó únicamente su TOML temporal: radio mínimo/máximo 220, vidrio
azul, puentes apagados y océano activo. Un segundo proceso reabrió el mundo
parcial y comparó cinco chunks FULL con la referencia: `(-8,0)`, `(0,0)`,
`(23,0)`, `(-40,0)`, `(31,0)`. Los tres últimos se solicitaron por primera vez
después del reinicio. Todos coincidieron. El control negativo creó otro mundo
con el TOML nuevo y comprobó que su resultado era distinto.

Los eventos de entrada/reaparición se publicaron en el bus real de NeoForge.
Los jugadores de prueba usan `ServerPlayer` y el manejador real de teletransporte
del servidor, con conexiones sin canal de red. Dos jugadores del mismo mundo
guardado se recolocaron independientemente usando el vidrio guardado; el del
mundo nuevo usó el vidrio azul y el control vanilla permaneció en su posición.
Esto no prueba conexión/autenticación, sincronización de clientes ni el ciclo
completo de muerte y reaparición.

Evidencia: [persistencia](../../work/engine-audit/runs/with/results/classic-p7-reopen.txt),
[TOML inicial](../../work/engine-audit/runs/with/results/classic-config-before.toml),
[TOML cambiado](../../work/engine-audit/runs/with/results/classic-config-after.toml),
[eventos de jugadores](../../work/engine-audit/runs/with/results/players.txt).

### Portales y memoria

La prueba de portal utilizó `PortalForcer`, `NetherPortalBlock.getPortalDestination`,
`EndPortalBlock.getPortalDestination` y `Entity.changeDimension`, con dimensiones temporales enlazadas en el
servidor exclusivamente durante la prueba y restauradas en `finally`. La entidad
viajó al Nether y regresó junto al portal original del Overworld clásico.
También viajó al End, se comprobaron los 25 bloques de obsidiana de la plataforma
de llegada y regresó al punto de aparición del Overworld. La columna de llegada
se despejó expresamente: Minecraft calcula la altura de retorno de una entidad
con `MOTION_BLOCKING_NO_LEAVES`, no con la Y configurada del spawn. Se comprobó
el heightmap de la zona preparada y la posición final exacta.
No cubre clientes conectados, créditos, búsqueda de stronghold ni activación de
un marco mediante ojos de ender.

La prueba de retención creó ocho ProtoChunks por las rutas reales `createBiomes`
y `fillFromNoise`, comprobó que sus NoiseChunks estaban marcados como clásicos
y que sus acuíferos eran `NoiseBasedAquifer`. Tras soltar los fixtures, las
16 referencias débiles quedaron vacías después del GC. Es evidencia contra la
retención global de esos objetos; no es una demostración de ausencia de toda
fuga de memoria en una partida larga.

Evidencia: [portal](../../work/engine-audit/runs/with/results/portals.txt),
[registro final de portales](../../work/engine-portals.log),
[memoria](../../work/engine-audit/runs/with/results/aquifer-memory.txt),
[registro de las comprobaciones adicionales](../../work/engine-remaining.log).

## Perfil de generación

Tres mundos clásicos temporales nuevos, misma semilla/configuración (radio 220,
océano activo), 3 GiB máximos de heap y cuatro procesadores visibles para Java.
Cada pasada solicita las mismas seis posiciones y su anillo: 130 chunks FULL;
el motor mantiene además sus dependencias en otros estados, con **2.737 chunks
cargados** al tomar la medida. La medición incluye espera de generación y colas,
pero no el arranque del proceso. Se registró JFR durante las tres pasadas.

| Pasada | Tiempo | Heap ocupado tras GC, con el mundo aún referenciado |
|---|---:|---:|
| 1 | 24,984 s | 368,58 MiB |
| 2 | 21,239 s | 368,69 MiB |
| 3 | 20,786 s | 368,68 MiB |

La grabación dura 71 segundos y contiene 8.293 muestras de ejecución y 79 eventos
de GC, seis de ellos solicitados expresamente por el banco. La memoria medida
después de cerrar cada fixture también permaneció aproximadamente en 369 MiB;
no es un mínimo de memoria de un servidor vacío ni un máximo de la sesión.
El calentamiento de Java, la caché y el propio perfilador impiden convertir estas
tres cifras en una mejora porcentual histórica.

Entre las ubicaciones superiores de las muestras figuran el ruido vanilla,
`ResourceLocation.isValidNamespace`/`validPathChar`, `SphereLayout.sphereAtCell`
y `SphereLayout.isInsideSatellite`. También aparecen llamadas desde heightmaps
y `ClassicWorldSettings.Bound.isShell`. El código de este último método
([ClassicWorldSettings.java:128](../../src/main/java/mx/issacinzunza/biosphereworlds/worldgen/ClassicWorldSettings.java#L128))
resuelve los nombres de materiales en cada consulta. Es un candidato acotado para
un diagnóstico posterior; las muestras de validación de identificadores no
conservan siempre el llamador y no permiten atribuirle todo ese coste. No se
modificó geometría ni se aplicó una optimización durante esta tanda.

Evidencia: [métricas](../../work/engine-audit/runs/with/results/metrics-remaining.txt),
[grabación JFR](../../work/engine-audit/runs/with/results/worldgen.jfr),
[resumen de muestras](../../work/engine-audit/runs/with/results/profile-summary.json).

## Reproducción local

El banco usa el wrapper de la raíz. Los auxiliares permanecen en `work/`.
Los directorios de cada ejecución deben ser nuevos; no se borraron ni se
sobrescribieron partidas del usuario. No se ejecutó `clean`.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat auditJar --offline --no-daemon --no-configuration-cache --init-script work/export-audit.gradle
.\gradlew.bat -p work/engine-audit runGameTestServer -Pvariant=baseline -PrunName=baseline-serial -Pphase=vanilla -Pserial=true -Pprocessors=2 --offline --no-daemon --no-configuration-cache
.\gradlew.bat -p work/engine-audit runGameTestServer -Pvariant=with -PrunName=with-serial -Pphase=vanilla -Pserial=true -Pprocessors=2 --offline --no-daemon --no-configuration-cache
python work/engine-audit/compare.py with-serial baseline-serial
```

Para persistencia se ejecutó primero `-Pvariant=with` (fase `initial`) en su
directorio nuevo; con el proceso cerrado se aplicaron los cinco cambios de TOML
descritos arriba. Después se ejecutó `-Pvariant=with -Pphase=remaining` en el
mismo directorio. Esta fase incluye reinicio/P7, memoria, portales, eventos de
jugadores y perfil. Ambos procesos finalizaron con `BUILD SUCCESSFUL`.
La ampliación del 23 de octubre se ejecutó con `-Pvariant=with -Pphase=portals`;
repitió Nether e incluyó End, terminando también con `BUILD SUCCESSFUL`.

La preparación del banco detectó y corrigió tres problemas del propio fixture:
muestreo antes de terminar la decoración vecina, comparación dependiente del
orden de un conjunto de block entities, y tareas de POI que quedaban pendientes
al cerrar un almacenamiento. Los controles sin Biospheres permitieron aislarlos.
El error de obtención de claves públicas Yggdrasil en el registro adicional es un
timeout de red del entorno; no intervino en estas pruebas sin clientes conectados.
En la ampliación del End se corrigió otra premisa del fixture: se esperaba una Y
fija sobre terreno sin despejar. La fuente de `Entity.adjustSpawnLocation`
confirmó el ajuste por heightmap; se preparó una columna despejada y se mantuvo la
aserción de posición exacta, sin cambiar el comportamiento del mod.

## Contratos del motor consultados

Se verificaron en el ZIP local de fuentes de Minecraft/NeoForge, sin inferir APIs:
`C:\Users\issac\.gradle\caches\neoformruntime\intermediate_results\sourcesWithNeoForge_69ef217555e2d7537686b6c8b26fc1ff01d8487f_output.zip`.

- `net/minecraft/server/level/ServerLevel.java:208`: constructor del mundo temporal.
- `net/minecraft/server/level/ServerChunkCache.java:211,302`: futures y guardado.
- `net/minecraft/world/level/chunk/storage/ChunkSerializer.java:287,359`: escritura y conjunto de block entities.
- `net/minecraft/server/level/ServerLevel.java:1443`: tareas de POI tras cambiar bloques; explica por qué se drenan antes del cierre.
- `net/minecraft/world/level/portal/PortalForcer.java:42,53`: localización/creación.
- `net/minecraft/world/level/block/NetherPortalBlock.java:130`: resolución del destino.
- `net/minecraft/world/level/block/EndPortalBlock.java:70` y `net/minecraft/world/level/levelgen/feature/EndPlatformFeature.java:28`: destino y plataforma del End.
- `net/minecraft/world/entity/Entity.java:1062`: altura de retorno al punto de aparición según heightmap.
- `net/neoforged/neoforge/common/util/FakePlayer.java:91,149`: contratos de construcción de jugador y manejador; no se usa su teletransporte vacío para validar movimiento.

## Límites que siguen abiertos

- Compatibilidad con C2ME, TerraBlender y otros mods: pendiente del usuario.
- Sesión con clientes reales, tráfico de red, renderizado y jugabilidad prolongada.
- Activación del marco del End/búsqueda de stronghold y una matriz más amplia de semillas, biomas, estructuras y presets solares.
- Una comparación histórica controlada para atribuir un porcentaje de mejora.

La suite previa del proyecto sigue siendo **109 JUnit y 32 GameTests aprobados**;
esta tanda amplía la evidencia con un banco temporal independiente y no modifica
esa cuenta ni vuelve a atribuir al usuario pruebas manuales de P7.
