# Auditoría de sistemas solares — Biosphere Worlds 2.0

Fecha: 20 de octubre de 2026. Revisión: `6ddeeb2fe1a943a39a35949a7cc956ae5d7c666d`.

## Alcance y límites de la evidencia

Auditoría estática del generador Solar, geometría, clima, terreno, minerales, guardianes, eventos, mixins, configuración, preset y pruebas relacionadas. No se ejecutaron Gradle, Minecraft ni pruebas que escriban mundos. No se leyeron `build/`, `.gradle/`, `.idea/` ni archivos `biospheres.json`. La única escritura de esta auditoría es este informe. No se hicieron correcciones, commits ni push.

No está disponible aquí la conversación que originó todos los requisitos. Se reconstruye el contrato verificable a partir de `PROGRESS.md:269–340`, sus revisiones posteriores, `README.md` y `docs/es/ARQUITECTURA.md`. No se presentan las descripciones de progreso como resultados comprobados durante esta auditoría. Las revisiones superiores de PROGRESS sustituyen explícitamente varias decisiones iniciales: configuración independiente, doce jaulas, ausencia de lunas centrales y una órbita por planeta en layout 6.

Las referencias Java siguientes tienen como raíz `src/main/java/mx/issacinzunza/biosphereworlds/`; las de pruebas puras, `src/test/java/mx/issacinzunza/biosphereworlds/`. Los intervalos son líneas del árbol auditado. No se consultó la caché de fuentes de Minecraft: no se certifican detalles internos del motor a partir de nombres de métodos, comentarios o documentación histórica. `PROGRESS.md:288–298` cita una caché usada anteriormente, pero no sustituye su inspección actual.

**Resultado:** hay implementación sustancial, con dos defectos de casos límite confirmados por el flujo del código, inconsistencias documentales y varias garantías todavía sin aceptación integral. No se ha demostrado que un mundo Solar completo conserve todas las propiedades exigidas al generar, guardar, reiniciar y jugar con varios usuarios.

Clasificación: **C** = defecto confirmado estáticamente; **R** = riesgo potencial con prueba pendiente; **D** = decisión o limitación explícita, sin atribuirle un fallo no demostrado. Gravedad alta: bloquea generación/acceso o puede comprometer continuidad; media: afecta garantías, compatibilidad o comportamiento; baja: documentación/diagnóstico. Una prueba propuesta no significa que haya sido ejecutada.

## Comparación de requisitos con implementación

