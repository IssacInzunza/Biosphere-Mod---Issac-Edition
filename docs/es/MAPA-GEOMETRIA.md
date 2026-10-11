# Mapa de la geometría de esferas

Documento de la **FASE 1** del refactor a `SphereLayout`. Es solo un inventario:
no describe código ya escrito, describe **qué hay hoy**. Todas las rutas son
relativas a la raíz del repositorio. Los números de línea corresponden al estado
actual de la rama `SphereLayout-(refactor)`.

Objetivo: que toda la geometría (celda, hash, centro, radio, «¿está dentro?»,
distancia al borde) viva en una sola clase `SphereLayout`, **sin cambiar el
resultado para ninguna coordenada**.

---

## 1. Inventario de sitios donde se calcula geometría

### 1.1 Celda de la cuadrícula (índice de celda)

| # | Archivo | Clase | Método | Fórmula | Notas |
|---|---------|-------|--------|---------|-------|
| C1 | `src/main/java/cn/mlus/biosphereworlds/carver/SphereCarver.java:125` | `SphereCarver` | `getSphereGridX(double)` | `(int) Math.round(x / SPACING)` | Canónica, `double` |
| C2 | `src/main/java/cn/mlus/biosphereworlds/carver/SphereCarver.java:129` | `SphereCarver` | `getSphereGridZ(double)` | `(int) Math.round(z / SPACING)` | Canónica, `double` |
| C3 | `src/main/java/cn/mlus/biosphereworlds/carver/SphereCarver.java:52-55` | `SphereCarver` | `carve(...)` | `floor(minX/spacing)` … `ceil(maxX/spacing)` | Enumeración de celdas que tocan el chunk (no es «la celda de un punto») |
| C4 | `src/main/java/cn/mlus/biosphereworlds/carver/SphereBridgeCarver.java:48-51` | `SphereBridgeCarver` | `carve(...)` | idéntico a C3 | **Duplicado literal de C3** |
| C5 | `src/main/java/cn/mlus/biosphereworlds/util/SphereUtil.java:11-12` | `SphereUtil` | `isInSideSphere(...)` | `Math.round((float) x / spacing) * spacing` | **`float`**, devuelve el **centro**, no el índice. Código muerto (ver §1.6) |
| C6 | `src/main/java/cn/mlus/biosphereworlds/density/MultipleSpheresDistanceFunction.java:32-33` | `MultipleSpheresDistanceFunction` | `compute(...)` | `Math.round((double) bx / spacing) * spacing` | Devuelve el **centro**; usa `SPACING` del config, ignora su propio campo `spacing` |
| C7 | `src/main/java/cn/mlus/biosphereworlds/mixin/MixinNoiseBasedAquifer.java:44-45` | `MixinNoiseBasedAquifer` | `onComputeSubstance(...)` | `Math.round((float) x / sphereSpacing) * sphereSpacing` | **`float`**, devuelve el **centro** |
| C8 | `src/main/java/cn/mlus/biosphereworlds/feature/SphereFeature.java:34-35` | `SphereFeature` | `place(...)` | `Math.round((double) ox / spacing) * spacing` | `spacing` es `double` del config de la feature. **Clase marcada «Not in use»** |

**Riesgo de equivalencia:** C5 y C7 usan división en `float`; C1/C2/C6/C8 en
`double`. Coinciden para las coordenadas normales de un mundo, pero difieren en
coordenadas muy grandes (a partir de ~2^24 el `float` pierde precisión). Al
unificar hay que decidir cuál es la canónica; **mi recomendación es conservar
`double`** (C1/C2) y aceptar que C5/C7 cambien en ese rango extremo, o replicar
el `float` exactamente. Lo marco como decisión pendiente (§5).

### 1.2 Hash y radio por celda

| # | Archivo | Clase | Método | Detalle |
|---|---------|-------|--------|---------|
| H1 | `.../carver/SphereCarver.java:141-148` | `SphereCarver` | `getSphereRadius(int, int)` | `min >= max → max`; si no `seed = gx*31 + gz`; `hash = seed*1664525 + 1013904223`; `min + Math.abs(hash) % (max - min + 1)`. **Aritmética `int` con desbordamiento** |
| H2 | `.../carver/SphereCarver.java:150-152` | `SphereCarver` | `getSphereRadiusAt(double, double)` | `getSphereRadius(getSphereGridX(x), getSphereGridZ(z))` |

