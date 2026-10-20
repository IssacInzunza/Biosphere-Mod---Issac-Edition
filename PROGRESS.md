# Solar System - Issac Edition — progreso

## Revisión actual — configuraciones independientes y jaulas (2026-10-19)

Esta sección sustituye las decisiones de configuración y guardianes de las revisiones inferiores.

- Preset visible: Solar System - Issac Edition, con identificador solar_systems conservado.
- config/biosphereworlds/BiosphereWorlds.toml y SolarSystems.toml independientes.
  GeneralOptions/SolarOptions centralizan definiciones y traducciones; los valores
  de ambos builders son distintos. La migración no borra archivos raíz ni pisa destinos.
  Prioriza el último config unificado sobre un Solar experimental antiguo al inicializar.
- Versión 4: ninguna luna generada para el sol; los planetas conservan sus satélites.
  Versiones 1–3 conservan la geometría y el spawn antiguos. Crear un mundo nuevo
  para obtener esta revisión; no se borran lunas ni se colocan jaulas en chunks existentes.
- Hasta tres pedestales de obsidiana con jaulas, fuera del sol y sin colisiones,
  generados al finalizar la decoración: ghast, phantom y blaze. Jaulas destructibles;
  no se reparan automáticamente. guardians_enabled=false no escribe nada adicional.
- Un mob por ciclo, intervalo 30–60 s, activación a 96 bloques. El control de jaulas
  marcadas limita el entorno del sistema a 2 ghasts, 3 phantoms y 3 blazes, respeta
  Pacífico/doMobSpawning y pausa phantoms de día. No usa cupo natural de monstruos
  ni fuerza chunks para spawn. Las jaulas normales no se modifican.
- /work/ ignorado en .gitignore; no había archivos de work rastreados.
- Fuente de APIs: véase la sección de fuentes de jaulas al final de ARQUITECTURA.
- Pruebas nuevas: jaulas reales activas con jugador a 80 bloques y reaparición
  después de matar cada especie; guardado de jaula y marca del mob; generación
  de pedestales/jaulas y no-op al desactivar; migración sin sobrescrituras;
  geometría de 100 semillas sin lunas centrales ni cambios en los planetas.
- Compilación, trece pruebas JUnit Solar y diez GameTests aprobados.
  Evidencia actual: run/logs/solar-spawners.log (BUILD SUCCESSFUL y 10/10).
- Sigue pendiente la aceptación en un mundo Solar real: dificultad práctica,
  comprobación visual, portal activo, rendimiento y compatibilidad con mods.

## Revisión de configuración y aspecto — 2026-10-19

Solicitud aprobada: simplificar controles, explicar cada ajuste como en el config
clásico, repartir planetas alrededor del centro y darle superficie de magma/hielo
según la dirección térmica.

### Cambios actuales (sustituyen las decisiones iniciales que se detallan abajo)

- Configuración unificada en `BiosphereWorlds.toml`, registrada por SphereConfig.
  SolarConfig solo adapta los valores generales existentes a una instantánea Solar;
  no registra otro archivo ni mantiene copias de los controles generales.
- SolarOrbits contiene solo `planet_count`, `planet_gap`, `system_gap` y
  `climate_direction`, más `guardians_enabled` solicitado después: 8 planetas, 400 bloques entre superficies, 1500 entre sistemas.
  Temperatura: entero 0..1, mostrado con el deslizador nativo de NeoForge;
  0 = centro de magma/lava y exterior frío; 1 = centro de hielo y exterior cálido.
- Radios, altura, cascarón, bedrock, océanos y satélites se leen de Settings.
  Solar acepta los rangos clásicos de satélites y océanos; valida radios positivos
  y min <= max sin el límite planetario de 160. close_open_top compartido controla
  el cierre en Y=319 sin reducir radio ni cambiar center_y. Los puentes están
  siempre desactivados; cuadrícula y hemisferios no aplican. Se explica en los tooltips. SolarSystems.toml ya no se lee.
- Se retiran los controles exclusivos de radio de sistema, radio central,
  proporciones climáticas y clima central independiente. El espacio
  del sistema se calcula de forma conservadora, con espacio para lunas; el tamaño
  central y la dificultad de guardianes permanecen internos.
- `SolarLayout` v2 genera exactamente la cantidad elegida en hasta tres órbitas
  concéntricas, repartidas angularmente con variación pequeña, sin anclarlas a
  planetas anteriores. La distancia es un mínimo entre superficies; las órbitas
  pueden dejar huecos mayores. La banda intermedia siempre es templada.
