# Order Management System REST API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-7.4.0-black.svg)](https://kafka.apache.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8-orange.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue.svg)](.github/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/Coverage-JaCoCo-green.svg)](https://www.jacoco.org/)

A complete, **production-ready** Order Management System REST API built from scratch. Features stateless JWT Authentication, JPA persistence on PostgreSQL database, transactional stock validation and restoration, declarative Kafka event streaming with Dead Letter Topic (DLT) resilience, Spring Boot Actuator health monitoring, CORS support, automated CI/CD pipeline, and interactive OpenAPI documentation.

---

## 🌟 Key Features

- **Stateless JWT Security**: Highly secure registration and login endpoints. All other catalog and ordering paths require a valid JWT token in the `Authorization: Bearer <token>` header.
- **Database Consistency & Transactions**: Placing and cancelling orders execute under atomic database transactions (`@Transactional`).
- **Real-time Event Streaming (Kafka)**:
  - `order.created` (Sent when a new order is placed successfully)
  - `order.status.updated` (Sent when status changes)
  - `order.cancelled` (Sent when order is cancelled)
- **Kafka Dead Letter Topic (DLT)**: Poison-pill messages that fail processing after 3 retries are automatically routed to `order.events.DLT` for manual investigation. Includes retry logging and structured error handling.
- **High-Fidelity Event Consumer**: Logs all incoming order events with formatted timestamps showing message keys, topic partitions, and JSON payloads.
- **Transactional Stock Control**:
  - Validates stock quantities before placing orders (throws `InsufficientStockException` on catalog gaps).
  - Automatically restores items to the product's catalog stock levels if the order is cancelled.
- **Status Lifecycle State Machine**: Enforces a strict status lifecycle path: `PENDING` → `CONFIRMED` → `SHIPPED` → `DELIVERED`. Prevents illegal transitions and blocks cancellation once shipped or delivered.
- **Production Monitoring (Actuator)**: Health checks (`/actuator/health`), application info (`/actuator/info`), and metrics (`/actuator/metrics`) endpoints for production monitoring and load balancer integration.
- **CORS Support**: Configurable Cross-Origin Resource Sharing for frontend client integration.
- **Swagger Documentation**: Complete OpenAPI schemas with Bearer token authentication support via Swagger UI.
- **CI/CD Pipeline**: Automated GitHub Actions workflow for build, test, and JaCoCo code coverage on every push.
- **Comprehensive Unit Tests**: Full JUnit 5 + Mockito test suite (29 tests) covering all service layers — order placement, stock validation, status transitions, cancellation, product CRUD, and authentication flows.
- **Structured Logging**: Logback configuration with colored console output, rolling file appender (30-day retention), and fine-grained logging levels.
- **Docker Orchestration**: Multi-stage Docker build, docker-compose with healthchecks, restart policies, and container dependency ordering.

---

## 🏛️ Architecture Overview

```text
┌──────────────┐       ┌────────────────────────────────────────────────────┐
│   Client     │       │              Spring Boot Application              │
│  (Postman /  │──────▶│                                                    │
│   Frontend)  │       │  ┌──────────┐  ┌───────────┐  ┌───────────────┐   │
│              │◀──────│  │Controller│─▶│  Service   │─▶│  Repository   │   │
└──────────────┘       │  │  Layer   │  │   Layer    │  │    (JPA)      │   │
                       │  └──────────┘  └─────┬─────┘  └───────┬───────┘   │
                       │                      │                │           │
                       │               ┌──────▼──────┐  ┌─────▼─────┐    │
                       │               │    Kafka     │  │PostgreSQL │    │
                       │               │   Producer   │  │    DB     │    │
                       │               └──────┬───────┘  └───────────┘    │
                       │                      │                           │
                       │  ┌───────────────────▼────────────────────┐      │
                       │  │         Apache Kafka Cluster           │      │
                       │  │  ┌─────────┐ ┌──────────┐ ┌────────┐  │      │
                       │  │  │ order.  │ │  order.  │ │ order. │  │      │
                       │  │  │ created │ │  status  │ │canceled│  │      │
                       │  │  └────┬────┘ │ .updated │ └───┬────┘  │      │
                       │  │       │      └─────┬────┘     │       │      │
                       │  │       └────────────┼──────────┘       │      │
                       │  │                    ▼                  │      │
                       │  │          ┌─────────────────┐          │      │
                       │  │          │  Kafka Consumer  │          │      │
                       │  │          │   (order-group)  │          │      │
                       │  │          └────────┬────────┘          │      │
                       │  │                   │ On Failure (3x)   │      │
                       │  │          ┌────────▼────────┐          │      │
                       │  │          │  order.events   │          │      │
                       │  │          │     .DLT        │          │      │
                       │  │          └─────────────────┘          │      │
                       │  └───────────────────────────────────────┘      │
                       │                                                    │
                       │  ┌──────────────────────────────────────────┐     │
                       │  │  Security Layer (JWT Filter Chain)       │     │
                       │  │  • JwtAuthenticationFilter               │     │
                       │  │  • JwtTokenProvider                      │     │
                       │  │  • BCrypt Password Encoding              │     │
                       │  └──────────────────────────────────────────┘     │
                       └────────────────────────────────────────────────────┘
```

