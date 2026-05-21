# Pacem Deus Bodas - Backend AWS

Backend serverless para la app movil del Coro Pacem Deus.
Sigue el patron del laboratorio de la Semana 5 (IS276 - Plataformas Moviles UPC)
extendido para soportar el dominio completo del proyecto.

**Stack**: AWS RDS SQL Server Express + Lambda Python 3.13 + pyodbc + API Gateway + S3 + Firebase FCM
**Patron**: Stored Procedures para CRUD + Lambdas con naming `<verbo>_<entidad>_<accion>.py`

> **Linea base inventariada el 2026-05-17** (ver el vault Obsidian: `01-Proyectos/PacemDeusAndroid/inventario/INVENTARIO-v01-2026-05-17.md`).
> 21 tablas, 62 SPs, 1 Lambda con 51 handlers internos, 23 archivos SQL, 19 pantallas Android.

---

## Estructura del proyecto

```
pacem-deus-api/
|- README.md
|- requirements.txt
|- .gitignore
|- .pre-commit-config.yaml
|
|- infra/sql/                       23 archivos: 11 originales + 12 migraciones (m04-m15)
|  |
|  |- Originales del checkpoint v01 (11)
|  |- 01_schema.sql                 21 tablas T-SQL con CHECK constraints + FKs
|  |- 02_procs.sql                  SPs base (CRUD por entidad)
|  |- 03_seed_pricing.sql           Configuracion precios (single-row)
|  |- 04_seed_instruments.sql       9 instrumentos del coro
|  |- 05_seed_moments.sql           14 momentos liturgicos
|  |- 06_seed_seasons.sql           5 temporadas con restricciones JSON
|  |- 07_seed_season_dates.sql      Fechas 2025-2027
|  |- 08_seed_songs.sql             250 cantos del repertorio
|  |- 09_seed_song_moments.sql      321 pares cancion-momento
|  |- 10_seed_song_requirements.sql 1092 requerimientos instrumentales
|  |- 11_seed_song_styles.sql       124 estilos
|  |
|  |- Migraciones posteriores (12)
|  |- m04_planners_seed.sql         3 planners de ejemplo
|  |- m05_fotos_caption_autor.sql   Agrega caption + autor a boda_foto
|  |- m07_sps_aux_obtener.sql       SPs auxiliares de consulta
|  |- m08_disponibilidad.sql        Calculo de disponibilidad por mes
|  |- m09_marker_seed.sql           Marker para identificar filas de demo
|  |- m10_anotaciones_xl.sql        Tabla boda_anotacion + SPs (modulo en standby)
|  |- m11_fixes_checkpoint_v01.sql  Fix usp_instrumentos_listar + surge_*
|  |- m12_movilidad_v2.sql          Refactor movilidad (curva exponencial + tramos)
|  |- m13_limpiar_planners_duplicados.sql
|  |- m14_notificaciones.sql        Tabla notificacion + SPs (in-app)
|  |- m15_fcm_tokens.sql            Agrega fcm_token a usuario
|  \- seed_99_bodas_demo.sql        24 bodas demo para QA
|
|- lambdas/                         57 archivos .py (1 router + 51 handlers + 5 shared)
|  |
|  |- lambda_function.py            Router maestro. Recibe el event de API Gateway
|  |                                y enruta segun path + httpMethod a uno de los
|  |                                51 handlers internos.
|  |- pdf_builder.py                Helper para generar PDFs (contrato, setlist).
|  |
|  |- shared/                       Codigo de la layer pacem-deus-shared
|  |  |- db.py                      pyodbc con env vars + add_output_converter
|  |  |                             para DATETIMEOFFSET (-155)
|  |  |- responses.py               Helpers API Gateway con CORS unificado
|  |  |- auth.py                    JWT decode/encode + bcrypt verify
|  |  |- pricing.py                 Motor de precios v02 (curva exponencial,
|  |  |                             surge fuera-Lima, vehiculo XL, traffic minutes)
|  |  |- seasons.py                 Logica de temporadas liturgicas (JSON)
|  |  |- distance.py                Google Distance Matrix + Haversine fallback
|  |  |- notifications.py           Helper para crear notificacion in-app
|  |  \- push.py                    Helper para enviar push FCM via Firebase Admin
|  |
|  |- auth/                         4 handlers
|  |  |- post_auth_login.py
|  |  |- post_auth_registrar.py
|  |  |- get_auth_me.py
|  |  \- put_auth_fcm_token.py
|  |
|  |- catalogo/                     4 handlers publicos
|  |  |- get_instrumentos_listar.py
|  |  |- get_momentos_listar.py     (aplica restricciones de temporada)
|  |  |- get_canciones_listar.py    (filtra por compatibilidad con instrumentos)
|  |  \- get_planners_publico.py    (para que la pareja elija)
|  |
|  |- bodas/                        28 handlers (la entidad central)
|  |  |  CRUD basico de la boda
|  |  |- get_bodas_listar.py
|  |  |- get_boda_obtener.py
|  |  |- post_boda_crear.py
|  |  |- put_boda_editar.py
|  |  |- post_bodas_cotizar.py
|  |  |  Maquina de estados
|  |  |- post_boda_enviar.py        DRAFT -> SUBMITTED
|  |  |- post_boda_desenviar.py     SUBMITTED -> DRAFT
|  |  |- post_boda_cancelar.py      -> CANCELLATION_REQUESTED
|  |  |  Fotos del local (S3)
|  |  |- post_boda_foto.py          (legacy: campo boda.foto_local_url)
|  |  |- post_boda_foto_agregar.py  (actual: tabla boda_foto)
|  |  |- get_boda_fotos.py
|  |  |- put_boda_foto_caption.py
|  |  |- delete_boda_foto.py
|  |  |  Instrumentos y setlist
|  |  |- put_boda_instrumentos.py   (recalcula precio_instrumentos)
|  |  |- get_boda_precio.py         (con what-if via query)
|  |  |- get_setlist_listar.py
|  |  |- post_setlist_agregar.py
|  |  |- delete_setlist_quitar.py
|  |  |  Contrato (doble firma)
|  |  |- get_contrato_obtener.py
|  |  |- get_boda_contrato_pdf.py
|  |  |- post_contrato_firmar.py
|  |  |- get_boda_setlist_pdf.py
|  |  |  Planner y anotaciones
|  |  |- post_boda_planner_couple.py    (pareja elige planner)
|  |  |- get_boda_anotacion_pendiente.py
|  |  |- post_boda_anotacion_responder.py
|  |  |  Validacion y agenda
|  |  |- post_validar_conflicto.py
|  |  |- get_disponibilidad_mes.py
|  |  \- get_mapa_bodas_mes.py
|  |
|  |- planner/                      1 handler
|  |  \- get_planner_bodas.py
|  |
|  |- admin/                        8 handlers (solo rol ADMIN)
|  |  |- get_pricing_obtener.py
|  |  |- put_pricing_actualizar.py
|  |  |- get_planners_listar.py
|  |  |- put_boda_planner.py
|  |  |- post_boda_aprobar.py
|  |  |- post_boda_devolver_anotaciones.py
|  |  |- get_pagos_listar.py
|  |  \- post_pago_crear.py
|  |
|  \- notifications/                2 handlers (modulo m14)
|     |- get_notifications_poll.py
|     \- post_notification_mark_read.py
|
\- scripts/                         10 scripts PowerShell + 2 hooks pre-commit
   |  Flujo de checkpoint (corre en orden)
   |- 0_aplicar_checkpoint.ps1      Orquestador maestro: corre los 4 pasos
   |- 1_aplicar_sql.ps1             Aplica migraciones SQL via SqlClient
   |- 2_empaquetar.ps1              Empaqueta Layer + Lambda en dist/
   |- 3_desplegar_aws.ps1           Publica layer + sube codigo + reconfigura
   |- 4_verificar.ps1               Smoke tests post-deploy via API
   |
   |  Utilidades
   |- load_database.ps1             Bootstrap inicial (esquema + seeds desde cero)
   |- package_all.ps1               Empaqueta todas las lambdas
   |- package_lambda.ps1            Empaqueta una sola lambda
   |- package_layer.ps1             Empaqueta la layer shared
   |
   \- hooks/                        Hooks invocados por pre-commit
      |- validate_sql_go.ps1        Valida GO entre CREATE/ALTER PROC
      \- validate_lambda_naming.ps1 Valida convencion <verbo>_<entidad>_<accion>
```

