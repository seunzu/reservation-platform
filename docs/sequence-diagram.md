# Sequence Diagrams

## Checkout API

상품 정보, 현재 조회 가능한 재고, 가용 포인트를 반환 \
이 값은 화면 표시용 스냅샷, 최종 예약 가능 여부는 Booking API에서 다시 검증

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as BookingController
    participant Facade as BookingFacade
    participant User as UserService
    participant Product as ProductService
    participant Stock as StockService
    participant Redis as Redis
    participant StockDB as StockRepository
    participant Point as PointService

    Client->>Controller: GET /api/v1/checkout?productId&userId
    Controller->>Facade: checkout(productId, userId)
    Facade->>User: getUser(userId)
    User-->>Facade: user
    Facade->>Product: getProduct(productId)
    Product-->>Facade: product
    Facade->>Stock: getRemaining(productId)
    Stock->>Redis: GET stock:product:{productId}
    alt Redis hit
        Redis-->>Stock: remaining
    else Redis miss or failure
        Stock->>StockDB: findByProductId(productId)
        StockDB-->>Stock: remaining
    end
    Stock-->>Facade: remaining
    Facade->>Point: getAvailablePoint(userId)
    Point-->>Facade: availablePoint
    Facade-->>Controller: CheckoutResponse
    Controller-->>Client: 200 OK
```

## Booking API - Success

멱등성 검증, 재고 선점, 결제 승인, 포인트 차감, 예약 확정을 순서대로 처리 \
현재 구현은 오케스트레이션 Saga 방식

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Idem as IdempotencyInterceptor
    participant Controller as BookingController
    participant Facade as BookingFacade
    participant Stock as StockService
    participant StockDBLock as StockDbLockService
    participant Redis as Redis
    participant Saga as BookingSagaService
    participant Order as OrderService
    participant Payment as PaymentService
    participant Processor as PaymentProcessor
    participant PG as PgClient
    participant Point as PointService
    participant Booking as BookingService
    participant Advice as IdempotencyResponseAdvice

    Client->>Idem: POST /api/v1/bookings + X-Idempotency-Key
    Idem->>Redis: SET idempotency:{key}=PROCESSING NX EX 300
    Redis-->>Idem: OK
    Idem->>Controller: continue

    Controller->>Facade: book(request, userId)
    Facade->>Facade: user/product/payment validation
    Facade->>Stock: reserve(productId)
    Stock->>Redis: EXISTS stock:product:{productId}
    alt Redis key missing
        Stock->>Redis: acquire lock:stock:init:{productId}
        Stock->>Stock: load DB stock remaining
        Stock->>Redis: SET stock:product:{productId}=DB remaining
        Stock->>Redis: release init lock
    end
    Stock->>Redis: DECR stock:product:{productId}
    alt remaining >= 0
        Redis-->>Stock: reserved
    else remaining < 0
        Stock->>Redis: INCR stock:product:{productId}
        Stock-->>Facade: Stock exhausted
    end
    Stock-->>Facade: StockReservation(redisReserved=true)

    Facade->>Saga: processBooking(request, userId, product, reservation)
    Saga->>Order: create(orderToken)
    Order-->>Saga: Order(PENDING)
    Saga->>Stock: confirm(reservation)
    Stock->>StockDBLock: decreaseWithLock(productId)
    StockDBLock-->>Stock: DB stock decreased with SELECT FOR UPDATE
    Stock-->>Saga: StockReservation(dbConfirmed=true)
    Saga->>Payment: create(order, request)
    Payment-->>Saga: Payment(PENDING)
    Saga->>Payment: approve(payment, request)
    Payment->>Processor: process(payment, request)
    Processor->>PG: approve(PgRequest)
    PG-->>Processor: PgResponse(success)
    Processor-->>Payment: PaymentResult(success)
    Payment-->>Saga: Payment(COMPLETED)
    Saga->>Point: use(userId, orderId, pointAmount)
    Point-->>Saga: point transaction saved
    Saga->>Booking: create(order, product)
    Booking-->>Saga: Booking(reservationNumber)
    Saga->>Order: confirm(orderId)
    Order-->>Saga: Order(CONFIRMED)
    Saga-->>Facade: BookingResponse
    Facade-->>Controller: BookingResponse
    Controller-->>Advice: response body
    Advice->>Redis: SET idempotency:{key}=response EX 86400
    Controller-->>Client: 200 OK
```