Particularidades que **hay que conservar bit a bit**:

- El hash **no depende de la semilla del mundo**; solo de `gridX`, `gridZ` y de
  `MIN_RADIUS`/`RADIUS`.
- `Math.abs(Integer.MIN_VALUE)` sigue siendo negativo. Si `hash == Integer.MIN_VALUE`,
  el módulo sale negativo y el radio queda **por debajo de `minRadius`**. Es un
  caso raro pero real; no hay que «arreglarlo».
- Si `minRadius == maxRadius` (config por defecto: 128 y 128) el hash **no se
  evalúa** y el radio es constante.

### 1.3 Centro

| # | Archivo | Clase | Método | Detalle |
|---|---------|-------|--------|---------|
| S1 | `.../carver/SphereCarver.java:133-139` | `SphereCarver` | `getSphereCenterX/Z(int)` | `gridIndex * SPACING` — **multiplicación `int` × `int`**, luego ensanchada a `double` |
| S2 | `.../carver/SphereCarver.java:60-61` | `SphereCarver` | `carve(...)` | `double centerX = gridX * spacing;` — mismo producto `int`×`int` |
| S3 | `.../carver/SphereBridgeCarver.java:55-56` | `SphereBridgeCarver` | `carve(...)` | idéntico a S2 |
| — | (todos) | — | — | `centerY = CENTER_Y` es **global**, no depende de la celda |

### 1.4 Bloques del cascarón

| # | Archivo | Clase | Método | Detalle |
|---|---------|-------|--------|---------|
| B1 | `.../carver/SphereCarver.java:154-160` | `SphereCarver` | `getSphereBlockState(int, int)` | `Math.abs(gx*31 + gz) % size`. **Hash distinto** al de H1 (sin paso LCG). Requiere `BuiltInRegistries.BLOCK` |
| B2 | `.../util/SphereUtil.java:23-30` | `SphereUtil` | `isSphereBlock(BlockState)` | Compara contra `SPHERE_BLOCK` del config |
| B3 | `.../mixin/MixinHeightmap.java:26,41` | `MixinHeightmap` | `onUpdateBlockState`, `onPrimeGetBlockState` | Usan B2 y `y > CENTER_Y`. **No es geometría de esfera**, solo la constante `CENTER_Y` |

Uso de B1: `SphereCarver.carve:67`. Uso de B2: `PlayerSpawnHandler:45,65,79` y
`MixinHeightmap:26,41`.

### 1.5 «¿Está esta posición dentro de una esfera?» y distancia al borde

| # | Archivo | Clase | Método | Predicado exacto | Margen |
|---|---------|-------|--------|------------------|--------|
| D1 | `.../carver/SphereCarver.java:72-105` | `SphereCarver` | `carve(...)` | **Cascarón**: `(r-2)² <= d² <= r²` (ambos inclusivos) | — |
| D2 | `.../carver/SphereCarver.java:166-189` | `SphereCarver` | `isInsideAnySphere(x,y,z,margin)` | Vecindad **3×3 celdas**; `d² < (r - 2.0 - margin)²` (estricto) | parámetro |
| D3 | `.../carver/SphereCarver.java:162-164` | `SphereCarver` | `isInsideAnySphere(x,y,z)` | `= D2 con margin 0` | `0` |
| D4 | `.../density/MultipleSpheresDistanceFunction.java:40-42` | `MultipleSpheresDistanceFunction` | `compute(...)` | `d² / r²` (normalizado, `double`) | — |
| D5 | `.../mixin/MixinNoiseBasedAquifer.java:51-52` | `MixinNoiseBasedAquifer` | `onComputeSubstance(...)` | `d² - r² >= -2r` → devuelve aire. Umbral **lineal** `2r`, no cuadrático | — |
| D6 | `.../util/SphereUtil.java:9-21` | `SphereUtil` | `isInSideSphere(x,y,z,radius,spacing,centerY)` | `d² <= radius²`. **Sin llamadas en todo el repo: código muerto** | — |
| D7 | `.../event/PlayerSpawnHandler.java:72-74` | `PlayerSpawnHandler` | `findValidPosition(...)` | Barrido en el plano XZ: `dx² + dz² <= radius²`. Usa el radio de `getSphereRadiusAt` | — |
| D8 | `.../feature/SphereFeature.java:57-59` | `SphereFeature` | `place(...)` | Banda: `abs(d - radius) <= tolerance`. **Clase «Not in use»** | — |

