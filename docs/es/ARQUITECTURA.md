# Arquitectura de Biosphere Worlds

## Solar Systems — configuración simplificada y órbitas (experimental)

El preset se presenta como **Solar System - Issac Edition**; su identificador
solar_systems no cambia. `SolarChunkGenerator` mantiene un circuito independiente.
`SphereConfig` registra `config/biosphereworlds/BiosphereWorlds.toml` y
`config/biosphereworlds/SolarSystems.toml`, con valores independientes.
GeneralOptions y SolarOptions definen los controles una sola vez; SolarConfig los
instancia con su propio builder. Las opciones generales mantienen las mismas
etiquetas, comentarios y validadores. Solar no expone puentes, cuadrícula ni hemisferios.
La migración copia archivos raíz sin borrarlos ni sobrescribir destinos. Si el
archivo clásico contiene SolarOrbits, esos valores recientes inicializan Solar;
si no, se considera el archivo Solar anterior. Se convierten SolarGeneral y la
dirección térmica textual de los formatos experimentales.

El codec conserva la instantánea completa. `layout_version` ausente se interpreta
como 1, con los valores generales heredados; así los mundos Solar existentes mantienen
su geometría y perfil anteriores. Los mundos nuevos guardan versión 5 y los ajustes
generales de cascarón, altura, bedrock, océanos y satélites. **Hace falta un mundo Solar
nuevo para ver el reparto y el centro corregidos.** No se migra un mundo guardado
silenciosamente. Se requiere Generate Structures activado.

`SolarLayout` v2 reparte exactamente la cantidad solicitada en hasta tres órbitas
concéntricas. Cada órbita distribuye sectores angulares uniformes con variación pequeña.
La separación se mide entre superficies y es un mínimo; las órbitas pueden dejar
huecos mayores. El radio de la región se calcula internamente a partir de cantidad,
tamaño, separación y espacio para satélites. Las consultas siguen acotadas por celdas
y los sistemas se publican como instantáneas inmutables en una caché de 256 entradas.
Las implementaciones v1, v2 y v3 conservan su comportamiento en las instantáneas guardadas.
La versión 3 usa el radio y center_y compartidos sin limitar el radio planetario a 160
ni recolocar el centro para que quepa. close_open_top controla la tapa de Y=319;
la geometría publica una única prueba de cascarón/tapa para generador y heightmaps.

La versión 5 usa el holder original biosphereworlds:biospheres como plantilla,
sin editar biospheres.json. SolarDensityFunction adapta los marcadores existentes:
MultipleSpheresDistanceFunction recibe los cuerpos solares y SphereEdgeOceanFunction
reutiliza la matemática de SphereEdgeOcean (peso tridimensional smoothstep y
transformación FLOOR/CLIMATE), sin reescribir bloques para el océano después del ruido.
SphereEdgeOcean extrae funciones compartidas conservando el orden de operaciones;
una copia literal anterior en JUnit comprueba igualdad bit a bit para el preset clásico.
Solar obtiene su RandomState independiente con los mismos parámetros de ruido y semilla.
El estado externo y el holder original quedan intactos. mapAll recorre los hijos.
Los adaptadores solo existen en memoria: el codec del generador guarda la plantilla
y la instantánea Solar. Se evita marcar como Biospheres un generador Solar incluso
si usa ese holder. Todos los puntos de generación y columnas usan el estado propio.
Solar v5 omite carvers adicionales vanilla: el preset clásico solo ejecuta los carvers
de cascarón/puentes; sus cuevas vienen del router de ruido. Las versiones 1–4 conservan
su circuito anterior por compatibilidad. Hace falta un mundo Solar nuevo; los chunks
existentes no se regeneran. El sol mantiene su perfil extremo y la cota del Stronghold.

El centro v2 es cálido o frío según la dirección seleccionada, sin otra opción de
clima central. Usa Badlands o Ice Spikes si la fuente contiene ese bioma, y conserva
el fallback climático si no lo contiene. La superficie cálida se cubre de magma y
el agua superficial pasa a lava; la fría usa hielo compacto. El tratamiento ocurre
después de superficie y antes de estructuras, solo en Y>=56. La construcción del
Stronghold conserva su envolvente completa en Y<=48. El centro mantiene radio 180
y altura 127 automáticamente. La banda orbital intermedia permanece templada.

Los porcentajes climáticos y el radio del sistema no se exponen.
`guardians_enabled` controla hasta tres pedestales orbitales con jaulas vanilla
para ghasts, phantoms y blazes, colocadas al terminar la decoración. SolarLayout
elige posiciones sin colisión con cuerpos ni lunas. Cada jaula genera un mob,
con demora de 600–1200 ticks y RequiredPlayerRange=96; reglas explícitas evitan
restricciones de dimensión/luz incompatibles, manteniendo colisiones y Pacífico.
MixinSolarSpawner solo controla jaulas marcadas en Solar: limita el entorno del
sistema a 2 ghasts, 3 phantoms y 3 blazes, pausa phantoms de día y respeta
Peaceful/doMobSpawning. No fuerza chunks para aparecer. Las jaulas rotas no se
reconstruyen. Los mobs llevan la marca que protege el cascarón de explosiones.
La versión 4 omite lunas de la biosfera central. Las instantáneas anteriores
conservan lunas y spawn heredados; se requiere mundo nuevo para esta revisión. Los satélites reutilizan
SatellitePlacement/SatelliteLayers/MineralCatalog con opciones generales persistidas,
incluyendo menas admitidas/excluidas y pesos de mods. La selección por planeta del
cascarón se aplica también a sus lunas y se excluye de los heightmaps.

### Opciones propias — SolarOrbits

| Opción | Predeterminado | Rango | Efecto |
|---|---|---|---|
| `planet_count` | `8` | 1–16 | Cantidad de planetas orbitales, sin contar la biosfera central del Stronghold ni los satélites mineros. Se reparten alrededor del centro en hasta tres franjas orbitales. |
| `planet_gap` | `400` | 0–4000 blocks / bloques | Separación mínima de espacio vacío entre las superficies de las esferas, en bloques; no es una distancia entre centros. También separa el centro de la órbita interior. La geometría orbital puede dejar huecos mayores. |
| `system_gap` | `1500` | 0–16000 blocks / bloques | Separación mínima de espacio vacío entre los límites exteriores de sistemas vecinos, en bloques. El tamaño del sistema se calcula automáticamente a partir de los tamaños y distancias planetarias. |
| `climate_direction` | `0` | 0–1 (deslizador entero) | 0: centro de magma/lava, planetas interiores cálidos, intermedios templados y exteriores fríos. 1: centro de hielo compacto, interiores fríos, intermedios templados y exteriores cálidos. Con pocos planetas se conserva al menos uno templado. |
| `guardians_enabled` | `true` | booleano | Genera jaulas orbitales de ghasts, phantoms y blazes con frecuencia y población bajas. No modifica otros mobs. Se guarda al crear cada mundo Solar. |

### Ajustes generales independientes — Settings

Reutilizan las definiciones del archivo clásico, pero sus valores se leen de SolarSystems.toml al crear cada mundo Solar.
Los mundos Solar guardados conservan su instantánea. Puentes automáticos: **desactivados siempre**.
La cuadrícula clásica y los hemisferios no se aplican a Solar. La tapa superior
usa su propio close_open_top. Se validan min <= max en radios, tamaños, huecos y elevaciones.
Solar usa los radios positivos de Settings sin un límite planetario adicional;
la altura del mundo recorta las esferas grandes. Se acepta tamaño de satélite del 100 %, pero
se omiten candidatos tan grandes como su planeta.

