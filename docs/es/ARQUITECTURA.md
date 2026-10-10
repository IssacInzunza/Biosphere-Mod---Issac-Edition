# Arquitectura de Biosphere Worlds

Guía de lectura para **Biosphere Worlds - Issac Edition**, fork de
NeoBiosphere para Minecraft 1.21.1 y NeoForge 21.1.216. Las rutas de este
documento son relativas a la raíz del repositorio. Cuando una afirmación no se
puede demostrar leyendo el código, se marca como **No verificado:**.

## 1. Mapa del proyecto

### 1.1 Punto de entrada, configuración y utilidades

- `src/main/java/cn/mlus/biosphereworlds/BiosphereWorlds.java` —
  clase anotada con `@Mod("biosphereworlds")`. En el constructor registra la
  configuración común y los registros diferidos de funciones de densidad,
  carvers, features, biome sources y serializers de biome modifiers.
- `src/main/java/cn/mlus/biosphereworlds/config/SphereConfig.java` —
  declara y construye la `ModConfigSpec` que NeoForge carga como
  `BiosphereWorlds.toml`. También valida los identificadores de bloques
  configurados.
- `src/main/java/cn/mlus/biosphereworlds/util/SphereUtil.java` —
  contiene cálculos auxiliares de pertenencia a una esfera y comprueba si un
  `BlockState` pertenece a la lista configurada de bloques del cascarón.

### 1.2 Worldgen y geometría

- `src/main/java/cn/mlus/biosphereworlds/carver/SphereCarver.java` —
  `WorldCarver` principal. Recorre las celdas de la cuadrícula que pueden tocar
  un chunk y coloca el cascarón esférico; también calcula centros, radios,
  selección determinista del bloque y pruebas de interior.
- `src/main/java/cn/mlus/biosphereworlds/carver/SphereBridgeCarver.java` —
  subclase del carver que une centros vecinos con un tubo hueco de bloques,
  respetando `generate_bridge`, `bridge_block` y `bridge_radius`.
- `src/main/java/cn/mlus/biosphereworlds/density/MultipleSpheresDistanceFunction.java` —
  `DensityFunction.SimpleFunction` registrada como
  `biosphereworlds:multiple_spheres`; devuelve la distancia cuadrática
  normalizada al centro de la esfera más cercana.
- `src/main/java/cn/mlus/biosphereworlds/feature/SphereFeature.java` —
  `Feature` que podría colocar una esfera completa a partir de un
  `FeatureConfiguration`. El propio archivo la marca como `Not in use`; el
  worldgen activo usa `SphereCarver`.
- `src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereBiomeSource.java` —
  wrapper de `BiomeSource` con Codec propio. Delega el bioma original dentro
  de una esfera y devuelve `minecraft:the_void` fuera; también filtra las
  consultas de búsqueda de biomas.
- `src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereWorldgen.java` —
  almacén de marcas para identificar biome sources, generators, noise chunks,
  aquifers y chunks asociados al preset de biosferas.
- `src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereBiomeSourceAccess.java` —
  interfaz que expone la marca booleana inyectada en las implementaciones de
  `BiomeSource`.
- `src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereGeneratorAccess.java` —
  interfaz usada por el mixin del `NoiseBasedChunkGenerator` para identificar
  el generator del preset.
- `src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereNoiseChunkAccess.java` —
  interfaz para marcar un `NoiseChunk` como perteneciente al worldgen de
  biosferas.
- `src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereChunkAccess.java` —
  interfaz para marcar un `ChunkAccess` como chunk de biosferas.

### 1.3 Eventos

- `src/main/java/cn/mlus/biosphereworlds/event/PlayerSpawnHandler.java` —
  escucha login y respawn. En un `ServerLevel` cuyo generator es el de
  biosferas, busca una superficie sólida dentro de la esfera y teletransporta
  al jugador si apareció sobre el cascarón.

### 1.4 Registros

- `src/main/java/cn/mlus/biosphereworlds/registry/ModBiomeAccess.java` —
  guarda el `HolderGetter<Biome>` capturado desde el preset de multi-noise,
  para resolver posteriormente `Biomes.THE_VOID`.
- `src/main/java/cn/mlus/biosphereworlds/registry/ModBiomeSources.java` —
  registra el `MapCodec` de `BiosphereBiomeSource` con el id
  `biosphereworlds:biospheres`.
- `src/main/java/cn/mlus/biosphereworlds/registry/ModCarvers.java` —
  registra `sphere_carver` y `sphere_bridge_carver`, además de sus claves de
  configured carver.
- `src/main/java/cn/mlus/biosphereworlds/registry/ModDensityFunctions.java` —
  registra el tipo de density function `multiple_spheres` y su clave de
  función configurada.