#### Quién llama a D2/D3 (el predicado compartido)

| Llamante | Archivo:línea | Margen pasado | Efecto |
|----------|---------------|---------------|--------|
| `BiosphereBiomeSource.isInsideSphere` | `.../worldgen/BiosphereBiomeSource.java:90` | `-4` | El más caliente: decide void vs bioma |
| `BiosphereBiomeSource` (3 métodos) | `...:63, 74, 85` | vía `isInsideSphere` | Filtra resultados de búsqueda de bioma |
| `MixinMultiNoiseBiomeSource.getNoiseBiome` | `.../mixin/MixinMultiNoiseBiomeSource.java:37` | `-4` | Sustituye por `THE_VOID` fuera |
| `MixinChunkGeneratorBiomeSource.markBiosphereBiomeSource` | `.../mixin/MixinChunkGeneratorBiomeSource.java:28` | `-4` | **Solo** mensaje de DEBUG |
| `MixinChunkGeneratorStructures.restrictStructureToSphere` | `.../mixin/MixinChunkGeneratorStructures.java:48` | `STRUCTURE_EDGE_MARGIN` (def. 16) | Filtro de estructuras |
| `SphereBridgeCarver.carveBridge` | `.../carver/SphereBridgeCarver.java:124` | `0` (vía D3) | No poner puente dentro del cascarón |

**Particularidades que hay que conservar bit a bit:**

- `margin = -4` **agranda** el radio efectivo en 2 (`r - 2 + 4 = r + 2`).
- Con `margin > r - 2` el radio efectivo se vuelve **negativo** y al elevarlo al
  cuadrado vuelve a ser positivo: el predicado se invierte parcialmente. Con los
  valores por defecto es alcanzable (`STRUCTURE_EDGE_MARGIN` admite hasta 128 y
  `MIN_RADIUS` puede ser 128 → radio efectivo `-2` → «dentro» = a menos de 2
  bloques del centro). **No hay que normalizarlo ni clampearlo.**
- La comparación de D2 es `d² < effectiveRadius²`, equivalente a `d < |effectiveRadius|`.
  Por eso `estaDentro` **no** puede derivarse simplemente de una distancia
  firmada al borde sin romper la equivalencia.
- D1 no usa la vecindad 3×3: enumera celdas por chunk (C3). Dentro de cada celda
  las condiciones de poda (`dx2 > outerSq`, `horizontalDistSq > outerSq`) son
  solo optimización y no cambian el conjunto de bloques colocados.
- D2 usa `d` en `double` y `radius` en `int` convertido a `double`.
- D5 es un umbral **aproximadamente** igual a «fuera del cascarón», pero no
  idéntico a D1/D2. No lo unificaría con el mismo predicado sin decisión tuya.

### 1.6 Código muerto / no alcanzable detectado

- `SphereUtil.isInSideSphere(...)` (D6): **cero llamadas** en el repositorio.
- `SphereFeature` (C8, D8): el propio archivo dice `Not in use`; su código no se
  registra en ningún `biome_modifier` activo (`SphereBiomeBoostrap.java:32` está
  comentado).

---

## 2. API propuesta de `SphereLayout`

Sin escribir código todavía. Nombres nuevos que **no existen** hoy y por tanto
los propongo yo; los que sí existen hoy se conservan.

### 2.1 Tipos

```java
public final class SphereLayout {

    /** Parámetros inmutables de la retícula. Sustituye a las lecturas sueltas de SphereConfig. */
    public record Params(int spacing, int centerY, int minRadius, int maxRadius) {}

    /** Una esfera concreta. Inmutable. */
    public record Sphere(int cellX, int cellZ, double centerX, double centerZ, int radius) {}
}
```

