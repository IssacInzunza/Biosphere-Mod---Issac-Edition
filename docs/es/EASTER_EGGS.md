# Registro de Easter Egg

Fecha de diseño: 27 de octubre de 2026. Cada easter egg es una fase del proyecto.
Antes de cada implementación: diagnóstico de solo lectura y OK del usuario.
La sección y archivo de configuración se llaman **Easter Egg**.
Su única opción pública es `enabled = true/false`. Este registro técnico contiene
spoilers; las frecuencias y demás reglas son internas y no aparecen en el menú
ni en el README.
Todos se limitan a Biospheres clásico y Solar Systems. Vanilla queda excluido.
El menú abre directamente el interruptor desde Easter Egg, sin una categoría
intermedia repetida ni el rótulo genérico Common File. Mantiene la ruta y valores
guardados del TOML; en instalaciones nuevas está activado por defecto.

## Fase 1 — El observador

**Historia:** Como jugador de Biospheres, quiero encontrar luces que se mueven
entre las esferas, forman figuras y parecen comunicarse con destellos, para
sentir que algo desconocido habita y observa el vacío.

**Estado:** revisión de presencia y comportamiento implementada; validación visual
y compatibilidad con naves reales pendientes.

Validación automática: compilación con Java 21, 120 pruebas JUnit y 49 GameTests
de motor (32 de worldgen y 17 del observador, ejecutados por separado). Las comprobaciones de motor incluyen equivalencia exacta de señales
vanilla y con el observador apagado en un nivel Biospheres clásico real.
También se comprueban la frontera de 64 bloques en ambas direcciones, exclusión
a 65 bloques, máquinas lejanas, ausencia de carga adicional y vuelo por encima
del techo de construcción. La revisión añade variantes continuas, rareza de
ataque, origen exterior sobre el cascarón desde el centro, límites interiores,
paredes finas, acompañamiento de un testigo móvil y daño único sin empuje.
La revisión de misterio añade prueba de pulso más allá de 128 bloques, aviso previo,
pared añadida durante el aviso, distancias a todos los testigos, casco genérico,
agresor fijado, desconexión del agresor, continuidad multijugador y contrabatería
de proyectil automático vanilla sin mods de cañones instalados.
La revisión de represalia comprueba carga continua de 60 ticks, seguimiento del
agresor móvil, movimiento hostil de una luz, daño mortal, vidrio destruido,
resistencia de obsidiana, límite exterior, impacto sobre cobertura añadida durante
la carga y cancelación en vanilla, con el interruptor apagado o sin chunks cargados.
La revisión de fuego real comprueba ignición del objetivo, daño periódico de
quemadura del motor, fuego colocado durante la carga e incendios tras explotar.
Se comprueban los nuevos mínimos 96/80, altura del acompañamiento y cobertura
añadida durante la carga que evita encender al objetivo detrás de ella.
Una aparición interior real comprueba altura de 48–64, distancia mínima y rechazo
cuando falta espacio bajo el cascarón, sin forzar chunks ni comprimir el perfil.
La compilación y las 17 pruebas del observador terminan correctamente. En la
regresión de worldgen, las 32 pruebas pasan en dos ejecuciones, pero ambas quedan
atascadas después al cerrar mundos, en `ChunkMap.processUnloads` durante
`MinecraftServer.stopServer`. Se finalizaron únicamente esos servidores de prueba;
esa tarea Gradle no termina con éxito. Las pilas se conservaron en
`run/observer-real-fire-distance-shutdown-20261027.log` y su variante `-retry`.
No se cambió código de worldgen ni del motor para ocultar ese problema de cierre.
Los FakePlayer de estas pruebas no crean tickets de jugador: un listener temporal
los incluye en la lista de entidades de la explosión, y Minecraft calcula exposición,
daño y muerte. No sustituye una prueba con jugadores conectados.
Prueba adicional aislada con Create 6.0.8: aprobada con transmisor y receptor
reales, indicador, recuperación de entradas/frecuencias cambiadas y reemplazo de
antena. No verifica visualmente el cliente ni otras versiones de Create.
Pruebas adicionales anteriores a la represalia destructiva con CBC 5.11.7,
Create 6.0.10 y RPL 2.1.2: dos pruebas aprobadas
en NeoForge 21.1.228, usando un argumento temporal de Gradle. La base del proyecto
sigue en 21.1.216. Se verifican el proyectil `shot` real, origen sin artillero,
tres pulsos separados, interrupción/recuperación del control y antenas de Create.
No equivale a montar y disparar un cañón completo ni a probar todos los mods de armas.
La nueva explosión de contrabatería se verifica con proyectil automático vanilla;
queda repetir la comprobación con CBC instalado y un cañón real.