- `src/main/java/cn/mlus/biosphereworlds/registry/ModFeature.java` —
  registra `SphereFeature` y conserva la clave de su `PlacedFeature`.
- `src/main/java/cn/mlus/biosphereworlds/registry/SphereBiomeModifierSerializers.java` —
  registra los codecs de los biome modifiers personalizados para añadir
  carvers y features.

### 1.5 Datos y biome modifiers

- `src/main/java/cn/mlus/biosphereworlds/data/DataGenerators.java` —
  atiende `GatherDataEvent` y añade el proveedor de entradas de datapack.
- `src/main/java/cn/mlus/biosphereworlds/data/ModDatapackEntries.java` —
  configura un `RegistrySetBuilder` para generar entradas de
  `BIOME_MODIFIERS`.
- `src/main/java/cn/mlus/biosphereworlds/data/SphereBiomeBoostrap.java` —
  registra los biome modifiers que añaden el carver de esfera, el de puentes y
  `minecraft:freeze_top_layer` al bioma `the_void`.
- `src/main/java/cn/mlus/biosphereworlds/data/SphereBiomeCarverModifier.java` —
  añade configured carvers en la fase `GenerationStep.Carving.AIR`, excepto en
  biomas etiquetados como Nether o End.
- `src/main/java/cn/mlus/biosphereworlds/data/SphereBiomeFeatureModifier.java` —
  añade placed features en la fase `TOP_LAYER_MODIFICATION`. En el estado
  actual existe el serializer, pero la llamada que lo usaría en
  `SphereBiomeBoostrap` está comentada.

### 1.6 Mixins y accessor

- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinChunkAccess.java` —
  añade a `ChunkAccess` una marca booleana de chunk de biosferas.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinChunkGeneratorBiomeSource.java` —
  marca el `BiomeSource` devuelto por un generator de biosferas y, si está
  activado, registra una ruta de depuración.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinChunkGeneratorStructures.java` —
  intercepta `ChunkGenerator.tryGenerateStructure` y rechaza inicios fuera del
  interior reducido de una esfera.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinHeightmap.java` —
  hace que el cascarón por encima de `center_y` se comporte como aire al
  actualizar o inicializar heightmaps.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinMultiNoiseBiomeSource.java` —
  marca un `MultiNoiseBiomeSource` y sustituye por `the_void` las consultas
  fuera de las esferas.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinMultiNoiseBiomeSourceParameterList.java` —
  captura el `HolderGetter<Biome>` durante la construcción de la lista preset
  de multi-noise y lo entrega a `ModBiomeAccess`.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinNoiseBasedAquifer.java` —
  identifica aquifers del worldgen de biosferas y fuerza aire cerca del borde
  interior para evitar que el aquifer rellene el vacío.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinNoiseBasedChunkGenerator.java` —
  identifica el generator por su `NoiseGeneratorSettings`, marca chunks,
  noise chunks y aquifers, y reemplaza `applyCarvers` para ejecutar los dos
  carvers de esfera de forma controlada.
- `src/main/java/cn/mlus/biosphereworlds/mixin/MixinNoiseChunk.java` —
  añade a `NoiseChunk` la marca usada por el mixin del aquifer.
- `src/main/java/cn/mlus/biosphereworlds/mixin/accessor/StructureManagerAccessor.java` —
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
  preset opt-in: solo cambia el Overworld a generator noise con el biome source
  wrapper y settings propios; Nether y End conservan generators vanilla.
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
`NoiseGeneratorSettings` y `biosphereworlds:biospheres` como `BiomeSource`
wrapper. Nether y End siguen apuntando a sus settings vanilla.

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

### Paso 5: biome source wrapper y biomas

El JSON del preset decodifica `BiosphereBiomeSource` con dos campos:
`source`, el `minecraft:multi_noise` de Overworld, y `void_biome`, que es
`minecraft:the_void`. En `getNoiseBiome`, el wrapper convierte las coordenadas
de cuarto de bloque recibidas por Minecraft con `QuartPos.toBlock`, llama al
source original y devuelve el resultado solo si
`SphereCarver.isInsideAnySphere(blockX, blockY, blockZ)`. Fuera devuelve
`voidBiome`.

Sus tres métodos de búsqueda (`findBiomeHorizontal`, ambas sobrecargas, y
`findClosestBiome3d`) delegan primero en el source original y convierten en
`null` los resultados cuya posición no esté dentro de una esfera. Así se evita
que consultas como `/locate biome` acepten un resultado fuera del espacio
jugable.