| Requisito disponible | Implementación y evidencia | Estado real |
|---|---|---|
| Segundo generador; aislamiento de Biospheres/vanilla | `SolarWorldgen.register:23`; `SolarChunkGenerator`; exclusión explícita en `mixin/MixinNoiseBasedChunkGenerator.biosphereworlds$isBiosphereGenerator:48–50` | Cableado presente. Falta comparación integral de chunks con una referencia anterior; no se afirma regresión vanilla. |
| Sin puentes automáticos ni dependencia de generate_bridge | El circuito Solar pinta volúmenes y jaulas; no invoca SphereBridgeCarver. Los carvers clásicos comprueban marca Biospheres | Presente en las rutas examinadas. |
| Generación por semilla; centros procedurales | `SolarLayout.centralAtCell:233–241`, `createOrbitalSystem:309–346`; ids derivados de semilla y celda | Presente para geometría; reproducibilidad de bloques requiere también datos y versión constantes, R1/D1. |
| Cantidad exacta de planetas y órbitas distintas | Bucle sin omisiones para v2+; radio orbital por índice para v6, `SolarLayout:327–343` | Presente en v6. La reducción de cantidad pertenece únicamente a v1 y es compatibilidad deliberada. |
| Separación de cuerpos y sistemas | Radio envolvente, spacing y jitter, `SolarLayout:42–45,93–103,233–240,327–340`; validación de lunas `349–374` | Hay respaldo geométrico; no se encontró una grieta o solapamiento confirmado con parámetros ordinarios. R8 para extremos numéricos. |
| Clima radial, inversión y al menos un templado | Grupos orbitales en `SolarLayout:313–337`; temperatura sustituida en `SolarBiomeSource:62–70` | Presente como clasificación/objetivo climático; no equivale a clasificar todos los biomas de mods, D2. |
| Perfil central magma/lava o hielo; Stronghold protegido | `SolarVolumePainter.paint:78–123`; cotas en `SolarTerrain:5–16` | Tratamiento presente antes de decoración. Aspecto final y coexistencia con features pendientes, R7. |
| Un Stronghold completo con portal por sistema, solo central | Conjunto exclusivo `SolarChunkGenerator.createState:98–134`; candidato `SolarPlacement:21–30`; piezas y contención `SolarStronghold.generate:21–53` | Construcción de grafo y comprobaciones presentes. Colocación física, activación y viaje no demostrados por las pruebas inspeccionadas, R3. |
| Locate y ojos compatibles | SolarPlacement hereda RandomSpread y comparte centralAtCell; posición de locate Y=32 | Consistencia de candidato probada en fixture; recorrido real de ambos mecanismos pendiente, R3. |
| Ningún portal natural fuera de sala central | `SolarPortalGuard:30–33,49–63`; mixins WorldGenRegion/ProtoChunk | Cubre esas rutas; no todas las escrituras posibles de otros mods, R4. |
| Satélites menores, elevados, minerales y sin colisiones | `SolarLayout.moons`; `SatellitePlacement`; `SatelliteLayers`; catálogo independiente al enlazar mundo | Implementado con omisión de candidatos inviables. Defecto C1 y dependencia de datos R1. |
| Opciones apagadas como no-op | Retornos tempranos en lunas/jaulas/océano; pruebas puras y fixture de volumen | Cobertura parcial, no demostración universal de igualdad del mundo. Apagar satélites conserva cuerpos con los demás parámetros fijos. |
| Spawn orbital templado sobre superficie natural | `SolarEvents.spawnPosition:33–45`, sin plataforma | Selección presente; C2 y R2 impiden asumir spawn válido para todas las configuraciones. |
| Persistir ajustes sin sustituir TOML al reabrir | `SolarChunkGenerator.CODEC:53–60,109–125`; `SolarSettings.CODEC:170–178` | Snapshot presente. Round-trip no demuestra reinicio de servidor; minerales resueltos no persistidos, R1/D1. |
| Heightmaps ignoran shell y lunas; restauración tras carga | `SolarVolumePainter.heightmapState:185–196`; `MixinHeightmap:34–93`; `SolarEvents.chunkLoaded:26–30` | Rutas presentes. Falta ciclo real guardar/descargar/cargar y generación concurrente. |
| Guardianes con límites, Pacífico, gamerule, sin chunks forzados | `SolarGuardians.beforeTick:112–138`, `adjustSpawn:198–218`, `placeCages:250–272` | Política presente; aceptación con distancias normales y varios jugadores pendiente, R5. Los chunks forzados vistos pertenecen al fixture. |
| Multihilo/C2ME sin estado mutable compartido peligroso | Snapshots inmutables y caches atómicas; lock de StrongholdPieces | Buenas medidas locales; no certifican interacción con otros generadores/mods, R6. |

## Defectos confirmados

### C1 — Catálogo no vacío con peso total cero: fallo diferido de generación

**Gravedad: alta, configuración específica.**

- **Archivo/método:** `worldgen/solar/SolarChunkGenerator.createState:114–118`; `worldgen/MineralCatalog.assemble:33–45`; `worldgen/SatelliteLayers.selectMineral:110–120`; `worldgen/solar/SolarVolumePainter.paint:130–136`.
- **Evidencia:** el enlace solo rechaza `minerals.isEmpty()`. El ensamblador conserva minerales adicionales de peso cero y `SolarSettings.Satellites` permite `modWeight=0`. Si se excluyen los ocho minerales vanilla y queda un candidato adicional de peso cero, el mundo pasa esa validación, pero `selectMineral` lanza `IllegalArgumentException("total mineral weight must be > 0")` al pintar una luna con núcleo.
- **Prueba reproducible:** excluir coal_ore, copper_ore, iron_ore, gold_ore, redstone_ore, lapis_ore, diamond_ore y emerald_ore; incluir `minecraft:stone`; fijar `satellites_mod_ore_weight=0`, satélites activos y radios que permitan núcleo. En fixture, comprobar que createState acepta y pintar una luna de radio suficiente con SHELL: debe observarse la excepción. En juego, explorar hasta generar esa luna. Una lista con solo `new Mineral("minecraft:stone", 0, 20)` reproduce directamente la precondición fallida de selectMineral.
- **Límite:** no afecta al catálogo vanilla predeterminado. No se ha ejecutado la reproducción.

### C2 — Radio 1 aceptado, pero la consulta de spawn devuelve el fondo del mundo