`Sphere` **no** incluye el bloque del cascarón: el bloque sale de un hash distinto
(B1) y necesita `BuiltInRegistries`. Lo dejo fuera de la geometría pura.
**Decisión pendiente (§5).**

### 2.2 Métodos

| Método | Sustituye a | Notas |
|--------|-------------|-------|
| `static SphereLayout current()` | (nuevo) | Instantánea de `SphereConfig` con caché por huella de configuración |
| `Params params()` | (nuevo) | Acceso a los parámetros |
| `int cellX(double x)` / `int cellZ(double z)` | C1, C2 | Canónica `Math.round` |
| `Sphere sphereAtCell(int cellX, int cellZ)` | H1 + S1 + S2 | Índices de celda. Pasa por la caché por celda |
| `Sphere sphereContaining(double x, double z)` | H2 | Coordenadas de bloque: celda que contiene el punto |
| `List<Sphere> candidatesAt(double x, double y, double z)` | El bucle 3×3 de D2 | Vecindad 3×3, **orden fijo** (gx de -1 a +1, gz de -1 a +1) |
| `List<Sphere> spheresAt(double x, double y, double z, double margin)` | D2 (parte que cumple) | Subconjunto de candidatas que cumple el test |
| `boolean isInside(double x, double y, double z, double margin)` | D2, D3 | **Sin asignar memoria** (no construye lista) |
| `double distanceToEdge(double x, double y, double z)` | **nada** (nuevo) | Métrica nueva, ver §2.4 |

`esferas que afectan a una posición` = `spheresAt`. Hoy devuelve 0 o 1 elementos
(el `r=128` contra `spacing=500` hace que las esferas vecinas no se toquen); la
vecindad 3×3 se conserva solo por fidelidad al comportamiento actual.

### 2.3 Requisitos de diseño

- **Inmutable**: todos los campos `final`; `Sphere` y `Params` son `record`.
- **Determinista**: salida función pura de `(Params, coordenada)`.
- **Segura entre hilos (C2ME)**: sin estado mutable compartido salvo la caché,
  que será `ConcurrentHashMap` (lecturas sin bloqueo). Carrera al construir la
  instancia de `current()`: benigna, el contenido es idéntico.
- **Barata**: la ruta caliente (`isInside`) no asigna ni recorre listas; solo lee
  hasta 9 `Sphere` de la caché. Hoy `isInsideAnySphere` hace **entre 4 y 8
  lecturas de `SphereConfig.get()` por llamada**, así que la caché lo mejora.
- **Caché por celda**: clave = `(cellX, cellZ)` empaquetados en un `long`.
  Cota de tamaño con vaciado al superar un umbral; vaciar es seguro porque el
  resultado no depende del estado de la caché.
- **Invalidación por configuración**: si cambia `spacing`, `centerY`,
  `minRadius` o `maxRadius`, `current()` publica una instancia **nueva** con
  caché nueva. Esto evita devolver esferas obsoletas, que es el riesgo real de
  cachear valores derivados de un config que hoy se lee en vivo.

### 2.4 `distanceToEdge` (métrica nueva)

Hoy **no existe** ninguna función «distancia al borde». La defino así:

```text
distanceToEdge(x, y, z) = (radius - 2.0) - distancia_euclidea(hasta el centro
                          de la esfera que contiene el punto)
```

Positivo dentro del hueco, negativo fuera del cascarón. **No** es una
reformulación de D2: D2 compara contra `|r - 2 - margin|` por el efecto del
cuadrado. Por eso `isInside` se implementará **con la fórmula cuadrática actual**,
no como `distanceToEdge > margin`, para garantizar equivalencia exacta.
Si prefieres que `distanceToEdge` sea exactamente la inversa de `isInside`
(incluido el caso de radio efectivo negativo), dímelo y la defino como la
distancia firmada a la esfera `|r - 2 - margin|` — pero entonces hay que decidir
qué hacer cuando la esfera no contiene el punto.

### 2.5 Alcance que propongo **dejar fuera**

