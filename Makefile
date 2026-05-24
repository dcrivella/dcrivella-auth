# Root convenience Makefile.
# Delegates to infra/makefile so local runtime paths stay in infra/.

INFRA_DIR := infra

.PHONY: up down build build-auth build-client build-resource logs logs-auth logs-client logs-resource logs-db ps db-reset restart check compose-up compose-down compose-build-up compose-restart compose-logs compose-logs-auth compose-logs-client compose-logs-resource compose-logs-db compose-ps compose-db-reset compose-check k3d-up k3d-build-up k3d-cluster-up k3d-cluster-down k3d-cluster-stop k3d-cluster-start k3d-load k3d-deploy k3d-logs k3d-logs-auth k3d-logs-client k3d-logs-resource k3d-logs-db k3d-ps k3d-db-reset k3d-check

up down build build-auth build-client build-resource logs logs-auth logs-client logs-resource logs-db ps db-reset restart check compose-up compose-down compose-build-up compose-restart compose-logs compose-logs-auth compose-logs-client compose-logs-resource compose-logs-db compose-ps compose-db-reset compose-check k3d-up k3d-build-up k3d-cluster-up k3d-cluster-down k3d-cluster-stop k3d-cluster-start k3d-load k3d-deploy k3d-logs k3d-logs-auth k3d-logs-client k3d-logs-resource k3d-logs-db k3d-ps k3d-db-reset k3d-check:
	$(MAKE) -C $(INFRA_DIR) -f makefile $@
