# ============================================================================
# Comandos principales:
#   make release      → test + coverage + build + push
#   make run-remote   → pull de Docker Hub + levanta el stack completo
#   make build        → compila y empaqueta con Gradle
#   make test             → corre los unit tests (sin Docker)
#   make test-integration → corre los tests de integracion (requiere Docker)
#   make docker-build → construye la imagen de Docker
#   make docker-push  → publica la imagen en Docker Hub
#   make up           → levanta el stack completo con docker-compose
#   make down         → detiene y elimina los contenedores
#   make clean-db     → limpia BD y Redis
#   make reset        → limpia BD/Redis + reinicia stack limpio
#   make clean        → limpia artifacts de build
# ============================================================================

DOCKER_REGISTRY  ?= docker.io
DOCKER_NAMESPACE ?= martinbuldain
IMAGE_NAME       ?= tenpo-challenge
IMAGE_TAG        ?= latest
FULL_IMAGE       := $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/$(IMAGE_NAME):$(IMAGE_TAG)

# Base de datos
DB_NAME     ?= tenpo_db
DB_USERNAME ?= tenpo
DB_PASSWORD ?= tenpo_pass

# Redis
REDIS_HOST  ?= redis
REDIS_PORT  ?= 6379

# Aplicación
SPRING_PROFILES_ACTIVE ?= docker,mock
EXTERNAL_SERVICE_URL   ?= http://tenpo-api:8080/mock/percentage
JAVA_OPTS              ?= -Xms256m -Xmx512m

GRADLE           := ./gradlew
COMPOSE          := docker-compose

export PATH := /Applications/OrbStack.app/Contents/MacOS/xbin:$(PATH)