---

## Endpoints del API

Todos bajo el stage `v1`. Base URL: `https://57qk0t3z61.execute-api.us-east-1.amazonaws.com/v1`.
JWT en header `Authorization: Bearer <token>` excepto para los endpoints publicos.

**Routing real:** API Gateway tiene una sola ruta `/{proxy+}` que recibe todo y delega
a la Lambda `pacem-deus-api`. El routing path -> handler ocurre dentro de
`lambda_function.py`. Eso significa que agregar un endpoint NO requiere reconfigurar
API Gateway; solo agregar el handler nuevo y deployar la Lambda.

### Auth (4)

- `POST /v1/auth/login`
- `POST /v1/auth/registrar`
- `GET  /v1/auth/me`              (requiere token)
- `PUT  /v1/auth/fcm-token`       (requiere token, m15)

### Catalogo publico (4)

- `GET  /v1/instrumentos`
- `GET  /v1/momentos?fecha=YYYY-MM-DD`     (filtrado por temporada)
- `GET  /v1/canciones?id_momento=&criterio=&idioma=&instrumentos=`
- `GET  /v1/planners`

### Bodas (28)

- `GET    /v1/bodas`                              (filtrado por rol)
- `POST   /v1/bodas/cotizar`                      (cotizar sin crear)
- `POST   /v1/bodas`                              (crear)
- `GET    /v1/bodas/{id_boda}`
- `PUT    /v1/bodas/{id_boda}`                    (editar)
- `POST   /v1/bodas/{id_boda}/enviar`             (DRAFT -> SUBMITTED)
- `POST   /v1/bodas/{id_boda}/desenviar`          (SUBMITTED -> DRAFT)
- `POST   /v1/bodas/{id_boda}/cancelar`           (solicitar cancelacion)
- `POST   /v1/bodas/{id_boda}/foto`               (legacy)
- `POST   /v1/bodas/{id_boda}/fotos`              (actual)
- `GET    /v1/bodas/{id_boda}/fotos`
- `PUT    /v1/bodas/{id_boda}/fotos/{id_foto}/caption`
- `DELETE /v1/bodas/{id_boda}/fotos/{id_foto}`
- `PUT    /v1/bodas/{id_boda}/instrumentos`       (recalcula precios)
- `GET    /v1/bodas/{id_boda}/precio`             (con what-if via query)
- `GET    /v1/bodas/{id_boda}/setlist`
- `POST   /v1/bodas/{id_boda}/setlist`
- `DELETE /v1/bodas/{id_boda}/setlist/{id_setlist}`
- `GET    /v1/bodas/{id_boda}/contrato`
- `GET    /v1/bodas/{id_boda}/contrato/pdf`
- `POST   /v1/bodas/{id_boda}/contrato/firmar`    (doble firma -> CONTRACTED)
- `GET    /v1/bodas/{id_boda}/setlist/pdf`
- `POST   /v1/bodas/{id_boda}/planner`            (pareja elige planner)
- `GET    /v1/bodas/{id_boda}/anotacion-pendiente`
- `POST   /v1/bodas/{id_boda}/anotacion/responder`
- `POST   /v1/bodas/validar-conflicto`
- `GET    /v1/bodas/disponibilidad?anio=&mes=`
- `GET    /v1/bodas/mapa?anio=&mes=`