---

## 🏗 Project Structure

```text
com.example.order
├── OrderManagementApplication.java
├── config/
│   ├── SecurityConfig.java          # Spring Security 6 + CORS + Actuator config
│   ├── KafkaConfig.java             # Topic provisioning + DLT + Error handler
│   ├── SwaggerConfig.java           # OpenAPI Bearer security schema
│   ├── JwtTokenProvider.java        # JWT encoding/decoding logic
│   ├── JwtAuthenticationFilter.java # Auth header token extraction filter
│   └── CustomUserDetailsService.java# UserDetails loading from DB
├── controller/
│   ├── AuthController.java          # Registration and login endpoints
│   ├── ProductController.java       # CRUD catalog with Admin protection
│   └── OrderController.java         # Order placement, status, cancellation
├── service/
│   ├── AuthService.java
│   ├── ProductService.java
│   ├── OrderService.java
│   └── impl/
│       ├── AuthServiceImpl.java
│       ├── ProductServiceImpl.java
│       └── OrderServiceImpl.java
├── repository/
│   ├── UserRepository.java
│   ├── ProductRepository.java
│   └── OrderRepository.java
├── model/
│   ├── User.java                    # JPA entity + UserDetails
│   ├── Product.java                 # Catalog items with stock tracking
│   ├── Order.java                   # Orders with bi-directional items
│   ├── OrderItem.java               # Line items with unit prices
│   ├── Role.java                    # USER, ADMIN enum
│   └── OrderStatus.java             # PENDING → CONFIRMED → SHIPPED → DELIVERED
├── dto/
│   ├── request/                     # Validated JSR-303 request DTOs
│   └── response/
│       ├── ApiResponse.java         # Standard response wrapper
│       ├── PageResponse.java        # Pagination metadata envelope
│       └── OrderEvent.java          # Kafka event serialization
├── kafka/
│   ├── producer/
│   │   └── OrderEventProducer.java  # Async event publishing
│   └── consumer/
│       └── OrderEventConsumer.java  # Event logging + DLT handler
└── exception/
    ├── GlobalExceptionHandler.java  # @RestControllerAdvice error mapping
    ├── ProductNotFoundException.java
    ├── OrderNotFoundException.java
    ├── InsufficientStockException.java
    └── InvalidStatusTransitionException.java
```

---

## 📡 API Endpoints

### 🔐 Authentication

| Method | Endpoint | Description | Security | Status |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Register new user | Public | `201 Created` |
| `POST` | `/api/v1/auth/login` | Login user, get JWT token | Public | `200 OK` |

### 📦 Product Catalog

| Method | Endpoint | Description | Security | Status |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/products` | Paginated catalog fetch | JWT | `200 OK` |
| `GET` | `/api/v1/products/{id}` | Fetch product by ID | JWT | `200 OK` |
| `POST` | `/api/v1/products` | Create product | JWT (Admin Only) | `201 Created` |
| `PUT` | `/api/v1/products/{id}` | Update product | JWT (Admin Only) | `200 OK` |
| `DELETE` | `/api/v1/products/{id}`| Delete product | JWT (Admin Only) | `200 OK` |

### 🛒 Orders

| Method | Endpoint | Description | Security | Status |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/orders` | Get all orders | JWT | `200 OK` |
| `GET` | `/api/v1/orders/{id}` | Fetch order by ID | JWT | `200 OK` |
| `POST` | `/api/v1/orders` | Place new order (Stock checked & reduced)| JWT | `201 Created` |
| `PUT` | `/api/v1/orders/{id}/status`| Transition order state | JWT | `200 OK` |
| `DELETE` | `/api/v1/orders/{id}` | Cancel order (Restores catalog stock) | JWT | `200 OK` |

