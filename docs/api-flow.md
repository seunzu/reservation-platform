# API Flow

HTTP API의 요청/응답 예시와 애플리케이션 내부 호출 흐름 정리

## GET `/api/v1/checkout`

주문서 진입 시점에 상품 정보, 현재 조회 가능한 재고, 사용자의 가용 포인트를 반환 \
재고/포인트 값은 화면 표시용 스냅샷이며, 최종 구매 가능 여부는 Booking API에서 다시 검증

Request:

```http
GET /api/v1/checkout?productId=1&userId=1
```

Response:

```json
{
  "success": true,
  "data": {
    "productId": 1,
    "productName": "Limited Stay",
    "price": 120000,
    "checkInAt": "2026-05-03T15:00:00",
    "checkOutAt": "2026-05-04T11:00:00",
    "saleOpenAt": "2026-05-03T00:00:00",
    "remainingStock": 10,
    "availablePoint": 30000
  }
}
```

Error response example:

```json
{
  "success": false,
  "code": "S001",
  "message": "재고가 부족합니다.",
  "data": null
}
```

Flow:

```text
BookingController.checkout()
  -> BookingFacade.checkout()
      -> UserService.getUser()
      -> ProductService.getProduct()
      -> StockService.getRemaining()
          -> StockRedisRepository.getRemaining()
          -> Redis miss/failure: StockRepository.findByProductId()
      -> PointService.getAvailablePoint()
  <- CheckoutResponse
```

---

## POST `/api/v1/bookings`

주문 정보를 입력받아 재고 선점, 결제 승인, 포인트 차감, 예약 확정을 처리 \
중복 결제를 방지하기 위해 `X-Idempotency-Key` 헤더가 필수

Request:

```http
POST /api/v1/bookings?userId=1
X-Idempotency-Key: 01HX2D6M7V6R3R09VY6F9S5W4B
Content-Type: application/json
```

```json
{
  "productId": 1,
  "orderToken": "ORDER-20260503-0001",
  "primaryMethod": "CREDIT_CARD",
  "usePoint": true,
  "pointAmount": 20000,
  "cardAmount": 100000
}
```

`primaryMethod`는 `CREDIT_CARD`, `Y_PAY`, `POINT`를 표현. 신용카드와 Y페이는 `primaryMethod` 하나만 선택할 수 있으므로 서로 혼용되지 않음. 포인트는 `usePoint=true`와 `pointAmount>0`일 때 복합 결제 금액으로 사용되며, `primaryMethod=POINT`, `cardAmount=0`, `pointAmount=상품가격`이면 포인트 단독 결제도 가능

Response:

```json
{
  "success": true,
  "data": {
    "orderId": 1,
    "orderToken": "ORDER-20260503-0001",
    "reservationNumber": "RES-1234ABCD",
    "orderStatus": "CONFIRMED",
    "totalAmount": 120000,
    "cardAmount": 100000,
    "pointAmount": 20000,
    "createdAt": "2026-05-03T00:00:01"
  }
}
```

Flow:

```text
IdempotencyInterceptor.preHandle()
  -> X-Idempotency-Key 필수 확인
  -> Redis SET NX EX idempotency:{key}=PROCESSING
  -> 완료 응답 캐시가 있으면 Controller 진입 차단 후 캐시 응답 반환

BookingController.book()
  -> BookingFacade.book()
      -> UserService.getUser()
      -> ProductService.getProduct()
      -> PaymentValidator.validate()
      -> StockService.reserve()
          -> Redis 재고 key가 없으면 DB 원장 기준으로 초기화
          -> 초기화 구간은 짧은 Redisson RLock으로 보호
          -> Redis DECR로 재고 선점
          -> Redis 장애 시 StockDbLockService.decreaseWithLock()으로 DB 즉시 차감
      -> BookingSagaService.processBooking()
          -> OrderService.create()
          -> StockService.confirm()
              -> StockDbLockService.decreaseWithLock()
              -> Redis 선점분을 DB 원장에 확정 차감
          -> PaymentService.create()
          -> PaymentService.approve()
              -> CompositePaymentProcessor.process()
              -> PgClient.approve()
          -> PointService.use()
              -> PointJpaRepository.findByUserIdWithLock()
          -> BookingService.create()
              -> 확정 예약 정보 생성. 처리 상태는 Order가 소유
          -> OrderService.confirm()
      <- BookingResponse

IdempotencyResponseAdvice.beforeBodyWrite()
  -> 성공 응답 바디만 Redis에 24시간 저장
  -> 실패 응답이면 PROCESSING key 삭제 후 재시도 허용
```

Failure compensation:

```text
BookingSagaService.processBooking() 실패
  -> PaymentService.cancel()      이미 PG 승인된 경우 승인 취소
  -> PointService.refund()        포인트 차감 완료 시 환불
  -> OrderService.fail()          주문 실패 상태 기록
  -> StockService.release()       Redis 선점분/DB 확정분 복구
      -> DB 복구가 필요하면 StockDbLockService.increaseWithLock()
  -> 원 예외 재전파
```
