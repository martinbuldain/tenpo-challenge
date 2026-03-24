# Tenpo Challenge

## Requisitos
Tener instalado gradle: `brew install gradle`

1. [Resumen](#resumen)
2. [Stack tecnologico](#stack-tecnologico)
3. [Decisiones tecnicas](#decisiones-tecnicas)
4. [Docker Hub](#docker-hub)
5. [Ejecucion](#ejecucion)
6. [Endpoints](#endpoints)
7. [Tests](#tests)
8. [Mejoras no implementadas por falta de tiempo](#mejoras-no-implementadas-por-falta-de-tiempo)
9. [Escalabilidad](#repasando-la-escalabilidad-de-la-solucion)

---

## Resumen

API REST desarrollada con **Spring Boot 3.3 y Java 21** que implementa:

| Funcionalidad | Detalle |
|---|---|
| **Calculo dinamico** | `POST /api/v1/calculate` → `(num1 + num2) + porcentaje` |
| **Cacha distribuida** | Porcentaje cacheado en Redis (TTL 30 min) con fallback permanente |
| **Reintentos** | 3 intentos con backoff exponencial ante fallo del servicio externo |
| **Circuit Breaker** | Resilience4j: abre circuito tras 50% de fallos en ventana de 10 llamadas |
| **Historial** | `GET /api/v1/history` – registro asíncrono en PostgreSQL con paginación |
| **Rate Limiting** | 3 RPM por IP con Bucket4j + Redis (Token Bucket distribuido, multi-réplica) |
| **Manejo de errores** | Respuestas standard para los 4XX y 5XX |

---

## Stack tecnologico

```
Java 21
Spring Boot 3.3.4
├── Spring WebFlux
├── Spring Data R2DBC + Flyway para las migraciones
├── Spring Data Redis Reactive
└── Spring Actuator
Resilience4j 2.2 para Circuit Breaker (resilience4j-reactor)
PostgreSQL para el historial
Redis (cache distribuida + rate limiting + distributed lock)
Gradle + JaCoCo (el proyecto tiene cobertura mínima 74%. Cubre poco por cuestiones de tiempo no llegue a revisar mas casos)
Logs en formato JSON
Springdoc OpenAPI(Swagger)
Testcontainers + MockServer (para tests de integracion)
Docker + Docker Compose
Make para tener los comandos encapsulados de forma mas amigagle de ejecutar
```

---

## Decisiones tecnicas

### 1. Modelo reactivo con WebFlux

Se eligio esta tecnologia porque permite a la aplicacion que ningun thread del event loop se bloquee en operaciones de tipo I/O.

### 2. Cache distribuida en entorno multi replica

**Problema:** Con N replicas hay un problema, y es que cada instancia tiene su propia JVM. Una cache en memoria no es compartida; las replicas podrian llamar al servicio externo de forma independiente.

**Solucion: Redis como cache centralizada con estrategia de dos claves.**

```
percentage:fresh    → TTL 30 min. Si expira, Redis lo elimina automaticamente.
percentage:fallback → Sin TTL. Se actualiza solo cuando el servicio externo responde.
```

### 3. Cache en concurrencia con multi replica

**Problema:** Puede pasar que la cache expire de forma simultanea, entonces multiples replicas detectan el cache miss y todas intentan llamar al servicio externo.

**Solucion: Distributed lock reactivo con Redis `setIfAbsent`.**

`setIfAbsent` es atomico para Redis. Ahi solo una replica toma el lock; las demas leen del fallback.

### 4. Rate limiting distribuido

**Problema:** Un rate limiter local/memoria cuenta solo los requests de su propia replica. Con 3 replicas y limite de 3 RPM por ejemplo, un cliente poria enviar 9 RPM.

**Solucion: Bucket4j**

Bucket4j maneja la atomicidad internamente.

### 5. Retry + circuit breaker reactivos

Se combinan dos patrones complementarios usando operadores Reactor nativos:

| Patrón | Implementación | Propósito |
|---|---|---|
| **Retry** | `Retry.backoff(3, 500ms)` de Reactor | Reintenta ante fallos random (timeout, 5xx) |
| **Circuit Breaker** | `CircuitBreakerOperator.of(cb)` de resilience4j-reactor | Abre el circuito ante fallos recurrentes |

### 6. Paginacion

Spring Data no tiene `Page<T>` reactivo nativo. Por eso se implementa manualmente con `Mono.zip`.

### 7. Logging en formato JSON

Para seguir los mejores estandares en logueo y que sea compatible con ELK y Datadog que permiten explotar este formato en consultas anidadas:

### 8. Arquitectura en capas vs. Hexagonal

Decidi ir por la arquitectura en capas tradicional (`controller → service → repository`) en lugar de arquitectura hexagonal.

Hexagonal agrega valor real cuando hay muchos adapters para el mismo port (Por ejemplo: intercambiar PostgreSQL por Mongo sin tocar el dominio), muchos canales de entrada (REST + gRPC + mensajería), o un dominio complejo que justifique que se aisle completamente del framework. En este caso no aplica ninguna de esas condiciones: solo hay un canal de entrada HTTP, un solo motor de bbdd, y la logica de negocio es una suma con porcentaje.

Lo que si se conserva al menos del "espiritu" hexagonal es el uso de interfaces para los servicios clave (`CalculationService`, `ExternalPercentageService`), que desacoplan las implementaciones y permiten mockearlas en tests sin levantar infraestructura.

### 9. Guardado asíncrono del historial

El historial de llamadas se registra sin bloquear la respuesta al cliente. El flujo es:

1. `CallHistoryFilter` (Order=1) intercepta cada request con el patron **Decorator**: envuelve el `ServerHttpRequest` y el `ServerHttpResponse` para capturar los bodies mientras fluyen (sin consumirlos).
2. Al terminar la cadena de filtros, lanza el guardado **fire-and-forget**: se llama a `historyService.recordCall(...)` y se hace `.subscribe()` sin esperar el resultado.
3. Dentro de `HistoryService`, el `Mono` de guardado se ejecuta en `Schedulers.boundedElastic()`, el scheduler de Reactor para I/O bloqueante, para no ocupar el event loop de Netty.
4. Si el guardado falla (por ejemplo, la bbdd no esta disponible), el error se loguea y se devuelve `Mono.empty()` con `onErrorResume`. El cliente nunca se entera.

Resumiendo: el cliente recibe la respuesta del calculo antes de que el registro termine de persistirse.

### 10. Patrones de diseño aplicados

| Patron | Donde |
|---|---|
| **Circuit Breaker** | `ExternalPercentageServiceImpl` + `CircuitBreakerOperator` (Resilience4j Reactor) |
| **Retry** | `Retry.backoff()` de Reactor |
| **Repository** |  |
| **Strategy** | `ExternalPercentageService` interface que puede cambiar la implementacion |
| **Distributed Lock** | Redis `setIfAbsent` reactivo para evitar cache stampede |
| **Token Bucket** | Bucket4j + Redis para rate limiting distribuido |
| **Decorator** | para capturar body en WebFlux |

---

### 11. Se utilizo IA?

Si, lo use unicamente para:
- embeceller la documentacion en archivos como Make file con colores e identacion
- agregar documentacion con styling para mayor legibilidad, sobre todo en clases y funciones del codigo.
- ayuda en la creacion de un docker file mas robusto en una version, luego de que la mia(v1) funcionara.
- ayuda en la construccion de este README file para mayor legibilidad
- ayuda en cubrir la cobertura del test RateLimitFilterTest ya que hacia caer la cobertura menos del 74%, la use para identificar casos faltantes de test.

En particular use Github Copilot, como agente usando 2 modelos:
 - Claude Haiku para todo lo relacionado a resumenes y documentacion , ya que es suficiente para este tipo de tareas.
 - Claude Sonnet: para la identificacion de test unitarios faltantes.

 ---
 ## Docker Hub

```bash
# Repositorio en Github
https://github.com/martinbuldain/tenpo-challenge

# Imagen publica disponible en:
https://hub.docker.com/r/martinbuldain/tenpo-challenge

# Para descargarla(no es necesario para correrlo, mas abajo proveo un comando que corre en remoto directamente)
docker pull martinbuldain/tenpo-challenge:latest

# Levantar directamente desde Docker Hub (sin clonar el repo):
curl -O https://raw.githubusercontent.com/<repo>/main/docker-compose.yml
docker compose up -d
```
---

## Ejecucion

### Remoto (es necesario tener docker corriendo)
```bash
make run-remote
```

### Local
```bash
git clone <repo-url>
cd tenpo-challenge

# Levanta todo el stack
make up

# La APIs entonces vn a estar en:
#   http://localhost:8080
#   http://localhost:8080/swagger-ui.html
```

### Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/tenpo_db` | URL R2DBC para Spring Data |
| `DB_URL` | `jdbc:postgresql://localhost:5432/tenpo_db` | URL JDBC para Flyway |
| `DB_USERNAME` | `tenpo` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `tenpo_pass` | Password de PostgreSQL |
| `REDIS_HOST` | `localhost` | Host de Redis |
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `EXTERNAL_SERVICE_URL` | `http://localhost:8080/mock/percentage` | URL del servicio externo |

---

## Endpoints

### POST /api/v1/calculate

Calcula `(num1 + num2) + porcentaje`.

**Request:**
```json
POST http://localhost:8080/api/v1/calculate

{
  "num1": 5,
  "num2": 5
}
```

**Response 200:**
```json
{
  "num1": 5,
  "num2": 5,
  "sum": 10.0000,
  "appliedPercentage": 10,
  "result": 11.0000,
  "percentageSource": "EXTERNAL"
}
```

**Errores posibles:**
- `400 Bad Request` – parametros invalidos o nulos
- `429 Too Many Requests` – excede los 3 RPM
- `503 Service Unavailable` – el servicio externo esta caido

---

### GET /api/v1/history

Devuelve el historial paginado de llamadas.

**Request:**
```
GET http://localhost:8080/api/v1/history?page=0&size=10
```

**Response 200:**
```json
{
  "content": [
    {
      "id": 1,
      "endpoint": "/api/v1/calculate",
      "httpMethod": "POST",
      "requestParams": "{\"num1\":5,\"num2\":5}",
      "responseBody": "{\"result\":11.0000,...}",
      "errorMessage": null,
      "httpStatus": 200,
      "status": "SUCCESS",
      "clientIp": "127.0.0.1",
      "createdAt": "2026-01-01T12:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

### Swagger UI

Disponible en `http://localhost:8080/swagger-ui.html` con la documentacion de todos los endpoints.

---

## Tests

```bash
# Corre todos los tests unitarios
make test

# Corre todos los tests de integracion(docker tiene que estar corriendo)
make test-integration
```

---

## Mejoras no implementadas por falta de tiempo

### Idempotencia en POST /api/v1/calculate

`POST /api/v1/calculate` no es idempotente. Si un cliente reintenta tras un timeout de red, el sistema procesa el request dos veces y genera dos registros en el historial.

**Como lo hubiera resuelto:** con un header `Idempotency-Key` (UUID generado por el cliente):
1. Al recibir el request, busco en Redis si ya existe una respuesta guardada para esa key.
2. Si existe, devuelvo la respuesta cacheada sin volver a calcular.
3. Si no existe, hago el calculo, guardo la respuesta en Redis con un TTL razonable, y devuelvo el resultado.

El key en Redis podria ser algo como `idempotency:<client-ip>:<idempotency-key>` para evitar colisiones entre diferentes clientes.

### CORS

No hay configuracion de CORS en la aplicacion. Si la API es consumida desde un frontend en un dominio diferente, los browsers bloquearian los requests por polittiica de mismo origen.

**Como lo hubiera resuelto:** agregar un `CorsWebFilter` en la configuración de WebFlux, definiendo los origenes permitidos, metodos HTTP, y headers expuestos.

No lo implemente porque el challenge no especifica un consumidor frontend, y en un escenario real seguramente CORS se configuraria a nivel de API antes de llegar a la aplicacion.

---

## Repasando la escalabilidad de la solucion

- **Horizontal scaling:** Se pueden agregar replicas ya que el rate limiting y el distributed lock funcionan en entornos multi instancia.
- **Flyway:** Las migraciones de DB son idempotentes. Si N replicas arrancan simultaneamente, Flyway usa un lock de base de datos para que solo una ejecute las migraciones.
- **Non blocking I/O:** Descansamos en que el event loop de Netty gestiona todas las operaciones I/O (como HTTP externo, Redis, PostgreSQL) en forma no bloqueante. El pool de threads se mantiene chiquito aunque la concurrencia sea alta.
- **Circuit Breaker:** Protege al sistema ante fallos del servicio externo, evitando que un servicio que esta caido genere cascadas de timeouts que saturen el sistema.