| Opción | Predeterminado | Rango | Efecto |
|---|---|---|---|
| `min_radius` | `128` | entero positivo | Radio mínimo de los planetas orbitales, en bloques. No puede superar el radio máximo. Pon ambos radios iguales para obtener tamaños fijos. |
| `radius` | `128` | entero positivo | Radio máximo de los planetas orbitales, en bloques. La biosfera del Stronghold conserva automáticamente el tamaño necesario para alojarlo completo. |
| `center_y` | `62` | −64–319 | Altura Y exacta del centro de los planetas orbitales. La altura del mundo recorta las esferas que la superan. El centro del Stronghold permanece en Y=127. |
| `sphere_block` | `minecraft:glass` | block IDs / ids de bloques | Ids de bloque para los cascarones. Cada esfera elige uno de forma determinista. Si la lista está vacía o el bloque no es válido, se usa vidrio. Los puentes automáticos están desactivados en Solar. |
| `close_open_top` | `false` | booleano | Tapa las esferas cortadas por el límite de construcción con el bloque del cascarón en Y=319. Desactivado deja el corte abierto; no reduce el radio. |
| `bedrock_mode` | `FLOOR` | FLOOR / NONE / SHELL | FLOOR conserva la bedrock vanilla del fondo del mundo donde llegue una esfera. NONE la sustituye por deepslate. SHELL añade un forro de bedrock de tres bloques por dentro de la mitad inferior del cascarón. |
| `edge_ocean_enabled` | `false` | boolean / booleano | Baja e inunda la banda interior junto al cascarón de los planetas. La biosfera central conserva su perfil peligroso de magma o hielo. |
| `edge_ocean_width` | `20` | 0–100 % | Ancho como porcentaje del radio de la esfera, con el perfil suave tridimensional original. Cero elimina la banda. Se usa únicamente con el océano de borde activado. |
| `edge_ocean_depth` | `20` | 0–100 blocks / bloques | Profundidad del fondo oceánico bajo el nivel del mar, en bloques. Con nivel del mar 63 y profundidad 20, el fondo queda en Y=43. Se usa únicamente con el océano de borde activado. |
| `satellites_enabled` | `true` | boolean / booleano | Genera lunas mineras elevadas alrededor de los planetas, sin puentes. Desactivarlo conserva las posiciones y los climas de los planetas. |
| `satellites_max_count` | `3` | 0–64 | Límite por planeta. Puede haber menos si una luna no cabe sin colisiones o sin superar la altura de construcción. Cero desactiva los satélites. |
| `satellites_min_size_percent` | `8` | 1–100 % | Radio mínimo del satélite como porcentaje del radio de su planeta. No puede superar el máximo. Toda luna aceptada es más pequeña que su planeta. |
| `satellites_max_size_percent` | `12` | 1–100 % | Radio máximo del satélite como porcentaje del radio de su planeta. Los valores altos pueden dejar menos espacio para lunas dentro de la altura de construcción. |
| `satellites_min_gap_percent` | `25` | 0–1000 % | Separación mínima entre superficies del satélite y su planeta, como porcentaje del radio del planeta. También se aplica el mínimo absoluto en bloques. |
| `satellites_max_gap_percent` | `75` | 0–1000 % | Separación máxima sorteada respecto al planeta como porcentaje de su radio. Debe ser mayor o igual al porcentaje mínimo; el mínimo absoluto en bloques sigue teniendo prioridad. |
| `satellites_min_gap_blocks` | `16` | 0–1024 blocks / bloques | Separación mínima absoluta entre un satélite y cualquier planeta u otra luna, en bloques. Se usa el mayor valor entre este mínimo y la separación porcentual sorteada. |
| `satellites_min_elevation_degrees` | `30` | 0–90° | Ángulo mínimo sobre el plano horizontal del planeta, en grados. No puede superar la elevación máxima. |
| `satellites_max_elevation_degrees` | `55` | 0–90° | Ángulo máximo en grados; 90 queda directamente encima del planeta. Se omiten las posiciones que superen la altura de construcción. |
| `satellites_excluded_ores` | `classic exclusions / exclusiones clásicas` | IDs or #tags / ids o #tags | Ids de bloque o #tags que no se elegirán como mineral. Las exclusiones tienen prioridad sobre las inclusiones. Por defecto se excluyen escombros ancestrales, minerales del Nether y menas de deepslate. |
| `satellites_included_ores` | `[]` | IDs or #tags / ids o #tags | Ids de minerales o #tags adicionales a c:ores_in_ground/stone. Solo se aceptan bloques sólidos utilizables, sin entidades de bloque. |
| `satellites_mod_ore_weight` | `3` | 0–1000 | Peso de selección de las menas de otros mods; las vanilla conservan sus pesos. Cero excluye las menas de mods de la selección. |
| `satellites_mod_ore_density_permille` | `20` | 0–1000 ‰ | Bloques de mena por cada 1000 bloques del núcleo para minerales de mods: 20 equivale a 2 %. Se elige un solo tipo de mineral por luna, protegido por piedra y magma. |

Se mantienen la colocación exclusiva central de Stronghold, las piezas vanilla,
la validación del volumen completo y los bloqueos de portales en WorldGenRegion y
ProtoChunks marcados. `SolarEvents` conserva spawn, guardianes por intervalos,
protección de cascarón y restauración de geometría al cargar chunks.
No verificado: escrituras de mods que eviten esas rutas, C2ME, activación real del
portal, ojos, locate en un mundo jugable y rendimiento prolongado. La conservación
de la lógica clásica no sustituye una comparación de chunks contra su copia anterior.

Guía de lectura para **Biosphere Worlds - Issac Edition**, fork de
NeoBiosphere para Minecraft 1.21.1 y NeoForge 21.1.216. Las rutas de este
documento son relativas a la raíz del repositorio. Cuando una afirmación no se
puede demostrar leyendo el código, se marca como **No verificado:**.

## 1. Mapa del proyecto

### 1.1 Punto de entrada, configuración y utilidades

- `src/main/java/mx/issacinzunza/biosphereworlds/BiosphereWorlds.java` —
  clase anotada con `@Mod("biosphereworlds")`. En el constructor registra la
  configuración común y los registros diferidos de funciones de densidad,
  carvers, features, biome sources y serializers de biome modifiers.
- `src/main/java/mx/issacinzunza/biosphereworlds/config/SphereConfig.java` —
  declara y construye la `ModConfigSpec` que NeoForge carga como
  `BiosphereWorlds.toml`. También valida los identificadores de bloques
  configurados.
- `src/main/java/mx/issacinzunza/biosphereworlds/util/SphereUtil.java` —
  contiene cálculos auxiliares de pertenencia a una esfera y comprueba si un
  `BlockState` pertenece a la lista configurada de bloques del cascarón.

### 1.2 Worldgen y geometría

- `src/main/java/mx/issacinzunza/biosphereworlds/carver/SphereCarver.java` —
  `WorldCarver` principal. Recorre las celdas de la cuadrícula que pueden tocar
  un chunk y coloca el cascarón esférico; también calcula centros, radios,
  selección determinista del bloque y pruebas de interior.
- `src/main/java/mx/issacinzunza/biosphereworlds/carver/SphereBridgeCarver.java` —
  subclase del carver que une centros vecinos con un tubo hueco de bloques,
  respetando `generate_bridge`, `bridge_block` y `bridge_radius`.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/SatellitePlacement.java` —
  colocación pura y determinista de los satélites de una esfera principal
  (tamaño, hueco, elevación y ángulo por hash propio). No depende de Minecraft,
  de la semilla ni del orden de generación.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/SphereLayout.java` —
  caché por celda de la esfera principal y de sus satélites; `satellitesAtCell`
  e `isInsideSatellite` se cortocircuitan cuando los satélites están apagados.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/SatelliteLayers.java` —
  geometría pura de las capas del satélite (cascarón, piedra, magma y núcleo) y
  selección determinista del mineral sobre una lista inmutable de candidatos
  `(blockId, peso, densidad en milésimas)`. La lista vanilla es solo el valor por
  defecto de la etapa 2; la selección no la tiene cableada.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/MineralCatalog.java` —
  ensamblado puro de candidatos: vanilla primero, mods ordenados por id,
  exclusiones y deduplicación.
- `src/main/java/mx/issacinzunza/biosphereworlds/event/SatelliteMineralCatalog.java` —
  catálogo `volatile` e inmutable reconstruido en `TagsUpdatedEvent`; descubre
  menas de piedra por `#c:ores_in_ground/stone`, resuelve inclusión/exclusión y
  escribe un INFO por reconstrucción.
- `src/main/java/mx/issacinzunza/biosphereworlds/density/MultipleSpheresDistanceFunction.java` —
  `DensityFunction.SimpleFunction` registrada como
  `biosphereworlds:multiple_spheres`; devuelve la distancia cuadrática
  normalizada al centro de la esfera más cercana.