**Gravedad: alta, configuración específica.**

- **Archivo/método:** `config/SolarConfig.snapshot:25–29`; `worldgen/solar/SolarLayout.Body.contains:108–110`; `SolarChunkGenerator.getBaseColumn/getBaseHeight:203–215`; `event/SolarEvents.spawnPosition:39–45`.
- **Evidencia:** radio mínimo positivo 1 es válido. getBaseColumn conserva exclusivamente `bodyAt(..., margin=2)`. Para radio 1, `radius-margin=-1`, por lo que ningún punto del planeta puede pertenecer a ese interior. En su centro orbital, lejos de otros cuerpos, toda la columna queda en aire y getBaseHeight devuelve la altura mínima. spawnPosition usa ese resultado sin validación y cancela la selección normal. Además, el planeta entero pertenece al cascarón, no a una superficie de terreno interior.
- **Prueba reproducible:** mundo nuevo con `min_radius=radius=1`, `planet_count=1`, `planet_gap=400`, `center_y=62`, satélites y guardianes apagados. En fixture de generador enlazado, consultar el centro del único orbital: getBaseHeight debe devolver -64 para un Overworld normal. Capturar CreateSpawnPosition y verificar el spawn escrito en esa Y. Después entrar con un jugador y observar el resultado de la colocación efectiva del motor.
- **Límite:** está confirmado el cálculo inválido del spawn compartido. La posición final, caída o rescate del jugador por mecanismos vanilla necesita prueba en juego; no se afirma haber observado una muerte.

### C3 — Diagnósticos minerales descartados y promesa de /reload inaplicable a Solar

**Gravedad: baja para diagnóstico; media para expectativas de administración.**

- **Archivo/método:** `config/SphereConfig.GeneralOptions:125–132`; `SolarChunkGenerator.createState:114–123`; `event/SatelliteMineralCatalog.resolve:123–134`, `onTagsUpdated/rebuild:62–94`.
- **Evidencia:** los comentarios compartidos prometen advertencia por entradas inválidas y aplicación al reabrir o con /reload. Solar toma únicamente `.candidates()` y descarta `.ignored()` y `.excluded()`. El evento reconstruye el catálogo estático clásico con SphereConfig; no sustituye el catálogo final de SolarVolumePainter. Los valores TOML nuevos tampoco reemplazan el snapshot de un mundo Solar al reabrir.
- **Prueba reproducible:** crear Solar con un id sintácticamente válido pero inexistente, por ejemplo `missingmod:ore`, manteniendo minerales válidos. Inspeccionar que la resolución Solar no publica su diagnóstico. Cambiar después la inclusión en SolarSystems.toml y ejecutar /reload: el escritor enlazado mantiene su lista anterior; reabrir conserva los ids del snapshot guardado. Para cambios en contenido de tags, véase R1.
- **Límite:** un mensaje del catálogo clásico durante el mismo arranque no demuestra que se haya diagnosticado la configuración Solar.

### C4 — README contradice la versión efectiva de mundos nuevos

**Gravedad: baja.**

- **Archivo:** `README.md:16–18`; **método:** `config/SolarConfig.snapshot:39–43`.
- **Evidencia:** README afirma que los mundos nuevos usan versión 5 y después describe layout-v6; snapshot crea versión 6. Puede inducir a elegir una referencia de compatibilidad equivocada.
- **Prueba reproducible:** contrastar esa frase con el último argumento de `new SolarSettings` en snapshot y serializar un generador nuevo enlazado: layout_version debe ser 6.

## Riesgos potenciales y aceptación faltante

### R1 — El snapshot no congela el catálogo mineral resuelto

**Gravedad: media; continuidad de bloques tras cambios de datos.**

- **Archivo/método:** `SolarSettings.Satellites.CODEC:51–60`; `SolarChunkGenerator.createState:114–123`; `SatelliteMineralCatalog.resolve:123–134`; `MineralCatalog.assemble:39–45`; `SatelliteLayers.selectMineral:115–129`.
- **Evidencia:** se guardan ids/tags y pesos, no la lista resuelta. Se resuelve de nuevo con el registro al abrir. Añadir una mena a `#c:ores_in_ground/stone`, cambiar una exclusión por tag o retirar un mod cambia la lista/suma de pesos, aunque semilla y SolarSettings sean iguales. Durante /reload el escritor conserva la lista anterior; después de reiniciar puede usar otra.
- **Prueba:** guardar una copia de un mundo con una luna parcialmente generada; en otra copia cambiar un tag de menas, recargar y reiniciar. Generar los chunks restantes en ambas copias y comparar estados de bloques del mismo núcleo. También probar arranque con los tags aún no disponibles mediante un fixture de orden de eventos: esa disponibilidad no está acreditada por este análisis.
- **Qué no está demostrado:** una discontinuidad en un mundo concreto. Con registros/tags/versiones constantes, el catálogo tiene orden estable y no se encontró aleatoriedad dependiente del orden de chunks.