### Planner (1)

- `GET    /v1/planner/bodas`                      (sus bodas asignadas)

### Admin (8, solo rol ADMIN)

- `GET    /v1/admin/pricing`
- `PUT    /v1/admin/pricing`
- `GET    /v1/admin/planners`
- `PUT    /v1/admin/bodas/{id_boda}/planner`
- `POST   /v1/admin/bodas/{id_boda}/aprobar`
- `POST   /v1/admin/bodas/{id_boda}/devolver-anotaciones`
- `GET    /v1/admin/bodas/{id_boda}/pagos`
- `POST   /v1/admin/bodas/{id_boda}/pagos`

### Notificaciones (2, m14)

- `GET    /v1/notifications/poll`
- `POST   /v1/notifications/{id}/mark-read`

**Total: ~51 endpoints.**

---

## Setup en AWS Academy (paso a paso)

### Prerrequisitos

- Cuenta AWS Academy con credito disponible
- Windows + PowerShell (los scripts estan probados ahi)
- SSMS (incluye sqlcmd): https://aka.ms/ssmsfullsetup
- Python 3.13 + pip
- Archivo `pyodbc313.zip` (lo dio el profesor en Recursos_Semana_5)

### 1. Crear instancia RDS SQL Server Express

1. AWS Console -> RDS -> Create database
2. Engine: **Microsoft SQL Server**
3. Edition: **SQL Server Express Edition**
4. Templates: **Sandbox** (free tier, no production)
5. DB instance identifier: `pacem-deus-db`
6. Master username: `admin`
7. Master password: anota una password segura
8. Instance: `db.t3.micro` (free tier)
9. Storage: 20 GB gp3
10. Connectivity: **Public access = YES**, crea SG `pacem-deus-sg`
11. Database name: dejar por defecto, crearemos la BD via SSMS
12. Create database (espera ~10 min)