- `src/main/java/mx/issacinzunza/biosphereworlds/feature/SphereFeature.java` —
  `Feature` que podría colocar una esfera completa a partir de un
  `FeatureConfiguration`. El propio archivo la marca como `Not in use`; el
  worldgen activo usa `SphereCarver`.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereBiomeSource.java` —
  wrapper histórico de `BiomeSource` con Codec propio. Los mundos nuevos ya no lo
  usan: el preset declara `minecraft:multi_noise` directamente. Se conserva
  registrado para cargar mundos antiguos, que guardaron el wrapper en su
  `level.dat`; en esos mundos sigue devolviendo `minecraft:the_void` fuera de las
  esferas y sigue filtrando las consultas de búsqueda de biomas.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereWorldgen.java` —
  almacén de marcas para identificar biome sources, generators, noise chunks,
  aquifers y chunks asociados al preset de biosferas.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereBiomeSourceAccess.java` —
  interfaz que expone la marca booleana inyectada en las implementaciones de
  `BiomeSource`.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereGeneratorAccess.java` —
  interfaz usada por el mixin del `NoiseBasedChunkGenerator` para identificar
  el generator del preset.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereNoiseChunkAccess.java` —
  interfaz para marcar un `NoiseChunk` como perteneciente al worldgen de
  biosferas.
- `src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereChunkAccess.java` —
  interfaz para marcar un `ChunkAccess` como chunk de biosferas.

### 1.3 Eventos

- `src/main/java/mx/issacinzunza/biosphereworlds/event/PlayerSpawnHandler.java` —
  escucha login y respawn. En un `ServerLevel` cuyo generator es el de
  biosferas, busca una superficie sólida dentro de la esfera y teletransporta
  al jugador si apareció sobre el cascarón.

### 1.4 Registros

- `src/main/java/mx/issacinzunza/biosphereworlds/registry/ModBiomeAccess.java` —
  guarda el `HolderGetter<Biome>` capturado desde el preset de multi-noise,
  para resolver posteriormente `Biomes.THE_VOID`.
- `src/main/java/mx/issacinzunza/biosphereworlds/registry/ModBiomeSources.java` —
  registra el `MapCodec` de `BiosphereBiomeSource` con el id
  `biosphereworlds:biospheres`.
- `src/main/java/mx/issacinzunza/biosphereworlds/registry/ModCarvers.java` —
  registra `sphere_carver` y `sphere_bridge_carver`, además de sus claves de
  configured carver.
- `src/main/java/mx/issacinzunza/biosphereworlds/registry/ModDensityFunctions.java` —
  registra el tipo de density function `multiple_spheres` y su clave de
  función configurada.
- `src/main/java/mx/issacinzunza/biosphereworlds/registry/ModFeature.java` —
  registra `SphereFeature` y conserva la clave de su `PlacedFeature`.
- `src/main/java/mx/issacinzunza/biosphereworlds/registry/SphereBiomeModifierSerializers.java` —
  registra los codecs de los biome modifiers personalizados para añadir
  carvers y features.

### 1.5 Datos y biome modifiers

- `src/main/java/mx/issacinzunza/biosphereworlds/data/DataGenerators.java` —
  atiende `GatherDataEvent` y añade el proveedor de entradas de datapack.
- `src/main/java/mx/issacinzunza/biosphereworlds/data/ModDatapackEntries.java` —
  configura un `RegistrySetBuilder` para generar entradas de
  `BIOME_MODIFIERS`.
- `src/main/java/mx/issacinzunza/biosphereworlds/data/SphereBiomeBoostrap.java` —
  registra los biome modifiers que añaden el carver de esfera, el de puentes y
  `minecraft:freeze_top_layer` al bioma `the_void`.
- `src/main/java/mx/issacinzunza/biosphereworlds/data/SphereBiomeCarverModifier.java` —
  añade configured carvers en la fase `GenerationStep.Carving.AIR`, excepto en
  biomas etiquetados como Nether o End.
- `src/main/java/mx/issacinzunza/biosphereworlds/data/SphereBiomeFeatureModifier.java` —
  añade placed features en la fase `TOP_LAYER_MODIFICATION`. En el estado
  actual existe el serializer, pero la llamada que lo usaría en
  `SphereBiomeBoostrap` está comentada.

### 1.6 Mixins y accessor

- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinChunkAccess.java` —
  añade a `ChunkAccess` una marca booleana de chunk de biosferas.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinChunkGeneratorBiomeSource.java` —
  marca el `BiomeSource` devuelto por un generator de biosferas y, si está
  activado, registra una ruta de depuración.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinChunkGeneratorStructures.java` —
  intercepta `ChunkGenerator.tryGenerateStructure` y rechaza inicios fuera del
  interior reducido de una esfera.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinHeightmap.java` —
  hace que el cascarón por encima de `center_y` se comporte como aire al
  actualizar o inicializar heightmaps.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinMultiNoiseBiomeSource.java` —
  marca un `MultiNoiseBiomeSource` y sustituye por `the_void` las consultas
  fuera de las esferas. Usa prioridad `500` para ejecutarse antes que
  TerraBlender (`1000`) en el mismo punto de inyección.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinMultiNoiseBiomeSourceParameterList.java` —
  captura el `HolderGetter<Biome>` durante la construcción de la lista preset
  de multi-noise y lo entrega a `ModBiomeAccess.LOOKUP` (volatile).
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinNoiseBasedAquifer.java` —
  identifica aquifers del worldgen de biosferas y fuerza aire cerca del borde
  interior para evitar que el aquifer rellene el vacío.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinNoiseBasedChunkGenerator.java` —
  identifica el generator por su `NoiseGeneratorSettings`, marca chunks,
  noise chunks y aquifers, y reemplaza `applyCarvers` para ejecutar los dos
  carvers de esfera de forma controlada.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/MixinNoiseChunk.java` —
  añade a `NoiseChunk` la marca usada por el mixin del aquifer.
- `src/main/java/mx/issacinzunza/biosphereworlds/mixin/accessor/StructureManagerAccessor.java` —
  accessor mixin que expone el campo privado `level` de `StructureManager`.

### 1.7 Recursos de runtime y datos

- `src/main/resources/biosphereworlds.mixins.json` — declara los mixins
  obligatorios, su paquete, Java 21 y el `refmap`; no tiene mixins client-only.
- `src/main/resources/META-INF/accesstransformer.cfg` — archivo de access
  transformers; actualmente no contiene reglas.
- `src/main/resources/pack.mcmeta` — metadata del pack, `pack_format` 27 y
  descripción `Biosphere`.
- `src/main/resources/biosphereworlds.png` — logo del mod incluido en el jar.
- `src/main/resources/assets/biosphereworlds/lang/en_us.json` — nombre visible
  del preset en inglés.
- `src/main/resources/data/biosphereworlds/worldgen/world_preset/biospheres.json` —
  preset opt-in: solo cambia el Overworld a generator noise con
  `minecraft:multi_noise` (preset `minecraft:overworld`) y settings propios;
  Nether y End conservan generators vanilla.
- `src/main/resources/data/biosphereworlds/worldgen/noise_settings/biospheres.json` —
  settings de ruido basados en Overworld; su `final_density` usa
  `biosphereworlds:multiple_spheres` y contiene los `spawn_target`.
- `src/main/resources/data/biosphereworlds/worldgen/configured_carver/sphere.json` —
  configuración serializada de `sphere_carver`.
- `src/main/resources/data/biosphereworlds/worldgen/configured_carver/sphere_bridge.json` —
  configuración serializada de `sphere_bridge_carver`.
- `src/main/resources/data/biosphereworlds/worldgen/configured_feature/sphere_feature.json` —
  configuración de la feature no usada actualmente.
- `src/main/resources/data/biosphereworlds/worldgen/placed_feature/sphere_feature.json` —
  placement de esa feature, incluyendo heightmap, biome filter y rarity filter.
- `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json` —
  incluye `biosphereworlds:biospheres` en la etiqueta de presets normales.
- `src/generated/resources/data/biosphereworlds/neoforge/biome_modifier/add_carver/sphere_carver.json` —
  aplica el biome modifier del carver principal.
- `src/generated/resources/data/biosphereworlds/neoforge/biome_modifier/add_carver/sphere_bridge_carver.json` —
  aplica el biome modifier del carver de puentes.
- `src/generated/resources/data/biosphereworlds/neoforge/biome_modifier/add_feature/sphere_snow.json` —
  añade `minecraft:freeze_top_layer` a `minecraft:the_void`.
- `src/main/templates/META-INF/neoforge.mods.toml` — plantilla de metadata
  expandida por Gradle; declara el mod, dependencias de NeoForge/Minecraft,
  mixin config y access transformer. El archivo generado aparece bajo
  `build/generated/sources/modMetadata/META-INF/neoforge.mods.toml`.

### 1.8 Archivos de build y configuración del proyecto

- `build.gradle` — aplica Java/NeoForge ModDev, fija Java 21, define las
  ejecuciones `runClient`, `server`, `gameTestServer` y `data`, incorpora
  `src/generated/resources` y expande la plantilla de metadata.
- `gradle.properties` — fija Minecraft 1.21.1, NeoForge 21.1.216, mappings,
  id/version/licencia y parámetros de Gradle.
- `settings.gradle` — configuración raíz del proyecto Gradle.
- `gradlew` y `gradlew.bat` — wrappers para ejecutar Gradle sin instalar una
  distribución manualmente.