### R2 — Spawn natural calculado sin validar el terreno final ni la llegada multijugador

**Gravedad: media; alta si se reproduce llegada al vacío.**

- **Archivo/método:** `SolarEvents.spawnPosition:39–45`; `SolarChunkGenerator.getBaseColumn:203–208`; `PlayerSpawnHandler.isBiosphereWorld:37–39`.
- **Evidencia:** el spawn utiliza la columna de ruido filtrada, antes de superficies/features, sin comprobar espacio para el jugador. No existe aquí un manejador Solar equivalente para login/respawn: PlayerSpawnHandler solo acepta Biospheres clásico. No se inspeccionó el código vanilla que decide la dispersión final de jugadores.
- **Prueba:** servidor dedicado con radios 8, 16 y 128; dos jugadores nuevos, respawn sin cama, cama destruida, distintos spawnRadius y reinicio. Registrar posición compartida y posición real, colisión de pies/cabeza y bloque de soporte; incluir planeta acuático. El agua es una decisión admitida, no un defecto por sí misma. C2 ya demuestra un caso extremo de la consulta.

### R3 — Grafo válido de Stronghold no demuestra estructura física íntegra ni portal utilizable

**Gravedad: alta si falla; resultado todavía no verificado.**

- **Archivo/método:** `SolarStronghold.generate:21–53`; `SolarChunkGenerator.createStructures/applyBiomeDecoration:182–200`; `MixinSolarStructureBounds.solarContainment:30–36`; `SolarWorldgenGameTests.centralStrongholdsAndPersistence:568–616`.
- **Evidencia:** hay hasta 128 intentos y error explícito si ninguno cabe. El GameTest construye StructureStart, cuenta PortalRoom, comprueba cajas y hace codec round-trip. No coloca físicamente todas las piezas, no inserta ojos, no viaja al End ni llama el recorrido completo de locate. Usa además ajustes OVERWORLD y defaults v2 en ese método; otras pruebas amplían versiones, pero no reemplazan una partida Solar v6.
- **Prueba:** crear v6 con ambas direcciones térmicas, SHELL/FLOOR/NONE y semillas 0, -1, Long.MIN_VALUE y -4634224182739581737. Generar todos los chunks del Stronghold de varias celdas, en órdenes distintos; verificar doce marcos y orientación, sala única, accesibilidad, activar y viajar. Ejecutar locate y lanzar ojos desde centro, planetas, lunas y vacío; repetir tras reinicio. Ampliar el muestreo de construcción hasta encontrar o descartar, en ese conjunto, fallos de 128 intentos.
- **Distinción:** el fallo explícito evita declarar éxito falso, pero no garantiza generación exitosa para todas las semillas. No se encontró una ubicación orbital de Stronghold confirmada.

### R4 — Protección de portales acotada a rutas de escritura interceptadas

**Gravedad: media, compatibilidad; alta si rompe la exclusividad exigida.**

- **Archivo/método:** `MixinSolarPortalPlacement.solarPortalOnly:16–33`; `MixinSolarProtoChunkPortals.solarPortalBlocks:17–22`; `SolarPortalGuard.place:49–63`.
- **Evidencia:** WorldGenRegion valida generador y volumen; ProtoChunk valida una referencia transitoria de geometría. No hay interceptor equivalente para escrituras a secciones ni a LevelChunk. El ThreadLocal autoriza solo la ejecución síncrona del postProcess de PortalRoom, lo que no certifica tareas desplazadas a otro hilo por un mod.
- **Prueba:** datapack/fixture que coloque marcos fuera de sala por WorldGenRegion y por ProtoChunk marcado: deben rechazarse. Después probar un generador de otro mod con su ruta real de escritura y buscar marcos/portales naturales fuera del centro. No usar /setblock como fallo: los comandos y construcción del jugador están expresamente excluidos.