- `SphereCarver.getSphereBlockState` (B1) y `SphereUtil.isSphereBlock` (B2): son
  selección de bloques con registro, no geometría. Se quedan donde están.
- `SphereUtil.isInSideSphere` (D6): código muerto. Propongo **borrarlo** en FASE 2
  en vez de portarlo. Si prefieres conservarlo, dilo.
- `SphereFeature` (C8/D8): «Not in use». Propongo **no tocarlo** en este refactor.
- `MixinHeightmap` (B3): solo usa `CENTER_Y`; no es geometría de esfera. Sin cambios.

---

## 3. Restricciones de la fase 2

- No commit, no push. Cambios sin confirmar.
- No tocar `biospheres.json`, `neoforge.mods.toml` ni `gradle.properties`.
- `build.gradle` sí se puede tocar (hace falta para el test, ver §4).

---

## 4. Prueba de equivalencia: infraestructura

**Hallazgo: hoy no existe infraestructura de test.** No hay `src/test`, no hay
JUnit en `build.gradle` y el plugin `net.neoforged.moddev` solo declara
gameTests (que requieren arrancar Minecraft). Para cumplir «cópiala a una clase
de test y compara en una muestra grande», hay dos vías:

1. **JUnit 5 en `src/test/java`** (recomendada): añadir `testImplementation` y
   `useJUnitPlatform()` en `build.gradle`. `.\gradlew build` ejecuta `check` →
   `test`, así que la prueba corre en el build que pides. La geometría es pura
   (sin registros ni Minecraft), así que el test no necesita el juego **siempre
   que `SphereLayout` se construya con `Params` explícitos** y el test no llame a
   `current()`.
2. **Harness `main()` temporal** fuera de Gradle: más barato de montar, pero no
   se ejecuta en `build` y hay que borrarlo después.

La clase de test copiará **literalmente** las fórmulas viejas de H1, C1/C2, D2 y
D1 parametrizadas por `(spacing, centerY, minRadius, maxRadius)`, y comparará
contra `SphereLayout` en una muestra grande: varias celdas (positivas,
negativas, en los bordes de celda), varios `y` (centro, borde, fuera), varios
márgenes (incluidos `-4` y márgenes que vuelven negativo el radio efectivo) y
ambos caminos de `minRadius >= maxRadius`.

---

## 5. Decisiones pendientes de tu OK

1. **Paquete** de `SphereLayout`. Propongo `cn.mlus.biosphereworlds.worldgen`
   (junto a `BiosphereWorldgen`). Alternativa: `cn.mlus.biosphereworlds.util`.
2. **División `float` vs `double`** en C5/C7 (§1.1). Propongo `double` (unifica
   con la canónica) y documentar el cambio en coordenadas > ~2^24.
3. **`D5` (acuífero)**: ¿lo dejo con su umbral lineal propio, o lo expreso como
   un método aparte de `SphereLayout` (p. ej. `isOutsideForAquifer`)? Propongo
   dejarlo como está en FASE 2 para no cambiar comportamiento.
4. **Infraestructura de test**: JUnit 5 (opción 1) o harness `main()` (opción 2).
5. **Bloque del cascarón** fuera de `SphereLayout` (§2.1): confirmar.
6. **Borrar** `SphereUtil.isInSideSphere` (código muerto) en FASE 2: confirmar.

---

## 6. FASE 2: resultado

Decidido y aplicado tras el OK:

1. **Paquete**: `cn.mlus.biosphereworlds.worldgen.SphereLayout`.
2. **División**: se unifica en `double`. Documentado como única divergencia
   aceptada, solo por encima de ~2^24.
3. **D5 (acuífero)**: conserva su umbral lineal propio en el mixin; solo toma la
   celda, el centro y el radio de `SphereLayout`.
4. **Test**: JUnit 5 en `src/test/java`; `.\gradlew build` ejecuta `:test`.
5. **Bloque del cascarón**: se queda fuera de `SphereLayout` (sigue en
   `SphereCarver.getSphereBlockState`, que solo necesita los índices de celda).
6. `SphereUtil.isInSideSphere` (código muerto) se **eliminó**.

