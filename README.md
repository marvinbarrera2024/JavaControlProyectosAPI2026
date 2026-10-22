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