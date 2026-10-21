# Solar System - Issac Edition — progreso

## Revisión actual — límite del forro de bedrock central

- Causa: el modo SHELL usaba Y=127, centro geométrico elevado del sol, como techo
  del forro. Esa cota no representa el nivel de terreno de la plantilla (Y=63).
- SolarTerrain.bedrockLiner limita solo el sol a Y=63, sin elevar un centro que
  ya estuviera más bajo. Se mantiene el grosor y el techo relativo al centro en
  planetas orbitales. Desactivado retorna false antes de calcular la cota.
- Generación, protección de bloques y filtrado de heightmaps siguen compartiendo
  la misma regla. No se modifican guardianes, Biospheres clásico, geometría,
  codecs, opciones, mod_version ni biospheres.json.
- Pruebas puras contra el predicado literal anterior: solo se elimina forro solar
  por encima de Y=63; planetas, zonas inferiores y opción apagada se conservan.
  GameTest en Y=63/64/80/127 verifica bedrock bajo el límite, piedra conservada
  encima y heightmaps que cuentan ese terreno superior correctamente.
- La comparación del escritor mantiene reglas de terreno compartidas; valida el
  escritor y no se usa como única referencia de la política nueva. Esta política
  tiene las pruebas independientes indicadas arriba.
- El cambio afecta a chunks nuevos: no elimina murallas ya guardadas. La vista
  superior del sistema muestra órbitas, no relieve ni el forro. La inspección de
  la costa/sol con chunks recién generados sigue siendo aceptación manual.
- Evidencia: run/logs/solar-central-bedrock-final.log.
- Resultado: build, suite JUnit, Javadoc y dieciséis GameTests aprobados;
  git diff --check sin errores. Guardianes y generador clásico sin modificaciones.

## Revisión anterior — una órbita por planeta y defensa central reforzada

- Las instantáneas nuevas usan layout 6. Cada planeta recibe una distancia al sol
  distinta y conserva el cálculo angular anterior, su id, clima y Y configurada.
  El radio envolvente crece para incluir todas las órbitas y lunas; la separación
  entre sistemas usa el mismo cálculo de seguridad existente. Las instantáneas
  1–5 conservan su geometría. No se cambia mod_version ni biospheres.json.
- El cálculo envolvente de lunas se comparte entre las bandas históricas y las
  órbitas individuales para no duplicar esa regla. No se añaden clases ni opciones.
- Las jaulas intentan hasta dos guardianes cada 160–240 ticks, con límite ocho
  dentro del entorno de 32 bloques del jugador y 24 alrededor del sol. El ciclo
  usa solo el presupuesto restante. Se mantienen colisiones, Pacífico y gamerule.
- Alcance 224: incluye el centro del volumen central. Candidatos de ghast/blaze
  a menos de 16 bloques del jugador y phantom a menos de 12, con dispersión lateral
  para que una pareja no quede forzada a compartir una caja de colisión. Phantom
  sigue siendo nocturno; las apariciones pueden fallar si hay obstrucciones.
- Jaulas marcadas existentes: migración NBT una vez al activarse, después de validar
  mundo Solar y opción encendida. Cambian sus valores de combate, no sus bloques
  ni posiciones; las jaulas destruidas no reaparecen. No se fuerzan chunks.
- Pruebas: cantidad y órbitas distintas para 1–16 planetas; comparación con layout 5
  de ángulos, ids, climas y Y; separación entre planetas y envolventes de sistemas.
  Radios 512 conservan Y=-40/62/300 sin introducir límites verticales nuevos.
  GameTests de guardado y Stronghold completo incluyen layout 6; la comparación
  literal del escritor también incluye su geometría. Migración marcada/idempotente
  y spawners ordinarios sin cambios se verifican en el motor.
- Resultado: build, suite JUnit, Javadoc y quince GameTests aprobados; 240 fases
  del escritor comparadas con bloques y heightmaps idénticos a la rutina literal.
  La vista previa de -4634224182739581737 refleja las ocho órbitas nuevas, usando
  el SolarLayout actual y conservando la semilla completa como cadena.
