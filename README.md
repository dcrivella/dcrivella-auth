# dcrivella-auth
Spring Boot Authorization Server with GraalVM

This project provides an **OAuth2 Authorization Server** (with OIDC support) built on Spring Boot, with support for running on both a normal JVM and as a **GraalVM native image**.

## Project Structure
- **auth-server** → Spring Authorization Server (OIDC, OAuth2 flows)
- **client-server** → Example web client (PKCE, Thymeleaf UI)
- **resource-server** → Example API protected by JWT
- **stack** → Docker Compose stack (Postgres + app services)
- **postman** → Postman collection and environment

```text
dcrivella-auth/
├─ auth-server/
├─ client-server/
├─ resource-server/
├─ postman/
│  ├─ dcrivella-auth.postman_collection.json
│  └─ dcrivella-auth-LOCAL.postman_environment.json
└─ stack/
   ├─ compose.yaml                # base stack (db, auth, client, resource)
   ├─ compose.override.yaml       # dev overrides: ports, profiles
   ├─ .env                        # env vars used by compose files
   ├─ makefile                    # helper commands for build/run
   └─ db/
      └─ init/
         └─ 001-provision_auth.sql
```

## Prerequisites

### Docker & Docker Compose v2
- macOS/Windows: Docker Desktop includes Compose v2

- Linux: install Docker Engine + Compose plugin

