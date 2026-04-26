# Pacem Deus Bodas

Aplicación Android (Jetpack Compose) con backend Python (Flask/Lambda) para la coordinación musical de ceremonias matrimoniales con el Coro Pacem Deus.

**Curso:** IS276 — Plataformas Móviles y Análisis Cloud  
**Ciclo:** 2026-1 · Grupo 2

---

## Estructura del repositorio

```
Pacem_Deus_Android/
├── PacemDeusBodas/       ← App Android (Kotlin + Jetpack Compose)
├── pacem-deus-api/       ← Backend Python (Flask en local, Lambda en AWS)
├── deploy.ps1            ← Script de despliegue local
└── README.md
```

---

## Requisitos previos

Cada desarrollador debe tener instalado localmente:

- **Android Studio** (última versión estable)
- **PostgreSQL 18** — durante la instalación, recordar la contraseña del usuario `postgres`
- **Python 3.10 o superior** — marcar *Add to PATH* durante la instalación
- **Git** y **GitHub Desktop**

---

## Configuración inicial (solo la primera vez)

### 1. Clonar el repositorio

En GitHub Desktop: *File → Clone repository → PacemDeusBodas*. Se recomienda clonar en `C:\AMD\Pacem_Deus_Android`.

### 2. Crear la base de datos local

Abrir **pgAdmin** (se instala junto con PostgreSQL) y crear una base de datos llamada `pacem_deus_android`.

### 3. Configurar las variables de entorno

Dentro de `pacem-deus-api/`, copiar el archivo `.env.example` como `.env` y reemplazar el valor de `DB_PASSWORD` con la contraseña de PostgreSQL local.

> El archivo `.env` está excluido del repositorio: los valores de cada desarrollador se quedan en su máquina.

### 4. Instalar las dependencias de Python

```powershell
cd pacem-deus-api
pip install -r requirements.txt
```

### 5. Cargar el esquema y los datos de prueba

Desde PowerShell, en la raíz del repositorio:

```powershell
powershell -ExecutionPolicy Bypass -File deploy.ps1
```

El script ejecuta el esquema, carga los datos de prueba y levanta el servidor Flask en `http://localhost:5000`.

### 6. Abrir la app en Android Studio

*File → Open → PacemDeusBodas/*. Esperar a que Gradle sincronice y pulsar *Run*.

---

## Credenciales de prueba

Todos los usuarios comparten la contraseña: `PacemDeus2026!`

| Rol         | Correo                      |
|-------------|-----------------------------|
| Admin       | `renzonunez.af@gmail.com`   |
| Admin       | `aldo@pacemdeus.com`        |
| Planner     | `wedding1@correo.com`       |
| Planner     | `wedding2@correo.com`       |
| Planner     | `wedding3@correo.com`       |
| Novios      | `novia1@correo.com`         |
| Novios      | `novia2@correo.com`         |

---

## Flujo de trabajo con Git

1. Antes de empezar a trabajar: *Fetch origin* en GitHub Desktop.
2. Hacer los cambios en el código.
3. Revisar los cambios en GitHub Desktop (columna izquierda).
4. Escribir un mensaje de commit descriptivo y pulsar *Commit to main*.
5. Pulsar *Push origin* para subir los cambios al repositorio.

> Si al hacer *Push* aparece un mensaje de que el remoto tiene cambios nuevos, primero hay que hacer *Pull* para traer los cambios de los compañeros y luego *Push*.

---

## Arquitectura

**App Android** — Kotlin + Jetpack Compose, arquitectura por capas: `data/` (API Retrofit, Room, SharedPreferences) y `ui/` (composables organizados por rol). Tres roles: Coordinador, Novios y Wedding Planner.

**Backend** — Python con estructura Lambda (handlers en `functions/`, utilidades compartidas en `shared/`). En desarrollo local se simula API Gateway con Flask vía `server.py`. Base de datos PostgreSQL con 12 tablas.

Actualización de prueba #HUCH