Además, `MixinMultiNoiseBiomeSource` cubre el caso en que Minecraft llega
directamente al `MultiNoiseBiomeSource`: cuando está marcado por
`BiosphereWorldgen.markBiomeSource`, intercepta `getNoiseBiome` y devuelve el
holder de `Biomes.THE_VOID` fuera. `MixinChunkGeneratorBiomeSource` y
`MixinNoiseBasedChunkGenerator` son los que propagan esa marca.

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
BiomeSource: BiosphereBiomeSource(source=MultiNoise, void=the_void)
              |
              +--> posición (x,y,z)
              |      -> celda round(x/spacing), round(z/spacing)
              |      -> hash -> radio/bloque
              |      -> dentro: bioma + densidad vanilla
              |      -> fuera: densidad vacía + the_void
              |
              +--> applyCarvers
              |      -> SphereCarver: cascarón
              |      -> SphereBridgeCarver: puentes
              |
              +--> estructuras -> solo inicio dentro del radio reducido
              +--> aquifer/heightmap -> no rellenar ni contar el exterior
              +--> login/respawn -> buscar superficie y recolocar jugador
```

## 3. Decisiones de diseño y por qué

### Wrapper del `BiomeSource` en lugar de un mixin como mecanismo principal

El preset declara explícitamente `biosphereworlds:biospheres` y conserva el
`minecraft:multi_noise` original dentro del campo `source`. Esto permite que
la decisión "dentro de esfera usa los biomas normales; fuera usa
`the_void`" sea un objeto serializable del worldgen, igual que una estrategia
de selección declarativa.

El wrapper también puede filtrar las APIs de búsqueda de biomas, no solo el
lookup puntual de `getNoiseBiome`. Esto es importante para que una consulta
espacial no encuentre un bioma en una posición exterior.

Los mixins de `MixinMultiNoiseBiomeSource` y
`MixinChunkGeneratorBiomeSource` siguen existiendo como integración defensiva:
marcan el source y cubren la ruta directa del `MultiNoiseBiomeSource`. Por el
código se puede afirmar que el wrapper es la configuración principal y los
mixins son una segunda capa de enrutamiento/identificación. **No verificado:**
no hay un comentario de diseño que explique si esa segunda capa se añadió por
un bug histórico concreto o por compatibilidad futura.

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
  [`src/main/java/cn/mlus/biosphereworlds/mixin/`](../../src/main/java/cn/mlus/biosphereworlds/mixin/)
  y se activa desde [`src/main/resources/biosphereworlds.mixins.json`](../../src/main/resources/biosphereworlds.mixins.json).
- **Codec** — serializador/deserializador declarativo de Mojang; describe cómo
  convertir JSON/registries a un objeto tipado y viceversa. Ejemplos:
  [`BiosphereBiomeSource.CODEC`](../../src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereBiomeSource.java)
  y `SphereCarverConfig.CODEC` en
  [`SphereCarver.java`](../../src/main/java/cn/mlus/biosphereworlds/carver/SphereCarver.java).
- **BiomeSource** — componente que responde qué bioma corresponde a
  coordenadas de bioma; es análogo a un proveedor de lectura espacial, no a
  una tabla estática de entidades. El wrapper está en
  [`BiosphereBiomeSource.java`](../../src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereBiomeSource.java).
- **DensityFunction** — función numérica que el generador de ruido evalúa por
  posición para decidir densidad de terreno, cuevas y transiciones. La
  implementación del mod es
  [`MultipleSpheresDistanceFunction.java`](../../src/main/java/cn/mlus/biosphereworlds/density/MultipleSpheresDistanceFunction.java).
- **Noise settings** — documento de worldgen que combina altura, ruido,
  router de densidades, fluido por defecto y reglas de spawn. El preset usa
  [`data/biosphereworlds/worldgen/noise_settings/biospheres.json`](../../src/main/resources/data/biosphereworlds/worldgen/noise_settings/biospheres.json).
- **Datapack** — conjunto de recursos que Minecraft carga para datos
  registrables de mundo, como presets, carvers y biome modifiers; se parece a
  configuración versionada por recurso, pero con los codecs y registries del
  juego. Este proyecto genera parte de ellos desde
  [`SphereBiomeBoostrap.java`](../../src/main/java/cn/mlus/biosphereworlds/data/SphereBiomeBoostrap.java)
  y los conserva en `src/generated/resources/`.
- **Registro** — catálogo gestionado por Minecraft/NeoForge donde un id
  (`namespace:path`) se asocia a un tipo, por ejemplo un carver o biome source.
  El mod declara catálogos diferidos en
  [`ModCarvers.java`](../../src/main/java/cn/mlus/biosphereworlds/registry/ModCarvers.java),
  [`ModBiomeSources.java`](../../src/main/java/cn/mlus/biosphereworlds/registry/ModBiomeSources.java)
  y [`ModDensityFunctions.java`](../../src/main/java/cn/mlus/biosphereworlds/registry/ModDensityFunctions.java).
- **Holder** — referencia envuelta a un valor de registry; puede estar ligada
  (`bound`) o resolverse mediante un lookup. Los `Holder<Biome>` son la unidad
  que intercambian `BiomeSource` y los biome modifiers, por ejemplo en
  [`BiosphereBiomeSource.java`](../../src/main/java/cn/mlus/biosphereworlds/worldgen/BiosphereBiomeSource.java).
- **ResourceLocation** — identificador compuesto por namespace y path, como
  `minecraft:the_void` o `biosphereworlds:biospheres`. El helper del mod lo
  crea en [`BiosphereWorlds.prefix`](../../src/main/java/cn/mlus/biosphereworlds/BiosphereWorlds.java)
  y la configuración lo parsea en
  [`SphereConfig.java`](../../src/main/java/cn/mlus/biosphereworlds/config/SphereConfig.java).
- **Biome Modifier** — extensión de NeoForge que modifica las settings de un
  bioma durante fases de worldgen; es comparable a un hook de composición
  gestionado por el loader. Las implementaciones están en
  [`SphereBiomeCarverModifier.java`](../../src/main/java/cn/mlus/biosphereworlds/data/SphereBiomeCarverModifier.java)
  y [`SphereBiomeFeatureModifier.java`](../../src/main/java/cn/mlus/biosphereworlds/data/SphereBiomeFeatureModifier.java).

## 5. Configuración

Todas las opciones se declaran en
[`SphereConfig.java`](../../src/main/java/cn/mlus/biosphereworlds/config/SphereConfig.java)
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
| `generate_bridge` | `ConfigValue<Boolean>` | `true` | `SphereBridgeCarver.carve` | Activa o desactiva la colocación de puentes entre centros vecinos. |
| `bridge_block` | `ConfigValue<String>` | `"minecraft:oak_planks"` | `SphereBridgeCarver.carve` | Id del bloque usado por los puentes. |
| `bridge_radius` | `ConfigValue<Number>` | `1` | `SphereBridgeCarver.carveBridge` | Grosor radial del tubo de puente. |
| `restrict_structures_to_spheres` | `ConfigValue<Boolean>` | `true` | `MixinChunkGeneratorStructures.restrictStructureToSphere` | Activa la cancelación de inicios de estructura fuera de las esferas. |
| `structure_edge_margin` | `IntValue` | `16` (rango `0..128`) | `MixinChunkGeneratorStructures.restrictStructureToSphere` | Margen adicional hacia dentro del radio para el punto inicial de una estructura. |
| `debug_biome_lookups` | `BooleanValue` | `false` | `MixinMultiNoiseBiomeSource`, `MixinChunkGeneratorBiomeSource` | Escribe rutas DEBUG para consultas de bioma fuera de las esferas y para marcar el source. |

La validación de `sphere_block` y `bridge_block` está en
`SphereConfig.validateItemName(Object)`: exige un `String` y que el
`ResourceLocation` exista en el registro de **items**. **No verificado:** el
código no comprueba explícitamente el registro de bloques durante la carga;
un id que sea item pero no un bloque podría producir un estado de bloque no
válido al leerlo desde `BuiltInRegistries.BLOCK`.

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
[`SphereConfig.java`](../../src/main/java/cn/mlus/biosphereworlds/config/SphereConfig.java).
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

1. Confirma que el mundo usa el preset nuevo y no un preset vanilla:
   `world_preset/biospheres.json`.
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
   `HolderGetter<Biome>` en `ModBiomeAccess.LOOKUP`. Si el holder de
   `Biomes.THE_VOID` no está bound, el mixin no fuerza el resultado.
5. Comprueba las coordenadas de depuración: Minecraft entrega coordenadas de
   cuarto de bloque al `BiomeSource`; `BiosphereBiomeSource` y el mixin deben
   convertirlas con `QuartPos.toBlock`.
6. Compara la geometría con `SphereCarver.isInsideAnySphere`, incluyendo
   `spacing`, `center_y`, radios variables y el margen `-4` que usa el
   `BiomeSource`.
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
- **Holder y resolución de biomas.** `ModBiomeAccess.LOOKUP` se rellena desde
  el constructor de `MultiNoiseBiomeSourceParameterList`. Si ese constructor
  deja de recibir `HolderGetter<Biome>`, la resolución de `Biomes.THE_VOID` y
  la defensa del mixin dejan de funcionar.
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