### 2. Abrir puerto 1433 al mundo (solo desarrollo)

1. RDS -> tu instancia -> Connectivity & security -> click en el security group
2. Inbound rules -> Edit -> Add rule:
   - Type: **MSSQL**
   - Source: `0.0.0.0/0` (cualquiera, solo desarrollo)
3. Save rules

### 3. Crear la base de datos via SSMS

1. Abre SSMS, conecta al endpoint RDS:
   - Server: `pacem-deus-db.xxxxx.us-east-1.rds.amazonaws.com`
   - Authentication: SQL Server Authentication
   - Login: `admin`
   - Password: la que anotaste
   - **Connection Properties -> Trust server certificate: marcar**
2. Una vez conectado, en Object Explorer:
   - New Query
   - Ejecutar: `CREATE DATABASE pacem_deus_bodas; GO`
   - Cerrar y volver a conectar usando esta BD por defecto

### 4. Cargar esquema, procs y seeds

```powershell
$env:DB_PWD = "tu-password-rds"

powershell -ExecutionPolicy Bypass -File .\scripts\load_database.ps1 `
    -DbServer "pacem-deus-db.xxxxx.us-east-1.rds.amazonaws.com" `
    -DbName "pacem_deus_bodas" `
    -DbUser "admin"
```

Cargara los **11 SQLs originales en orden** (schema, procs y seeds basicos). Las
12 migraciones posteriores (`m04..m15`) deben aplicarse despues con el orquestador
de checkpoint (siguiente seccion) o manualmente via SSMS.

### 5. Crear bucket S3 para fotos

1. AWS Console -> S3 -> Create bucket
2. Bucket name: `pacem-deus-fotos` (debe ser unico global)
3. Region: la misma que el RDS
4. Block all public access: **desmarcar** (necesitamos URLs publicas)
5. Bucket Versioning: Disabled
6. Create bucket
7. Permissions -> Bucket Policy:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Sid": "PublicReadGetObject",
       "Effect": "Allow",
       "Principal": "*",
       "Action": "s3:GetObject",
       "Resource": "arn:aws:s3:::pacem-deus-fotos/*"
     }]
   }
   ```

### 6. Crear las dos Lambda Layers

#### Layer A: pyodbc (la que dio el profesor)

1. AWS Console -> Lambda -> Layers -> Create layer
2. Name: `pyodbc313`
3. Upload zip: `pyodbc313.zip` (de Recursos_Semana_5)
4. Compatible architectures: `x86_64`
5. Compatible runtimes: `Python 3.13`
6. Anota el ARN

#### Layer B: shared (nuestro codigo + bcrypt + PyJWT)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package_layer.ps1
```

Sube el `dist\pacem-deus-shared-layer-vN.zip`:
- Name: `pacem-deus-shared`
- Runtime: `Python 3.13`

### 7. Generar el JWT_SECRET

```powershell
$bytes = New-Object byte[] 64
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

Anota ese string. Sera el JWT_SECRET de la Lambda.

### 8. Crear la Lambda (UNA sola)

