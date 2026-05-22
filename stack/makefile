# This Makefile is a small convenience tool for the dcrivella-auth stack.
# It keeps the repeated Gradle module image-build commands and Docker Compose
# lifecycle commands in one place for the three independent service modules
# that make up the dcrivella-auth Gradle project: auth-server, client-server,
# and resource-server.

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

ROOT_DIR := ..

COMPOSE := docker compose

MAKEFLAGS += -rR   # disable built-in rules

.PHONY: up down build build-auth build-client build-resource logs logs-auth logs-client logs-resource logs-db ps db-reset restart check

# ------------------------
# Build targets
# ------------------------
build: build-auth build-client build-resource

build-auth:
	@echo "==> Building auth-server image $(AUTH_SERVER_IMAGE)"
	cd $(ROOT_DIR) && ./gradlew :auth-server:clean :auth-server:bootBuildImage \
		-PBP_NATIVE_IMAGE=true \
		-Pspring-boot.build-image.imageName=$(AUTH_SERVER_IMAGE) \
		-Pversion=$(AUTH_SERVER_IMAGE_TAG)
	@echo "==> auth-server build complete."

build-client:
	@echo "==> Building client-server image $(CLIENT_SERVER_IMAGE)"
	cd $(ROOT_DIR) && ./gradlew :client-server:clean :client-server:bootBuildImage \
		-PBP_NATIVE_IMAGE=true \
		-Pspring-boot.build-image.imageName=$(CLIENT_SERVER_IMAGE) \
		-Pversion=$(CLIENT_SERVER_IMAGE_TAG)
	@echo "==> client-server build complete."

build-resource:
	@echo "==> Building resource-server image $(RESOURCE_SERVER_IMAGE)"
	cd $(ROOT_DIR) && ./gradlew :resource-server:clean :resource-server:bootBuildImage \
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

logs-auth:
	$(COMPOSE) logs -f auth-server

logs-client:
	$(COMPOSE) logs -f client-server

logs-resource:
	$(COMPOSE) logs -f resource-server

logs-db:
	$(COMPOSE) logs -f db

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
	@echo "Root Gradle wrapper?   : $$( [ -x $(ROOT_DIR)/gradlew ] && echo yes || echo NO )"
	@echo "Auth module exists?    : $$( [ -d $(ROOT_DIR)/auth-server ] && echo yes || echo NO )"
	@echo "Client module exists?  : $$( [ -d $(ROOT_DIR)/client-server ] && echo yes || echo NO )"
	@echo "Resource module exists?: $$( [ -d $(ROOT_DIR)/resource-server ] && echo yes || echo NO )"
