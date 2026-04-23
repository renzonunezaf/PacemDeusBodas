# Pacem Deus Bodas — API REST (Backend)

## IS276 — Plataformas Móviles y Análisis Cloud
**Grupo 2** · Ciclo 2026-1 · Prof. Jaiver James Huiza Pereyra

---

## Descripción

API REST desarrollada con **AWS Lambda (Python)** + **API Gateway** + **RDS PostgreSQL**
para el sistema de gestión de música litúrgica del Coro Pacem Deus.

Provee los servicios web que consume la aplicación Android nativa (Kotlin).

## Arquitectura

```
┌──────────────┐     HTTPS      ┌───────────────┐     SQL      ┌──────────────┐
│   Android    │ ──────────────► │  API Gateway  │ ───────────► │  RDS         │
│   (Kotlin)   │                 │  + Lambda     │              │  PostgreSQL  │
└──────────────┘                 │  (Python)     │              └──────────────┘
                                 └───────────────┘
```

## Estructura del proyecto

```
pacem-deus-api/
├── README.md                 # Este archivo
├── requirements.txt          # Dependencias Python
├── sql/
│   ├── schema.sql            # Creación de tablas
│   └── seed.sql              # Datos de prueba
├── shared/
│   └── db.py                 # Conexión BD + JWT + utilidades
└── functions/
    ├── auth.py               # Login, registro, perfil
    ├── weddings.py           # Listado, detalle, aprobar, foto
    ├── catalog.py            # Momentos, canciones, instrumentos
    ├── setlist.py            # Gestión del setlist
    └── planner.py            # Dashboard del wedding planner
```

## Endpoints

| Método | Recurso                          | Lambda      | Descripción                    |
|--------|----------------------------------|-------------|--------------------------------|
| POST   | /auth/login                      | auth        | Iniciar sesión                 |
| POST   | /auth/register                   | auth        | Registro (novio o planner)     |
| GET    | /auth/me                         | auth        | Perfil del usuario autenticado |
| GET    | /weddings                        | weddings    | Lista de eventos (por rol)     |
| GET    | /weddings/{id}                   | weddings    | Detalle de un evento           |
| POST   | /weddings/{id}/approve           | weddings    | Aprobar o devolver evento      |
| POST   | /weddings/{id}/photo             | weddings    | Subir foto del local           |
| PUT    | /weddings/{id}/planner           | weddings    | Asignar wedding planner        |
| GET    | /moments                         | catalog     | Momentos litúrgicos            |
| GET    | /songs                           | catalog     | Canciones (filtrar por momento)|
| GET    | /instruments                     | catalog     | Instrumentos disponibles       |
| GET    | /weddings/{id}/setlist           | setlist     | Setlist de un evento           |
| POST   | /weddings/{id}/setlist           | setlist     | Agregar canto al setlist       |
| DELETE | /weddings/{id}/setlist/{itemId}  | setlist     | Quitar canto del setlist       |
| GET    | /planner/weddings                | planner     | Eventos del wedding planner    |
| GET    | /wedding-planners                | weddings    | Lista de planners (admin)      |

## Credenciales de prueba

Todas usan contraseña: `PacemDeus2026!`

| Rol               | Email                        | Nombre           |
|--------------------|------------------------------|------------------|
| Admin              | renzonunez.af@gmail.com      | Renzo Núñez      |
| Admin              | aldo@pacemdeus.com           | Aldo Cárdenas    |
| Wedding Planner    | wedding1@correo.com          | Valeria Campos   |
| Wedding Planner    | wedding2@correo.com          | Daniela Ríos     |
| Wedding Planner    | wedding3@correo.com          | Fernanda López   |
| Novios             | novia1@correo.com            | Carlos & Ana L.  |
| Novios             | novia2@correo.com            | Diego & Sofía    |

## Configuración

### Variables de entorno (Lambda)

```
DB_HOST=tu-rds-endpoint.amazonaws.com
DB_PORT=5432
DB_NAME=pacem_deus
DB_USER=postgres
DB_PASSWORD=tu-password-rds
JWT_SECRET=tu-secreto-jwt
```

### Despliegue

1. Crear instancia RDS PostgreSQL en AWS
2. Ejecutar `sql/schema.sql` y `sql/seed.sql`
3. Crear Lambda Layer con las dependencias (`requirements.txt`)
4. Subir cada archivo de `functions/` como Lambda independiente
5. Configurar API Gateway con los recursos y métodos
6. Agregar las variables de entorno a cada Lambda

## Tecnologías

- **Python 3.12** — Lenguaje del backend
- **AWS Lambda** — Funciones serverless
- **AWS API Gateway** — Exposición de endpoints REST
- **AWS RDS PostgreSQL** — Base de datos relacional
- **psycopg2** — Driver de PostgreSQL para Python
- **bcrypt** — Hashing de contraseñas
- **PyJWT** — Tokens de autenticación