### R5 — Límite local referido a un jugador y cobertura dependiente de chunks activos

**Gravedad: media; multijugador y dificultad.**

- **Archivo/método:** `SolarGuardians.beforeTick:120–138`, `nearestPlayer:168–179`, `adjustSpawn:198–218`, `spawnAreaLoaded:182–188`; `SolarWorldgenGameTests.modernCagesInitializeAndRespawnEveryGuardianAtRange:358–379`.
- **Evidencia:** cada jaula cuenta guardianes en la caja inflada 32 del jugador más cercano a ella; el candidato se desplaza hacia ese jugador. No valida las cajas de todos los otros jugadores afectados por la posición resultante. El límite de sistema cuenta únicamente una caja central de radio horizontal `central.radius+160`, no asignación persistida de sistema a cada mob. La activación geométrica de 224 no mantiene las jaulas cargadas; el fixture usa jugador simulado, chunks forzados y hooks directos en un mundo plano.
- **Prueba:** dos jugadores a distintas distancias de una jaula, con áreas locales parcialmente superpuestas y ocho guardianes cerca del segundo pero fuera de la caja del primero; activar ciclos y contar ambos entornos después de cada aparición. Repetir en extremos opuestos del sol, moviendo guardianes fuera de la caja central y con distancias de simulación 4/6/10. Medir aparición y persecución reales, no solo distancia geométrica al punto de jaula.
- **Distinción:** no se afirma una carrera de dos jaulas ni duplicación observada. Un tope de 24 en la caja definida y un tope por pertenencia al sistema son contratos diferentes; el código implementa el primero.

### R6 — El lock protege las construcciones Solar entre sí, no todo StrongholdPieces

**Gravedad: media; compatibilidad concurrente pendiente.**

- **Archivo/método:** `SolarStronghold.generate:28–49`; `MixinSolarStronghold.solarPieces:27–31`.
- **Evidencia:** el monitor StrongholdPieces.class engloba reset y construcción Solar; el mixin abandona la ruta para otros generadores. El proyecto no impone ese monitor a las llamadas externas. La prueba JUnit `SolarLayoutTest.deterministicAcrossOrderCacheEvictionAndThreads` ejercita geometría pura, no generación concurrente de piezas del motor.
- **Prueba:** con C2ME, generar sistemas distintos simultáneamente y comparar grafos/bloques contra generación secuencial con igual semilla/configuración. Si otro mod genera Strongholds fuera del circuito Solar, incluir esa dimensión/circuito concurrente. Vigilar sala ausente, excepciones y diferencias de piezas. Sin código fuente actual del motor consultado no se certifica el inventario de sus campos estáticos.

### R7 — El tratamiento extremo central precede a decoración y estructuras

**Gravedad: media, aspecto y compatibilidad.**

- **Archivo/método:** `SolarChunkGenerator.buildSurface:169–172`, `applyBiomeDecoration:182–186`; `SolarVolumePainter.paint:78–123`.
- **Evidencia:** magma/hielo se aplica solo en SURFACE; después corre decoración y se ejecuta SHELL, que conserva el interior. No se vuelve a imponer el perfil extremo a bloques interiores colocados después por features/mods. Esto es compatible con mantener estructuras, pero deja pendiente que la superficie final conserve el aspecto solicitado en todos los biomas/datapacks.
- **Prueba:** generar centro caliente/frío con features vanilla y mods que coloquen vegetación, agua o lagos; comparar bloques después de SURFACE y después de decoración, a Y>=56. Documentar qué excepciones forman parte del diseño. No se ha observado aquí una feature concreta incumpliendo ese aspecto.

### R8 — Rangos numéricos amplios y validación tardía

**Gravedad: media; alta para configuraciones extremas.**

- **Archivo/método:** `SphereConfig.GeneralOptions:107–110`; `SolarConfig.snapshot:25–43`; `SolarLayout.enclosingSystemRadius:100–103`, `Params.spacing:44–45`.
- **Evidencia:** radius/min_radius se definen como Number sin rango superior; snapshot convierte con intValue. El cálculo envolvente termina convirtiendo a int y spacing usa Math.toIntExact. No hay validación conjunta de tamaño de sistema, rango de coordenadas jugables y spawn. También son válidas combinaciones con planet_gap=0 para las que guardianPosts puede agotar rotaciones y fallar explícitamente durante decoración (`SolarLayout:400–435`).
- **Prueba:** primero fixture sin mundo: radios muy grandes, números fraccionarios si el sistema de config los admite y combinaciones 16 planetas/gap 0/guardianes activos; capturar qué valores se rechazan y en qué fase. Después barrer semillas para encontrar una configuración admitida que falle al calcular jaulas. No se afirma que un gap 0 falle siempre ni que todos los formatos numéricos pasen NeoForge.

