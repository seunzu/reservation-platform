# Core Features

예약과 결제 흐름에서 중요한 동시성, 멱등성, 결제 확장, 장애 보상 설계를 정리
- 상세 설계 판단과 트레이드오프: [Architecture Decisions](DECISIONS.md)
- API 호출 흐름: [API Flow](api-flow.md), [Sequence Diagram](sequence-diagram.md)

## Summary

| 영역 | 구현 |
| --- | --- |
| 재고 정합성 | Redis `DECR` 선점 + MySQL `SELECT ... FOR UPDATE` 확정 차감 |
| Redis 장애 대응 | Redis 장애 시 DB 비관적 락 fallback |
| 멱등성 | `X-Idempotency-Key`, Redis `SET NX EX`, 성공 응답 캐시, `orders.order_token` unique |
| 결제 확장 | `PaymentProcessor` 전략과 `CompositePaymentProcessor` |
| 실패 보상 | `BookingSagaService`에서 PG 취소, 포인트 환불, 주문 실패, 재고 복구 수행 |
| 포인트 동시성 | 사용자 포인트 row `SELECT ... FOR UPDATE` |
| 상태 모델 | `Order`가 처리 상태를 소유하고 `Booking`은 확정 예약만 표현 |

---

## Stock Consistency

```text
BookingFacade.book()
  -> StockService.reserve()
      -> Redis key 없으면 DB 원장 기준 초기화
      -> Redis DECR로 원자적 선점
      -> Redis 장애 시 StockDbLockService.decreaseWithLock() fallback
  -> BookingSagaService.processBooking()
      -> StockService.confirm()
          -> StockDbLockService.decreaseWithLock()
          -> DB SELECT ... FOR UPDATE로 최종 재고 원장 차감
```

- Redis: 순간 트래픽의 빠른 재고 선점에 사용
- MySQL `stocks`: 최종 재고 원장으로 사용
- Redis 장애 시에도 DB 비관적 락으로 초과 판매를 방지

상세 판단: [Architecture Decisions - 재고 정합성](DECISIONS.md#1-재고-정합성-redis-선점--db-원장-확정)

---

## Idempotency

```text
IdempotencyInterceptor.preHandle()
  -> X-Idempotency-Key 필수 검증
  -> Redis SET NX EX로 PROCESSING 원자 선점
  -> PROCESSING이면 중복 요청 차단
  -> 완료 응답이 있으면 Controller 진입 없이 캐시 응답 반환

IdempotencyResponseAdvice.beforeBodyWrite()
  -> 성공 응답만 24시간 저장
  -> 실패 응답은 PROCESSING key 삭제 후 재시도 허용

DB 최후 방어
  -> orders.order_token unique constraint
```

상세 판단: [Architecture Decisions - 멱등성](DECISIONS.md#4-멱등성-http-idempotency-key--db-unique-constraint)

---

## Payment Extension

```text
PaymentService.approve()
  -> CompositePaymentProcessor.process()
      -> PaymentProcessor.supports(request)
      -> CreditCardProcessor 또는 YPayProcessor
      -> PgClient.approve()
```

- Booking API는 결제 수단별 승인 구현에 직접 의존하지 않음
- 신규 결제 수단은 `PaymentProcessor` 구현체 추가로 확장할 수 있음

상세 판단: [Architecture Decisions - 결제 확장성](DECISIONS.md#5-결제-확장성-paymentprocessor-전략)

---

## Failure Compensation

```text
BookingSagaService.processBooking()
  -> 단계별 처리 중 RuntimeException catch
  -> compensate()
      -> PG 승인 완료 시 cancel
      -> 포인트 차감 완료 시 refund
      -> 주문 생성 완료 시 fail
      -> 재고 선점/확정 완료 시 release
      -> 보상 실패 시 error log 기록
```

상세 판단: [Architecture Decisions - Saga](DECISIONS.md#3-결제예약-처리-2pc-대신-오케스트레이션-saga)

---

## Point Concurrency

```text
PointService.use()
  -> PointRepository.findByUserIdWithLock()
  -> SELECT ... FOR UPDATE
  -> 잔액 검증 후 차감
  -> point_transactions 이력 저장
```

- 포인트는 사용자별 잔액 원장이므로 정확성을 우선
- 상품 재고와 달리 사용자 row 단위로 lock 경합이 분산

