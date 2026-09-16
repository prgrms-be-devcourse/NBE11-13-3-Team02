# Local observability

## Application settings

Spring Boot exposes only these Actuator endpoints without JWT authentication:

- `GET /actuator/health`
- `GET /actuator/prometheus`

All business APIs keep their existing authentication policy.

## Run with Docker

1. Start the application with `dev-start.cmd` from the project root.
2. Copy `.env.example` to `.env` and change the Grafana password.
3. Run `docker compose up -d` in this directory.

The Docker Prometheus target is `host.docker.internal:8080` because it reaches a backend on the host machine.

## Run directly on Windows

1. Start the application with `dev-start.cmd` from the project root.
2. Start Prometheus with `prometheus/prometheus.windows.yml`.
3. Open Prometheus at `http://localhost:9090` and Grafana at `http://localhost:3000`.

The direct Windows Prometheus target is `127.0.0.1:8080`, avoiding an IPv6 `localhost` resolution mismatch.
