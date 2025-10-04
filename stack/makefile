# Load .env vars
-include .env
export AUTH_SERVER_IMAGE_TAG CLIENT_SERVER_IMAGE_TAG RESOURCE_SERVER_IMAGE_TAG \
       SUPERUSER_DATABASE_USER SUPERUSER_DATABASE_PASSWORD

AUTH_SERVER_IMAGE_NAME ?= dcrivella/auth-server
CLIENT_SERVER_IMAGE_NAME ?= dcrivella/client-server
RESOURCE_SERVER_IMAGE_NAME ?= dcrivella/resource-server

AUTH_SERVER_IMAGE := $(AUTH_SERVER_IMAGE_NAME):$(AUTH_SERVER_IMAGE_TAG)
CLIENT_SERVER_IMAGE := $(CLIENT_SERVER_IMAGE_NAME):$(CLIENT_SERVER_IMAGE_TAG)
RESOURCE_SERVER_IMAGE := $(RESOURCE_SERVER_IMAGE_NAME):$(RESOURCE_SERVER_IMAGE_TAG)

AUTH_SERVER_DIR := ../auth-server
CLIENT_SERVER_DIR := ../client-server
RESOURCE_SERVER_DIR := ../resource-server

COMPOSE := docker compose

MAKEFLAGS += -rR   # disable built-in rules

.PHONY: up down build build-auth build-client logs ps db-reset restart check

# ------------------------
# Build targets
# ------------------------
build: build-auth build-client build-resource

build-auth:
	@echo "==> Building auth-server image $(AUTH_SERVER_IMAGE)"
	cd $(AUTH_SERVER_DIR) && ./gradlew clean bootBuildImage \
		-PBP_NATIVE_IMAGE=true \
		-Pspring-boot.build-image.imageName=$(AUTH_SERVER_IMAGE) \
		-Pversion=$(AUTH_SERVER_IMAGE_TAG)
	@echo "==> auth-server build complete."

build-client:
	@echo "==> Building client-server image $(CLIENT_SERVER_IMAGE)"
	cd $(CLIENT_SERVER_DIR) && ./gradlew clean bootBuildImage \
		-PBP_NATIVE_IMAGE=true \
		-Pspring-boot.build-image.imageName=$(CLIENT_SERVER_IMAGE) \
		-Pversion=$(CLIENT_SERVER_IMAGE_TAG)
	@echo "==> client-server build complete."

build-resource:
	@echo "==> Building resource-server image $(RESOURCE_SERVER_IMAGE)"
	cd $(RESOURCE_SERVER_DIR) && ./gradlew clean bootBuildImage \
		-PBP_NATIVE_IMAGE=true \
		-Pspring-boot.build-image.imageName=$(RESOURCE_SERVER_IMAGE) \
		-Pversion=$(RESOURCE_SERVER_IMAGE_TAG)
	@echo "==> resouce-server build complete."			

# ------------------------
# Compose targets
# ------------------------
up: 
	@echo "==> Starting Compose stack"
	$(COMPOSE) up -d --remove-orphans
	@echo "==> Stack is up. Run 'make ps' or 'make logs' to inspect."

down:
	@echo "==> Stopping Compose stack"
	$(COMPOSE) down

restart: down up

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

db-reset:
	@echo "!! This will delete DB volumes. Ctrl+C to abort, or wait 3s..."
	@sleep 3
	$(COMPOSE) down -v
	$(COMPOSE) up -d --remove-orphans

check:
	@echo "AUTH_SERVER_IMAGE      : $(AUTH_SERVER_IMAGE)"
	@echo "CLIENT_SERVER_IMAGE    : $(CLIENT_SERVER_IMAGE)"
	@echo "RESOURCE_SERVER_IMAGE  : $(RESOURCE_SERVER_IMAGE)"
	@echo "Compose version        : $$($(COMPOSE) version 2>/dev/null | head -n1 || echo 'not found')"
	@echo "Auth dir exists?       : $$( [ -d $(AUTH_SERVER_DIR) ] && echo yes || echo NO )"
	@echo "Client dir exists?     : $$( [ -d $(CLIENT_SERVER_DIR) ] && echo yes || echo NO )"
	@echo "Resource dir exists?   : $$( [ -d $(RESOURCE_SERVER_DIR) ] && echo yes || echo NO )"