- `gradle/wrapper/gradle-wrapper.properties` y
  `gradle/wrapper/gradle-wrapper.jar` — versión y bootstrap del wrapper.
- `LICENSE` — licencia GPL-3.0 del proyecto.
- `README.md` — documentación de instalación, configuración, compatibilidad,
  build y créditos.
- `BiosphereWorlds.toml` — **no es un archivo versionado del repositorio**:
  NeoForge lo crea en el directorio de configuración de la instancia (`run/config`
  en desarrollo) a partir de `SphereConfig`.

**No verificado:** el inventario anterior describe los archivos versionados
relevantes y la estructura observada en `src`; no incluye artefactos generados
del directorio `build/` como código fuente mantenible.

## 2. Flujo de generación de un mundo

El preset se selecciona en la pantalla de creación de mundos y se serializa
desde `src/main/resources/data/biosphereworlds/worldgen/world_preset/biospheres.json`.
El Overworld usa `minecraft:noise`, con `biosphereworlds:biospheres` como
`NoiseGeneratorSettings` y un `minecraft:multi_noise` con preset
`minecraft:overworld` como `BiomeSource`. El wrapper
`biosphereworlds:biospheres` sigue registrado solo para cargar mundos antiguos.
Nether y End siguen apuntando a sus settings vanilla.

### Paso 1: posición y celda de la cuadrícula

La geometría toma una posición de bloque `(x, y, z)`. En
`SphereCarver.getSphereGridX(double)` y `getSphereGridZ(double)`, y también en
`MultipleSpheresDistanceFunction.compute(FunctionContext)`, se calcula la celda
más cercana con:

```text
gridX = round(x / spacing)
gridZ = round(z / spacing)
centro = (gridX * spacing, center_y, gridZ * spacing)
```

`spacing` y `center_y` se leen de `SphereConfig`. La feature auxiliar
`SphereUtil.isInSideSphere` repite la misma idea con argumentos explícitos, pero
no es el camino principal del worldgen.

### Paso 2: radio determinista por celda

`SphereCarver.getSphereRadius(int gridX, int gridZ)` usa
`min_radius` y `radius`. Si ambos son iguales, devuelve el máximo; si no,
calcula:

```text
seed = gridX * 31 + gridZ
hash = seed * 1664525 + 1013904223
radio = min_radius + abs(hash) % (max_radius - min_radius + 1)
```

Por tanto, la forma depende de la celda, no de un RNG que avance durante la
generación. `getSphereRadiusAt(x, z)` convierte primero la posición a celda.
La selección de bloque de `getSphereBlockState(gridX, gridZ)` usa el mismo
`gridX * 31 + gridZ` como índice determinista sobre `sphere_block`.

### Paso 3: density function y sólido del terreno

`src/main/resources/data/biosphereworlds/worldgen/noise_settings/biospheres.json`
conecta `final_density` a la función registrada
`biosphereworlds:multiple_spheres`. Su `input` se decodifica mediante el Codec
de `MultipleSpheresDistanceFunction`, que llama a `compute` para obtener
`distancia_al_centro^2 / radio^2`.

El `range_choice` del JSON usa ese resultado para seleccionar la rama de
densidad dentro del rango `[0, 1)` y la rama fuera de él. En consecuencia, la
densidad vanilla forma el terreno dentro de la esfera y la densidad fuera deja
el espacio sin terreno. La función declara `minValue() = 0` y
`maxValue() = 2.0`, aunque el cálculo real puede depender de la combinación de
radio y coordenadas.

### Paso 4: carver del cascarón

Los biome modifiers generados en
`src/generated/resources/data/biosphereworlds/neoforge/biome_modifier/add_carver/`
añaden los configured carvers a la fase `AIR`. En
`SphereBiomeCarverModifier.modify`, se omiten Nether y End.

`MixinNoiseBasedChunkGenerator.applyCarvers` sustituye el flujo vanilla para
un generator cuyo `settings` tiene la clave
`biosphereworlds:biospheres`. Recorre los chunks vecinos usados por el paso de
carving, evita dimensiones distintas del Overworld y ejecuta una vez el
`SphereCarver` y una vez el `SphereBridgeCarver` cuando el configured carver
está listo.

`SphereCarver.carve` recorre las celdas cuyo disco horizontal puede tocar el
chunk. Para cada bloque comprueba:

```text
innerRadius^2 <= (x-cx)^2 + (y-cy)^2 + (z-cz)^2 <= radius^2
```

con un grosor fijo de cascarón de `2.0`, y coloca el estado elegido por
`getSphereBlockState`. `isStartChunk` siempre devuelve `true`; el
`probability` del JSON es parte del `CarverConfiguration`, pero el camino
especial del mixin controla qué carvers se ejecutan.

Si `only_upper_hemisphere` está activo, `SphereCarver.carve` reduce el radio en
uno y restringe el reemplazo: por debajo de Y 55 solo permite agua y exige que
el bloque sea reemplazable según `canReplaceBlock`.

Con `close_open_top` activo, la misma condición del bucle acepta además el disco
completo (`distanceSq <= radius^2`) cuando `y == maxBuildHeight - 1`. En ese Y el
cascarón ya cubre todo el disco si `center_y + radius <= 321`, así que la
cláusula solo añade bloques cuando la esfera está realmente cortada, y usa el
mismo radio efectivo y el mismo bloque que el cascarón. La opción se lee en
`carve` y no forma parte de la caché de `SphereLayout.current()`.

Con `bedrock_mode` el carver también controla el bedrock del suelo. Las reglas
de superficie se aplican en el estado SURFACE
(`ChunkStatusTasks.generateSurface` → `NoiseBasedChunkGenerator.buildSurface` →
`SurfaceSystem.buildSurface`) y los carvers después, en el estado CARVERS
(`ChunkStatusTasks.generateCarvers` → `NoiseBasedChunkGenerator.applyCarvers`),
así que en `carve` la bedrock de la regla `bedrock_floor` ya está colocada y se
puede sustituir sin tocar el JSON:

- `floor` (por defecto): no se activa ninguna rama nueva; el conjunto de
  bloques escritos es idéntico al anterior.
- `none`: `replaceBedrockFloor` barre las primeras capas del chunk
  (`min_y .. min_y + 5`) y cambia por deepslate solo los bloques que son
  bedrock; el aire y el agua no se tocan.
- `shell`: además, la rama `else if` del bucle rellena con bedrock las tres
  capas estrictamente por dentro del cascarón (`radius - 5 <= d < radius - 2`)
  en la mitad inferior (`y <= center_y`), sustituyendo cualquier bloque salvo el
  cascarón. Con `only_upper_hemisphere` el forro no se genera y `shell` se
  comporta como `floor`.

### Paso 5: biome source y biomas

En los mundos nuevos el preset declara directamente:

```json
{
  "type": "minecraft:multi_noise",
  "preset": "minecraft:overworld"
}
```

`MixinNoiseBasedChunkGenerator` marca ese `MultiNoiseBiomeSource` a partir de la
clave de sus `NoiseGeneratorSettings` (`biosphereworlds:biospheres`), en el
constructor y al principio de `createBiomes`. `MixinChunkGeneratorBiomeSource`
repite la marca al devolver `getBiomeSource`. `MixinMultiNoiseBiomeSource` usa
esa marca, con prioridad `500`, para sustituir por `Biomes.THE_VOID` las
consultas de `getNoiseBiome` fuera de las esferas. El predicado es el mismo de
siempre: `SphereLayout.current().isInside(x, y, z, -4)`.

La prioridad `500` es deliberada: TerraBlender inyecta en el mismo
`getNoiseBiome` con prioridad por defecto `1000` y es cancelable. Un valor menor
hace que nuestro handler corra primero; fuera de la esfera cancela antes de que
TerraBlender devuelva su bioma, y dentro no cancela, de modo que TerraBlender
puede aplicar su región. El filtro solo se activa en generadores con settings
`biosphereworlds:biospheres`; un Overworld vanilla o el Nether llevan otra clave
y no se marcan.

Al ser un `MultiNoiseBiomeSource` real, TerraBlender inicializa sus regiones de
Overworld, añade sus biomas a `possibleBiomes()` y aplica sus reglas de
superficie. Eso es lo que permite que los biomas de Biomes O' Plenty aparezcan
dentro de las esferas. `/locate` y las brújulas siguen pasando por
`getNoiseBiome`, así que el filtro de `the_void` también las cubre.

Los mundos antiguos siguen deserializando `BiosphereBiomeSource` con sus campos
`source` y `void_biome`. Ese wrapper conserva sus tres métodos de búsqueda
(`findBiomeHorizontal`, ambas sobrecargas, y `findClosestBiome3d`), que delegan
en el source original y convierten en `null` los resultados fuera de una esfera.
**TerraBlender sigue sin inicializar ese wrapper**, de modo que los mundos ya
creados no generan biomas de TerraBlender/BOP; el arreglo solo se aplica a
mundos nuevos.

