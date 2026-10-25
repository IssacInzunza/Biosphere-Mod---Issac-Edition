# Diagnóstico aislado de Ars y C2ME — 24 de octubre de 2026

Instancia investigada: `Planet Avanti - 1.21.1`. El análisis inicial fue de solo
lectura. La preparación del banco fue autorizada después por el usuario.
No se modifica la instancia ni sus partidas, el código productivo,
`mod_version` o `biospheres.json`.

## Evidencia inicial

- `mods/biosphereworlds-2.0.jar`, Minecraft 1.21.1, NeoForge 21.1.253.
- `saves/Mundo nuevo/level.dat`: semilla `-1508672473046624236`, generador
  `biosphereworlds:solar_systems`, geometría 7, ocho planetas por sistema,
  radios 256–512 y clima Solar activado.
- `logs/latest.log:1129` y `:1138`: TerraBlender registra las regiones de Ars
  Nouveau y Ars Elemental. `:1757` confirma la inicialización del Overworld.
  Pesos configurados: Ars Nouveau 2 y Ars Elemental 1.
- Se leyeron 6.876 chunks guardados; 3.525 estaban en `structure_starts`.
  Ninguna paleta de biomas leída contenía Ars; 1.228 chunks contenían algún
  bioma de BOP. Son ocurrencias por chunk, no cantidades de bloques ni prueba
  de ausencia en todo el mundo.
- `logs/latest.log:2663`: ModernFix detecta un tick de más de 40 segundos.
  `:3238–3277`: el servidor espera un chunk solicitado desde
  `Naturalist / Whale.aiStep`. `:2801–2907`: un trabajador de C2ME está en
  DataFixerUpper, pasando por `BnCDataFixer.updateWithFixers` y carga/generación
  de estructuras, incluida `SolarChunkGenerator.createStructures`.
  `:3706` en adelante muestra recuperación y guardados posteriores.
  La captura no demuestra un deadlock ni identifica por sí sola al culpable.
- `logs/debug.log:12008`: integración de C2ME con Lithostitched falla al buscar
  `FastNoiseDensityFunction`. Es un aviso distinto de la pausa.

Referencias de código del proyecto: `SolarBiomeSource.climateSampler`,
`SolarBiomeSource.java:73`, adapta la temperatura; `SolarChunkGenerator`,
`SolarChunkGenerator.java:209`, delega la creación de estructuras antes de
validar la fortaleza central. `MixinSolarStructureBounds`,
`MixinSolarStructureBounds.java:27`, comprueba contención después de generar.
No se consultó la caché de fuentes de Minecraft durante este diagnóstico;
las referencias al motor anteriores proceden de las pilas del registro.

## Banco reproducible

El auxiliar está en [`work/ars-c2me-audit`](../../work/ars-c2me-audit/), ignorado
por Git como los bancos anteriores. No se incluye en el JAR del mod.

- `export_fixture.py` solo lee `level.dat` y `logs/latest.log`, y exporta
  semilla, receta, hash del mundo y orden de regiones registrado. El destino
  es siempre el directorio del auxiliar.
- Gradle usa el JAR **2.0 instalado**, sin compilar ni sustituir el mod local.
  Incluye Ars Nouveau, Ars Elemental, sus dependencias, BOP, GlitchCore,
  TerraBlender y YUNG’s Cave Biomes/API. Este último conserva el conjunto de
  regiones de biomas observado en el registro. No es el modpack completo.
- NeoForge 21.1.253; Java 21; cuatro procesadores visibles; heap de 4 GiB.
  Caché y temporales se guardan dentro de `work/`.
- Se crean mundos descartables independientes con la receta guardada, la misma
  receta con clima desactivado y el generador normal como control.
  Se inicializa TerraBlender explícitamente en cada fixture nuevo.
