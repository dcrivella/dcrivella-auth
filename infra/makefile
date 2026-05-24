# This Makefile is a small convenience tool for the dcrivella-auth stack.
# It keeps the repeated Gradle module image-build commands and Docker Compose
# lifecycle commands in one place for the three independent service modules
# that make up the dcrivella-auth Gradle project: auth-server, client-server,
# and resource-server.

# Load Compose .env vars
-include compose/.env
export AUTH_SERVER_IMAGE_TAG CLIENT_SERVER_IMAGE_TAG RESOURCE_SERVER_IMAGE_TAG \
       SUPERUSER_DATABASE_USER SUPERUSER_DATABASE_PASSWORD

AUTH_SERVER_IMAGE_NAME ?= dcrivella/auth-server
CLIENT_SERVER_IMAGE_NAME ?= dcrivella/client-server
RESOURCE_SERVER_IMAGE_NAME ?= dcrivella/resource-server
K3D_CLUSTER_NAME ?= dcrivella-auth
K8S_NAMESPACE ?= dcrivella-auth

AUTH_SERVER_IMAGE := $(AUTH_SERVER_IMAGE_NAME):$(AUTH_SERVER_IMAGE_TAG)
CLIENT_SERVER_IMAGE := $(CLIENT_SERVER_IMAGE_NAME):$(CLIENT_SERVER_IMAGE_TAG)
RESOURCE_SERVER_IMAGE := $(RESOURCE_SERVER_IMAGE_NAME):$(RESOURCE_SERVER_IMAGE_TAG)

ROOT_DIR := ..
COMPOSE_DIR := compose
K3D_CONFIG := k3d/cluster-config.yaml
K8S_OVERLAY := k8s/overlays/local
POSTGRES_INIT_SQL := db/init/001-provision_auth.sql
K8S_POSTGRES_INIT_SQL := $(K8S_OVERLAY)/001-provision_auth.sql

COMPOSE := docker compose --env-file $(COMPOSE_DIR)/.env -f $(COMPOSE_DIR)/compose.yml -f $(COMPOSE_DIR)/compose.override.yml

MAKEFLAGS += -rR   # disable built-in rules

.PHONY: up down build build-auth build-client build-resource logs logs-auth logs-client logs-resource logs-db ps db-reset restart check compose-up compose-down compose-build-up compose-restart compose-logs compose-logs-auth compose-logs-client compose-logs-resource compose-logs-db compose-ps compose-db-reset compose-check k3d-up k3d-build-up k3d-cluster-up k3d-cluster-down k3d-cluster-stop k3d-cluster-start k3d-load k3d-deploy k3d-logs k3d-logs-auth k3d-logs-client k3d-logs-resource k3d-logs-db k3d-ps k3d-db-reset k3d-check

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

compose-up: up
compose-down: down
compose-build-up: build up
compose-restart: restart
compose-logs: logs
compose-logs-auth: logs-auth
compose-logs-client: logs-client
compose-logs-resource: logs-resource
compose-logs-db: logs-db
compose-ps: ps
compose-db-reset: db-reset
compose-check: check

# ------------------------
# k3d / Kubernetes targets
# ------------------------
k3d-build-up: build k3d-up

k3d-up: k3d-cluster-up k3d-load k3d-deploy

k3d-cluster-up:
	@echo "==> Creating k3d cluster $(K3D_CLUSTER_NAME) if needed"
	@mkdir -p $$HOME/.k3d-dcrivella-auth/data
	@if k3d cluster list | grep -q "^$(K3D_CLUSTER_NAME)[[:space:]]"; then \
		echo "==> k3d cluster $(K3D_CLUSTER_NAME) already exists"; \
	else \
		k3d cluster create --config $(K3D_CONFIG); \
	fi
	kubectl config use-context k3d-$(K3D_CLUSTER_NAME)

k3d-cluster-down:
	@echo "==> Deleting k3d cluster $(K3D_CLUSTER_NAME)"
	k3d cluster delete $(K3D_CLUSTER_NAME)

