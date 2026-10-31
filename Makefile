# Thin wrappers over Gradle. Memory is capped for small machines; override with GRADLE_ARGS.
GRADLE ?= ./gradlew
GRADLE_ARGS ?= --no-daemon -Dorg.gradle.jvmargs=-Xmx2g

.PHONY: check build test lint run clean

## check: everything CI runs (compile + tests + jar)
check:
	$(GRADLE) $(GRADLE_ARGS) build

build:
	$(GRADLE) $(GRADLE_ARGS) assemble

test:
	$(GRADLE) $(GRADLE_ARGS) test

## lint: no linter is configured yet; compilation warnings are the lint
lint:
	$(GRADLE) $(GRADLE_ARGS) compileJava

## run: local Velocity with the plugin (needs NuVotifier in run/plugins for vote events)
run:
	$(GRADLE) $(GRADLE_ARGS) runVelocity

clean:
	$(GRADLE) $(GRADLE_ARGS) clean