Criterios:

- Cada luz es una entidad real; un encuentro contiene entre una y cinco.
- Distribución: 1 luz 35 %, 2 luces 30 %, 3 luces 20 %, 4 luces 10 %, 5 luces 5 %.
  Las variantes interiores limitan el grupo a tres luces.
- Núcleo y halo cuatro veces mayores; halo exterior de seis bloques de diámetro.
  Observación, formación, cambios bruscos de rumbo, aproximación al vidrio,
  acompañamiento durante el vuelo y ataque excepcional tienen recorridos distintos.
  Observación y acompañamiento mantienen luz estable; las otras variantes cambian
  intensidad o intercambian destellos. No se añaden modelos de personajes.
- Mayor frecuencia nocturna; pesos integrados 80/15/5 para noche/crepúsculo/día,
  no cuotas garantizadas para el tiempo de juego de cada persona.
- Mayor frecuencia en biomas de montaña y del 25 de octubre al 2 de noviembre.
- Exteriores estáticos: origen a 80–160 bloques en horizontal y al menos 80–160 por encima
  del jugador, elevado por encima de la cota superior del cascarón real cuando
  sea necesario. No exige caminar hasta el borde para aparecer. Vuelo por encima
  del techo de construcción; renderizado hasta 1024 bloques, sujeto al seguimiento
  de entidades y chunks del cliente/servidor. Esferas extremas pueden excederlo.
- Se intenta una variante interior en el 15 % de sorteos compatibles cuando el
  jugador está dentro; exige aire, distancia del cascarón y un recorrido cargado.
  El origen está a 88–120 bloques en horizontal y 48–64 por encima de los ojos;
  cada luz debe aparecer en esa banda de altura. No se comprime la formación al
  45 %. El acompañamiento y ataque usan perfiles interiores propios. Si no cabe
  el recorrido a distancia suficiente, se omite el intento, sin acercar las luces.
  Es una probabilidad de intento, no una cuota: obstáculos pueden aplazarlo.
- Curiosidad aérea: acompaña al jugador lateralmente y se aleja; detecta élitras,
  vuelo creativo o desplazamiento sostenido sobre aire durante dos segundos.
  No contiene nombres de mods de transporte. Aeronautics y Simple Planes requieren
  prueba manual: un transporte que no actualice coordenadas reales del jugador no
  se puede identificar con esta heurística. El encuentro no fuerza chunks.
- Distancia mínima a cualquier jugador vivo no espectador: 96 bloques fuera y
  80 dentro, medida en 3D desde los ojos. El acompañamiento exterior queda dentro
  de la banda de 120–160 bloques laterales y 64–96 por encima durante la observación;
  la retirada final gana altura. En interiores mantiene 48–64 por encima durante
  el acompañamiento, con un recorrido distinto y sin reducción uniforme. La
  aparición evita también el margen de retirada anticipada de 16 bloques.
  Si alguien se aproxima, las luces
  se retiran y desvanecen; si entra en el margen mínimo, desaparecen. Se comprueba
  el segmento completo, los vehículos montados y entidades grandes del motor.
  No garantiza reconocer geometría de naves que su mod mantenga fuera de esas
  representaciones; requiere pruebas con Aeronautics, Simple Planes y sus versiones.
- Ataque: 1 % de sorteos naturales, independiente de Halloween y vuelo. Una sola
  oportunidad natural por encuentro, a distancia y con advertencia de dos segundos.
  El pulso visible dura diez ticks y alcanza hasta 224 bloques, sin superar la
  distancia mínima. Recalcula obstáculos al disparar: paredes, vidrio y vehículos
  representados en el motor detienen el rayo, incluso si aparecieron tras el aviso.
  Comprueba los chunks del trayecto antes de consultarlos. Solicita 2 puntos de
  daño base (un corazón), sin reducirlos según la salud restante ni reservar vida.
  Puede matar; respeta armadura e invulnerabilidad normales de Minecraft.
  El tipo de daño no produce empuje ni altera las élitras; no hay embestida.
