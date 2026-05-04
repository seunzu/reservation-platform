# Test Plan

## Test Scope

| 영역 | 테스트 | 목적 |
| --- | --- | --- |
| 결제 요청 검증 | `PaymentValidatorTest` | 잘못된 결제 금액/수단 조합을 Booking API 진입 전에 차단 |
| 재고 정책 | `StockServiceTest` | Redis 선점, DB 확정 차감, Redis 장애 fallback, 실패 복구 검증 |
| 멱등성 응답 저장 | `IdempotencyResponseAdviceTest` | 성공 응답만 캐시하고 실패 응답은 재시도 가능하게 처리 |
| Saga 보상 | `BookingSagaServiceIntegrationTest` | 결제/포인트/주문/재고 보상 순서와 보상 실패 지속 처리 검증 |
| 애플리케이션 스모크 | `PlatformApplicationTests` | 애플리케이션 진입 클래스 로드 검증 |
| HTTP 경계 | `BookingApiIntegrationTest` | header, validation, interceptor, response advice, exception handler 검증 |
| Checkout/Booking 성공 플로우 | `BookingFlowContainerIntegrationTest` | MySQL/Redis 기반 조회와 확정 예약 side effect 검증 |
| 재고 동시성 | `StockConcurrencyContainerIntegrationTest` | 동시 예약 요청에서 초과 판매 방지 |
| 멱등성 통합 | `IdempotencyContainerIntegrationTest` | 실제 Redis idempotency key와 DB unique 기반 중복 처리 검증 |

---

## Service Tests

### `PaymentValidatorTest`

검증 내용:

- 신용카드/Y페이/포인트 단독 결제 허용
- 신용카드 + 포인트, Y페이 + 포인트 결제 허용
- 주 결제 금액이 0인 카드/Y페이 결제 거부
- `primaryMethod=POINT`인데 카드 금액이 있는 요청 거부
- `usePoint=false`인데 포인트 금액이 있는 요청 거부
- 결제 금액 합계가 상품 가격과 다른 요청 거부

의미:

```text
BookingRequest + Product
  -> PaymentValidator.validate()
  -> 정상 조합은 통과
  -> 잘못된 조합은 ApplicationException
```

### `StockServiceTest`

검증 내용:

- Redis 재고 key가 없으면 DB 원장 기준으로 Redis 초기화 후 선점
- Redis 재고 key가 있으면 DB 초기화를 건너뛰고 선점
- Redis 차감 결과가 음수면 즉시 `INCR` 복구 후 재고 소진 처리
- Redis 선점 이후 DB 확정 차감 수행
- 실패 보상 시 Redis 선점분과 DB 확정분을 상태에 맞게 복구
- Redis 장애 시 `StockDbLockService.decreaseWithLock()` 경로로 fallback

의미:

```text
StockService.reserve()
StockService.confirm()
StockService.release()
```

재고 정책의 핵심 분기를 외부 Redis/MySQL 없이 빠르게 검증

### `IdempotencyResponseAdviceTest`

검증 내용:

- 성공 `ApiResponse`만 idempotency cache에 저장
- 실패 `ApiResponse`는 PROCESSING key 삭제
- `ApiResponse.ok(null)`도 성공 응답으로 저장
- request attribute가 없거나 body가 `ApiResponse`가 아니면 무시

의미:

```text
성공 응답 -> 같은 key 재요청 시 cached response 반환 가능
실패 응답 -> 같은 key로 재시도 가능
```

### `BookingSagaServiceIntegrationTest`

검증 내용:

- 정상 예약 응답 생성
- 포인트 미사용 결제 성공
- PG 승인 실패 시 주문 실패 처리와 재고 복구
- 예약 생성 실패 시 PG 취소, 포인트 환불, 주문 실패, 재고 복구
- 포인트 미사용 실패에서는 포인트 환불 미호출
- 주문 생성 실패 시에도 재고 복구
- PG 취소 보상이 실패해도 이후 보상 계속 진행

