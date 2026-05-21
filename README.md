# Coro Pacem Deus Bodas

Aplicacion movil para coordinar la musica de ceremonias de boda entre el
**Coro Pacem Deus** (coro liturgico de Lima) y las parejas que contratan
sus servicios.

Trabajo final del curso **IS276 - Plataformas Moviles** de la UPC
(ciclo 2026-1, profesor Jaiver James Huiza Pereyra).

## Estructura del monorepo

```
PacemDeusBodas/
|-- android/    Cliente Android nativo (Kotlin + Jetpack Compose)
|-- backend/    Backend serverless (AWS Lambda Python 3.13)
```

> El contenido anterior de este repo se conservo en la rama
> [`backup/previo-tb2-20260521-2`](https://github.com/renzonunezaf/PacemDeusBodas/tree/backup/previo-tb2-20260521-2).

## Equipo

- Renzo Nunez Berdejo
- Angelica Romero
- Fabrizio Vera
- Carlos Huallanca
- Neil Hilario

## Stack tecnologico

**Cliente Android:**
- Kotlin 2.0.21 + Jetpack Compose + Material 3
- Google Maps SDK + Maps Compose
- Firebase Cloud Messaging
- SQLite local (HU-06 setlist offline)
- HttpURLConnection + org.json (sin Retrofit, sin Room)

**Backend:**
- AWS Lambda Python 3.13 + API Gateway REST
- AWS RDS SQL Server Express + pyodbc + ODBC Driver 18
- AWS S3 (fotos del local)
- Firebase Cloud Messaging API V1 (push notifications)
- Google Maps Distance Matrix API (motor de movilidad)
- JWT con PyJWT + bcrypt para autenticacion

## Tres roles del sistema

1. **COUPLE (Novios)**: crean su evento, eligen ensamble, arman setlist,
   firman contrato, pagan.
2. **ADMIN (Coordinador del coro)**: aprueba bodas, ve mapa de ubicaciones,
   calendario de disponibilidad, devuelve con anotaciones, firma contratos.
3. **WEDDING_PLANNER**: rol secundario para que la pareja vincule a su
   organizador externo del evento.

## Como ejecutarlo

### Backend
Ya esta desplegado en AWS (cuenta 897054758210, region us-east-1).
Para redesplegarlo desde cero o levantar una copia, ver `backend/README.md`.

### Cliente Android
1. Abrir `android/` en Android Studio Ladybug o posterior.
2. Crear el archivo `android/local.properties` con:
   ```
   sdk.dir=C\:\\ruta\\a\\tu\\Android\\Sdk
   MAPS_API_KEY=<tu_api_key_de_google_maps>
   ```
3. Agregar el archivo `android/app/google-services.json` de tu propio
   proyecto Firebase.
4. Sincronizar Gradle y ejecutar en emulador o device real.

> **Nota de seguridad:** `local.properties` y `google-services.json`
> NO se commitean al repo por contener credenciales. Cada desarrollador
> los crea localmente.