- Represalia: una agresión atribuida activa diez segundos de hostilidad, color cálido,
  maniobras laterales incluso con una luz y pulsos eléctricos más frecuentes.
  Durante 60 ticks (tres segundos a 20 TPS) mantiene un haz grueso sobre el agresor,
  con quemaduras reales al alcanzar una entidad, fuego en superficies próximas
  al impacto, llamas, humo y sonido de carga. Después produce una
  explosión real, destructiva y potencialmente mortal: potencia vanilla 8, radio
  de daño de 16 bloques y destrucción limitada además a 16 bloques desde el centro,
  respetando resistencias. No garantiza un cráter uniforme de 32 bloques.
  No protege la salud, construcciones, ganado, naves ni vidrio dentro de la explosión.
  La carga prende a la entidad alcanzada durante ocho segundos, renovables si el
  haz continúa alcanzándola. Las quemaduras usan las reglas normales del motor.
  Busca una superficie válida hasta cuatro bloques bajo el impacto en una zona
  de 3×3; no atraviesa bloques para colocar fuego ni lo coloca flotando. La
  explosión también puede dejar incendios. La colocación de fuego exige un área
  cargada de 32 bloques alrededor; su propagación posterior sigue las reglas de
  fuego de Minecraft. El haz recalcula obstáculos y sigue al
  mismo agresor; la explosión ocurre donde termina. Se omite si el área necesaria
  no está cargada, sin forzar chunks. Un proyectil que pasa junto a una luz también
  cuenta como intento. Si el agresor sale, no se reasigna la represalia.
  Repetir ataques no reinicia el temporizador ni crea ráfagas infinitas.
- Contrabatería genérica: registra el punto real de aparición de proyectiles
  nuevos durante un encuentro, con límite de 512 y caducidad de veinte segundos.
  Proyectiles sin dueño o asociados a un vehículo, entidad mecánica grande o bloque
  de máquina tras la boca reciben tres pulsos contra su origen, separados ocho
  ticks, tras una carga inicial de tres segundos y con alcance máximo de 384 bloques.
  Cada impacto produce la misma explosión destructiva; puede destruir arma, terreno
  y entidades cercanas. La interferencia local se traslada temporalmente al origen
  alcanzable y cargado. Si el arma se mueve, responde al
  punto registrado al disparar. No hay lista de mods de cañones: CBC sirve como
  prueba, no como dependencia. Armas que no exponen proyectil/origen pueden carecer
  de esta reacción; el daño sin atribución no se achaca al jugador más cercano.
- Rareza base sorteada entre 180 y 360 minutos ponderados; mínimo real de
  40 minutos activos entre encuentros naturales; duración entre 15 y 40 segundos.
  La primera represalia puede prolongar el encuentro hasta diez segundos más;
  ataques repetidos no prolongan ese plazo.
- Separación de 512 bloques respecto al origen o a las luces de otro encuentro.
  Grupos alejados pueden tener varios encuentros simultáneos. Los testigos cercanos
  comparten avistamiento y descanso; un testigo que llega después también lo comparte.
  Si el testigo seguido muere, sale o se aleja demasiado, se selecciona otro que
  permanezca cerca. La represalia conserva su agresor original y no se transfiere.
- Redstone: pulsos temporales en fuentes cercanas, sin reemplazar bloques.
- Zona de interferencia de 64 bloques de radio desde la posición inicial del
  jugador. Búsqueda por chunks cargados y secciones, con trabajo acotado por tick.
- Redstone Links de Create: el receptor y su indicador parpadean con los pulsos,
  y recuperan la señal actual de su frecuencia al terminar. Create es opcional.
- CC:Tweaked opcional: durante los pulsos se descartan mensajes inalámbricos si
  emisor o receptor están dentro del radio. Incluye módems Ender; una conexión
  remota puede perder mensajes si su extremo local está afectado. No cambia
  canales, contenido, archivos ni programas. La red cableada usa otro recorrido.
- Drenaje: la mitad de encuentros tiene un presupuesto aleatorio,
  hasta 5000 FE por defecto, compartido por todas las luces. Tope del 5 % inicial
  por bloque FE consultado o red AE2. La energía consumida no se devuelve.
- IE, Create Crafts & Additions, AE2, Mekanism y radares son opcionales.
  La extracción FE respeta salidas; AE2 usa su API pública opcional.
- Detección con Advanced Peripherals y Create Radars pendiente de validar en sus
  versiones instaladas. No se simulan jugadores para engañar detectores.
- Comandos para generar, inspeccionar y finalizar. Sin entidades guardadas
  acumulándose al cerrar o descargar chunks.