## Booking API - Redis Fallback

Redis 장애 시 재고 선점은 DB 비관적 락 경로로 전환됨

```mermaid
sequenceDiagram
    autonumber
    participant Facade as BookingFacade
    participant Stock as StockService
    participant StockDBLock as StockDbLockService
    participant Redis as Redis
    participant DB as MySQL stocks

    Facade->>Stock: reserve(productId)
    Stock->>Redis: DECR stock:product:{productId}
    Redis--xStock: RedisConnectionFailureException
    Stock->>StockDBLock: decreaseWithLock(productId)
    StockDBLock->>DB: SELECT * FROM stocks WHERE product_id=? FOR UPDATE
    DB-->>Stock: locked stock row
    StockDBLock->>DB: remaining = remaining - 1
    Stock-->>Facade: StockReservation(dbConfirmed=true)
```

## Booking API - Failure Compensation

Saga 중간에 실패하면 이미 성공한 단계를 보상 \
보상은 가능한 한 독립 트랜잭션으로 커밋되어 원 실패와 분리됨

```mermaid
sequenceDiagram
    autonumber
    participant Saga as BookingSagaService
    participant Payment as PaymentService
    participant PG as PgClient
    participant Point as PointService
    participant Order as OrderService
    participant Stock as StockService
    participant Redis as Redis
    participant StockDBLock as StockDbLockService

    Saga->>Saga: RuntimeException catch
    alt payment completed
        Saga->>Payment: cancel(payment)
        Payment->>PG: cancel(pgTransactionId)
        PG-->>Payment: cancel success
        Payment-->>Saga: Payment(CANCELLED)
    else cancel failed
        Saga->>Saga: write error log
    end
    alt point used
        Saga->>Point: refund(userId, orderId, amount)
        Point-->>Saga: refund transaction saved
    else refund failed
        Saga->>Saga: write error log
    end
    alt order created
        Saga->>Order: fail(orderId)
        Order-->>Saga: Order(FAILED)
    else fail status failed
        Saga->>Saga: write error log
    end
    Saga->>Stock: release(stockReservation)
    alt redisReserved
        Stock->>Redis: INCR stock:product:{productId}
    end
    alt dbConfirmed
        Stock->>StockDBLock: increaseWithLock(productId)
        StockDBLock-->>Stock: DB stock increased with SELECT FOR UPDATE
    end
    alt stock release failed
        Saga->>Saga: write error log
    end
    Saga-->>Saga: rethrow original exception
```

## Idempotency State

```mermaid
stateDiagram-v2
    [*] --> Empty
    Empty --> Processing: SET NX EX succeeds
    Empty --> CachedResponse: existing completed response
    Processing --> DuplicateRejected: same key while running
    Processing --> CachedResponse: response advice saves body
    CachedResponse --> CachedResponse: same key returns cached response
    Processing --> Expired: TTL 5 minutes
    CachedResponse --> Expired: TTL 24 hours
    Expired --> Empty
```

## Order/Payment State And Booking Creation

```mermaid
stateDiagram-v2
    [*] --> OrderPending: OrderService.create
    OrderPending --> PaymentPending: PaymentService.create
    PaymentPending --> PaymentCompleted: PG approve success
    PaymentPending --> PaymentFailed: PG approve failure
    PaymentCompleted --> BookingCreated: BookingService.create
    BookingCreated --> OrderConfirmed: OrderService.confirm
    PaymentCompleted --> PaymentCancelled: compensation cancel
    OrderPending --> OrderFailed: compensation fail
```