k3d-cluster-stop:
	@echo "==> Stopping k3d cluster $(K3D_CLUSTER_NAME)"
	k3d cluster stop $(K3D_CLUSTER_NAME)

k3d-cluster-start:
	@echo "==> Starting k3d cluster $(K3D_CLUSTER_NAME)"
	k3d cluster start $(K3D_CLUSTER_NAME)
	kubectl config use-context k3d-$(K3D_CLUSTER_NAME)

k3d-load:
	@echo "==> Importing application images into k3d"
	k3d image import $(AUTH_SERVER_IMAGE) $(CLIENT_SERVER_IMAGE) $(RESOURCE_SERVER_IMAGE) -c $(K3D_CLUSTER_NAME)

k3d-deploy:
	@echo "==> Applying Kubernetes manifests"
	cp $(POSTGRES_INIT_SQL) $(K8S_POSTGRES_INIT_SQL)
	kubectl apply -k $(K8S_OVERLAY)
	kubectl -n $(K8S_NAMESPACE) set image deployment/auth-server auth-server=$(AUTH_SERVER_IMAGE)
	kubectl -n $(K8S_NAMESPACE) set image deployment/client-server client-server=$(CLIENT_SERVER_IMAGE)
	kubectl -n $(K8S_NAMESPACE) set image deployment/resource-server resource-server=$(RESOURCE_SERVER_IMAGE)
	kubectl -n $(K8S_NAMESPACE) rollout status statefulset/db --timeout=180s
	kubectl -n $(K8S_NAMESPACE) rollout status deployment/auth-server --timeout=180s
	kubectl -n $(K8S_NAMESPACE) rollout status deployment/resource-server --timeout=180s
	kubectl -n $(K8S_NAMESPACE) rollout status deployment/client-server --timeout=180s
	@echo "==> k3d stack is up. Open http://localhost:8080"

k3d-logs:
	kubectl -n $(K8S_NAMESPACE) logs -f --all-containers=true --max-log-requests=20 -l app

k3d-logs-auth:
	kubectl -n $(K8S_NAMESPACE) logs -f deployment/auth-server

k3d-logs-client:
	kubectl -n $(K8S_NAMESPACE) logs -f deployment/client-server

k3d-logs-resource:
	kubectl -n $(K8S_NAMESPACE) logs -f deployment/resource-server

k3d-logs-db:
	kubectl -n $(K8S_NAMESPACE) logs -f statefulset/db

k3d-ps:
	kubectl -n $(K8S_NAMESPACE) get pods,svc,pvc

k3d-db-reset:
	@echo "!! This deletes the Postgres PVC in k3d. Ctrl+C to abort, or wait 3s..."
	@sleep 3
	kubectl -n $(K8S_NAMESPACE) delete statefulset db --ignore-not-found
	kubectl -n $(K8S_NAMESPACE) delete pvc -l app=db --ignore-not-found
	cp $(POSTGRES_INIT_SQL) $(K8S_POSTGRES_INIT_SQL)
	kubectl apply -k $(K8S_OVERLAY)
	kubectl -n $(K8S_NAMESPACE) rollout status statefulset/db --timeout=180s

k3d-check:
	@echo "K3D_CLUSTER_NAME       : $(K3D_CLUSTER_NAME)"
	@echo "K8S_NAMESPACE          : $(K8S_NAMESPACE)"
	@echo "K3D config             : $(K3D_CONFIG)"
	@echo "K8s local overlay      : $(K8S_OVERLAY)"
	@echo "Postgres init SQL      : $(POSTGRES_INIT_SQL)"
	@echo "k3d version            : $$(k3d --version 2>/dev/null | head -n1 || echo 'not found')"
	@echo "kubectl client         : $$(kubectl version --client=true 2>/dev/null | head -n1 || echo 'not found')"
	@echo "kubectl kustomize      : $$(kubectl kustomize --help >/dev/null 2>&1 && echo yes || echo NO)"
