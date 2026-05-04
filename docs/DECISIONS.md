# Architecture Decisions

## 1. 재고 정합성: Redis 선점 + DB 원장 확정

### 상황

짧은 시간 동안 같은 상품에 예약 요청이 집중될 수 있음 \
-> 상품이 재고 이상 판매되지 않도록 막으면서, 모든 요청을 DB 비관적 락 하나로 직렬화해 처리량을 지나치게 낮추지 않아야 함

### 선택

```text
StockService.reserve()
  -> Redis 재고 key가 없으면 DB 원장 기준으로 초기화
  -> 초기화 구간은 짧은 Redisson RLock으로 보호
  -> Redis DECR로 빠르게 선점
  -> Redis 장애 시 DB SELECT ... FOR UPDATE로 fallback

StockService.confirm()
  -> StockDbLockService.decreaseWithLock()
  -> 예약 Saga 성공 경로에서 DB 재고를 비관적 락으로 확정 차감

StockService.release()
  -> StockDbLockService.increaseWithLock()
  -> 실패 시 Redis 선점분과 DB 확정분을 상태에 맞게 복구
```

Redis 재고 선점 여부와 DB 재고 확정 여부는 `StockReservation` 값 객체로 전달. 이 객체를 통해 실패 시 어떤 재고를 복구해야 하는지 명시적으로 판단

DB 비관적 락 차감/복구는 `StockDbLockService`로 분리 \
-> Spring transaction proxy의 self-invocation 문제를 피하고, `REQUIRES_NEW` 트랜잭션 경계를 명확히 할 수 있음

### 근거

Redis `DECR`는 단일 key counter를 원자적으로 감소시키는 데 적합 -> 순간적인 진입 요청을 빠르게 선별할 수 있음 \
단, Redis만 최종 원장으로 쓰면 Redis 장애나 데이터 유실 시 성공 예약 수와 재고가 달라질 수 있음 \
=> 예약 성공 흐름에서는 MySQL `stocks`를 최종 원장으로 보고 `SELECT ... FOR UPDATE` 기반으로 확정 차감

Redis key가 없는 상태에서 바로 `DECR`를 호출하면 Redis는 값을 0으로 보고 -1을 만들 수 있음 \
-> `reserve()`는 Redis 재고 key가 없을 때 DB 원장의 현재 재고로 Redis를 초기화한 뒤 차감 \
(초기화 구간은 짧은 Redisson lock으로 보호해 여러 애플리케이션 인스턴스가 동시에 같은 key를 초기화하지 않게 함)

MySQL InnoDB는 `SELECT ... FOR UPDATE`로 읽은 row를 다른 transaction이 수정하지 못하도록 잠금 \
-> Redis 장애 시에도 이 fallback 경로가 초과 판매 방지의 최후 방어선

Reference:

- Redis INCR/DECR counter pattern: https://redis.io/docs/latest/commands/incr/
- MySQL locking reads: https://dev.mysql.com/doc/en/innodb-locking-reads.html

### 트레이드오프

- (+): 정상 상황에서는 Redis로 빠르게 선점하고, 최종 원장은 DB에 남길 수 있음
- (+): Redis key 유실이나 애플리케이션 재시작 후 첫 요청도 DB 원장 기준으로 복구됨
- (+): Redis 장애 시에도 DB 비관적 락으로 예약 처리가 가능
- (-): Redis 재고와 DB 재고가 모두 관여하므로 reconciliation이 필요
- (-): DB 확정 차감은 row lock을 사용하므로 트래픽이 더 커지면 DB가 병목이 될 수 있음

운영 환경에서는 예약 성공/실패 이벤트를 기준으로 Redis와 DB 재고를 주기적으로 비교하는 reconciliation job을 추가하는 것이 안전

---

## 2. 상품 단위 분산락을 전체 예약 흐름에 걸지 않은 이유

### 상황

초기 설계에서는 상품 단위 Redisson `RLock`으로 Booking API 전체를 감싸는 방법을 고려함. 그러나 Booking API에는 PG 승인처럼 외부 I/O가 포함됨

### 선택

현재 구현은 Booking API 전체에 상품 단위 분산락을 걸지 않음 \
재고 정합성은 Redis 원자 차감과 DB 비관적 락으로 보장하고, PG 호출은 재고 선점 이후 락 없는 상태에서 수행

### 근거

