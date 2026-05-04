# Reservation Payment Platform

특정 시각에 한정 수량으로 판매되는 상품을 예약하고 결제하는 서버 애플리케이션

## 기술 스택

- Java 17
- Spring Boot 4.0.6
- Spring Web MVC, Spring Data JPA, Validation, Actuator
- MySQL 8.0
- Redis 7.2
- Redisson 4.3.1
- Gradle 9.x wrapper
- Docker Compose

Reference:

- Spring Boot: https://docs.spring.io/spring-boot/installing.html
- Redis counter commands: https://redis.io/docs/latest/commands/incr/
- MySQL InnoDB locking reads: https://dev.mysql.com/doc/en/innodb-locking-reads.html
- Redisson locks: https://redisson.pro/docs/data-and-services/locks-and-synchronizers/

---

## 실행 방법

### 1. Docker Compose 실행

```bash
docker compose up --build
```

백그라운드로 실행하려면 아래 명령을 사용합니다.

```bash
docker compose up -d --build
```

서비스:

- Application: `http://localhost:8080`
- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`

상태 확인:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
```

로그 확인:

```bash
docker compose logs -f app
docker compose logs -f mysql
docker compose logs -f redis
```

종료:

```bash
docker compose down
```

```bash
# 볼륨까지 삭제해 DB/Redis 데이터를 초기화
docker compose down -v
```

### 2. 로컬 실행

로컬 JVM으로 애플리케이션만 실행하려면 MySQL과 Redis를 먼저 실행한 뒤 애플리케이션을 실행

```bash
docker compose up -d mysql redis
./gradlew bootRun
```

기본 로컬 설정:

- MySQL database: `reservation_db`
- MySQL username: `admin`
- MySQL password: `1234`
- Redis host: `localhost`
- Redis port: `6379`

### 3. 테스트

```bash
./gradlew test --no-daemon
```

---

## API

- `GET /api/v1/checkout`: 주문서 진입에 필요한 상품, 조회용 재고 스냅샷, 가용 포인트 반환
- `POST /api/v1/bookings`: 재고 선점, 결제 승인, 포인트 차감, 예약 확정 처리

---

## 패키지 구조

```text
com.reservation.platform
  booking       예약 유스케이스, 예약 도메인
  order         주문 도메인과 예약/결제 처리 상태 전이
  payment       결제 도메인, 결제 수단 확장 인터페이스, PG adapter
  point         포인트 잔액과 포인트 거래 내역
  product       상품 조회
  stock         Redis/DB 재고 선점, 확정, 복구
  user          사용자 조회
  interfaces    HTTP API controller
  global        공통 응답, 예외, 멱등성, 설정
```

---

## 문서

- [Core Features](docs/core-features.md)
- [Application Service](docs/application-service.md)
- [API Flow](docs/api-flow.md)
- [Architecture Decisions](docs/DECISIONS.md)
- [ERD](docs/erd.md)
- [Sequence Diagram](docs/sequence-diagram.md)