GREEN  := \033[0;32m
YELLOW := \033[1;33m
RED    := \033[0;31m
NC     := \033[0m  # Sin color

# Variables de entorno pasadas a docker compose (sobreescribibles en cada invocacion)
COMPOSE_ENV := \
	DOCKER_IMAGE=$(FULL_IMAGE) \
	DB_NAME=$(DB_NAME) \
	DB_USERNAME=$(DB_USERNAME) \
	DB_PASSWORD=$(DB_PASSWORD) \
	REDIS_HOST=$(REDIS_HOST) \
	REDIS_PORT=$(REDIS_PORT) \
	SPRING_PROFILES_ACTIVE=$(SPRING_PROFILES_ACTIVE) \
	EXTERNAL_SERVICE_URL=$(EXTERNAL_SERVICE_URL) \
	JAVA_OPTS="$(JAVA_OPTS)"

.PHONY: all build test test-integration coverage docker-build docker-push release run-remote up down down-volumes clean-db reset clean logs help

# Default
all: build

# Compila y zipea
build:
	@echo "$(GREEN)▶  Building application...$(NC)"
	$(GRADLE) clean bootJar --no-daemon
	@echo "$(GREEN)✔  Build complete: build/libs/tenpo-challenge-*.jar$(NC)"

# Tests
test:
	@echo "$(GREEN)▶  Running unit tests...$(NC)"
	$(GRADLE) test --no-daemon
	@echo "$(GREEN)✔  Tests complete. Report: build/reports/tests/test/index.html$(NC)"

# Integration tests. Docker debe estar corriendo
test-integration:
	@echo "$(GREEN)▶  Running integration tests (Docker required)...$(NC)"
	$(GRADLE) integrationTest --no-daemon
	@echo "$(GREEN)✔  Integration tests complete. Report: build/reports/tests/integrationTest/index.html$(NC)"

coverage:
	@echo "$(GREEN)▶  Running tests with coverage verification (min 74%)...$(NC)"
	$(GRADLE) check --no-daemon
	@echo "$(GREEN)✔  Coverage report: build/reports/jacoco/test/html/index.html$(NC)"

docker-build: build
	@echo "$(GREEN)▶  Building Docker image: $(FULL_IMAGE)$(NC)"
	docker build \
		--build-arg BUILD_DATE=$(shell date -u +"%Y-%m-%dT%H:%M:%SZ") \
		--build-arg GIT_COMMIT=$(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown") \
		-t $(FULL_IMAGE) \
		-t $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/$(IMAGE_NAME):$(shell git rev-parse --short HEAD 2>/dev/null || echo latest) \
		.
	@echo "$(GREEN)✔  Docker image built: $(FULL_IMAGE)$(NC)"

docker-push: docker-build
	@echo "$(YELLOW)▶  Pushing image to Docker Hub: $(FULL_IMAGE)$(NC)"
	docker push $(FULL_IMAGE)
	docker push $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/$(IMAGE_NAME):$(shell git rev-parse --short HEAD 2>/dev/null || echo latest)
	@echo "$(GREEN)✔  Image pushed: $(FULL_IMAGE)$(NC)"

# Pipeline completo: tests + coverage + build + push
release:
	@echo "$(GREEN)▶  Release pipeline: clean → test → coverage → build → docker push$(NC)"
	$(GRADLE) clean check bootJar --no-daemon
	@echo "$(GREEN)▶  Building Docker image: $(FULL_IMAGE)$(NC)"
	docker build \
		--build-arg BUILD_DATE=$(shell date -u +"%Y-%m-%dT%H:%M:%SZ") \
		--build-arg GIT_COMMIT=$(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown") \
		-t $(FULL_IMAGE) \
		-t $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/$(IMAGE_NAME):$(shell git rev-parse --short HEAD 2>/dev/null || echo latest) \
		.
	@echo "$(YELLOW)▶  Pushing to Docker Hub...$(NC)"
	docker push $(FULL_IMAGE)
	docker push $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/$(IMAGE_NAME):$(shell git rev-parse --short HEAD 2>/dev/null || echo latest)
	@echo "$(GREEN)✔  Release complete: $(FULL_IMAGE)$(NC)"

# Levanta el stack usando la imagen remota (pull + compose up, sin build local)
run-remote:
	@echo "$(GREEN)▶  Pulling $(FULL_IMAGE) from Docker Hub...$(NC)"
	docker pull $(FULL_IMAGE)
	@echo "$(GREEN)▶  Starting full stack with remote image...$(NC)"
	$(COMPOSE_ENV) $(COMPOSE) up -d --wait
	@echo ""
	@echo "$(GREEN)✔  Stack running!$(NC)"
	@echo "   API:       http://localhost:8080"
	@echo "   Swagger:   http://localhost:8080/swagger-ui.html"
	@echo "   Actuator:  http://localhost:8080/actuator/health"

# Docker Compose
up:
	@echo "$(GREEN)▶  Starting full stack (API + PostgreSQL + Redis)...$(NC)"
	$(COMPOSE_ENV) $(COMPOSE) up -d --wait
	@echo ""
	@echo "$(GREEN)✔  Stack running!$(NC)"
	@echo "   API:       http://localhost:8080"
	@echo "   Swagger:   http://localhost:8080/swagger-ui.html"
	@echo "   Actuator:  http://localhost:8080/actuator/health"

up-build: docker-build up

down:
	@echo "$(YELLOW)▶  Stopping stack...$(NC)"
	$(COMPOSE) down
	@echo "$(GREEN)✔  Stack stopped$(NC)"

down-volumes:
	@echo "$(RED)▶  Stopping stack and removing volumes (DATA WILL BE LOST)...$(NC)"
	$(COMPOSE) down -v
	@echo "$(GREEN)✔  Stack stopped and volumes removed$(NC)"

clean-db:
	@echo "$(RED)▶  Cleaning database and Redis (DATA WILL BE LOST)...$(NC)"
	$(COMPOSE) down
	docker volume rm tenpo-challenge_postgres_data tenpo-challenge_redis_data 2>/dev/null || true
	@echo "$(GREEN)✔  Database and Redis cleaned$(NC)"

reset: clean-db up
	@echo ""
	@echo "$(GREEN)✔  Fresh environment ready!$(NC)"

logs:
	$(COMPOSE) logs -f tenpo-api

logs-all:
	$(COMPOSE) logs -f

# Clean
clean:
	@echo "$(YELLOW)▶  Cleaning build artifacts...$(NC)"
	$(GRADLE) clean --no-daemon
	@echo "$(GREEN)✔  Clean complete$(NC)"

# Local development
dev-infra:
	@echo "$(GREEN)▶  Starting infrastructure only (DB + Redis)...$(NC)"
	$(COMPOSE) up -d postgres redis --wait
	@echo "$(GREEN)✔  Infrastructure ready. Run 'make run-local' to start the API$(NC)"

run-local: dev-infra
	@echo "$(GREEN)▶  Starting API locally (profile: mock)...$(NC)"
	$(GRADLE) bootRun --args='--spring.profiles.active=mock' --no-daemon

help:
	@echo ""
	@echo "$(GREEN)Tenpo Challenge – Available Make targets:$(NC)"
	@echo ""
	@echo "  $(YELLOW)release$(NC)        Full pipeline: test + coverage + build + push to Docker Hub"
	@echo "  $(YELLOW)run-remote$(NC)     Pull latest image from Docker Hub and start full stack"
	@echo ""
	@echo "  $(YELLOW)build$(NC)          Compile and package with Gradle"
	@echo "  $(YELLOW)test$(NC)              Run unit tests (no Docker required)"
	@echo "  $(YELLOW)test-integration$(NC) Run integration tests (Docker required)"
	@echo "  $(YELLOW)coverage$(NC)         Run unit tests + verify 74% coverage"
	@echo "  $(YELLOW)docker-build$(NC)   Build Docker image"
	@echo "  $(YELLOW)docker-push$(NC)    Build and push image to Docker Hub"
	@echo "  $(YELLOW)up$(NC)             Start full stack with Docker Compose"
	@echo "  $(YELLOW)up-build$(NC)       Build image and start full stack"
	@echo "  $(YELLOW)down$(NC)           Stop all containers"
	@echo "  $(YELLOW)down-volumes$(NC)   Stop containers and remove data volumes"
	@echo "  $(YELLOW)clean-db$(NC)       Clean database and Redis (remove volumes only)"
	@echo "  $(YELLOW)reset$(NC)          Clean database + Redis and restart stack"
	@echo "  $(YELLOW)logs$(NC)           Follow API logs"
	@echo "  $(YELLOW)dev-infra$(NC)      Start only DB + Redis (for local dev)"
	@echo "  $(YELLOW)run-local$(NC)      Start DB + Redis then run API locally"
	@echo "  $(YELLOW)clean$(NC)          Remove build artifacts"
	@echo ""
	@echo "  Override variables:"
	@echo "  $(YELLOW)DOCKER_NAMESPACE$(NC)       Docker Hub username (default: martinbuldain)"
	@echo "  $(YELLOW)IMAGE_TAG$(NC)              Image tag (default: latest)"
	@echo "  $(YELLOW)DB_NAME$(NC)                Database name (default: tenpo_db)"
	@echo "  $(YELLOW)DB_USERNAME$(NC)            Database user (default: tenpo)"
	@echo "  $(YELLOW)DB_PASSWORD$(NC)            Database password (default: tenpo_pass)"
	@echo "  $(YELLOW)REDIS_HOST$(NC)             Redis host (default: redis)"
	@echo "  $(YELLOW)REDIS_PORT$(NC)             Redis port (default: 6379)"
	@echo "  $(YELLOW)SPRING_PROFILES_ACTIVE$(NC) Active profiles (default: docker,mock)"
	@echo ""
	@echo "  Examples:"
	@echo "  $(YELLOW)make release IMAGE_TAG=1.2.0$(NC)"
	@echo "  $(YELLOW)make reset$(NC)             Clean + restart stack with fresh DB/Redis"
	@echo "  $(YELLOW)make run-remote DB_PASSWORD=prod_secret IMAGE_TAG=1.2.0$(NC)"
	@echo ""