### Paso 6: estructuras

`MixinChunkGeneratorStructures.restrictStructureToSphere` intercepta
`ChunkGenerator.tryGenerateStructure` al principio. Solo aplica en el
Overworld, para el generator de biosferas y si
`restrict_structures_to_spheres` está activo. Comprueba el centro del chunk a
`center_y` con `SphereCarver.isInsideAnySphere(..., structure_edge_margin)`.
Si falla, cancela devolviendo `false`.

El margen se resta, junto con los `2.0` bloques del cascarón, al radio efectivo.
La intención explícita del comentario es evitar que las piezas de una
estructura alcancen el vacío; el código restringe el **inicio** de estructura,
no recorta individualmente cada pieza.

### Paso 7: aquifer, heightmap y vacío

`MixinNoiseBasedChunkGenerator.createNoiseChunk` marca el `NoiseChunk` y su
`NoiseBasedAquifer`. `MixinNoiseBasedAquifer.computeSubstance` devuelve aire
cuando la distancia al centro está suficientemente cerca del borde:
`distance - radius^2 >= -radius * 2`. Esto evita que el agua del aquifer
rellene la región exterior del cascarón.

`MixinHeightmap` trata los bloques del cascarón por encima de `center_y` como
aire al actualizar y al inicializar heightmaps. Es una adaptación del cálculo
de superficies a una esfera hueca; no elimina físicamente los bloques.

### Paso 8: puentes

`SphereBridgeCarver.carve` calcula centros vecinos en las direcciones +X y +Z.
`carveBridge` proyecta cada bloque del volumen alrededor del segmento entre
centros y coloca el `bridge_block` cuando la distancia al segmento cae entre
`bridge_radius - 1` y `bridge_radius`. Solo escribe sobre aire y no escribe
dentro de una esfera según `SphereCarver.isInsideAnySphere`.

El toggle `generate_bridge` se lee dentro de `SphereBridgeCarver.carve`, por lo
que el configured carver puede seguir registrado aunque la opción esté
desactivada.

### Paso 9: spawn

`PlayerSpawnHandler.onPlayerLoggedIn` y `onPlayerRespawn` solo actúan en un
`ServerLevel` cuyo generator está marcado por
`BiosphereWorldgen.isBiosphereGenerator`. Lanzan un hilo que llama a
`teleportIntoSphere`.

Si la posición actual es un bloque de cascarón (`SphereUtil.isSphereBlock`),
`findValidPosition` primero busca hacia abajo una superficie sólida con dos
bloques de aire encima. Si no la encuentra, explora un disco de radio
`getSphereRadiusAt(x, z)` y busca allí. Finalmente programa en el hilo del
servidor un `teleportTo` al espacio sobre esa superficie.

El `spawn_target` de `biospheres.json` pertenece a la selección de posiciones
de spawn del `NoiseGeneratorSettings`; el teletransporte de seguridad anterior
es lógica adicional del evento de jugador.

### Diagrama resumido

```text
world_preset/biospheres.json
              |
              v
NoiseBasedChunkGenerator -- settings --> noise_settings/biospheres.json
              |                                  |
              |                                  +--> MultipleSpheresDistanceFunction
              |                                  +--> spawn_target
              v
BiomeSource: MultiNoise (mundos nuevos) / BiosphereBiomeSource (mundos antiguos)
              |
              +--> posición (x,y,z)
              |      -> celda round(x/spacing), round(z/spacing)
              |      -> hash -> radio/bloque
              |      -> dentro: bioma + densidad vanilla
              |      -> fuera: densidad vacía + the_void
              |
              +--> TerraBlender (solo MultiNoise de mundos nuevos): regiones, biomas y surface rules
              +--> applyCarvers
              |      -> SphereCarver: cascarón
              |      -> SphereBridgeCarver: puentes
              |
              +--> estructuras -> solo inicio dentro del radio reducido
              +--> aquifer/heightmap -> no rellenar ni contar el exterior
              +--> login/respawn -> buscar superficie y recolocar jugador
```

## 3. Decisiones de diseño y por qué

### Wrapper del `BiomeSource` conservado para mundos antiguos

Los mundos nuevos usan un `minecraft:multi_noise` normal. Eso permite que
TerraBlender lo reconozca e inicialice sus regiones; el wrapper
`biosphereworlds:biospheres` ya no participa en la generación nueva.

El wrapper se conserva registrado porque un mundo antiguo guarda su biome source
en el `level.dat`. Al cargarlo, el juego sigue decodificando
`BiosphereBiomeSource` con `source` y `void_biome`, y el comportamiento de las
esferas no cambia. La contrapartida conocida es que TerraBlender no inicializa
ese wrapper: los mundos antiguos no reciben biomas de TerraBlender/BOP. Para
tenerlos hay que crear un mundo nuevo.

El filtro de `the_void` en los mundos nuevos vive en `MixinMultiNoiseBiomeSource`
y se apoya en una marca por instancia que sobrevive al clon que TerraBlender
hace por generador. El wrapper antiguo conserva además el filtrado de las APIs
de búsqueda de biomas, que en los mundos nuevos ya no hace falta porque
`findBiomeHorizontal` y `findClosestBiome3d` pasan por `getNoiseBiome`.

### Restricción de estructuras

`MixinChunkGeneratorStructures` se coloca en `tryGenerateStructure`, un punto
común antes de que se generen las piezas. La prueba usa el chunk candidato,
`center_y` y `structure_edge_margin`; así no necesita conocer el tipo de cada
estructura ni duplicar su lógica interna.

El comentario del método sí explica la razón: una estructura cuyo inicio está
cerca del borde puede extender sus piezas al vacío. El código no demuestra que
el margen sea suficiente para todas las estructuras posibles; solo demuestra
que el inicio debe estar en el radio reducido.

### Aislamiento del preset respecto de vanilla

`world_preset/biospheres.json` modifica únicamente la entrada del Overworld
del preset nuevo. El README documenta que los presets vanilla permanecen sin
cambios, y el JSON deja Nether y End con sus generators vanilla. La prueba
explícita de `Level.OVERWORLD` en los carvers y estructuras refuerza ese
aislamiento en runtime.

La razón observable en el código es evitar activar el carving especial en
otras dimensiones y hacer que la adopción sea opt-in. **No verificado:** no
existe una explicación más detallada del autor sobre por qué se eligió un
preset nuevo en vez de reemplazar el preset vanilla.

### Datos declarativos y código Java

Los JSON describen identificadores, settings y composición de worldgen;
`Codec` los convierte a objetos Java. Los carvers hacen el cálculo geométrico
porque necesitan bucles sobre bloques y acceso a chunks, mientras que el
`BiomeModifier` solo compone carvers/features en las fases de generación.
Esto separa "qué se conecta" de "cómo se calcula".

### Marcas en objetos vanilla

Las interfaces `Biosphere*Access` y los mixins añaden flags a
`ChunkAccess`, `NoiseChunk`, `BiomeSource` y `NoiseBasedChunkGenerator`.
`BiosphereWorldgen` centraliza las comprobaciones. En términos de Java
empresarial, es una forma de asociar metadatos de contexto a objetos que el
mod no puede subclasificar porque Minecraft los instancia directamente.

El aquifer se guarda adicionalmente en un `Set` concurrente porque
`NoiseBasedAquifer` no recibe una interfaz del mod. **No verificado:** el
código no muestra una estrategia de limpieza de ese set; no se puede afirmar
desde aquí cómo se comporta su memoria durante muchos mundos o recargas.

## 4. Glosario

- **Mixin** — mecanismo de SpongePowered Mixin para inyectar campos, interfaces
  o código en una clase existente de Minecraft sin cambiar su fuente; se
  parece a programación orientada a aspectos aplicada a clases ya compiladas.
  En este proyecto aparece en
  [`src/main/java/mx/issacinzunza/biosphereworlds/mixin/`](../../src/main/java/mx/issacinzunza/biosphereworlds/mixin/)
  y se activa desde [`src/main/resources/biosphereworlds.mixins.json`](../../src/main/resources/biosphereworlds.mixins.json).
- **Codec** — serializador/deserializador declarativo de Mojang; describe cómo
  convertir JSON/registries a un objeto tipado y viceversa. Ejemplos:
  [`BiosphereBiomeSource.CODEC`](../../src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereBiomeSource.java)
  y `SphereCarverConfig.CODEC` en
  [`SphereCarver.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/carver/SphereCarver.java).
- **BiomeSource** — componente que responde qué bioma corresponde a
  coordenadas de bioma; es análogo a un proveedor de lectura espacial, no a
  una tabla estática de entidades. Los mundos nuevos usan
  `minecraft:multi_noise`; el wrapper histórico está en
  [`BiosphereBiomeSource.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereBiomeSource.java)
  y solo se decodifica en mundos antiguos.