분산락을 PG 승인까지 포함한 긴 구간에 걸면 같은 상품의 요청이 모두 직렬화됨 -> 00시 트래픽에서 PG 지연이 곧 전체 처리량 저하로 이어짐 \
Redisson 공식 문서에서도 `RLock`은 Redis 기반 분산 lock이며, lock holder가 살아있는 동안 watchdog이 lease를 연장하는 구조 \
=> 락은 유용한 동시성 제어 수단이지만 외부 I/O를 포함한 긴 비즈니스 트랜잭션 전체를 묶는 도구로 쓰면 가용성 측면에서 불리

Reference:

- Redisson locks: https://redisson.pro/docs/data-and-services/locks-and-synchronizers/

### 트레이드오프

- (+): PG 지연이 재고 선점 처리량을 막지 않음
- (+): 재고 정합성 책임이 Redis/DB 원장에 명확히 남음
- (-): Redis 선점 후 PG 실패가 발생하므로 보상 로직이 반드시 필요

---

## 3. 결제/예약 처리: 2PC 대신 오케스트레이션 Saga

### 상황

예약 완료는 주문 생성, 재고 확정, PG 승인, 포인트 차감, 예약 생성, 주문 확정의 여러 단계를 거침 \
PG는 외부 시스템이며, Redis와 MySQL도 서로 다른 저장소

### 선택

2PC 대신 오케스트레이션 Saga를 사용 \
`BookingSagaService`: 전체 흐름을 지휘하고, 실패 시 이미 성공한 단계를 역순 또는 영향도 순서로 보상

```text
정상 경로
  -> 주문 생성
  -> DB 재고 확정
  -> 결제 생성
  -> PG 승인
  -> 포인트 차감
  -> 예약 생성
  -> 주문 확정

실패 보상
  -> PG 취소
  -> 포인트 환불
  -> 주문 실패 처리
  -> 재고 복구
```

### 근거

- 2PC
  - 참여자가 prepare/commit 프로토콜에 참여해야 강한 원자성을 제공
  - 외부 PG는 일반적으로 애플리케이션의 transaction coordinator에 참여하지 않음
  - 00시 피크 상황에서 여러 자원에 걸친 prepare lock은 처리량과 가용성을 떨어뜨릴 수 있음
- Saga
  - 각 단계를 로컬 트랜잭션으로 확정하고, 실패 시 비즈니스 보상 트랜잭션으로 최종 정합성을 회복

=> 즉시 원자성보다 결제 취소/포인트 환불/재고 복구를 통한 최종 정합성 중요

### 트레이드오프

- (+): 외부 PG와 Redis를 하나의 분산 트랜잭션으로 묶지 않아도 됨
- (+): 실패 지점별 보상이 명확
- (-): 보상 자체가 실패할 수 있으므로 재처리 체계가 필요
- (-): 순간적으로 중간 상태가 노출될 수 있으므로 상태 모델과 모니터링이 중요

현재 구현 범위에서는 보상 실패 재처리 worker까지 포함하지 않음 (보상 실패 시 error log를 남김) \
운영 환경에서는 `compensation_failures` 또는 outbox 테이블에 실패 보상 단계를 기록하고, background worker가 PG 취소/포인트 환불/재고 복구를 멱등적으로 재시도하도록 확장

--- 

## 4. 멱등성: HTTP idempotency key + DB unique constraint

### 상황

사용자가 결제 버튼을 연속 클릭하거나 네트워크 timeout 후 재시도하면 같은 주문이 여러 번 처리될 수 있음

### 선택

Booking API에는 `X-Idempotency-Key` 헤더를 필수로 요구 \
최초 요청은 Redis에 `PROCESSING` 상태로 저장하고, 완료 후 응답 body를 24시간 캐싱 \
-> 이후 동일 key 요청은 Controller에 진입하지 않고 저장된 응답을 반환

중복 주문의 DB 최후 방어선으로 `orders.order_token` unique constraint를 사용 \
성공 응답만 24시간 캐싱하고 실패 응답은 PROCESSING key를 삭제해 일시 장애 후 재시도를 허용

### 근거

멱등성 key 처리에서 `GET -> SET` 순서로 구현하면 같은 key의 동시 요청이 모두 통과할 수 있음 \
-> Redis `SET NX EX` 의미의 `setIfAbsent`를 사용해 최초 요청만 원자적으로 PROCESSING 상태를 선점하게 함

### 트레이드오프

- (+): 짧은 간격의 중복 결제 요청을 HTTP 계층에서 차단
- (+): 완료 후 재시도는 같은 응답을 반환하므로 client가 안전하게 retry할 수 있음
- (-): Redis 장애 시 HTTP 멱등성 캐시는 사용할 수 없음
- 보완: DB unique constraint가 최후 방어선
  - 운영 환경에서는 idempotency key를 DB에도 저장해 Redis 장애 시에도 동일 응답을 재구성할 수 있게 하는 편이 더 안전