### Pruebas manuales de la fase 1

Usar una copia desechable con trucos/permisos de operador. Para compilar:
`.\gradlew build`; cliente: `.\gradlew runClient --no-configuration-cache`.
En Windows, `.\gradlew.bat` es el mismo wrapper. Si el lanzador Java de PATH
falla, seleccionar Java 21 para esa sesión, sin modificar archivos del proyecto.

1. En cada preajuste, situarse en el centro y cerca del borde, con cielo visible
   y distancia de simulación suficiente. Ejecutar `/biospheres debug observer`.
   El comando informa coordenadas o explica que no encontró un recorrido cargado.
   Sin argumentos sortea cantidad y variante; ya no fuerza una sola luz.
2. Probar `/biospheres debug observer 3` y luego `5`, terminando el encuentro
   anterior con `/biospheres debug observer stop`. Observar figuras, pausas,
   destellos, profundidad contra vidrio/paredes y desaparición en 15–40 segundos.
3. Usar `/biospheres debug observer status`; distingue luces, encuentros de toda la
   dimensión, encuentros cercanos y hostiles. Comprobar bandera de montaña en
   biomas etiquetados, peso horario con `/time set day`, `sunset`, `midnight` y
   calendario del servidor. Los comandos de tiempo no saltan el descanso mínimo.
4. En un banco de pruebas dentro de 64 bloques de la posición inicial, conectar
   palanca, cable, repetidor y lámpara. Ejecutar
   `/biospheres debug observer interference 5`. Observar pulsos y recuperación
   tras `stop`, final natural y apagado de `enabled`. Verificar cambios
   del jugador durante el encuentro. No probar inicialmente junto a TNT.
   Añadir un transmisor y un receptor Redstone Link de Create: probar el receptor
   inicialmente encendido y apagado, cambiar la entrada y frecuencia durante la
   ráfaga y comprobar recuperación tras `stop` y `enabled = false`. `status`
   indica cuántos receptores recogió el encuentro. El efecto alcanza receptores
   cercanos; no interrumpe todos los enlaces de una frecuencia a distancia.
   Repetir con componentes a 8, 32 y 64 bloques, y fuera del radio. La búsqueda de
   redstone ordinaria avanza desde las secciones cercanas; sus efectos pueden
   comenzar después de los de antenas/máquinas que ya estaban cargadas.
5. Instalar cada mod eléctrico por separado y después juntos. Colocar una batería
   FE con salida habilitada cerca, registrar energía antes/después y comprobar
   límite global y cuota por bloque. Repetir con salida deshabilitada: no debe
   extraer. En AE2, varios cables de una misma red deben compartir su cuota.
   En Mekanism verificar las caras/configuración energética de la versión usada.
   Repetir sin mods opcionales: arranque y encuentro deben funcionar normalmente.
6. Advanced Peripherals: conectar un Environment Detector, usar
   `peripheral.find("environment_detector")` en 1.21.1 y ejecutar
   `scanEntities(rango)` según el alcance configurado. Buscar las entidades por
   coordenadas/UUID, incluso durante destellos apagados. El Player Detector no es
   un detector genérico de entidades.
7. Create Radars: registrar archivo JAR y filtros, incluir entidades del tipo
   `biosphereworlds:observer` si la versión admite ese filtro, comprobar alcance,
   barrido y contactos individuales para 1/3/5 luces. Si no detecta entidades MISC,
   registrar ese resultado: requiere integración específica, no asumir éxito.
8. Con dos jugadores cercanos, comprobar la misma formación y desaparición para
   ambos; ningún segundo encuentro a menos de 512 bloques. Con otro grupo a más
   de 1024 bloques, comprobar dos encuentros independientes. Probar muerte,
   desconexión y alejamiento >1024 de la luz principal: debe continuar con otro
   testigo válido y terminar cuando no quede ninguno. Un participante que viaja
   puede usar `stop` aunque ya no esté junto al origen. Probar recarga del mundo
   y descarga de chunks.
9. Apagar `enabled`: no deben aparecer luces ni efectos ni avanzar el reloj.
   Comprobar ambos preajustes y los tres idiomas. El archivo y el menú solo deben
   mostrar ese booleano; no deben revelar tipos de encuentros, probabilidades,
   fechas, duración, radios ni efectos.
10. En un mundo vanilla, Nether y End, los comandos deben rechazar el mundo y no
    deben aparecer luces ni alterarse señales, energía o generación.