A diferencia del patron clasico del profesor (una Lambda por endpoint), este
proyecto usa **una sola Lambda monolitica** llamada `pacem-deus-api` que
internamente enruta a 51 handlers. Trade-off conocido: un solo deploy + costos
minimos, a cambio de cold start global y sin throttling fino por endpoint.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package_all.ps1
```

Esto empaqueta TODA la app en `dist\pacem-deus-api-vN.zip` (~70 KB).

Pasos en AWS Console:

1. Lambda -> Create function -> Author from scratch
2. Function name: `pacem-deus-api`
3. Runtime: Python 3.13
4. Architecture: x86_64
5. Permissions -> Use existing role: **LabRole**
6. Create function

Una vez creada:

7. Code source -> Upload from -> .zip file -> sube `dist\pacem-deus-api-vN.zip`
8. Configuration -> Layers -> Add layer (dos veces):
   - Custom layer -> `pyodbc313`
   - Custom layer -> `pacem-deus-shared`
9. Configuration -> Environment variables -> Edit:

| Key | Value |
|-----|-------|
| `DB_SERVER` | endpoint de tu RDS |
| `DB_DATABASE` | `pacem_deus_bodas` |
| `DB_UID` | `admin` |
| `DB_PWD` | tu password |
| `JWT_SECRET` | el secreto generado en paso 7 |
| `JWT_EXP_HOURS` | `168` |
| `S3_BUCKET` | `pacem-deus-fotos` |
| `GOOGLE_MAPS_API_KEY` | tu API key (opcional, sin esto usa Haversine) |

10. Configuration -> General configuration -> Edit:
    - Memory: **512 MB**
    - Timeout: **30 segundos**

> **Nota de seguridad** (deuda registrada): las credenciales viven aqui como
> variables de entorno y son visibles para cualquiera con permiso
> `lambda:GetFunctionConfiguration`. Para production real conviene migrar a
> AWS Secrets Manager o Parameter Store con SecureString.

### 9. Crear el API Gateway REST

1. AWS Console -> API Gateway -> Create API -> REST API -> Build
2. API name: `pacem-deus-api`
3. Endpoint Type: Regional

Configurar **una sola ruta** que captura todo:

- En el resource root `/`, crear resource con path `{proxy+}` y marcado como **proxy resource**
- En `/{proxy+}`, crear methods:
  - `ANY` con integration type **Lambda function** + Lambda proxy integration **YES**
  - `OPTIONS` para CORS preflight

Habilitar CORS: Actions -> Enable CORS.

### 10. Deploy del API

1. Actions -> Deploy API
2. Stage: crear `v1`
3. Anota la **Invoke URL**: `https://xxxxx.execute-api.us-east-1.amazonaws.com/v1`

### 11. Probar

```powershell
# Registrar novios
$body = @{
    rol = "COUPLE"
    email = "test@test.com"
    password = "<password-de-prueba>"
    nombreNovio = "Carlos"
    nombreNovia = "Ana"
    documentoNovio = "12345678"
    documentoNovia = "87654321"
    telefono = "999999999"
    comoSeEntero = "REFERIDO"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "https://xxxxx.execute-api.us-east-1.amazonaws.com/v1/auth/registrar" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

Debe devolver `token` y `usuario`.

### 12. Apagar RDS cuando no se use

Para no quemar credito: RDS -> tu instancia -> Actions -> **Stop temporarily**.
Se detiene hasta 7 dias sin cobrar. Cuando vuelvas, Start.

---

## Flujo de checkpoint (deploy iterativo)

Una vez que el setup inicial esta hecho, los cambios se aplican via el
**orquestador maestro** que corre los 4 pasos en orden:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\0_aplicar_checkpoint.ps1
```

Este script:

1. Hace pre-flight: verifica archivos clave del checkpoint.
2. Resetea `$LASTEXITCODE` para evitar falsos positivos heredados de sesiones previas
   (sub-scripts son cmdlets .NET puros y nunca lo reescriben).
3. Corre en orden:
   - `1_aplicar_sql.ps1`     (aplica migraciones SQL via SqlClient nativo)
   - `2_empaquetar.ps1`      (empaqueta Layer + Lambda en dist/)
   - `3_desplegar_aws.ps1`   (publica layer + sube codigo + reconfigura)
   - `4_verificar.ps1`       (smoke tests post-deploy via API)
4. Si algun paso falla, aborta limpio con mensaje claro.

Todos los sub-scripts son **idempotentes**: re-correrlos no rompe nada.

---

## Modulos por estado

### Activos en produccion

| Modulo | Notas |
|---|---|
| Auth (JWT + bcrypt) | login, registrar, me, fcm-token |
| Catalogo | instrumentos, momentos, canciones, planners |
| Bodas (CRUD completo) | maquina de estados completa, fotos, setlist, contrato |
| Pricing v02 | curva exponencial, surge fuera-de-Lima, vehiculo XL, traffic minutes |
| Disponibilidad + Mapa | m08 y m09 |
| FCM push (m15) | tokens guardados, push para cambios de estado de boda |

### Implementados pero sin uso real (a confirmar para TB2)

