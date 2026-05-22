# Root convenience Makefile.
# Delegates to stack/makefile so Compose paths and stack/.env stay local to stack/.

STACK_DIR := stack

.PHONY: up down build build-auth build-client build-resource logs logs-auth logs-client logs-resource logs-db ps db-reset restart check

up down build build-auth build-client build-resource logs logs-auth logs-client logs-resource logs-db ps db-reset restart check:
	$(MAKE) -C $(STACK_DIR) -f makefile $@