- Las primeras ejecuciones mostraron órdenes de registro distintos entre sí
  y respecto de la instancia. No sirven para atribuir diferencias a C2ME ni
  para dar coordenadas de la partida original. Antes de abrir los fixtures,
  el auxiliar reordena las mismas instancias de regiones mediante la API
  pública `Regions.remove/register`, conserva sus pesos, comprueba índices y
  restaura el orden de arranque después de cerrar todos los fixtures. Solo
  cambia el proceso de prueba. `region_order` y `startup_region_order` dejan
  constancia de ambos órdenes. Una región ausente o adicional aborta la prueba.
- La geometría se consulta al `SolarLayout` del JAR instalado. No se duplica.
  Malla global X/Z de 64 bloques, Y cada 32 desde -48 hasta 304; solo puntos
  interiores de planetas orbitales con margen 8. Se excluye el sol.
- `Cells = 1` cubre nueve sistemas (celdas de -1 a 1 en ambos ejes).
  El muestreo consulta biomas; no crea chunks en cada coordenada ni prueba
  decoración, estructuras, altura superficial, iluminación o entidades.
- Se obtiene por reflexión el `terrainRandom` del generador instalado para
  conservar su adaptación del océano. También se lee `MultiNoiseBiomeSource`
  mediante su método privado `parameters`, para inspeccionar la región de
  TerraBlender. Es instrumentación del auxiliar, no una nueva API del mod.

Desde la raíz del proyecto, con la partida original cerrada:

```powershell
python work/ars-c2me-audit/export_fixture.py
.\work\ars-c2me-audit\run.ps1 -Variant without-c2me -RunName base01
.\work\ars-c2me-audit\run.ps1 -Variant with-c2me -RunName c2me01
python work/ars-c2me-audit/compare.py base01 c2me01
```

Usar un `RunName` nuevo en cada ejecución. Una ejecución ya iniciada se rechaza
para evitar comparar chunks reutilizados o resultados parciales.
`input-mods.json` guarda nombres y SHA-256 de los JAR; `samples.tsv` contiene
coordenadas, tres consultas de bioma y región; `planets.tsv`, centros y climas;
`report.json`, recuentos y primeras coordenadas de Ars; `COMPLETE` solo se
escribe después del cierre correcto de los fixtures.

`compare.py` exige recetas, semillas, malla, Java, orden de regiones,
configuraciones de biomas e inventarios no-C2ME iguales;
verifica integridad y compara todas las filas. Una ejecución incompleta o una
diferencia devuelve error. La igualdad no certifica generación multihilo.

## Pruebas adicionales y límites

Con `-Chunks`, el auxiliar solicita chunks FULL en los centros de los cuerpos
del sistema (0,0) y una zona explorada conocida, y registra tiempos en
`chunks.tsv`. `pending-chunk.txt` permite identificar la solicitud pendiente
si falla. No se afirma que esa zona sea donde ocurrió la pausa original.
Los tiempos incluyen inicialización y calentamiento; repetir A/A antes de
atribuir diferencias a C2ME. Una prueba con chunks tarda más que el muestreo.

Las variantes `suspects-without-c2me` y `suspects-with-c2me` añaden Naturalist,
Brewin’ and Chewin’, Farmer’s Delight y ModernFix, con los mismos JAR locales.
No recrean todos los mods de estructuras ni hacen avanzar la IA de ballenas.

Pendiente en el juego: comprobar las coordenadas devueltas dentro de planetas,
su superficie y decoración, y reproducir la carga alrededor de una ballena en
copias descartables del mundo, comparando con/sin C2ME. Cambiar una sola
variable por comparación. Los cambios de clima/pesos solo afectan la generación
nueva y pueden introducir discontinuidades; no se recomienda aplicarlos a la
partida original sin establecer primero la causa.

## Resultados de esta preparación

Las ejecuciones `pinned-without-c2me` y `pinned-with-c2me` terminaron con el
GameTest aprobado y cierre correcto. Se compararon **89.242 puntos** en los
mismos 72 planetas orbitales de nueve sistemas. Las tres columnas de biomas
coinciden en todos los puntos, con **cero diferencias**. SHA-256 común del
contenido de las muestras (sin cabecera):
`be0254ec31c45d6fb1d9323cd1aaf48b8fae384d356cd7f7461432e7a3299b4c`.