- El centro v2 sigue la dirección elegida: Badlands con superficie de magma y
  agua superficial convertida en lava, o Ice Spikes con hielo compacto. Se usan
  esos biomas si están disponibles en la fuente; en caso contrario se conserva
  la selección climática compatible. El tratamiento se realiza antes de
  estructuras, únicamente desde Y=56; las piezas del Stronghold siguen en Y<=48.
- `SolarSettings` guarda las opciones generales y `layout_version=3`. Un snapshot
  antiguo sin esos campos conserva versión 1 y sus valores heredados. La ruta v1
  de colocación se conserva, igual que el ajuste vertical de v2. Hace falta crear un mundo Solar nuevo para ver la
  revisión; no se migra ni se reubica un mundo guardado.
- README y ARQUITECTURA sustituyen las tablas anteriores por las cinco opciones
  solares y las generales compartidas, con sus significados actuales.

### Unificación, radios y guardianes

- Se elimina el archivo registrado SolarSystems.toml; no se borra el archivo antiguo.
- Deslizador entero 0/1, un solo conjunto de opciones generales y tooltips corregidos.
- Versión 3 persiste close_open_top opcional (false al leer snapshots anteriores).
- Capas del cascarón y tapas usan la misma prueba en generador, protección y heightmaps.
- guardians_enabled controla únicamente el spawn adicional de guardianes centrales.
- Pruebas nuevas: lectura de config compartido, rangos de satélites/océanos,
  planetas de radio 512 con centro Y=62, tapas activadas/desactivadas y persistencia.

### Verificación de esta revisión

- Compilación y suite JUnit correctas. Las doce pruebas Solar cubren geometría,
  concurrencia, satélites, no-op, temperaturas y separación de la superficie
  respecto a la envolvente del Stronghold.
- Nueva prueba angular: cantidades 1–16, 40 semillas por cantidad, cantidad exacta
  y distancia mínima. Con tres o más planetas se comprueba que no quepan todos en
  un mismo semiplano alrededor del centro.
- Nueva prueba de clima: banda intermedia templada y posiciones idénticas al
  intercambiar los extremos térmicos.
- Siete GameTests de NeoForge: restricciones de Stronghold/portal, heightmaps,
  superficie extrema, snapshots antiguos, config compartido y tapas de radio 512.
  La prueba de superficie ejecuta el tratamiento real sobre ProtoChunks, comprueba
  magma/lava e hielo y conserva un marco de portal de prueba a Y=32. También
  comprueba que ambos perfiles admitan un Stronghold con sala única.
- Evidencia actual: `run/logs/solar-config-unified.log` (BUILD SUCCESSFUL y
  siete pruebas de motor aprobadas). La revisión anterior está en solar-revision.log.
- Pendiente en cliente: inspección visual del menú y del mundo, proporciones
  visuales de las órbitas, acceso, temperatura aparente, guardianes y portal activo.
  Los GameTests no certifican esa aceptación ni compatibilidad con mods externos.

### Archivos de esta revisión

SolarConfig; SolarSettings, SolarLayout, SolarBiomeSource, SolarChunkGenerator y
SolarTerrain; MixinSolarPortalPlacement para respetar el cascarón configurado;
SolarLayoutTest, SolarTerrainTest y SolarWorldgenGameTests; idiomas en_us/es_es/es_mx,
README.md, docs/es/ARQUITECTURA.md y este registro. No se modifica el generador
clásico, mod_version ni biospheres.json. No hay commits ni push.

## Contrato

- Segundo generador independiente; Biospheres y los presets vanilla conservan su lógica.
- Sin puentes automáticos ni dependencia de `generate_bridge`.
- Exclusivamente Solar: exactamente un Stronghold completo con sala de portal funcional
  por sistema, dentro de su biosfera central. Ningún Stronghold ni portal del End
  natural en planetas orbitales, satélites, vacío u otras ubicaciones del generador.
- La existencia de clases o una compilación correcta no acredita las pruebas en juego.

## Auditoría (fase 1 aprobada)

- El preset clásico usa NoiseBasedChunkGenerator y ajustes biosphereworlds:biospheres.
- Sus mixins, geometría, acuíferos y heightmaps dependen de la configuración clásica.
- La restricción clásica de estructuras solo valida el inicio, no todas las piezas.
- Existen colocación de satélites, capas de magma y catálogo de minerales por tags.
- SphereLayout y las semillas de SatellitePlacement no reciben la semilla del mundo.
- El catálogo de minerales lee configuración clásica: no debe reutilizarse como
  configuración Solar implícita.

## Fuente de Minecraft consultada

Minecraft 1.21.1 / NeoForge 21.1.216, caché local:
`C:/Users/issac/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_d1701b2d84878ca1ab690eb7c5e8e0275ec75727_output.jar`.

- StrongholdStructure.java:23-45: construcción y repetición hasta obtener portal.
- StrongholdPieces.java:66-71: estado estático mutable; requiere serializar la
  construcción Solar, sin compartir builders o fuentes aleatorias.