---

## 5. 결제 확장성: PaymentProcessor 전략

### 상황

현재 결제 수단은 신용카드, 페이, 포인트이며, 신용카드와 페이는 혼용할 수 없음 \
단, 신용카드+포인트 또는 페이+포인트 복합 결제는 가능해야 함

### 선택

카드/페이 계열 결제는 `PaymentProcessor` 인터페이스로 분리 \
`CompositePaymentProcessor`가 요청의 `primaryMethod`에 맞는 processor를 선택

```text
PaymentService.approve()
  -> CompositePaymentProcessor.process()
      -> CreditCardProcessor 또는 PayProcessor 선택
          -> PgClient.approve()
```

포인트는 외부 PG가 필요 없으므로 `PointService`에서 별도 도메인 거래로 처리

### 근거

Booking API의 비즈니스 흐름이 결제 수단별 상세 구현에 의존하면 신규 결제 수단 추가 시 예약 흐름을 수정해야 함 \
-> 전략 인터페이스를 두면 신규 수단은 processor 추가로 확장 가능

### 트레이드오프

- (+): 결제 수단 추가 시 변경 범위가 작음
- (+): PG 연동과 예약 오케스트레이션의 책임이 분리됨
- (-): 복합 결제 수단이 더 다양해지면 현재 `primaryMethod/cardAmount/pointAmount` DTO는 표현력이 부족

추후 확장 시에는 `paymentLines: [{method, amount}]` 구조로 변경하는 것이 더 일반적

--- 

## 6. 포인트 동시성: DB 비관적 락

### 상황

같은 사용자가 여러 요청에서 포인트를 동시에 사용할 수 있음. 단순 조회 후 차감은 잔액 초과 사용을 만들 수 있음

### 선택

포인트 차감은 `PointJpaRepository.findByUserIdWithLock()`에서 `SELECT ... FOR UPDATE`를 사용해 사용자 포인트 row를 잠근 뒤 수행

### 근거

포인트는 사용자별 잔액 원장이므로 정확성이 처리량보다 중요. 상품 재고와 달리 key cardinality가 사용자 단위로 분산되므로 row lock 병목도 제한적

--- 

## 7. 공통 응답과 에러 코드 구조

### 상황

API 호출자는 성공/실패 여부, 에러 코드, 메시지, 데이터를 일관된 형식으로 받아야 함 \
예약/결제/재고처럼 실패 원인이 다양한 API에서는 HTTP status만으로는 client가 재시도 가능 여부나 사용자 안내 문구를 판단하기 어려움

### 선택

공통 응답은 `ApiResponse`로 통일하고, 도메인별 에러는 `ErrorCode` 인터페이스를 구현한 enum으로 관리 \
비즈니스 예외는 `ApplicationException`에 `ErrorCode`를 담아 던지고, `GlobalExceptionHandler`가 HTTP status와 응답 body를 결정

```text
Domain Service
  -> throw new ApplicationException(DomainErrorCode.X)
  -> GlobalExceptionHandler
  -> ResponseEntity<ApiResponse.fail(errorCode)>
```

### 근거

Controller와 Service마다 응답 형식을 직접 만들면 에러 body가 쉽게 달라짐
- 공통 handler로 모으면 validation 실패, JSON 파싱 실패, 비즈니스 예외, 예상치 못한 서버 오류를 한 곳에서 다룰 수 있음
- idempotency 응답 저장 정책이 `ApiResponse.success`를 기준으로 동작하므로, 성공/실패 구조가 명확해야 성공 응답만 캐싱하고 실패 응답은 재시도 가능하게 삭제할 수 있음

### 트레이드오프

- (+): client가 `success`, `code`, `message`, `data`를 항상 같은 방식으로 해석할 수 있음
- (+): 도메인별 에러 코드가 분리되어 재고, 결제, 포인트 실패를 명확히 구분할 수 있음
- (+): 멱등성 응답 캐싱처럼 공통 응답 구조에 의존하는 횡단 관심사를 구현하기 쉬움
- (-): 모든 API를 envelope으로 감싸므로 HTTP status만 사용하는 단순 API보다 응답이 장황해짐
- (-): binary download, streaming, 외부 webhook 수신 같은 API에는 별도 응답 정책이 필요할 수 있음

--- 

## 8. 패키지 분리 판단

### 선택

도메인 단위 패키지 구조 사용

```text
booking / order / payment / point / product / stock / user / interfaces / global
```