- Evidencia: run/logs/solar-single-orbits-final.log. Pendiente manual: dificultad
  efectiva en supervivencia, persecución y chunks activos del mundo del jugador.
  La migración no instala jaulas adicionales en chunks antiguos ni certifica
  combate obligatorio en cada punto. Para la nueva distribución orbital se
  requiere mundo nuevo; cambiar geometría de chunks existentes rompería continuidad.

## Revisión anterior — reglas compartidas y contratos del código

- SolarPortalGuard.allowsNaturalPortal concentra la autorización completa: sala
  activa en el hilo, posición dentro de ella y margen de tres bloques en el centro.
  Ambos mixins conservan sus filtros de preset/chunk y tipo de bloque antes de
  delegar. La restauración del ThreadLocal sigue protegida por finally.
- SolarLayout.Moon concentra la distancia al cuadrado y la frontera inclusiva que
  usan el escritor de bloques y los heightmaps. Conserva Math.pow y el orden de
  sumas; el escritor reutiliza la distancia para las capas, sin calcularla dos veces.
- SolarTerrain da nombre a la reserva del Stronghold (Y máximo 48), inicio del
  perfil peligroso (Y mínimo 56), profundidad superficial y márgenes. El margen
  del Stronghold y el de bedrock tienen nombres distintos: compartir el valor 5
  no significa que representen la misma regla.
- Javadocs ampliados en contratos de portales, terreno, geometría lunar, parámetros
  de colocación, instantáneas Solar, densidad y escritura/filtrado de bloques.
  No se añadieron clases, opciones ni cambios de codec o versión en este bloque.
- Verificación: 250 000 puntos lunares comparados bit a bit con la fórmula literal,
  fronteras y sus doubles adyacentes; reglas de superficie comparadas con los
  predicados anteriores para las 147 456 combinaciones de Y/superficie.
- El GameTest de portales ahora prueba autorización positiva/negativa, sala central,
  sala orbital, ausencia de sala, frontera a dos/tres bloques, lunas, vacío y
  escrituras de portal/marco en ProtoChunks marcados; la ruta no Solar sigue intacta.
- Se mantienen las 192 comparaciones de fases contra el escritor literal anterior,
  incluyendo igualdad de bloques y datos crudos de heightmaps. Evidencia de la
  validación de este bloque: run/logs/solar-debt-policy-final.log.
- Resultado final: build, suite JUnit, generación de Javadoc y los catorce
  GameTests aprobados; git diff --check sin errores. Los tiempos diagnósticos
  fluctúan entre ejecuciones y no demuestran una mejora de rendimiento de este bloque.
- Pendientes: Javadocs de otras APIs del proyecto siguen produciendo advertencias;
  el informe no representa deuda técnica global cerrada. Activación del portal,
  ojos/locate en una partida completa y generación concurrente con C2ME requieren
  aceptación en juego. Este refactor conserva la política, no certifica esas rutas.

## Revisión anterior — cobertura de guardianes y refactor modular

- Doce jaulas alrededor del centro, incluidos los polos; alcance 136 bloques,
  un guardián cada 400–800 ticks, máximo cuatro cerca del jugador y 24 por sol.
  Ghast/blaze de día y también phantom de noche. No se reparan jaulas rotas.
- SolarGuardians reúne construcción, eventos de spawn, población y compatibilidad.
  SolarEvents solo enruta sus eventos tras verificar el generador. La marca se añade
  al finalizar el spawn para conservar la inicialización vanilla de los tres mobs.
- SolarChunkGenerator coordina el motor; BoundWorld publica juntos sus recursos
  derivados de la semilla. SolarVolumePainter concentra los bloques y heightmaps.
  SatelliteMineralCatalog resuelve ids/tags para ambos presets sin duplicar esa lógica.
- Optimizaciones: paleta de cascarón resuelta una vez, caché acotada de posiciones,
  descarte de chunks lejanos antes de calcular jaulas, sin consultas de población
  durante cooldown, sin resolver minerales con satélites apagados y sin recorrer
  aire por encima de bloques existentes y cuerpos. Se conserva el primado de heightmaps.
