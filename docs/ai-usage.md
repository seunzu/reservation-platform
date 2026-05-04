# AI Usage

리뷰와 문서화, 테스트 작성 보조 도구로 활용

## 직접 설계 및 구현
- 도메인 모델링: `Order`는 처리 상태를 관리하고 `Booking`은 확정 예약 데이터를 표현하도록 책임 분리
- 재고 관리: Redis 기반 선점과 RDB 기반 확정 차감을 조합해 성능과 정합성 균형 설계
- Saga 보상 트랜잭션: 결제 실패 및 런타임 예외 발생 시 PG 취소, 포인트 환불, 주문 실패, 재고 복구 흐름 구현
- 멱등성 인프라: Redis 기반 idempotency key 처리, 중복 요청 방지, 성공 응답 캐싱 구현

## AI 활용 범위 (Codex, Claude)

- 테스트 고도화: 대량의 테스트 케이스 초안 생성 및 다양한 실패 시나리오 보강
- 기술 학습 및 트레이드오프 검토: 구현 중 필요한 개념 검토하고, Redis 기반 선점/RDB 비관적 락/분산락/queue 기반 처리 방식의 장단점 비교
- 설계 리뷰: 도메인 책임 경계, 상태 모델, 용어 통일, 보상 흐름에 대한 대안 비교
- 문서화 보조: API flow, sequence diagram, test plan, architecture decisions 문서 작성 및 중복 점검

## 검증 방식
- AI가 제안한 코드는 도메인 규칙과 현재 코드 구조에 맞는지 직접 리뷰하고 수정한 뒤 반영
- Spring, Redis, MySQL, Testcontainers 공식 문서를 확인하며 구현 방향 검토
- 최종 검증: `./gradlew clean test --no-daemon`으로 전체 테스트 실행