| Modulo | Estado actual |
|---|---|
| Pagos (admin) | tabla `pago` vacia, 2 endpoints disponibles |
| Anotaciones (m10) | tabla `boda_anotacion` vacia, 3 endpoints disponibles |
| Notificaciones in-app (m14) | tabla `notificacion` con 1 fila, sistema operativo pero poco trigger |

---

## Reglas de negocio clave

### Maquina de estados de Boda

```
DRAFT --> SUBMITTED --> APPROVED --> CONTRACTED --> COMPLETED
        ^             ^                                   ^
        |             |                                   |
        \_____________v____________________________________|
        CANCELLATION_REQUESTED (desde APPROVED o CONTRACTED)
```

### Pricing v02 (modelo m12)

Toda la logica esta en `lambdas/shared/pricing.py`. Lee de la tabla
`configuracion_precios` via `usp_pricing_obtener` + `usp_tramo_movilidad_listar`.
Para cambiar precios, NO toques el codigo: edita la tabla con `PUT /v1/admin/pricing`.

Componentes del calculo:

- **Paquete base**: precio fijo cuando la boda es dentro del radio Lima.
- **Instrumentos adicionales**: por cada instrumento elegido que no este
  `incluido_en_paquete_base`, suma `precio_instrumento_adicional`.
- **Movilidad (curva exponencial)**: si la distancia supera `mov_distancia_libre_km`,
  aplica `factor ^ mov_curva_exponente` segun el tramo (tabla `tramo_movilidad`).
  Topa en `mov_tope_movilidad`.
- **Traffic surcharge**: si `duration_in_traffic - duration > mov_traffic_umbral_min`,
  suma `(diff - umbral) * mov_traffic_tarifa_minuto`.
- **Surge fuera-de-Lima**: si esta fuera del radio, factor escalonado segun
  cuanto fuera (hasta `surge_fuera_lima_factor_max`).
- **Vehiculo XL**: si grupo > `movilidad_xl_umbral_pasajeros`, factor multiplicativo
  `movilidad_xl_factor`.
- **Redondeo final** al S/.10 hacia arriba.

> **Nota tecnica**: `configuracion_precios` aun mantiene columnas legacy del modelo
> v01 (cols 2-22) que ya no se usan. Son legacy borrables pero conservadas hasta
> proximo refactor.

### Tiempos liturgicos

`lambdas/shared/seasons.py` aplica 4 modificadores en JSON:

- `oculto_en`: el momento no aparece
- `mostrado_solo_en`: solo aparece en estas temporadas
- `deshabilitado_en`: aparece grayed con razon
- `momentos_deshabilitados` (en la temporada): tambien aplica

Resultado practico:

- Cuaresma + Adviento -> Gloria deshabilitado
- Cuaresma -> Aleluya oculto, Aclamacion del Evangelio aparece en su lugar

### Compatibilidad canto-instrumento

Cada canto tiene `MINIMUM` (sin esto no suena) y `OPTIMAL` (con esto suena ideal).
Cuando la pareja elige instrumentos, el catalogo marca `compatible=false` para
los cantos cuyos MINIMUM no estan cubiertos.

### Doble fuente de verdad: foto del local

Por compatibilidad con clientes viejos coexisten:

- **Legacy**: `boda.foto_local_url` (columna unica). Setteada por `post_boda_foto.py`.
- **Actual**: tabla `boda_foto` (multiples fotos por boda, con `caption`,
  `creado_por_id_usuario`, S3 key, etc.). Manipulada por `post_boda_foto_agregar.py`,
  `get_boda_fotos.py`, `put_boda_foto_caption.py`, `delete_boda_foto.py`.

Pendiente de unificacion en proximo refactor.

---

## Variables de entorno (resumen)

| Variable | Requerida | Default | Descripcion |
|----------|-----------|---------|-------------|
| `DB_SERVER` | Si | - | Endpoint RDS |
| `DB_DATABASE` | Si | - | Nombre BD (`pacem_deus_bodas`) |
| `DB_UID` | Si | - | Usuario SQL Server |
| `DB_PWD` | Si | - | Password |
| `JWT_SECRET` | Si | - | Secreto random para firmar JWT |
| `JWT_EXP_HOURS` | No | 168 | Validez del token (7 dias) |
| `S3_BUCKET` | Si | - | `pacem-deus-fotos` |
| `GOOGLE_MAPS_API_KEY` | No | - | Sin esto usa Haversine (sin trafico) |