### JDK with SDKMAN for local development
We recommend [SDKMAN!](https://sdkman.io) to manage multiple JDK versions.

Install Temurin JDK 25:

```zsh
sdk install java 25-tem
sdk default java 25-tem
```

Check:
```zsh
java --version

openjdk 25 2025-09-16 LTS
OpenJDK Runtime Environment Temurin-25+36 (build 25+36-LTS)
OpenJDK 64-Bit Server VM Temurin-25+36 (build 25+36-LTS, mixed mode, sharing)
```

### Make
The **Makefile** in this project is just a convenience layer on top of Docker and Gradle commands.  
If you don't want to install `make`, you can always run the equivalent `docker compose` and `./gradlew` commands manually.

- macOS (Homebrew):
```zsh
brew install make
```

- Linux (Debian/Ubuntu):
```zsh
sudo apt install -y make
```

## Quick Run
⚠️ **host.docker.internal**
```zsh
echo "127.0.0.1 host.docker.internal" | sudo tee -a /etc/hosts
ping host.docker.internal
```
OIDC redirects happen in the browser, not inside Docker.
The browser is outside the Docker network, but your Authorization Server (AS) must use a hostname that works consistently for:

- services inside containers (service-to-service calls / token validation), where the **"iss"** claim in minted JWTs must exactly match the issuer that validators are configured with (**issuer-uri**). <br>
If you copy a token minted before (e.g., with iss = http://localhost:9000), the **resource-server** expecting http://host.docker.internal:9000 will reject it.
- the browser outside containers (for redirects like http://host.docker.internal:9000/...).

The alias **host.docker.internal** ensures both containers and the browser can reach the **auth-server**, and that tokens' iss (e.g., http://host.docker.internal:9000) matches what the **resource-server** is configured to trust.

Start the stack from inside **`stack`**:
```zsh
cd stack
make build up
```

- make build up → builds the images and then starts the stack.

- make build → only builds the images.

- make up → only starts the stack (reuses already built images).

➡️ Open the client application in your browser:

http://localhost:8080
```text
Username: user
Password: pass
```

## Running Options

### JVM (normal)
To run normally with JDK 25 (same as pressing Run in IntelliJ):
```zsh
cd auth-server
./gradlew clean bootRun
```

### GraalVM (native)
For native image compilation, install **GraalVM with Native Image Kit** (NIK):

```zsh
sdk install java 25.r25-nik
sdk use java 25.r25-nik
```

Check:
```zsh
java --version

openjdk version "25" 2025-09-16 LTS
OpenJDK Runtime Environment Liberica-NIK-25.0.0-1 (build 25+37-LTS)
OpenJDK 64-Bit Server VM Liberica-NIK-25.0.0-1 (build 25+37-LTS, mixed mode, sharing)
```

Understanding GraalVM versions:

- 25 Java language level (JDK 25)
- r25 GraalVM release train based on JDK 25
- nik Native Image Kit, required to build native binaries

#### Native Image Build
To compile as a native executable:
```zsh
cd auth-server
./gradlew clean nativeCompile
```

Run it directly:
```zsh
./build/native/nativeCompile/auth-server
```

Shortcut (compile + run in one step):
```zsh
./gradlew clean nativeRun
```

### Docker Build (Paketo Buildpacks)
This project uses Paketo Buildpacks via Spring Boot's bootBuildImage to produce OCI images.
By default our Gradle task is configured to build native images but you can choose to use JVM with a property.

- Build native image (default) → Banner will show: "JVM :: Substrate VM..."
    ```zsh
    cd auth-server
    ./gradlew clean bootBuildImage -Pversion=DEV-SNAPSHOT
    ```

- Build JVM image → Banner will show: "JVM :: OpenJDK 64-Bit Server VM..."
    ```zsh
    cd auth-server
    ./gradlew clean bootBuildImage -Pversion=DEV-SNAPSHOT -PBP_NATIVE_IMAGE=false
    ```

Run it:
```zsh
docker run --rm -p 9000:9000 \
  -e SPRING_PROFILES_ACTIVE=docker \
  dcrivella/auth-server:DEV-SNAPSHOT
```

## Gradle
The project uses the Gradle wrapper (./gradlew).

Install Gradle locally:
```zsh
sdk install gradle 9.1.0
sdk default gradle 9.1.0
gradle -v
```

Update wrapper inside a service (e.g., auth-server):
```zsh
cd auth-server
gradle wrapper --gradle-version 9.1.0
./gradlew -v
```

## Make Commands

- **make build-auth** → builds the `auth-server` Docker image using Gradle’s `bootBuildImage` (via **Paketo Buildpacks**).
- **make build-client** → builds the `client-server` Docker image using Gradle’s `bootBuildImage` (via **Paketo Buildpacks**).
- **make build-resource** → builds the `resource-server` Docker image using Gradle’s `bootBuildImage` (via **Paketo Buildpacks**).
- **make down** → stops the Compose stack.
- **make restart** → stops and then restarts the stack (`down` + `up`).
- **make logs** → tails logs of all running services (`docker compose logs -f`).
- **make ps** → shows container status (`docker compose ps`).
- **make db-reset** → stops the stack, deletes Postgres volumes, and restarts with a fresh database. <br> ⚠️ This wipes all local data.
- **make check** → prints diagnostic info (image names, Compose version, whether `auth-server`, `client-server` and `resource-server` directories exist).

## Database Connection Details

- **Database name**: `auth_server`
- **Schema**: `auth`
- **User**: `auth_user`
- **Password**: `auth_pass`
- **Host**:
  - Inside Docker Compose → `db`
  - From host machine → `localhost` (or `127.0.0.1`)
- **Port**: `5432` (default PostgreSQL port)

### Connection Strings

- **JDBC (SQuirrel SQL, IntelliJ, etc.)** → `jdbc:postgresql://localhost:5432/auth_server?currentSchema=auth`

- **psql CLI (from host ZSH)** →
  ```zsh
  psql -h localhost -p 5432 -U auth_user -d auth_server
  ```

### Installing psql
If you don't have the PostgreSQL CLI (psql) installed, install it:

- macOS (Homebrew)

    ```zsh
    brew install libpq
    brew link --force libpq
    psql --version
    ```

- Linux (Debian/Ubuntu)
    ```zsh
    sudo apt install -y wget ca-certificates gnupg
    
    wget -qO- https://www.postgresql.org/media/keys/ACCC4CF8.asc | \
      gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/pgdg.gpg > /dev/null

    echo "deb http://apt.postgresql.org/pub/repos/apt noble-pgdg main" | \
      sudo tee /etc/apt/sources.list.d/pgdg.list

    sudo apt update

    sudo apt install -y postgresql-client-18

    psql --version
    ```