의미:

```text
BookingSagaService.processBooking()
  -> 성공 단계 기록
  -> 실패 시 이미 성공한 단계 보상
```

외부 PG는 DB transaction rollback 대상이 아니므로 Saga 보상 동작을 직접 검증

### `PlatformApplicationTests`

검증 내용:

- 애플리케이션 진입 클래스가 테스트 classpath에서 로드되는지 확인

의미:

```text
PlatformApplication
  -> context load 없이 애플리케이션 클래스 로드 검증
```

---

## Web/API Tests

### `BookingApiIntegrationTest`

검증 내용:

- `X-Idempotency-Key` 누락/blank 요청 거부
- 정상 Booking 응답은 idempotency cache 저장
- 비즈니스 실패 응답은 PROCESSING key 삭제
- validation 실패와 malformed JSON은 공통 에러 응답으로 변환
- PROCESSING key 요청은 중복 요청으로 차단
- 완료된 key 요청은 cached response 반환

의미:

```text
BookingController
  + IdempotencyInterceptor
  + IdempotencyResponseAdvice
  + GlobalExceptionHandler
```

DB 없이 HTTP 경계와 횡단 관심사를 빠르게 검증

---

## Container Integration Tests

### `BookingFlowContainerIntegrationTest`

검증 내용:

- Checkout API가 MySQL의 사용자, 상품, 재고, 포인트 정보를 조합해 응답
- Redis stock key가 없을 때 DB 재고로 조회 fallback
- Booking API 성공 시 주문, 결제, 예약 생성
- 성공 예약 후 DB 재고와 Redis 재고가 함께 차감

의미:

```text
MockMvc
  -> BookingController
  -> BookingFacade
  -> StockService / PaymentService / PointService / BookingService
  -> MySQL + Redis
```

### `StockConcurrencyContainerIntegrationTest`

검증 내용:

- 초기 재고보다 많은 동시 예약 요청 실행
- 성공 예약 수가 초기 재고 수와 동일
- 초과 요청은 재고 소진 응답
- DB 재고가 음수가 되지 않음
- 확정 주문과 예약 수가 성공 수와 일치

의미:

```text
동시 POST /api/v1/bookings
  -> Redis DECR 선점
  -> DB SELECT ... FOR UPDATE 확정 차감
  -> 초과 판매 방지
```

### `IdempotencyContainerIntegrationTest`

검증 내용:

- 같은 idempotency key로 완료 요청을 재시도하면 cached response 반환
- 같은 key 재시도로 주문/예약/결제가 중복 생성되지 않음
- PROCESSING key가 있으면 Controller 진입 전 중복 요청 차단

의미:

```text
Redis idempotency key
  + orders.order_token unique
  -> 중복 결제/예약 방지
```

---

## Test Support

직접적인 테스트 시나리오가 아닌 통합 테스트 실행을 위한 지원 코드

| 파일 | 역할 |
| --- | --- |
| `AbstractContainerIntegrationTest` | Testcontainers MySQL/Redis 설정, MockMvc/Spring context 설정, 테스트 데이터 정리 |
| `IntegrationTestFixtures` | 사용자, 상품, 재고, 포인트 fixture 생성 helper |

---

## Test Commands

서비스 테스트만 실행:

```bash
./gradlew test \
  --tests com.reservation.platform.payment.application.PaymentValidatorTest \
  --tests com.reservation.platform.stock.application.StockServiceTest \
  --tests com.reservation.platform.global.idempotency.IdempotencyResponseAdviceTest \
  --tests com.reservation.platform.booking.application.BookingSagaServiceIntegrationTest \
  --no-daemon
```

전체 테스트 실행:

```bash
./gradlew test --no-daemon
```

통합 테스트는 Testcontainers가 MySQL/Redis를 직접 실행하므로 별도 로컬 MySQL/Redis를 먼저 띄울 필요가 없음 (Docker daemon은 실행되어 있어야 함)