---

## Buenas practicas aplicadas

- **Cero hardcoded**: precios y parametros vienen todos de `configuracion_precios`
- **Cero parches**: SPs separan CRUD de logica de negocio
- **Conexion pyodbc cacheada** entre warm starts
- **JWT** con expiracion configurable, **bcrypt** cost 12
- **CORS** unificado en helper, sin repeticion
- **Permisos por rol** verificados en cada handler
- **Errores con codigos HTTP** consistentes (400/401/403/404/409/500)
- **Stored procedures** para CRUD; logica HTTP/calculo en Python (Distance Matrix, Haversine, JWT, pricing)
- **Comentarios en espanol** explicando intencion, no descripciones obvias
- **Hooks pre-commit**: gitleaks (secrets), check-yaml/json/merge-conflict,
  validacion local de `GO` en SQL y de naming en Lambdas

---

## Decisiones arquitectonicas vs el laboratorio del profesor

Mantenemos el patron del profesor (RDS SQL Server, pyodbc, stored procedures,
naming `<verbo>_<entidad>_<accion>.py`) con tres divergencias justificadas:

1. **Conexion via env vars en lugar de hardcoded**: el profesor mostro la conexion
   hardcodeada por simplicidad pedagogica. Aqui usamos variables de entorno de
   Lambda, lo cual es la practica estandar y NO requiere recompilar para cambiar
   credenciales.

2. **JWT + bcrypt para autenticacion**: el laboratorio no incluyo auth porque el
   ejemplo era un catalogo publico. Esta app tiene 3 roles diferenciados (ADMIN,
   COUPLE, WEDDING_PLANNER) con permisos distintos por endpoint, asi que JWT +
   bcrypt son necesarios.

3. **Una sola Lambda con routing interno** en lugar de una Lambda por endpoint.
   Trade-off conocido:
   - Ventaja: un solo deploy, costos minimos, conexion DB compartida entre handlers.
   - Costo: cold start global, sin throttling fino por endpoint.

Todo lo demas (motor BD, driver Python, layer pyodbc, naming convention de
archivos, patron stored procedures + Lambda como orquestador, mapping templates
en API Gateway) sigue exactamente el laboratorio.

---

## Troubleshooting comun

### "Cannot connect to database"

- Security Group con puerto 1433 abierto?
- RDS en estado **Available**?
- Las env vars apuntan al endpoint correcto (sin tilde, sin espacios)?
- Encrypt=yes y TrustServerCertificate=yes en la connection string

### "ImportError: cannot import name 'shared'"

- La layer `pacem-deus-shared` esta montada?
- Estructura del zip: `python/shared/__init__.py`, no `shared/__init__.py`

### "ImportError: pyodbc"

- La layer `pyodbc313` esta montada?
- Runtime Python 3.13 (no 3.12 ni 3.11)

### "InvalidSignatureException" en JWT

- `JWT_SECRET` identico en TODAS las invocaciones? Cualquier diferencia rompe la firma.
  Como la Lambda es unica, basta con verificarlo una vez.

### CORS error en el cliente

- API Gateway tiene CORS habilitado en `/{proxy+}` para `OPTIONS`?
- Despues redeploy del stage v1

### Cold start de 5+ segundos

- Normal con pyodbc + RDS. La conexion se cachea entre invocaciones del mismo
  container (warm starts ~100ms). Para reducir, usar Provisioned Concurrency
  (cuesta extra) o un keepalive con CloudWatch Events cada 5 min.

### `0_aplicar_checkpoint.ps1` aborta diciendo que un paso fallo, pero el paso parece haber corrido bien

- Es el bug del `$LASTEXITCODE` heredado. El master ya tiene el fix (v3): resetea
  `$global:LASTEXITCODE = 0` antes de cada `& $scriptPath`. Si el script que
  estas corriendo no tiene ese fix, actualizalo.

---

## Que viene despues del backend

- App Android Compose conectandose a estos endpoints (Volley, no Retrofit)
- Refactor a capas data/domain/ui (rubrica TB2 pide capas explicitas)
- CameraX para subir foto del local
- Maps SDK + GPS para mostrar ubicacion
- Intent ACTION_DIAL para llamar al planner
- Intent SHARE WhatsApp para compartir confirmacion de boda
- Notificaciones locales (FCM ya conectado)