### R9 — Coste repetido de volumen/heightmaps y reconstrucción por colisiones de caché

**Gravedad: media; rendimiento sin medición integral.**

- **Archivo/método:** `SolarVolumePainter.paint:74–160`; `SolarChunkGenerator:161–186`; `SolarLayout.systemAtCell:198–205`, `moons:349–374`; `SolarStronghold.generate:28–49`.
- **Evidencia:** se pinta tras ruido, superficie, carvers y decoración; cada pasada no vacía reinicia y prima todos los tipos de heightmap. Por bloque se prueban cuerpos, y el filtro de heightmaps vuelve a consultar geometría/lunas. La caché directa tiene 256 slots: colisiones reconstruyen sistemas y satélites, aunque sin cambiar resultados. La construcción de Strongholds Solar se serializa y puede repetir 128 grafos.
- **Prueba:** perfilar servidor dedicado mientras varios jugadores exploran sistemas distintos; registrar tiempo por fase/chunk, asignaciones, fallos de caché, GC y MSPT, con 1/8/16 planetas y 0/3/64 satélites, frente a Biospheres y vanilla. Comparar también generación secuencial y C2ME. Los tiempos diagnósticos del escritor en PROGRESS no acreditan el rendimiento del mundo completo.

## Decisiones, compatibilidad y código residual

### D1 — Compatibilidad conserva posiciones, pero no congela todas las reglas

`SolarSettings` conserva layout_version y las rutas históricas de geometría/océano. Sin embargo, `SolarTerrain.bedrockLiner:75–78` aplica el nuevo techo central Y=63 sin recibir versión: también afecta chunks nuevos de snapshots antiguos SHELL. `SolarGuardians.upgradeCage:149–163` migra explícitamente la política de jaulas guardadas. Son cambios deliberados documentados en PROGRESS y README; no se presentan como corrupción accidental. **Impacto medio:** un mismo snapshot no basta para reproducir bloques/comportamiento de otra revisión del mod.

**Prueba de compatibilidad:** mundo antiguo SHELL con centro parcialmente generado; generar el resto con ambas revisiones y comparar Y=64–127 en la banda interior. Registrar layout_version: puede mantenerse mientras cambian bloques. Para jaulas, comparar NBT antes/después de activación y reinicio. `oldSnapshotsKeepTheirOriginalLayout:754–775` comprueba parámetros y posiciones, no igualdad de terreno histórico.

### D2 — Clima categórico, no simulación ni clasificación universal de biomas

`SolarBiomeSource.getNoiseBiome:54–70` fuerza Badlands/Ice Spikes si están disponibles y sustituye solo temperatura por 0.8/0/-0.8 en multi-noise. Humedad, continentalidad, erosión, profundidad y rareza permanecen. Fuentes distintas se delegan. En v2+ las proporciones se implementan mediante hasta tres grupos; `climateAtRank` y los porcentajes persistidos se usan en la ruta v1, no para graduar las órbitas v6. Con 1–2 planetas falta algún extremo térmico deliberadamente para conservar un templado. **No es un error confirmado.**

**Prueba:** enumerar por órbita ids/temperatura de biomas reales, no únicamente Body.climate; invertir dirección y comparar posiciones y distribución. Repetir con cada mod de biomas solicitado. No confundir objetivo de ruido con temperatura del bioma elegido.

### D3 — Valores hardcodeados con función definida

Radio central 180 y proporciones 30/40/30 (`SolarConfig.snapshot:34–40`); centro central Y=127 (`SolarLayout:241`); shell de dos bloques (`shellBoundary:186–188`); Stronghold hasta Y=48, peligros desde 56 y liner hasta 63 (`SolarTerrain:5–16`); 128 intentos; doce jaulas, 64 rotaciones, gaps 24/16, alcance 224; límites 8/24 e intervalos 160–240 (`SolarGuardians:34–37`, `SolarLayout:400–435`). Son parámetros internos, algunos retirados expresamente de la configuración. No son defectos por estar fijados. Limitan compatibilidad con dimensiones de otra altura, perfiles de terreno o estructuras sustituidas.