- **DensityFunction** — función numérica que el generador de ruido evalúa por
  posición para decidir densidad de terreno, cuevas y transiciones. La
  implementación del mod es
  [`MultipleSpheresDistanceFunction.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/density/MultipleSpheresDistanceFunction.java).
- **Noise settings** — documento de worldgen que combina altura, ruido,
  router de densidades, fluido por defecto y reglas de spawn. El preset usa
  [`data/biosphereworlds/worldgen/noise_settings/biospheres.json`](../../src/main/resources/data/biosphereworlds/worldgen/noise_settings/biospheres.json).
- **Datapack** — conjunto de recursos que Minecraft carga para datos
  registrables de mundo, como presets, carvers y biome modifiers; se parece a
  configuración versionada por recurso, pero con los codecs y registries del
  juego. Este proyecto genera parte de ellos desde
  [`SphereBiomeBoostrap.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/data/SphereBiomeBoostrap.java)
  y los conserva en `src/generated/resources/`.
- **Registro** — catálogo gestionado por Minecraft/NeoForge donde un id
  (`namespace:path`) se asocia a un tipo, por ejemplo un carver o biome source.
  El mod declara catálogos diferidos en
  [`ModCarvers.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/registry/ModCarvers.java),
  [`ModBiomeSources.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/registry/ModBiomeSources.java)
  y [`ModDensityFunctions.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/registry/ModDensityFunctions.java).
- **Holder** — referencia envuelta a un valor de registry; puede estar ligada
  (`bound`) o resolverse mediante un lookup. Los `Holder<Biome>` son la unidad
  que intercambian `BiomeSource` y los biome modifiers, por ejemplo en
  [`BiosphereBiomeSource.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/worldgen/BiosphereBiomeSource.java).
- **ResourceLocation** — identificador compuesto por namespace y path, como
  `minecraft:the_void` o `biosphereworlds:biospheres`. El helper del mod lo
  crea en [`BiosphereWorlds.prefix`](../../src/main/java/mx/issacinzunza/biosphereworlds/BiosphereWorlds.java)
  y la configuración lo parsea en
  [`SphereConfig.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/config/SphereConfig.java).
- **Biome Modifier** — extensión de NeoForge que modifica las settings de un
  bioma durante fases de worldgen; es comparable a un hook de composición
  gestionado por el loader. Las implementaciones están en
  [`SphereBiomeCarverModifier.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/data/SphereBiomeCarverModifier.java)
  y [`SphereBiomeFeatureModifier.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/data/SphereBiomeFeatureModifier.java).

## 5. Configuración

Todas las opciones se declaran en
[`SphereConfig.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/config/SphereConfig.java)
y se registran como `ModConfig.Type.COMMON` con el nombre
`BiosphereWorlds.toml`. La ruta de desarrollo habitual es
`run/config/BiosphereWorlds.toml`; no es un archivo fuente versionado.

| Opción TOML | Tipo Java | Valor por defecto | Dónde se lee | Efecto |
|---|---|---:|---|---|
| `radius` | `ConfigValue<Number>` | `128` | `SphereCarver.getSphereRadius` | Radio máximo de las esferas y límite usado cuando `min_radius == radius`. |
| `min_radius` | `ConfigValue<Number>` | `128` | `SphereCarver.getSphereRadius` | Radio mínimo; si es menor que `radius`, el hash de la celda elige un radio entre ambos. |
| `spacing` | `ConfigValue<Number>` | `500` | `SphereCarver`, `MultipleSpheresDistanceFunction`, `SphereBridgeCarver`, `MixinNoiseBasedAquifer`, utilidades | Distancia entre centros en X/Z y tamaño de la cuadrícula. |
| `center_y` | `ConfigValue<Number>` | `62` | carvers, density function, estructuras, aquifer, heightmap y spawn handler | Altura Y del centro de cada esfera y referencia vertical de varias comprobaciones. |
| `sphere_block` | `ConfigValue<List<? extends String>>` | `["minecraft:glass"]` | `SphereCarver.getSphereBlockState`, `SphereUtil`, `PlayerSpawnHandler`, `MixinHeightmap` | Lista de ids de bloques posibles para el cascarón; la celda selecciona uno determinísticamente. Se valida contra `BuiltInRegistries.ITEM`, aunque luego se usa como bloque. |
| `only_upper_hemisphere` | `ConfigValue<Boolean>` | `false` | `SphereCarver.carve` | Activa el modo de hemisferio superior y sus reglas especiales de reemplazo bajo Y 55. |
| `close_open_top` | `BooleanValue` | `false` | `SphereCarver.carve` | Cierra con una tapa plana en el Y colocable más alto (319) las esferas cortadas por el límite de construcción, usando el bloque del cascarón. Solo afecta a mundos nuevos. |
| `bedrock_mode` | `EnumValue<BedrockMode>` | `floor` | `SphereCarver.carve` | Bedrock del suelo del mundo. `floor`: se mantiene la regla `bedrock_floor` de la surface_rule en el fondo (Y −64..−60, solo donde hay piedra). `none`: sin bedrock en el fondo; el carver sustituye esa bedrock por deepslate, así que el suelo se puede minar (el pumpjack de Create Diesel Generators necesita bedrock y no funcionará). `shell`: lo de `floor` más un forro de bedrock de 3 bloques por dentro del cascarón de vidrio en la mitad inferior (`y <= center_y`), que sustituye cualquier bloque salvo el cascarón. Con `only_upper_hemisphere` activo, `shell` se comporta exactamente como `floor`. Solo afecta a chunks nuevos. |
| `generate_bridge` | `ConfigValue<Boolean>` | `true` | `SphereBridgeCarver.carve` | Activa o desactiva la colocación de puentes entre centros vecinos. |
| `bridge_block` | `ConfigValue<String>` | `"minecraft:oak_planks"` | `SphereBridgeCarver.carve` | Id del bloque usado por los puentes. |
| `bridge_radius` | `ConfigValue<Number>` | `1` | `SphereBridgeCarver.carveBridge` | Grosor radial del tubo de puente. |
| `restrict_structures_to_spheres` | `ConfigValue<Boolean>` | `true` | `MixinChunkGeneratorStructures.restrictStructureToSphere` | Activa la cancelación de inicios de estructura fuera de las esferas. |
| `structure_edge_margin` | `IntValue` | `16` (rango `0..128`) | `MixinChunkGeneratorStructures.restrictStructureToSphere` | Margen adicional hacia dentro del radio para el punto inicial de una estructura. |
| `debug_biome_lookups` | `BooleanValue` | `false` | `MixinMultiNoiseBiomeSource`, `MixinChunkGeneratorBiomeSource` | Escribe rutas DEBUG para consultas de bioma fuera de las esferas y para marcar el source. |
| `edge_ocean_enabled` | `BooleanValue` | `false` | `SphereEdgeOceanFunction` | Inunda de océano la banda junto al cascarón: el terreno se aplana hasta un fondo plano para que el agua alcance el cristal. Solo afecta a mundos nuevos. |
| `edge_ocean_width` | `IntValue` | `20` (rango `0..100`) | `SphereEdgeOceanFunction` | Ancho de la banda de océano, como porcentaje del radio de la esfera. Solo afecta a mundos nuevos. |
| `edge_ocean_depth` | `IntValue` | `20` (rango `0..100`) | `SphereEdgeOceanFunction` | Profundidad del fondo oceánico por debajo del nivel del mar (63), en bloques. Solo afecta a mundos nuevos. |
| `satellites_enabled` | `BooleanValue` | `true` | `SphereLayout.current`, `SphereCarver.carve`, `MixinHeightmap` | Genera satélites sobre cada esfera principal. Con `false` no se reserva la caché de satélites y toda la ruta nueva se corta antes de calcular. |
| `satellites_max_count` | `IntValue` | `3` (rango `0..64`) | `SatellitePlacement.place` | Número máximo de satélites por esfera; cada celda elige entre 0 y este valor. `0` los desactiva aunque `satellites_enabled` sea `true`. |
| `satellites_min_size_percent` | `IntValue` | `8` (rango `1..100`) | `SatellitePlacement.candidate` | Radio mínimo del satélite como porcentaje del radio principal. |
| `satellites_max_size_percent` | `IntValue` | `12` (rango `1..100`) | `SatellitePlacement.candidate` | Radio máximo del satélite como porcentaje del radio principal. |
| `satellites_min_gap_percent` | `IntValue` | `25` (rango `0..1000`) | `SatellitePlacement.candidate` | Hueco mínimo entre superficies como porcentaje del radio principal. |
| `satellites_max_gap_percent` | `IntValue` | `75` (rango `0..1000`) | `SatellitePlacement.candidate` | Hueco máximo entre superficies como porcentaje del radio principal. |
| `satellites_min_gap_blocks` | `IntValue` | `16` (rango `0..1024`) | `SatellitePlacement.place` | Hueco mínimo absoluto con la principal y margen de seguridad respecto al borde de la celda y a otros satélites. |
| `satellites_min_elevation_degrees` | `IntValue` | `30` (rango `0..90`) | `SatellitePlacement.candidate` | Ángulo de elevación mínimo sobre el centro de la principal. |
| `satellites_max_elevation_degrees` | `IntValue` | `55` (rango `0..90`) | `SatellitePlacement.candidate` | Ángulo de elevación máximo sobre el centro de la principal. |
| `satellites_excluded_ores` | `ConfigValue<List<? extends String>>` | debris, cuarzo y oro del Nether, `#c:ores_in_ground/deepslate` | `SatelliteMineralCatalog.rebuild` | Ids o `#tags` que nunca se eligen para el núcleo. Entradas inválidas se ignoran con aviso. |
| `satellites_included_ores` | `ConfigValue<List<? extends String>>` | vacía | `SatelliteMineralCatalog.rebuild` | Ids o `#tags` añadidos aunque no estén en `#c:ores_in_ground/stone`, para mods sin tags. |
| `satellites_mod_ore_weight` | `IntValue` | `3` (rango `0..1000`) | `SatelliteMineralCatalog.rebuild` | Peso de selección de minerales de mods; los vanilla conservan el suyo. |
| `satellites_mod_ore_density_permille` | `IntValue` | `20` (rango `0..1000`) | `SatelliteMineralCatalog.rebuild` | Bloques de mineral por 1000 del núcleo para minerales de mods. |

