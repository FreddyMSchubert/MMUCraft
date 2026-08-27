PYTHON ?= python
COMPOSE := docker compose
MC := minecraft/main
SERVICE_NAME = $(if $(filter mc,$(SERVICE)),minecraft,$(SERVICE))

.DEFAULT_GOAL := start

ifeq ($(OS),Windows_NT)
GRADLEW := gradlew.bat
NULL := NUL
else
GRADLEW := ./gradlew
NULL := /dev/null
endif

RUNNING_SERVICES = $(shell $(COMPOSE) ps --status running --services 2>$(NULL))
check-service = $(if $(SERVICE),,$(error SERVICE is required. Valid values: api web mc velocity))$(if $(filter api web mc velocity,$(SERVICE)),,$(error Unknown SERVICE '$(SERVICE)'. Valid values: api web mc velocity))
check-running = $(if $(filter $(1),$(RUNNING_SERVICES)),,$(error Service '$(1)' is not running. Start Docker and then run make))
check-stack = $(if $(filter-out $(RUNNING_SERVICES),api web minecraft velocity),$(error One or more services are not running. Start Docker and then run make))

.PHONY: start restart stop logs shell console db-generate db-check db-studio

start:
	$(PYTHON) $(MC)/stage_item_data.py --root $(MC)
	cd $(MC)/mod && $(GRADLEW) generateProto runDatagen build
	cd $(MC)/mod && $(GRADLEW) -p ../../../services/velocity/plugin build
	$(PYTHON) $(MC)/respack/build-main-pack.py
	$(COMPOSE) --profile minecraft up --build

restart:
	$(check-stack)
	$(COMPOSE) --profile minecraft restart

stop:
	$(COMPOSE) down

logs:
	$(check-service)
	$(call check-running,$(SERVICE_NAME))
	$(COMPOSE) logs --tail all --follow $(SERVICE_NAME)

shell:
	$(check-service)
	$(call check-running,$(SERVICE_NAME))
	$(COMPOSE) exec $(SERVICE_NAME) sh

console:
	$(call check-running,minecraft)
	$(COMPOSE) --profile minecraft attach minecraft

db-generate:
	$(call check-running,api)
	$(COMPOSE) exec api npm run db:generate

db-check:
	$(call check-running,api)
	$(COMPOSE) exec api npm run db:check

db-studio:
	$(call check-running,api)
	$(COMPOSE) exec api npm run db:studio