- Verificación: `build` y `runGameTestServer --no-configuration-cache` correctos;
  JUnit y catorce GameTests aprobados. Javadoc se genera con advertencias de
  documentación incompleta en otras APIs del proyecto.
- La rutina literal anterior de volumen existe solo como baseline de GameTest;
  se verificó que su cuerpo coincide exactamente con HEAD antes del refactor.
  192 fases comparadas: todos los bloques y datos crudos de heightmaps idénticos.
  Incluye layouts 1/2/4/5, calor/frío, paleta inválida, bordes, lunas, vacío y
  un bloque alto existente que el recorrido acotado también debe borrar.
- Cobertura: radios 170–190 con cota continua de icosaedro y margen de redondeo;
  muestreo cada tres grados para tres radios y 30 semillas. Jaulas apagadas no
  escriben bloques ni block entities. Reaparición real de cada especie tras dos
  muertes a 132 bloques; los candidatos quedan a 32 bloques del jugador (12 para
  phantom), dentro de su rango de combate y sin omitir colisiones. El jugador
  simulado carga primero su vecindad y espera a que las secciones de entidades
  estén listas; la carga forzada pertenece exclusivamente al fixture de GameTest.
- Medición diagnóstica de las 192 fases: literal 2205 ms, refactor 1537 ms en
  una ejecución; no representa rendimiento de un mundo ni certificación con C2ME.
- Pendiente manual: línea de visión, persecución, combate y distancias de simulación
  habituales en un mundo Solar completo. No se promete combate obligatorio en cada
  punto por cobertura geométrica. Chunks existentes no se modifican: usar un mundo
  nuevo para evaluar las doce jaulas. Layouts 1–4 conservan sus reglas anteriores.

## Revisión anterior — reutilización del terreno Biospheres

- Solar v5 usa el holder biosphereworlds:biospheres, sin tocar biospheres.json.
- Se sustituye el océano recortado por bloques por los mismos cálculos de densidad
  y clima del océano original, adaptando solo los centros/radios solares.
- SphereEdgeOcean comparte peso 3D y transformación sin cambiar el orden aritmético;
  se compara contra una copia literal del archivo anterior, con opciones activadas
  y apagadas, varias anchuras/profundidades, radios y coordenadas.
- Un RandomState por mundo Solar evita modificar el estado o los ajustes clásicos.
  Los adaptadores visitan el hijo en mapAll y no se serializan: el codec conserva
  el holder original y la instantánea Solar. La clasificación de generador mantiene
  Solar fuera de los mixins clásicos aunque comparta la plantilla de ruido.
- Se omiten los carvers biome vanilla adicionales en v5, igual que en el preset
  Biospheres. Superficie, cuevas de ruido y océanos usan la misma plantilla.
- El sol conserva el perfil magma/hielo y el Stronghold completo en su cota segura.
- Snapshots v1–v4 conservan su lógica previa; legacyOceanOverride queda únicamente
  para ellos. Se requiere mundo Solar nuevo, sin regenerar chunks existentes.
- Nuevas pruebas de motor: perfil sembrado FLOOR/CLIMATE contra la matemática
  compartida, no mutación del estado original, columnas de terreno, ausencia de
  recorte posterior, sala de portal única y serialización sin adaptadores runtime.
- Verificación aprobada: 320 000 comparaciones bit a bit contra la matemática
  literal anterior, suite JUnit y 11 GameTests (incluidos Stronghold y guardado).
  Evidencia: run/logs/solar-shared-terrain.log, BUILD SUCCESSFUL.
- Pendiente de aceptación: inspección visual de la costa en una partida Solar nueva,
  activación del portal, rendimiento y compatibilidad con mods.

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
- Spawn inicial sin plataforma: usa la superficie natural del planeta templado,
  admite agua y no coloca piedra ni despeja bloques alrededor del jugador.
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
- Spawn inicial en un orbital templado sobre la superficie natural, sin plataforma.
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
