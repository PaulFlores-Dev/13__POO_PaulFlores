# APRAQ Desktop

Aplicación de escritorio para la Asociación de Productores Agrarios de Quilmaná. Permite gestionar productores, clientes, solicitudes comerciales, ventas, acopio e inventario desde una interfaz JavaFX conectada a MySQL.

El proyecto utiliza Java con nivel de compilación 17, JavaFX 17.0.10, FXML, CSS, Maven Wrapper, JDBC, MySQL Connector/J 8.4.0 y BCrypt. La persistencia se implementa con SQL parametrizado y transacciones JDBC.

## Funcionalidades actuales

| Módulo | Operaciones disponibles |
|---|---|
| Inicio | Indicadores de solicitudes, cotizaciones, ventas y stock físico, reservado y disponible. |
| Catálogos | Crear y editar categorías y productos; eliminación lógica y restauración; consulta de ubicaciones geográficas. |
| Productores | Crear y editar productores y campañas; eliminación lógica y restauración de productores. |
| Clientes | Crear y editar empresas, personas y contactos; eliminación lógica y restauración de empresas. |
| Solicitudes web | Consultar solicitudes y sus productos, asignar responsables y actualizar el estado. |
| Cotizaciones | Crear ofertas con líneas por lote y actualizar su estado comercial. |
| Ventas | Registrar ventas directas o desde cotizaciones aceptadas, confirmar reservas y cancelar ventas sin despachos. |
| Acopio | Recibir lotes y generar su movimiento de ingreso en una misma transacción. |
| Inventario | Consultar saldos, movimientos y reservas; registrar ajustes de entrada o salida. |
| Despachos | Registrar entregas parciales o completas vinculadas a una venta y sus reservas. |
| Noticias | Crear y editar contenido con estados de borrador, publicación y archivo. |
| Usuarios | Crear y editar cuentas, roles y contraseñas; eliminación lógica y restauración de usuarios. |
| Reportes | Buscar y exportar en CSV UTF-8 solicitudes, cotizaciones, ventas, despachos, inventario y productores. |

Las consultas de MySQL se ejecutan en una cola de trabajo en segundo plano. Las actualizaciones de la interfaz regresan al hilo JavaFX.

## Requisitos

- JDK 17 o 21. La compilación genera bytecode Java 17 y el proyecto también se ha verificado con JDK 21.
- Docker Desktop o Docker Engine con Docker Compose para la base de datos local.
- Conexión a Internet para descargar Maven y las dependencias en la primera ejecución.

Maven Wrapper está incluido; no es necesario instalar Maven globalmente. `JAVA_HOME`, si está definido, debe apuntar a la carpeta del JDK. Los ejecutables `java` y `javac` deben estar disponibles en `PATH`.

## Inicio en Windows con PowerShell

Ejecute los comandos desde la raíz del proyecto.

### 1. Comprobar Java y Maven

```powershell
java -version
javac -version
.\mvnw.cmd --version
```

### 2. Preparación de la configuración local

- Docker Compose lee `.env`.
- La aplicación Java lee las variables de entorno del proceso y después `local.properties`. No carga `.env` por sí misma.
- Si cambia la base de datos, el usuario, la contraseña o el puerto, mantenga ambos archivos coherentes.

La configuración de desarrollo incluida utiliza:

```properties
DB_HOST=127.0.0.1
DB_PORT=3307
DB_NAME=apraq
DB_USER=apraq_app
DB_PASSWORD=apraq_local_change_me
DB_SSL_MODE=DISABLED
```

### 3. Levantar MySQL y cargar los datos

```powershell
docker compose up -d --wait
docker compose ps -a
```

Compose publica MySQL en `127.0.0.1:3307`, salvo que se cambie `DB_PORT` en `.env`. La base y los datos se conservan en el volumen `apraq_mysql_data`.

### 4. Ejecutar la aplicación

```powershell
.\mvnw.cmd javafx:run
```

La aplicación abre una ventana de escritorio nativa. Docker ejecuta MySQL y la carga de datos; JavaFX se ejecuta directamente en la computadora.

En macOS o Linux, cree los mismos archivos locales y utilice `./mvnw` en lugar de `.\mvnw.cmd`:

```bash
./mvnw javafx:run
```

Para detener los servicios conservando la base de datos:

```powershell
docker compose down
```

## Base de datos y datos de demostración

El proyecto incluye tres scripts SQL:

| Archivo | Contenido |
|---|---|
| [dF_apraq_2.0_create.sql](dF_apraq_2.0_create.sql) | Tablas, índices, claves foráneas y restricciones del modelo. |
| [dF_apraq_2.0_ubigeo_peru.sql](dF_apraq_2.0_ubigeo_peru.sql) | Catálogo de 1 892 ubicaciones UBIGEO. |
| [dF_apraq_2.0_seed_demo.sql](dF_apraq_2.0_seed_demo.sql) | Catálogos, campaña, productor, cliente, contactos, usuarios y operaciones de ejemplo. |

En una base nueva, el servicio `mysql` ejecuta los tres scripts en ese orden. El esquema se crea únicamente cuando el volumen está vacío. El servicio `mysql-seed` aplica además los scripts de UBIGEO y demostración cuando se ejecuta, por lo que permite cargar esos datos en un volumen que ya tenga el esquema.

Los scripts de datos evitan duplicar su conjunto de demostración mediante claves únicas y comprobaciones de existencia. Al repetir la carga pueden actualizar campos y estados de esos registros de ejemplo; la carga no constituye una migración del esquema.

Compose habilita `IGNORE_SPACE` porque el DDL utiliza espacios entre nombres de funciones SQL y sus paréntesis, por ejemplo `trim (`.

La demostración incluye diez solicitudes web, una cotización aceptada, una venta con reserva y un despacho parcial. En una instalación limpia, el lote de ejemplo ingresa 1 000 kg, compromete 100 kg y despacha 40 kg: quedan 960 kg físicos, 60 kg reservados pendientes y 900 kg disponibles.

Los archivos `.env`, `local.properties`, los almacenes de certificados y los resultados de compilación están excluidos mediante `.gitignore`.

## Acceso y permisos

El acceso consulta los usuarios almacenados en MySQL, verifica la contraseña con BCrypt y exige que la cuenta esté activa. También admite las cuentas nuevas creadas desde **Usuarios**.

Las cuentas iniciales de demostración son:

| Usuario | Contraseña | Acceso |
|---|---|---|
| `admin` | `admin123` | Todos los módulos, incluida la administración de usuarios. |
| `sales` | `sales123` | Inicio, clientes, solicitudes web, cotizaciones, ventas y reportes. |
| `warehouse` | `warehouse123` | Inicio, productores, acopio, inventario, despachos y reportes. |
| `editor` | `editor123` | Inicio y noticias. |

La carga SQL incluye estas cuentas. Al preparar el inicio de sesión, la aplicación también crea las cuentas iniciales que falten, junto con sus personas y contactos; no reemplaza las cuentas existentes.

Para registrar personal, ingrese como administrador, cree primero la persona y su contacto en **Clientes** y después utilice **Usuarios → Nuevo usuario**. Al editar una cuenta, una contraseña vacía conserva la actual. Cada operación registra el identificador de la cuenta autenticada cuando el modelo lo requiere.

## Eliminación lógica y restauración

Las tablas de **Categorías**, **Productos**, **Productores**, **Empresas** y **Usuarios** permiten crear, consultar, editar, eliminar lógicamente y restaurar registros.

1. El filtro **Estado** muestra **Activos** al ingresar. Puede cambiarse a **Inactivos** o **Todos** y combinarse con la búsqueda.
2. El icono de tacho de cada fila cambia su `status` de `active` a `inactive`, conservando el identificador, los datos y las relaciones históricas.
3. En **Inactivos** o **Todos**, el icono de restaurar cambia ese mismo registro a `active`.

Los registros inactivos quedan fuera de sus selectores para nuevas operaciones. El selector de productos también excluye los productos de categorías inactivas. Las cuentas inactivas no pueden iniciar sesión, y la cuenta de la sesión actual no puede desactivarse desde la interfaz. La operación de cambio de estado verifica los permisos en la capa de persistencia.

Estas acciones utilizan los estados existentes del esquema. Campañas, solicitudes, cotizaciones, ventas, reservas y noticias conservan sus propios estados de negocio. Personas, contactos y ubicaciones no tienen un campo de disponibilidad en el modelo actual. Los movimientos y despachos conservan su historial transaccional.

## Flujo operativo

1. Revise o registre categorías, productos, productores, campañas, empresas, personas y contactos.
2. En **Acopio**, reciba un lote indicando productor, producto, campaña, fecha y peso. Se crea también su movimiento `receipt`.
3. En **Solicitudes web**, revise una solicitud y asigne un responsable.
4. En **Cotizaciones**, agregue líneas con lotes, cantidades y precios. La cotización no modifica el stock.
5. En **Ventas**, registre una venta directa o basada en una cotización aceptada. La venta queda pendiente.
6. Seleccione **Confirmar** para validar el stock disponible y crear las reservas en una transacción.
7. En **Despachos**, seleccione la venta, la reserva y la cantidad a entregar. Se admiten entregas parciales.
8. Consulte **Inventario** para verificar el saldo físico, las reservas pendientes y el disponible. Una entrega completa cumple la reserva y permite completar la venta.