### 근거

예약 흐름은 여러 도메인을 엮는 use case이고, 각 도메인은 자신의 상태와 규칙을 갖음

`BookingFacade`는 HTTP 요청을 예약 use case로 연결하고, `BookingSagaService`는 Saga 오케스트레이션과 보상 흐름을 담당

--- 

## 9. 예약 상태 모델: Order가 처리 상태를 소유하고 Booking은 확정 예약만 표현

### 상황

Booking API는 주문 생성, 재고 확정, 결제 승인, 포인트 차감 이후에 최종 예약 정보를 만듦 \
처리 중/실패/성공 상태를 `orders`, `payments`, `bookings`가 모두 가지면 같은 이벤트를 여러 테이블에 중복 표현하게 됨

### 선택

`Order`가 예약/결제 유스케이스의 처리 상태를 소유

```text
Order(PENDING)
  -> 결제/포인트/예약 생성 성공
  -> Order(CONFIRMED)

Order(PENDING)
  -> 중간 단계 실패
  -> 결제 승인 취소, 포인트 환불, 재고 복구
  -> Order(FAILED)
```

`Booking`은 별도 status를 두지 않고, row가 생성되면 확정 예약 정보로 해석 \
-> 예약 취소 API가 없는 현재 범위에서는 `Booking.cancel()`과 Booking 전용 취소 에러 코드가 없음

### 근거

현재 API 범위는 주문서 진입과 결제/예약 완료를 포함하며, 확정 예약을 사용자가 취소하는 API는 없음 \
결제 취소는 확정 예약 취소가 아니라 Saga 보상 과정에서 이미 승인된 PG 결제를 되돌리는 동작이기 때문에 `Payment.cancel()`에 둬야 함

처리 상태는 `orders.status`, 결제 상태는 `payments.status`, 확정 예약 식별 정보는 `bookings`가 담당

### 트레이드오프

- (+) Booking이 실패/대기/취소 같은 처리 상태를 중복 관리하지 않음
- (+) 현재 API 범위에 없는 예약 취소 도메인 규칙을 미리 만들지 않음
- (-) 향후 사용자 예약 취소 API가 추가되면 `BookingStatus` 또는 별도 cancellation 이력 모델을 다시 도입해야 함

---

## 10. Booking 번호 용어 통일

### 상황

코드와 API는 `BookingController`, `BookingService`, `/api/v1/bookings`, `bookings` table처럼 Booking 용어를 중심으로 구성되어 있음  \
반면 응답 필드와 DB column이 `reservationNumber`, `reservation_number`이면 같은 도메인 개념을 서로 다른 용어로 표현하게 됨

### 선택

확정 예약 식별자는 `bookingNumber`로 통일

```text
Booking.bookingNumber
BookingResponse.bookingNumber
bookings.booking_number
```

### 근거

내부 도메인, API path, table 이름이 모두 Booking을 사용하므로 응답 필드와 column도 같은 용어를 사용하는 편이 읽기 쉽고 변경 범위 추적도 단순함

---

## 11. 상품 Redis Cache 제거

### 상황

`ProductService.getProduct()`에 Redis cache를 적용하면 Redis 역직렬화 설정에 따라 `Product`가 아닌 `LinkedHashMap`으로 복원될 수 있음 \
-> 실제 동시 예약 통합 테스트에서 cache hit 요청이 `ClassCastException`으로 실패

### 선택

상품 조회 cache를 제거하고 Redis 사용처를 재고 선점과 멱등성 처리에 집중

### 근거

현재 핵심 병목은 상품 조회보다 한정 수량 재고 선점과 중복 요청 방지에 있음 \
JPA entity를 그대로 Redis cache에 저장하는 방식은 타입 안정성과 lazy/loading 경계 측면에서 위험이 있어, 과제 범위에서는 cache를 제거하는 편이 안정적

---

## 12. 결제 거래 이력 unique 제약

### 상황

PG 승인과 PG 취소는 같은 `pgTransactionId`를 공유할 수 있음 \
`payment_transactions.pg_transaction_id`에 단일 unique 제약을 두면 승인 이력 저장 이후 취소 이력을 저장할 때 unique 제약 위반이 발생함

### 선택

결제 거래 이력은 `(pg_transaction_id, type)` 복합 unique로 관리

```text
APPROVE + pgTransactionId
CANCEL  + pgTransactionId
```

### 근거

같은 PG 거래에 대한 승인/취소 이벤트를 모두 남기면서, 같은 타입의 거래가 중복 저장되는 것은 방지할 수 있음