### 🔍 Monitoring (Actuator)

| Method | Endpoint | Description | Security |
| :--- | :--- | :--- | :--- |
| `GET` | `/actuator/health` | Application health status | Public |
| `GET` | `/actuator/info` | Application metadata | Public |
| `GET` | `/actuator/metrics` | JVM & request metrics | Public |

---

## 📬 Unified Response Structure

All endpoints return a uniform JSON format:

### Success Response
```json
{
  "status": "success",
  "message": "Fetched product successfully",
  "data": {
    "id": 1,
    "name": "Wireless Mouse",
    "price": 29.99,
    "stockQuantity": 100
  }
}
```

### Error Response
```json
{
  "status": "error",
  "message": "Insufficient stock for product 'Wireless Mouse'. Requested: 200, Available: 100",
  "data": null
}
```

---

## 🚀 How to Run the Project Locally

### Prerequisites
- Java 17 installed
- Maven installed
- Docker & docker-compose installed

### Step 1: Clone and Configure Environment Variables
Create a local `.env` file (copied from `.env.example`):
```bash
cp .env.example .env
```

### Step 2: Spin Up Infrastructure (PostgreSQL & Kafka)
Launch PostgreSQL database and Kafka services in the background using docker-compose:
```bash
docker-compose up -d postgres zookeeper kafka
```

### Step 3: Run the Spring Boot App
Build the codebase and start the server:
```bash
mvn clean spring-boot:run
```
The server will boot up on port `8080`.

### Step 4: Run the Complete Docker Stack (App included)
To build the Docker image and run the entire ecosystem (App, Postgres, Kafka) in unified containers with healthchecks:
```bash
docker-compose up --build
```

---

## 📚 API Testing & Documentation

### Swagger UI (OpenAPI)
When the application is running, open your browser to:
- **Swagger Documentation**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI definition JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Postman Collection
We have provided a complete Postman collection named `Order_API.postman_collection.json` in the root of the project.
1. Import `Order_API.postman_collection.json` into Postman.
2. The Login Request is configured with a **Postman Test Script** that automatically captures the returned JWT token and sets it as the collection variable `{{jwt_token}}` to authorize all subsequent secured requests!

> **💡 Tip**: After the application boots up, run the Postman collection using Postman's **Collection Runner** to execute all requests in sequence. The Login endpoint automatically populates the `{{jwt_token}}` variable for all downstream authenticated requests!

---

## 🧪 Running Tests

This project includes a comprehensive unit test suite built with **JUnit 5**, **Mockito**, and **AssertJ**.

### Run All Tests
```bash
mvn test
```

### Generate Coverage Report (JaCoCo)
```bash
mvn test jacoco:report
```
The HTML coverage report will be generated at `target/site/jacoco/index.html`.

### Test Coverage Summary

| Test Class | Covers | Tests | Key Scenarios |
| :--- | :--- | :---: | :--- |
| `OrderServiceImplTest` | Order placement, cancellation & status transitions | 15 | Stock deduction, insufficient stock, stock restoration on cancel, all valid/invalid state transitions, Kafka event verification |
| `ProductServiceImplTest` | Product catalog CRUD operations | 7 | Create, read (paginated), update, delete, not-found exceptions |
| `AuthServiceImplTest` | User registration & authentication | 7 | Registration with default/admin roles, duplicate username/email, invalid roles, JWT login, bad credentials |

**Total: 29 unit tests** covering all 3 service layers with edge-case scenarios.

---

## 🔧 Scalability & Production Readiness

| Feature | Implementation |
| :--- | :--- |
| **Stateless Architecture** | JWT-based auth — no server-side sessions, horizontally scalable |
| **Event-Driven Design** | Kafka decouples order processing from downstream services |
| **Connection Pooling** | HikariCP (Spring Boot default) for efficient DB connections |
| **Docker Orchestration** | Container healthchecks, restart policies, dependency ordering |
| **Health Monitoring** | Spring Boot Actuator endpoints for load balancers & uptime checks |
| **Error Resilience** | Kafka DLT with retry backoff prevents message loss |
| **CORS Ready** | Configurable CORS for frontend client integration |
| **CI/CD Pipeline** | GitHub Actions auto-runs tests on every push/PR |
| **Structured Logging** | Logback with rolling file appender (30-day retention) |
| **Code Coverage** | JaCoCo reports generated automatically during CI |

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