**Prueba de alcance:** preset modificado con otra altura/sea level o Stronghold sustituido: documentar rechazo o incompatibilidad. El preset suministrado usa dimensión Overworld y createState rechaza la sustitución/eliminación de la estructura vanilla. Generate Structures apagado produce rechazo explícito en `SolarEvents:36–37`; no se ofrece un Solar sin Strongholds.

### D4 — Código histórico y recursos sin ruta activa ordinaria

- `SolarTerrain.legacyOceanOverride` y generación v1 siguen siendo alcanzables desde snapshots antiguos: **no son código muerto**. Lo mismo ocurre con los campos heredados de Guardians para tickLegacy.
- `SolarSettings.defaults:181–184` y constructor corto de SolarLayout usan v2; snapshot de config usa v6. Son defaults de compatibilidad/fixtures, no prueba de que el preset nuevo cree v2. Sí facilitan fixtures que no ejercitan la versión vigente.
- `feature/SphereFeature` declara “Not in use”; sigue registrado en `registry/ModFeature:15` y tiene configured/placed features. Su inclusión automática está comentada en `data/SphereBiomeBoostrap.bootstrap:33`. Es una implementación heredada inactiva en el circuito ordinario, **no inaccesible**: un datapack puede referenciarla. No hay evidencia de que genere sistemas solares.
- `gametest/SolarVolumeBaseline` es referencia literal de equivalencia, no solución de generación pendiente ni código muerto de producción por ausencia de llamadas normales. Preservar salida anterior no demuestra que esa salida cumpla todos los requisitos.

**Gravedad:** baja para mantenimiento. **Prueba:** seguir registros y referencias de recursos; contrastar generación del preset suministrado con un datapack que invoque expresamente sphere_feature. No se recomienda eliminar estas rutas sin decidir primero compatibilidad.

## Evidencia favorable y conclusiones que no deben inferirse

La semilla se conserva como long y participa en centros, ids y radios. Los ids completos se dividen en dos int para alimentar SatellitePlacement; no se encontró que se descarte la semilla del mundo en Solar. Las listas publicadas son inmutables; las carreras de la caché pueden repetir cálculos, pero no introducen por sí mismas resultados distintos. BoundWorld es volatile y createState synchronized; SolarBiomeSource publica layout después de sus recursos de enlace.

La pertenencia consulta solo la celda actual. Para parámetros ordinarios esto está respaldado por la envolvente: spacing reserva dos radios de sistema más jitter y hueco; los cuerpos/lunas se mantienen dentro de esa reserva. No hay evidencia para inventar un fallo en coordenadas negativas: se usa floor/floorDiv. Debe verificarse la continuidad física de chunks junto a fronteras, pero la ausencia de búsqueda 3×3 no demuestra por sí sola un defecto aquí.

Las pruebas existentes cubren geometría, equivalencia de reglas, parámetros, grafos y varias interacciones del motor. **No demuestran:** reinicio real con chunks y entidades persistidos; portal activado y viaje; ojos/locate reales; exclusión de todas las rutas de mods; entrada/respawn multijugador; rendimiento sostenido ni compatibilidad C2ME. PROGRESS ya reconoce expresamente buena parte de estas limitaciones.

## Verificación posterior propuesta

1. Reproducir C1/C2 con fixtures mínimos y después en copias de mundos, sin corregir todavía la implementación.
2. Ejecutar aceptación v6 del Stronghold completo, portal, ojos y locate en cliente y servidor dedicado; conservar semilla, config, versiones y lista de mods.
3. Guardar, descargar/cargar chunks y reiniciar; comparar snapshots, bloques/heightmaps, jaulas y marcas de mobs. Repetir con cambios controlados de tags para R1.
4. Probar dos o más jugadores, spawn/respawn, límites locales y distancias de simulación normales, sin chunks forzados del fixture.
5. Generar las mismas regiones en orden normal, inverso y concurrente; comparar bloques, biomas, estructuras y heightmaps. Ejecutar la comparación vanilla/Biospheres contra una referencia conocida.
6. Perfilar exploración simultánea y probar cada mod de biomas, estructuras y generación concurrente que se quiera declarar compatible.

Hasta completar estas comprobaciones, la afirmación sustentable es “implementado con aceptación integral pendiente”, no “terminado”. Ningún riesgo de este informe se contabiliza como fallo observado en juego.
