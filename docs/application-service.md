# Application Service

예약 유스케이스의 애플리케이션 서비스 책임 정리

## Booking Use Case Layers

예약 유스케이스는 HTTP 진입, 유스케이스 조합, Saga 오케스트레이션, 도메인 저장 책임으로 분리

```text
interfaces/api/BookingController
  -> HTTP 요청/응답 변환

booking/application/BookingFacade
  -> Checkout/Booking API의 진입점
  -> 사용자, 상품, 결제 금액, 재고 선점처럼 유스케이스 시작 전에 필요한 검증과 조회를 조합

booking/application/BookingSagaService
  -> Booking API의 Saga 오케스트레이션
  -> 주문 생성, DB 재고 확정, 결제 승인, 포인트 차감, 예약 생성, 주문 확정
  -> 실패 시 PG 취소, 포인트 환불, 주문 실패, 재고 복구 보상

booking/application/BookingService
  -> Booking 도메인 생성과 저장만 담당
  -> Booking은 별도 처리 상태 없이 생성되면 확정 예약 정보로 해석
```

---

## Responsibility Boundary

### `BookingController`
HTTP 입출력과 validation 경계만 담당. 비즈니스 흐름을 직접 조합하지 않음

### `BookingFacade`
API use case의 시작점. 사용자, 상품, 결제 금액, 재고 선점처럼 요청을 처리하기 전에 필요한 검증과 조회를 모음 \
Checkout API는 이 계층에서 조회 응답을 만들고, Booking API는 재고 선점 이후 Saga로 처리를 넘김

### `BookingSagaService`
여러 도메인과 외부 PG 호출이 섞인 예약 확정 흐름을 오케스트레이션 \
외부 PG는 DB transaction rollback 대상이 아니므로, 실패 시 이미 완료된 결제, 포인트, 주문, 재고 작업을 보상

### `BookingService` 
Booking 도메인의 생성과 저장만 담당. 현재 모델에서 `Booking` row는 확정 예약을 의미, 처리 상태는 `orders.status`, 결제 상태는 `payments.status`가 담당