| Bioma | Solar activo, sin C2ME | Solar activo, con C2ME | Solar sin clima | Normal en las mismas coordenadas |
|---|---:|---:|---:|---:|
| `ars_nouveau:archwood_forest` | 246 | 246 | 379 | 546 |
| `ars_elemental:blazing_forest` | 55 | 55 | 13 | 28 |
| `ars_elemental:cascading_forest` | 20 | 20 | 88 | 186 |
| `ars_elemental:flashing_forest` | 34 | 34 | 7 | 0 |
| `ars_elemental:flourishing_forest` | 0 | 0 | 51 | 39 |
| `ars_elemental:vexing_caves` | 15 | 15 | 24 | 18 |
| Total Ars | **370** | **370** | **562** | **817** |

Ars ocupa un 0,415 % de **esta malla**, no una estimación de todo el mundo.
El clima Solar cambia la selección y reduce el total de Ars en esta muestra;
no excluye por completo Ars Nouveau ni Ars Elemental. No encontrar Flourishing
en esta malla con clima activo tampoco demuestra que sea imposible generarlo.
C2ME no cambia las consultas comparadas. No se certifica el comportamiento
de los demás mods ni la generación completa de chunks a partir de este resultado.

Las muestras de Ars más cercanas a la posición de referencia del registro
`(20767.5, 28276.5)` quedaron lejos. Son candidatos del banco con el orden
original de regiones; **no se generó ni se inspeccionó su terreno en el juego**.
La malla no es una búsqueda exhaustiva y sus distancias no son mínimos globales.

| Bioma muestreado | X | Y | Z | Distancia horizontal aproximada |
|---|---:|---:|---:|---:|
| Vexing Caves | 63232 | -48 | 28032 | 42.465 |
| Blazing Forest | 68160 | 208 | 63488 | 59.042 |
| Flashing Forest | -17984 | 144 | -26752 | 67.304 |
| Archwood Forest | -24256 | 176 | 56512 | 53.145 |

Estos puntos sirven para una comprobación en una copia descartable, consultando
F3 y después buscando su superficie; la Y muestreada puede estar dentro del terreno.
La escasa coincidencia entre regiones, clima y planetas es compatible con las
búsquedas fallidas observadas, sin demostrar todavía su única causa.

Evidencia local:

- [Comparación completa](../../work/ars-c2me-audit/comparison.json).
- [Informe sin C2ME](../../work/ars-c2me-audit/runs/pinned-without-c2me/results/report.json).
- [Informe con C2ME](../../work/ars-c2me-audit/runs/pinned-with-c2me/results/report.json).
- [Muestras con C2ME](../../work/ars-c2me-audit/runs/pinned-with-c2me/results/samples.tsv).
- [Registro sin C2ME](../../work/ars-c2me-audit/runs/pinned-without-c2me/logs/latest.log).
- [Registro con C2ME](../../work/ars-c2me-audit/runs/pinned-with-c2me/logs/latest.log).

Comprobaciones del auxiliar: comparación idéntica, detección de bioma distinto,
rechazo de semilla u orden de regiones distintos, integridad de muestras,
rechazo de ejecución incompleta, cursor NBT tras arrays y rechazo de NBT truncado.

SHA-256 del JAR 2.0 ensayado:
`4ac3791cbb30e66513ea0a7a8ab67036d026d1d2c886e94f9d96375304839e76`.
Se comprobó al final que el `level.dat` original conserva su hash:
`8b452a45aaa3064d4ce179b64f57e72d52d95966f920b599af0b939c94bfb8a1`.

**Congelamiento: no reproducido por este muestreo.** No se ejecutaron las
variantes `suspects-*` ni `-Chunks`. Quedan preparadas para la prueba de generación;
la reproducción de la espera desde la ballena requiere además la prueba en juego.
No se ha implementado ni distribuido una corrección del mod.