- ChunkGenerator.java:137-174: localización distingue anillos y RandomSpread.
- EnderEyeItem.java:88: los ojos consultan EYE_OF_ENDER_LOCATED.
- LevelTickEvent.java:58-65 y ExplosionEvent.java:59-75: eventos disponibles.

## Implementación inicial (histórica; ver revisión actual arriba)

Implementado, con aceptación en juego pendiente:

- Preset `biosphereworlds:solar_systems`, con generador y biome source propios.
- Sistemas sembrados, centros con variación procedural, planetas sin solapamiento,
  límites verticales, caché acotada e instantáneas inmutables. Si se agotan los
  intentos, se producen menos planetas; existe una reserva determinista para
  conservar al menos un orbital templado.
- Configuración independiente `SolarSystems.toml`, 29 opciones con idiomas en_us,
  es_es y es_mx y tablas en README/ARQUITECTURA. La configuración se lee solo al
  enlazar un generador Solar nuevo a su semilla; decodificar su preset en un mundo
  vanilla no consulta ni valida los ajustes Solar del usuario.
- Instantánea de todos los ajustes en el codec del generador; al reabrir un mundo
  no se sustituyen por los valores actuales del TOML.
- Ruido/superficie/cuevas/decoración vanilla dentro de cuerpos; recorte exterior,
  cascarón de vidrio, forro inferior opcional y océano de borde opcional.
- Clima por rango orbital, inversión de extremos sin cambiar los templados,
  clima central independiente y possibleBiomes incluyendo the_void.
- Colocación central exclusiva de Stronghold, conservando su clave vanilla y el
  contrato RandomSpread que usa la localización. Se filtran las demás colocaciones
  de Stronghold exclusivamente en el estado del generador Solar.
- Construcción de piezas vanilla con sala de portal única, hasta 128 intentos y
  comprobación de las cajas reales de todas las piezas. Un fallo produce un error
  explícito, no un sistema silenciosamente incompleto.
- Permiso de escritura de portales limitado a la caja de la sala de portal de un
  Stronghold central; restauración del contexto con finally. Protección en
  WorldGenRegion y también en escrituras normales directas a ProtoChunks marcados
  como Solar. No intercepta comandos ni construcción de jugadores en LevelChunk.
- Guardianes vanilla mediante evento NeoForge por intervalos: proximidad de
  jugadores, chunks cargados, límites locales y presupuesto global de monstruos,
  sin Pacífico ni doMobSpawning=false. Phantoms solo de noche, sin exigir insomnio.
  Protección del cascarón central ante explosiones de ghasts guardianes marcados.
- Satélites reutilizando SatellitePlacement/SatelliteLayers/MineralCatalog:
  tamaño 8–12%, elevación configurable, chequeo entre todos los planetas y lunas,
  catálogo Solar inmutable por apertura del mundo obtenido de tags, sin leer la
  configuración clásica. No se genera ningún puente.
- Spawn inicial en un orbital templado, con una plataforma de piedra pequeña.
- ChunkEvent.Load restaura la referencia de geometría Solar para que los
  heightmaps sigan filtrando cascarones y lunas al cargar chunks, sin pedir chunks
  ni efectuar búsquedas durante ese evento.

### Archivos modificados

- Existentes: BiosphereWorlds.java, config/SphereConfig.java,
  registry/ModBiomeSources.java y mixin/MixinHeightmap.java, dentro de
  `src/main/java/mx/issacinzunza/biosphereworlds/`.
- Existentes: README.md, docs/es/ARQUITECTURA.md, los tres idiomas,
  biosphereworlds.mixins.json y el tag de presets normal.json.
- Nuevos en el paquete Java: config/SolarConfig.java, event/SolarEvents.java,
  gametest/SolarWorldgenGameTests.java; worldgen/solar/{SolarLayout,SolarSettings,
  SolarTerrain,SolarBiomeSource,SolarChunkGenerator,SolarPlacement,SolarWorldgen,
  SolarChunkAccess,SolarStronghold,SolarPortalGuard}.java.
- Nuevos mixins: MixinSolarChunkAccess, MixinSolarStronghold,
  MixinSolarStructureBounds, MixinSolarPortalRoom, MixinSolarPortalPlacement,
  MixinSolarProtoChunkPortals y accessor/SolarStructureStateInvoker.
- Nuevos recursos: world_preset/solar_systems.json y structure/solar_empty.nbt.
- Nuevas pruebas JUnit: SolarLayoutTest y SolarTerrainTest.
- No se han modificado mod_version, biospheres.json ni los hashes clásicos.
  No hay commits ni push.

## Pruebas

