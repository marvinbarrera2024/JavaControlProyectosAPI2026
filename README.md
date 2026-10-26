# Control Proyectos API

## Requisitos

- Docker con Docker Compose.
- Newman usa temporalmente la contraseña inicial del administrador escrita directamente en `compose.newman.yaml`.

## Arranque básico

Inicia MySQL y la API, sin insertar datos iniciales:

```bash
docker compose up --build -d
```

La base de datos se conserva en el volumen `mysql_data`. Para detener los servicios sin borrar los datos:

```bash
docker compose down
```

## Datos semilla

El servicio de seed crea los roles y el administrador inicial. Úsalo cuando quieras probar la API con ese usuario, por ejemplo, al iniciar una base nueva. No se ejecuta con el arranque básico.

```bash
docker compose -f compose.yaml -f compose.seed.yaml up --build -d
```

El script evita duplicar roles y el usuario `admin` si ya existen. La carga no borra ni reinicia la base de datos.

## Pruebas con Newman

La colección inicia sesión como `admin`. En una base nueva, primero levanta la API y ejecuta el seed en segundo plano:

```bash
docker compose \
  -f compose.yaml \
  -f compose.seed.yaml \
  up --build -d
```

Luego ejecuta Newman por separado:

```bash
docker compose \
  -f compose.yaml \
  -f compose.newman.yaml \
  up --build --abort-on-container-exit --exit-code-from newman
```

Si el administrador ya existe en la base de datos, basta con iniciar la base y la API usando `docker compose up --build -d`. Newman espera a que la API responda antes de ejecutar la colección. Al terminar, los reportes se guardan en `postman/newman.xml` y `postman/newman-report.html`.

Si cambias la contraseña inicial en `database/seed-admin.sql`, actualiza también el valor de `adminPassword` en `compose.newman.yaml`.

## Prueba con k6

El escenario inicia sesión con el administrador semilla, crea 10 categorías y 100 proyectos como preparación, y luego genera 10.000 tareas con 100 usuarios virtuales concurrentes. Los datos quedan en la base; cada ejecución agrega otro lote. En una base nueva, carga el administrador antes de la prueba:

```bash
docker compose -f compose.yaml -f compose.seed.yaml up --build -d
```

Ejecuta la prueba con:

```bash
docker compose \
  -f compose.yaml \
  -f compose.k6-data-load.yaml \
  up --build --abort-on-container-exit --exit-code-from k6-data-load
```

El dashboard en vivo queda disponible en `http://localhost:5665` mientras corre k6. Al finalizar, el reporte HTML se guarda en `k6/reports/data-load-report.html`. Puedes cambiar las cantidades con `K6_VUS`, `K6_CATEGORY_COUNT`, `K6_PROJECT_COUNT` y `K6_TASK_COUNT`. Para detener los contenedores sin borrar los datos persistentes:

```bash
docker compose -f compose.yaml -f compose.k6-data-load.yaml down
```

Para detener y eliminar los contenedores después de las pruebas, sin borrar el volumen de la base de datos:

```bash
docker compose down
```

### Carga de endpoints CRUD

Este escenario inicia sesión como `admin`, recorre los endpoints de categorías, proyectos y tareas, y elimina los registros temporales al finalizar cada iteración. Requiere el administrador semilla; en una base nueva, levanta la API y el seed primero:

```bash
docker compose -f compose.yaml -f compose.seed.yaml up --build -d
```

Ejecuta la carga con:

```bash
docker compose \
  -f compose.yaml \
  -f compose.k6-api.yaml \
  up --build --abort-on-container-exit --exit-code-from k6-api
```

La rampa sube a 100 VUs y mantiene esa carga durante 3 minutos. El dashboard está en `http://localhost:5667` y el reporte en `k6/reports/api-workflow-report.html`. Si se interrumpe el contenedor antes de completar una iteración, podrían quedar datos temporales en la base.

## Análisis de seguridad con OWASP ZAP

El overlay inicia sesión como el administrador semilla, aplica el JWT a las solicitudes y ejecuta el escaneo de API contra OpenAPI en modo seguro (`-S`), sin pruebas activas. En una base nueva, inicia primero la API y carga el administrador:

```bash
docker compose -f compose.yaml -f compose.seed.yaml up --build -d
```

Ejecuta el análisis como tarea puntual:

```bash
docker compose \
  -f compose.yaml \
  -f compose.zap.yaml \
  run --rm --no-deps zap
```

Los reportes HTML y JSON se guardan en `zap/reports/zap-report.html` y `zap/reports/zap-report.json`. Revisa los hallazgos antes de usar cualquier modo de escaneo activo.


## Publicar la imagen en GitHub Container Registry

El nombre sugerido para la imagen es `ghcr.io/bt1141022012/control-proyectos-api`. La imagen contiene la API; MySQL debe ejecutarse por separado.

1. Crea un token de acceso personal (clásico) en GitHub con el permiso `write:packages`. Si GitHub lo solicita, autoriza el token para SSO. No publiques ni compartas este token.
2. Desde la raíz del proyecto, inicia sesión en GHCR. Cuando solicite la contraseña, pega el token de GitHub creado en el paso anterior (no la contraseña de tu cuenta). El token no se mostrará en pantalla:

    ```bash
    docker login ghcr.io -u bt1141022012
    ```

3. Construye la imagen y publícala con una etiqueta de versión y otra `latest` (puedes cambiar `v1.0.0` por la versión que corresponda):

    ```bash
    IMAGE=ghcr.io/bt1141022012/control-proyectos-api
    VERSION=v1.0.0

    docker build -f .dockerfile -t "$IMAGE:$VERSION" .
    docker tag "$IMAGE:$VERSION" "$IMAGE:latest"
    docker push "$IMAGE:$VERSION"
    docker push "$IMAGE:latest"
    ```

4. Para permitir descargas públicas, abre el paquete en GitHub, entra en **Package settings** y cambia la visibilidad a **Public**. Los paquetes nuevos pueden quedar privados inicialmente.
5. Comprueba la publicación descargando una etiqueta:

    ```bash
    docker pull ghcr.io/bt1141022012/control-proyectos-api:v1.0.0
    ```

Para una publicación posterior, repite la construcción y el push con una nueva etiqueta de versión. `latest` es solo otra etiqueta; Docker no la actualiza automáticamente.

## Usar la imagen publicada con Docker Compose

El archivo `compose.published.yaml` descarga `ghcr.io/bt1141022012/control-proyectos-api:v1.0.0`; no compila la API localmente. Clona el repositorio en el equipo donde quieras desplegarla y ejecuta los comandos desde su directorio raíz, donde también está `database/seed-admin.sql`.

El paquete de GHCR debe ser público para descargarlo sin autenticar. Si es privado, inicia sesión en ese equipo con `docker login ghcr.io -u bt1141022012` y pega en el prompt de contraseña un token de GitHub con permiso `read:packages`.

Se incluye un `.env` con valores solo para probar localmente y un `.env.example` como plantilla. El `.env` está excluido de Git. No uses las credenciales de prueba fuera de tu equipo.

Para configurar otro entorno, copia los nombres de `.env.example` a `.env` y reemplaza los valores. Puedes generar contraseñas con `openssl rand -hex 32` y una clave JWT Base64 con `openssl rand -base64 64`:

```dotenv
MYSQL_ROOT_PASSWORD=pon_aqui_una_contrasena_generada
MYSQL_PASSWORD=pon_aqui_otra_contrasena_generada
SECURITY_JWT_SECRET_KEY=pon_aqui_la_clave_base64_generada
```

Levanta la base de datos, el seed y la API:

```bash
docker compose -f compose.published.yaml up -d
```

La API queda disponible en `http://localhost:8080`. El servicio de seed espera a que MySQL esté listo y solo ejecuta `database/seed-admin.sql` si faltan las tablas o alguno de los registros semilla (`ROLE_ADMIN`, `ROLE_USER` o el usuario `admin`). Si ya existen todos, omite el script. El volumen `published_mysql_data` conserva la base al detener los servicios:

```bash
docker compose -f compose.published.yaml down
```