La validación de `sphere_block` y `bridge_block` está en
`SphereConfig.validateItemName(Object)`: exige un `String` y que el
`ResourceLocation` exista en el registro de **items**. **No verificado:** el
código no comprueba explícitamente el registro de bloques durante la carga;
un id que sea item pero no un bloque podría producir un estado de bloque no
válido al leerlo desde `BuiltInRegistries.BLOCK`.

Las parejas `min`/`max` de los satélites se validan en el constructor de
`SphereLayout.Params`: cada valor se recorta a su rango y, si el mínimo supera al
máximo, el mínimo toma el valor del máximo. La comparación de `SphereLayout.current()`
usa esos valores ya normalizados, así que una configuración invertida no provoca
recreaciones de caché en cada llamada.

## 6. Cómo compilar y probar

### Compilar

Desde `C:\Personal\NeoBiosphere`, en PowerShell:

```powershell
.\gradlew.bat build
```

`build.gradle` usa Java 21, genera la metadata de NeoForge a partir de
`src/main/templates/META-INF/neoforge.mods.toml` e incorpora
`src/generated/resources`. Un build correcto debe terminar con `BUILD
SUCCESSFUL` y producir el jar bajo `build/libs/`.

Para regenerar los recursos de datapack durante una investigación:

```powershell
.\gradlew.bat runData
```

**No verificado:** el proyecto define la ejecución `data` en
`build.gradle`, pero no se ha inferido un alias adicional más allá del nombre
normal de tarea de Gradle `runData`.

### Cliente de desarrollo

```powershell
.\gradlew.bat runClient
```

Crea un mundo nuevo y selecciona **Biospheres - Issac Edition**. Usa un mundo
nuevo para no confundir chunks ya generados con cambios de worldgen.

### Comprobaciones en F3

Dentro de una esfera, usa F3 para revisar:

- `Biome`: debe mostrar el bioma que proporciona el `minecraft:multi_noise`
  original (por ejemplo, un bioma de Overworld), no `the_void`.
- `XYZ`: comprueba la distancia respecto a los centros de la cuadrícula
  (`spacing` por defecto 500) y `Y` respecto a `center_y` (62 por defecto).
- Fuera de la superficie esférica, en el vacío, `Biome` debe ser
  `minecraft:the_void`; la coordenada debe estar fuera del radio efectivo de
  la celda.
- El cascarón debe estar alrededor del radio, y los puentes, si están
  activados, deben aparecer aproximadamente en `center_y`.

F3 muestra el bioma de la posición del jugador, pero no prueba por sí solo las
consultas de `/locate biome`. Para ello, desde una posición exterior ejecuta,
por ejemplo:

```text
/locate biome minecraft:plains
```

La respuesta debe apuntar a una posición de un bioma dentro de una esfera, no
a una posición exterior con bioma lógico `the_void`. Repite con un bioma que
sepas que el preset puede generar; la disponibilidad exacta depende del
`MultiNoiseBiomeSource`.

### Spawn y estructuras

Comprueba login/respawn apareciendo o forzando una posición sobre el
cascarón: `PlayerSpawnHandler` debería encontrar una superficie sólida con dos
bloques de aire encima. Genera varias estructuras para verificar que los
inicios no quedan cerca del borde; el margen por defecto es 16 bloques.

### Prueba con y sin C2ME

1. Ejecuta `runClient` sin C2ME y crea un mundo nuevo; anota una semilla,
   coordenadas de varios centros, biomas, cascarones, puentes y resultados de
   `/locate biome`.
2. Cierra el cliente, añade una versión de C2ME compatible con Minecraft 1.21.1
   a la instancia de desarrollo y repite con otra copia o mundo nuevo.
3. Compara las mismas coordenadas y semilla, y revisa logs por errores de
   mixins, `Codec`, aquifers o chunks.

**No verificado:** este repositorio no declara C2ME en `build.gradle` ni en
`gradle.properties`, y no contiene una prueba automatizada de compatibilidad.
La comparación manual anterior es una prueba de integración, no una garantía
de que el orden/concurrencia de C2ME sea compatible.

## 7. Recetas para cambios comunes

Estas recetas describen dónde editar y qué superficies revisar; no se aplican
automáticamente al proyecto.

### Cambiar un valor por defecto

Edita la declaración correspondiente en
[`SphereConfig.java`](../../src/main/java/mx/issacinzunza/biosphereworlds/config/SphereConfig.java).
Por ejemplo, para cambiar el centro vertical:

```java
CENTER_Y = BUILDER.comment("Center Y level for sphere generation")
        .define("center_y", 80);
```

Después elimina o edita la clave existente en el
`run/config/BiosphereWorlds.toml` de la instancia de prueba, porque un valor
guardado allí prevalece sobre el default del código. Comprueba también los
JSON de carver: `configured_carver/sphere.json` y
`sphere_bridge.json` contienen valores de configuración serializada que todavía
dicen `center_y: 62`, aunque el código activo consulta
`SphereConfig.CENTER_Y`.

### Añadir una opción de configuración nueva

1. Declara el `ConfigValue` en `SphereConfig`, dentro de
   `BUILDER.push("Settings")`, con tipo y default explícitos:

   ```java
   public static final ModConfigSpec.ConfigValue<Boolean> MI_AJUSTE;

   MI_AJUSTE = BUILDER.comment("Descripción observable")
           .define("mi_ajuste", false);
   ```

2. Lee `SphereConfig.MI_AJUSTE.get()` en el punto que decide el
   comportamiento, por ejemplo en `SphereCarver.carve` o
   `SphereBridgeCarver.carve`. No basta con añadir la clave si ningún
   consumidor la lee.
3. Actualiza la tabla y el ejemplo TOML de `README.md` si el cambio se va a
   documentar para usuarios. Para esta guía, el inventario de la sección 5
   también tendría que reflejarla.
4. Arranca el cliente y revisa el archivo generado en
   `run/config/BiosphereWorlds.toml`; NeoForge registra la spec en
   `BiosphereWorlds` durante la construcción del mod.

Si la opción afecta datos serializados y también configuración runtime,
mantén claras ambas fuentes: los campos `spacing`, `radius`, `center_y` y
`block_state` presentes en `SphereCarverConfig` se decodifican desde JSON,
pero `SphereCarver` usa los valores de `SphereConfig` para el cálculo real.

### Cambiar el bloque del cascarón

Para un cambio de usuario, edita:

```toml
[Settings]
sphere_block = ["minecraft:glass", "minecraft:obsidian"]
```

La selección ocurre en
`SphereCarver.getSphereBlockState(int gridX, int gridZ)`:

```java
int blockIndex = Math.abs(gridX * 31 + gridZ) % sphereBlocks.size();
return BuiltInRegistries.BLOCK.get(
        ResourceLocation.parse(sphereBlocks.get(blockIndex))).defaultBlockState();
```

Para que las funciones que reconocen el cascarón sigan coincidiendo,
`SphereUtil.isSphereBlock` usa la misma lista. Esto afecta al spawn y a
`MixinHeightmap`. `bridge_block` es independiente: lo consume
`SphereBridgeCarver`, no `getSphereBlockState`.

**No verificado:** aunque `SphereConfig.validateItemName` comprueba el
registro de items, el consumo necesita un bloque registrado; usa ids que sean
válidos tanto como bloque como item.

### Depurar si los biomas fuera de las esferas no son `the_void`

Sigue la ruta en este orden:

1. Confirma que el mundo es **nuevo**. Un mundo antiguo guarda el wrapper
   `biosphereworlds:biospheres` en el `level.dat` y no recibe el arreglo de
   TerraBlender; en ese caso el source es `BiosphereBiomeSource`, no
   `MultiNoiseBiomeSource`.
2. Verifica que el generator tiene settings
   `biosphereworlds:biospheres`; `MixinNoiseBasedChunkGenerator` identifica el
   generator comparando esa `ResourceKey`.
3. Activa temporalmente:

   ```toml
   debug_biome_lookups = true
   ```

   y busca en el log `biome-route=ChunkGenerator#getBiomeSource` y
   `biome-route=MultiNoiseBiomeSource#getNoiseBiome`.
4. Revisa que `MixinMultiNoiseBiomeSourceParameterList` haya capturado un
   `HolderGetter<Biome>` en `ModBiomeAccess.LOOKUP` (volatile). Si el holder de
   `Biomes.THE_VOID` no está bound, el mixin no fuerza el resultado.
5. Comprueba las coordenadas de depuración: Minecraft entrega coordenadas de
   cuarto de bloque al `BiomeSource`; el mixin debe convertirlas con
   `QuartPos.toBlock`. En mundos nuevos el source es `MultiNoiseBiomeSource`;
   el wrapper solo aparece en mundos antiguos.
6. Compara la geometría con `SphereLayout.isInside(..., -4)`, incluyendo
   `spacing`, `center_y` y radios variables.
7. Si el bioma es correcto pero el terreno no es vacío, inspecciona
   `noise_settings/biospheres.json`, en particular `final_density` y
   `multiple_spheres`, y después `MixinNoiseBasedAquifer`/`MixinHeightmap`.

Para una traza adicional, deja `debug_biome_lookups` activo solo durante la
reproducción: el log puede crecer mucho porque las consultas de biomas ocurren
durante worldgen.

## 8. Puntos frágiles al cambiar de versión

- **Firmas de mixins.** Los injections apuntan a métodos concretos como
  `ChunkGenerator.tryGenerateStructure`, `NoiseBasedChunkGenerator.applyCarvers`,
  `createNoiseChunk`, `createBiomes`, `Heightmap.update`,
  `Heightmap.primeHeightmaps` y `MultiNoiseBiomeSource.getNoiseBiome`.
  Un cambio de nombre, descriptor, overload o momento de ejecución puede hacer
  que el mixin no aplique o cambie el comportamiento.
- **Métodos privados y campos shadow/accessor.**
  `MixinNoiseBasedChunkGenerator` hace `@Shadow` de `settings` y
  `createNoiseChunk`; `StructureManagerAccessor` expone el campo privado
  `StructureManager.level`. Son internals sin contrato estable.
- **Tipos y rutas de worldgen.** `NoiseChunk`, `Aquifer.NoiseBasedAquifer`,
  `CarvingContext`, `CarvingMask`, `ConfiguredWorldCarver` y las fases de
  generación pueden cambiar de paquete, firma o ciclo de vida. El código
  depende de que `NoiseBasedChunkGenerator` cree esos objetos en el orden
  esperado.
- **Conversión de coordenadas.** `BiomeSource.getNoiseBiome` trabaja con
  coordenadas de cuarto de bloque y el mod usa `QuartPos.toBlock`. Un cambio
  en la escala o en la API de climate sampling produciría decisiones
  incorrectas en el borde de las esferas.
- **Codecs y registries.** Los `MapCodec`, `RecordCodecBuilder`,
  `KeyDispatchDataCodec`, `Registries.BIOME_SOURCE`,
  `Registries.DENSITY_FUNCTION_TYPE` y serializers de biome modifiers deben
  seguir teniendo las mismas formas. Un campo JSON renombrado o un registry
  movido rompe la decodificación del preset o de los carvers.
- **Formato de recursos.** `pack_format` 27, la estructura de
  `worldgen/noise_settings`, `world_preset`, configured carvers y biome
  modifiers son contratos de la versión de Minecraft/NeoForge. Hay que
  comparar los JSON con los recursos equivalentes de la nueva versión, no
  asumir que los nombres se conservan.
- **Biome Modifier de NeoForge.** `SphereBiomeCarverModifier` depende de
  `BiomeModifier.Phase.ADD`, `ModifiableBiomeInfo.BiomeInfo.Builder` y
  `BiomeGenerationSettingsBuilder`. Cualquiera de esos hooks puede cambiar
  con NeoForge.
- **Registración diferida.** `DeferredRegister`, `DeferredHolder` y el
  momento en que se conectan al `IEventBus` en
  `BiosphereWorlds` son parte del ciclo de carga de NeoForge. Cambios en el
  evento o en los tipos genéricos pueden impedir que los codecs se resuelvan.
- **Holder y resolución de biomas.** `ModBiomeAccess.LOOKUP` es `volatile` y se
  rellena desde el constructor de `MultiNoiseBiomeSourceParameterList`. Si ese
  constructor deja de recibir `HolderGetter<Biome>`, la resolución de
  `Biomes.THE_VOID` y la defensa del mixin dejan de funcionar.
- **Semántica de carvers.** `SphereCarver` depende de que `WorldCarver` siga
  permitiendo `isStartChunk`, `canReplaceBlock`, escritura en `ChunkAccess` y
  la combinación con `CarvingMask`. Cambiar la semántica de carving puede
  duplicar, omitir o sobrescribir cascarones.
- **Aquifers y heightmaps.** Los redirects de `MixinHeightmap` y el retorno
  temprano de `MixinNoiseBasedAquifer` dependen de llamadas internas exactas.
  Aunque compilen tras una actualización, deben comprobarse visualmente en el
  borde: agua no deseada, alturas erróneas o cascarones contados como suelo
  indican una incompatibilidad.
- **Concurrencia y memoria.** `BiosphereWorldgen.BIOSPHERE_AQUIFERS` es un set
  concurrente global y el spawn crea un `Thread` nuevo por evento. La
  interacción con cambios de scheduler, generación paralela o mods como C2ME
  necesita una prueba específica; no se debe asumir seguridad solo porque el
  código compile.
- **Mappings y Java.** Las firmas visibles dependen de NeoForge/Parchment
  (`gradle.properties` fija `2024.11.13`) y de Java 21. Al actualizar
  Minecraft, NeoForge o mappings, recompilar no basta: hay que revisar los
  targets de mixin y ejecutar la prueba de mundo descrita en la sección 6.

La regla práctica es actualizar primero los recursos y mappings de la nuevaS
versión, después revisar cada target de
[`biosphereworlds.mixins.json`](../../src/main/resources/biosphereworlds.mixins.json),
y finalmente comprobar un mundo nuevo dentro y fuera de una esfera. No se
debe inferir compatibilidad de una compilación exitosa: los problemas de
worldgen pueden aparecer solo al generar un chunk, al consultar biomas o al
crear una estructura.

### Fuentes de Minecraft consultadas para las jaulas

Caché NeoForm `sourcesAndCompiledWithNeoForge_d1701b2d84878ca1ab690eb7c5e8e0275ec75727_output.jar`:
BaseSpawner.java:57–60 (activación), 112–164 (reglas/colisiones), 203–260 (NBT);
SpawnerBlockEntity.java:40–61 y 91–92; SpawnData.java:19–25 y 60–82.
EventHooks.java:258–263 confirma el tratamiento de reglas personalizadas;
ChunkAccess.java:129 permite publicar el block entity durante worldgen.

### Fuentes para la reutilización del terreno Solar v5

En la misma caché NeoForm citada arriba: RandomState.java:18, 31–42 y 101–125;
NoiseGeneratorSettings.java:19–30; NoiseChunk.java:138–159; NoiseBasedChunkGenerator.java:84–98.
SolarRandomStateAccess expone únicamente el catálogo de parámetros para construir
un estado nuevo; no cambia ningún campo del estado original.