- `gradlew build`: compilación y suite JUnit correctas, incluidas las pruebas
  previas del proyecto y siete pruebas nuevas de geometría, concurrencia,
  colisiones de satélites, clima y decisiones de no-op.
- `gradlew runGameTestServer --no-configuration-cache`: tres GameTests en
  Minecraft 1.21.1 / NeoForge 21.1.216. Última ejecución de verificación registrada
  en `run/logs/solar-acceptance-2026-10-19.log` al finalizar esta sesión.
- Motor: 20 construcciones de Stronghold (cuatro semillas, cinco celdas cada una),
  validación de sala única, contención y rechazo en orbitales; identidad entre
  candidatos de colocación/localización; codec y geometría tras serialización.
- Motor: rechazo de marcos y bloques de portal por ProtoChunk fuera del contexto
  de sala autorizada, en centro, orbitales, lunas disponibles y vacío; los chunks
  sin marca Solar conservan sus escrituras de marcos.
- Motor: primado y actualización descendente de heightmap ignoran el cascarón.
- JSON de idiomas/preset válidos y `git diff --check` sin errores.
- Estas pruebas NO equivalen a generar y explorar un mundo Solar completo ni a
  accionar el portal, usar ojos o ejecutar /locate en ese mundo.

### Errores encontrados y corregidos

- La caja ajustada de StructureStart puede incluir un margen de adaptación de
  terreno: se rechazaba una estructura cuyas piezas sí cabían. La validación
  ahora usa la caja de las piezas reales, conservando el requisito completo.
- Heightmap consulta posiciones locales al chunk tanto durante el primado como
  al buscar hacia abajo. La ruta Solar ahora las convierte a coordenadas globales;
  se añadió una prueba de motor que fallaba antes de la corrección.
- El recorte puede dejar columnas completamente vacías; primeHeightmaps por sí
  solo no borra alturas anteriores. Se reinician sus datos antes de recalcular.
- Los centros necesitan reservar espacio para la variación procedural y quedar
  alineados a chunks; el espaciado garantiza el hueco mínimo incluso entre
  desplazamientos opuestos.

## Límites y decisiones pendientes

- Ningún número de reintentos demuestra por sí solo que todas las semillas puedan
  producir un Stronghold completo dentro de una esfera: hace falta validar el
  volumen final. Esa validación y el fallo explícito están implementados; la prueba
  del motor cubre las muestras indicadas, no todas las semillas posibles.
- Mods externos pueden escribir bloques directamente: no se debe afirmar que una
  protección intercepta todos esos mecanismos. Escrituras directas a secciones,
  chunks no marcados o rutas de otros mods todavía no están certificadas.
- La restricción se aplica al Overworld cuyo generador es Solar. Nether y End
  conservan generación vanilla, incluido el portal de salida del End.
- Solar requiere Generate Structures activado: se rechaza su spawn inicial si
  esa opción está apagada, para no aceptar un mundo sin Strongholds.
- La temperatura objetivo multi-noise no certifica la clasificación de biomas de
  otros mods. Fuentes distintas de multi-noise se delegan sin clasificación nueva.
- El bloqueo de construcción protege las llamadas Solar a StrongholdPieces; no
  certifica modificaciones externas de ese estado estático ni C2ME.

## Funcionalidades y aceptación pendientes

- Crear un mundo Solar en cliente y servidor dedicado; verificar disponibilidad
  de ambos presets y persistencia tras reiniciar.
- Generar físicamente los chunks de Strongholds de varios sistemas, contar salas,
  insertar ojos y viajar al End; comprobar /locate structure minecraft:stronghold
  y trayectorias de ojos desde el centro, orbitales, satélites y vacío.
- Buscar marcos/portales naturales fuera de las salas autorizadas, incluidos
  contenidos de datapacks y mods. La restricción completa no está certificada.
- Comparar chunks de Biospheres y vanilla con una copia del código anterior,
  usando semillas y coordenadas idénticas. JUnit no sustituye esa comparación.
- Comprobar terreno, cuevas, vegetación, estructuras, océanos y heightmaps con
  satélites, incluyendo generación en distinto orden y con C2ME.
- Probar guardianes en Pacífico y otras dificultades, límites, explosiones,
  despawn, aproximación y dificultad real con jugadores.
- Probar minerales y biomas con cada mod solicitado; medir tiempo de generación,
  memoria y exploración prolongada. No se declara compatibilidad ni rendimiento.

## Próxima etapa recomendada

Aceptación en un mundo Solar nuevo con Generate Structures activado, empezando
por la colocación completa de la sala del portal y su localización. Las etapas
2–7 siguen pendientes de sus comprobaciones críticas en juego; no se declara
terminada la funcionalidad por compilar ni por pasar estas pruebas acotadas.