11. Forzar variantes con `/biospheres debug observer watch 3`, `formation 5`,
    `dart 3`, `glass 2`, `flyby 1` y `attack 1`; usar el prefijo completo en cada
    comando y `stop` antes del siguiente. Probar `/biospheres debug observer inside 3`
    y `/biospheres debug observer inside attack 1` en un claro interior. Revisar
    tamaño desde centro/borde, distintos radios, ambos preajustes y varios FOV.
    Las luces deben frenar, observar tres segundos y retirarse ante obstrucciones.
    No deben atravesar vidrio, paredes ni entrar en chunks descargados.
12. Volar con élitras, Aeronautics y Simple Planes, tanto como piloto como pasajero.
    Forzar `flyby` fuera o `inside flyby` dentro: comprobar acompañamiento lateral,
    distancia sostenida, salida acelerada y ausencia de teletransportes al girar o cambiar de altura.
    El sorteo natural favorece acompañamiento al detectar vuelo. El criterio es
    movimiento/altura del jugador, sin depender de que esté montado en una entidad.
13. Probar `attack` con supervivencia, armadura, poca salud, creativo, detrás de
    una pared y volando. Debe avisar y disparar desde lejos; probar una pared
    colocada durante el aviso y alcances superiores a 128 bloques. Comprobar un
    pulso natural, sin empuje ni desactivar élitras. Atacar una luz con arco o arma
    de proyectil en una copia desechable del mundo: la represalia debe fijarse al
    agresor, apuntar tres segundos con haz cálido visible de frente y de lado,
    mostrar llamas/humo y después explotar, pudiendo matar y romper vidrio.
    Revisar aviso, sonido, maniobra de una luz solitaria, resistencia de bloques y
    quemaduras reales e incendios durante la carga y después de explotar.
    Repetir con agua, resistencia al fuego y `doFireTick = false`; la propagación
    debe respetar las reglas del motor. Sacar al agresor antes del
    disparo y comprobar que no daña a otro testigo. Repetir ataques y comprobar
    que no prolongan indefinidamente el encuentro. La interferencia puede afectar
    controles programados de naves al perder mensajes de comunicaciones.
14. En CC:Tweaked enviar mensajes con número consecutivo entre módems dentro/fuera
    de 64 bloques; usar `interference 3`. Revisar pérdidas durante ráfagas y
    recuperación al terminar, con `stop` y al apagar el interruptor. Repetir con
    Ender, turtle y pocket móviles, transmisión desde otra dimensión y red cableada.
    Los mensajes perdidos no se reenvían automáticamente; los programas con timeout
    pueden reaccionar a esa pérdida aunque el efecto ya haya terminado.
15. Con CBC u otro mod de cañones, disparar desde una posición conocida y comprobar
    tres pulsos contra ese punto tras el aviso, conservando distancia. Repetir con
    un lanzador automático sin artillero y con un arma montada. Verificar recuperación
    de sus controles eléctricos, bloques intactos y ausencia de daño a observadores
    cercanos al cañón. Los disparos de daño instantáneo sin proyectil requieren
    comprobar qué atribución ofrece su mod; no se inventa el origen.

La prueba automática opcional de Create se ejecuta con su JAR en `run/mods`:
`.\gradlew runGameTestServer -PgameTestNamespaces=biosphereworlds_observer_create
--no-configuration-cache`. Sin ese mod la prueba informa que se omite. La copia
temporal usada para validar 6.0.8 se retira de `run/mods` al finalizar.

### Estado de las interrupciones

| Interrupción | Implementación | Verificación pendiente |
|---|---|---|
| Redstone local | Pulsos 0/15 y recuperación automática | Circuitos reales; no-op apagado y vanilla comprobados con motor. |
| Redstone Links de Create | Señal recibida e indicador parpadean; conserva frecuencia y recupera entrada actual | Motor 6.0.8 y 6.0.10 aprobados; visual en cliente y otras versiones pendientes. |
| Energía FE | Drenaje aleatorio acotado en salidas extraíbles | Baterías/configuración de IE, Crafts & Additions y Mekanism instalados por el usuario. |
| Energía interna AE2 | Adaptador opcional y cuota por red | Ejecución con AE2 instalado. |
| Módem inalámbrico CC:Tweaked | Pérdida temporal de paquetes por extremo local afectado | Red real 1.120.2 comprobada: frontera, salida Ender, recuperación, apagado y worker; hardware/cliente y otras versiones pendientes. |

