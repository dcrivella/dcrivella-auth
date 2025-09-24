# dcrivella-auth
Spring Boot Authorization Server with GraalVM

This project provides an **OAuth2 Authorization Server** (with OIDC support) built on Spring Boot, with support for running on both a normal JVM and as a **GraalVM native image**.

---

## Project Structure
- **auth-server** → Spring Authorization Server (OIDC, OAuth2 flows)
- **client-server** → Example web client (PKCE, Thymeleaf UI)
- **resource-server** → Example API protected by JWT
- **stack** → Docker Compose stack (auth, client, resource servers, Postgres)
- **postman** → Postman collection and environment

--- 

## Prerequisites

### Install JDK with SDKMAN
We recommend [SDKMAN!](https://sdkman.io) to manage multiple JDK versions.

Install Temurin JDK 24:

```zsh
sdk install java 24.0.2-tem
sdk default java 24.0.2-tem
```

Check:
```zsh
java --version

openjdk 24.0.2 2025-07-15
OpenJDK Runtime Environment Temurin-24.0.2+12 (build 24.0.2+12)
OpenJDK 64-Bit Server VM Temurin-24.0.2+12 (build 24.0.2+12, mixed mode, sharing)
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

Now start the stack:
```zsh
cd stack
make up
```

➡️ Open the client application in your browser:

http://localhost:8080

## Running Options

### JVM (normal)
To run normally with JDK 24 (same as pressing Run in IntelliJ):
```zsh
cd auth-server
./gradlew bootRun
```

### GraalVM (native)
For native image compilation, install **GraalVM with Native Image Kit** (NIK):

```zsh
sdk install java 24.2.r24-nik
sdk use java 24.2.r24-nik
```

Check:
```zsh
java --version

openjdk 24 2025-03-18
OpenJDK Runtime Environment Liberica-NIK-24.2.0-1 (build 24+37)
OpenJDK 64-Bit Server VM Liberica-NIK-24.2.0-1 (build 24+37, mixed mode, sharing)
```

Understanding GraalVM versions:

- 24 Java language level (JDK 24)
- r24 GraalVM release train based on JDK 24
- nik Native Image Kit, required to build native binaries

#### Native Image Build
To compile as a native executable:
```zsh
cd auth-server
./gradlew nativeCompile
```

Run it directly:
```zsh
./build/native/nativeCompile/auth-server
```

Shortcut (compile + run in one step):
```zsh
./gradlew nativeRun
```

### Docker Build
You can build OCI/Docker images using **Paketo Buildpacks** (with GraalVM native image support):
```zsh
cd auth-server
./gradlew bootBuildImage -PBP_NATIVE_IMAGE=true
```

Run it:
```zsh
docker run --rm dcrivella/auth-server:0.0.1-SNAPSHOT
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