Prueba de equivalencia:
`src/test/java/cn/mlus/biosphereworlds/worldgen/SphereLayoutEquivalenceTest.java`.
19 casos, 0 fallos. `Legacy` es la copia literal de las fórmulas viejas
parametrizada; la muestra cubre bordes de celda, celdas negativas, los dos
caminos de `minRadius >= maxRadius`, márgenes `-4`/`0`/`16`/`128`/`1000` y el caso
patológico `hash == Integer.MIN_VALUE` (resuelto con `modInverse` para forzarlo).

### 6.1 Regresión y arreglo: sobrecargas `sphereAt` (D4/D5)

La primera versión de `SphereLayout` tenía **dos sobrecargas llamadas `sphereAt`**:

```java
public Sphere sphereAt(int cellX, int cellZ)   // índice de celda
public Sphere sphereAt(double x, double z)     // posición de bloque
```

Java resuelve por tipo, así que **con argumentos `int` gana siempre la de índice
de celda** (coincidencia exacta, sin conversión). Tres llamadas de producción
pasaban coordenadas de bloque y aterrizaban en ella:

- `MultipleSpheresDistanceFunction.java` (D4) — `sphereAt(context.blockX(), context.blockZ())`
- `MixinNoiseBasedAquifer.java` (D5) — `sphereAt(x, z)` con `int x, z`
- `PlayerSpawnHandler.java` — `sphereAt(currentPos.getX(), currentPos.getZ())`

El centro pasaba a ser `bloque × spacing` en vez de `round(bloque/spacing) × spacing`
(distancia astronómica, y `cellX * spacing` **desborda `int`**). En el centro de una
esfera, D4 devolvía `1.2e8` en vez de `0`; el `range_choice` de `biospheres.json`
exige `[0,1)` y si no devuelve `-1000000` → **cascarón de vidrio sin terreno**. Los
carvers no se vieron afectados porque `SphereCarver.java:61` y
`SphereBridgeCarver.java:57` pasan `gridX`/`gridZ`, que sí son índices de celda.

**Arreglo**: se eliminó toda sobrecarga llamada `sphereAt`.
`sphereAt(int,int)` → **`sphereAtCell`**; `sphereAt(double,double)` →
**`sphereContaining`**. Pedir la esfera de una posición y la de una celda ya no se
pueden confundir por una conversión implícita de `int`.

**Regla para el futuro**: cualquier par de métodos que reciba índices de celda y
coordenadas de bloque debe distinguirse **por nombre**, nunca por sobrecarga, porque
`int`→`int` gana a `int`→`double` y el error no lo detecta el compilador.

Las 4 pruebas que cubren esto (`d4FuncionDeDensidadCoincideConLaLogicaVieja`,
`d4DaValorDeTerrenoEnElCentroDeCadaEsfera`, `d5AcuiferoCoincideConLaLogicaVieja`,
`sphereContainingDeUnaPosicionUsaLaCeldaQueLaContiene`) fallaban antes del arreglo
y pasan después.

---

## 7. Nota sobre C2ME y TerraBlender

- **No hay dependencia de C2ME ni de TerraBlender en este repositorio.**
  Comprobado con búsqueda sobre `build.gradle`, `settings.gradle`,
  `gradle.properties`, `neoforge.mods.toml` y los 35 `.java` del proyecto: las
  únicas coincidencias de «blender» son `net.minecraft.world.level.levelgen.blending.Blender`
  (clase vanilla, en `MixinNoiseBasedChunkGenerator.java`), sin relación con
  TerraBlender.
- **Riesgo C2ME**: si un mod externo genera chunks en paralelo, las clases de
  geometría actuales son estáticas y sin estado, así que ya son seguras; el
  riesgo del refactor es **introducir** estado mutable (la caché). Se mitiga con
  `ConcurrentHashMap` e instancias inmutables.
- **Riesgo TerraBlender**: no verificable aquí por ausencia de la dependencia.
  `BiosphereBiomeSource` (que sí usa D2 en la ruta más caliente) es el punto que
  TerraBlender podría envolver; al no cambiar su contrato ni su predicado, el
  riesgo es bajo. **No verificado.**