La detección por Create Radars y Advanced Peripherals sigue pendiente de probar.
La fase 1 está preparada para pruebas manuales; quedan visibilidad, multijugador
y compatibilidad de los mods opcionales antes de considerarla lista para publicar.
Las estructuras de las fases siguientes continúan sin implementar.

Siguiente entrega del observador, todavía sin implementar: abducciones temporales
de ganado y patrones en trigo como huellas de su actividad. Requiere diseño y OK
propios antes de modificar animales o cultivos; esta revisión solo cambia distancia,
ataque, represalias, contrabatería y continuidad multijugador.

Ante vidrio u otra obstrucción, las luces frenan, observan tres segundos y se
retiran; verifican segmentos de medio bloque para evitar atravesar paredes finas.
Las interiores permanecen dentro de la geometría y las exteriores fuera. El vidrio
solo se rompe si lo alcanza una explosión de represalia. Si la retirada también
está bloqueada, esperan hasta desvanecerse.

La prueba adicional de CC usa su JAR oficial 1.120.2 temporalmente en `run/mods`:
`.\gradlew runGameTestServer -PgameTestNamespaces=biosphereworlds_observer_cc
--no-configuration-cache`. Usa el `WirelessNetwork` real, endpoints de prueba y
paquetes sin alterar; comprueba la frontera 64/65, transmisor afectado, Ender,
lectura desde worker, recuperación, interruptor y no-op vanilla. No equivale a
una prueba de computadoras Lua, hardware móvil o red cableada reales.

Para las comprobaciones nuevas ejecutar por separado:
`runGameTestServer` y `runGameTestServer -PgameTestNamespaces=biosphereworlds_observer`.
La contrabatería con CBC real se reproduce con sus tres JAR en `run/mods` y
`runGameTestServer '-Pneo_version=21.1.228'
'-PgameTestNamespaces=biosphereworlds_observer_cbc,biosphereworlds_observer_create'
--no-configuration-cache` (argumentos entre comillas en PowerShell). Los JAR
temporales se retiran tras las pruebas; no se añaden dependencias al mod ni se
editan las versiones del proyecto.

## Fase 2 — La ventana

**Historia:** Como explorador, quiero descubrir un puesto abandonado cuya silla
y ventana miran hacia una biosfera, para sospechar que alguien observaba a sus
habitantes.

**Estado:** diseño; sin implementar.

- Silla vacía orientada a una biosfera visible.
- Estructura extremadamente rara, con separación de otros encuentros especiales.
- Sin explicación en pantalla y con comando de colocación para pruebas.
- Rareza inicial propuesta: una por cada 1500–2500 biosferas principales.

## Fase 3 — El visitante ausente

**Historia:** Como explorador, quiero encontrar una mesa para dos y una puerta
hacia el vacío en un fragmento flotante, para preguntarme quién estuvo allí.

**Estado:** diseño; sin implementar.

- Mesa, dos sillas vacías y puerta que abre al vacío.
- Sin invadir esferas, lunas o puentes; sin mensajes que resuelvan la escena.
- Comando de colocación para pruebas.
- Rareza inicial propuesta: una por cada 1000–2000 biosferas principales.

## Fase 4 — El mini planeta extraño

**Historia:** Como jugador familiarizado con los ocho planetas, quiero descubrir
una mini esfera adicional en una posición inesperada, para percibir una anomalía.

**Estado:** diseño; sin implementar.

- Adicional a los planetas existentes, con árbol muerto y construcción abandonada.
- Posición extraña sin colisiones. Adaptación al preajuste clásico por diseñar.
- Comando de generación para pruebas.
- Rareza inicial propuesta: uno por cada 800–1500 biosferas principales.

## Fase 5 — La señal

**Historia:** Como explorador, quiero descubrir que tres puestos abandonados
apuntan al mismo punto del vacío, para investigar una relación que nadie explica.

**Estado:** diseño; sin implementar.

- Tres puestos forman un solo encuentro y apuntan al mismo lugar.
- Sin flechas en el juego. El punto puede vincularse con el observador.
- El conjunto más raro, con un comando para generar e inspeccionar los tres.
- Rareza inicial propuesta: uno por cada 4000–6000 biosferas principales.

Las rarezas de estructuras son propuestas, no probabilidades implementadas ni
garantías de una aparición cada cierta distancia. Se revisarán en su propia fase.