## Reglas de inventario y transacciones

- El saldo físico se calcula exclusivamente desde los movimientos: ingresos y ajustes positivos menos despachos y ajustes negativos. El peso original del lote no se suma nuevamente.
- La reserva pendiente corresponde a la cantidad acordada menos lo despachado en reservas activas.
- Las confirmaciones, cancelaciones, ajustes y despachos bloquean los lotes en orden ascendente mediante `SELECT ... FOR UPDATE`.
- Un ajuste de salida no puede reducir el stock físico por debajo de las reservas pendientes.
- Acopio y despacho guardan sus registros y movimientos relacionados con `commit` o `rollback` conjunto.
- Una venta con despachos no puede cancelarse; cancelar una venta elegible libera sus reservas.
- Recepciones, ventas, ajustes y despachos utilizan claves de operación estables para evitar duplicados al reintentar.
- Cantidades y precios se manejan con `BigDecimal`.

## Estructura del proyecto

```text
.mvn/wrapper/                 Configuración de Maven Wrapper
src/main/java/vallegrande/edu/pe/
  concurrent/                Cola de trabajo de base de datos
  config/                    Configuración, conexiones y composición de dependencias
  controller/                Acceso, navegación y eventos FXML
  dto/                       Formularios de operaciones transaccionales
  exception/                 Errores y mensajes para la interfaz
  model/                     Sesión, entidades de estado y filas de consulta
  repository/                Contratos de persistencia
  repository/jdbc/           Consultas y escrituras JDBC
  security/                  Hash y verificación de contraseñas
  service/                   Reglas y coordinación de transacciones
  view/                      Módulos, tablas y formularios JavaFX
src/main/resources/          FXML, CSS e imágenes
src/test/java/               Pruebas unitarias, de interfaz y de integración
.env.example                 Ejemplo de configuración local
mvnw / mvnw.cmd              Ejecutables de Maven Wrapper
pom.xml                      Dependencias y configuración de compilación
```

## Compilación y pruebas

Para compilar y ejecutar las pruebas unitarias en PowerShell:

```powershell
.\mvnw.cmd clean test
```

En macOS o Linux utilice `./mvnw clean test`. Los resultados se guardan en `target/surefire-reports`. Las pruebas que requieren interfaz o MySQL se habilitan de forma explícita.

### Pruebas de las tablas JavaFX

Comprueban los botones de cada fila, los filtros, la eliminación y restauración, la cuenta protegida y las tablas de solo consulta. No necesitan MySQL:

```powershell
$env:APRAQ_UI_TEST = 'true'
.\mvnw.cmd '-Dtest=DataTablePaneTest' test
Remove-Item Env:APRAQ_UI_TEST
```

Generan una vista de verificación en `target/ui-status-actions.png`.

### Integración de consultas, usuarios e interfaz

`APRAQ_INTEGRATION_TEST=true` habilita `JdbcReadSmokeTest`, `JavaFxViewSmokeTest` y `UiSupportThreadTest`. Las pruebas de repositorio y carga de vistas usan la configuración `DB_*` o `local.properties`. Deben apuntar a una base de pruebas con el esquema del proyecto: preparan cuentas iniciales y comprueban la creación, edición y eliminación de una cuenta temporal.

### CRUD contra una base temporal

`JdbcStatusIntegrationTest` verifica la creación, edición, eliminación lógica y restauración de las cinco entidades con estado de disponibilidad. Requiere una base creada previamente con `dF_apraq_2.0_create.sql` y el modo SQL `IGNORE_SPACE`.

Se habilita exclusivamente cuando `APRAQ_STATUS_JDBC_URL` apunta a `jdbc:mysql://127.0.0.1:<puerto>/apraq_status_test`, junto con las variables `APRAQ_STATUS_DB_USER` y `APRAQ_STATUS_DB_PASSWORD`. No lee `local.properties`, no crea el contenedor ni la base y deja sus registros de prueba en esa base temporal.

## Alcance del modelo

- Las solicitudes web se consultan y gestionan desde el escritorio; su recepción no se implementa como un servidor web dentro de esta aplicación.
- El esquema no incluye pagos, facturación electrónica ni devoluciones.
- Los movimientos publicados se corrigen con ajustes justificados; la interfaz no elimina movimientos ni despachos.
- La carga de demostración sirve para recorrer los módulos con datos de ejemplo. Los datos operativos reales se gestionan desde la aplicación o mediante una carga independiente.
