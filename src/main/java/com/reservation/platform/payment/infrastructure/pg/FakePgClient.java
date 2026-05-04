package com.reservation.platform.payment.infrastructure.pg;

import com.reservation.platform.payment.infrastructure.pg.dto.PgRequest;
import com.reservation.platform.payment.infrastructure.pg.dto.PgResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class FakePgClient implements PgClient {

    @Override
    public PgResponse approve(PgRequest request) {
        log.info("[FakePG] 결제 요청 orderToken={}, amount={}", request.orderToken(), request.amount());

        // 카드 한도 초과: amount < 100만원
        if (request.amount() >= 1_000_000) {
            return new PgResponse(
                    null,
                    false,
                    "한도 초과",
                    "{\"code\":\"LIMIT_EXCEEDED\"}"
            );
        }

        String pgTransactionId = "PG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new PgResponse(
                pgTransactionId,
                true,
                null,
                "{\"code\":\"SUCCESS\",\"pgTransactionId\":\"" + pgTransactionId + "\"}"
        );
    }

    @Override
    public PgResponse cancel(String pgTransactionId) {
        log.info("[FakePG] 결제 취소 pgTransactionId={}", pgTransactionId);
        return new PgResponse(pgTransactionId, true, null,
                "{\"code\":\"CANCELLED\",\"pgTransactionId\":\"" + pgTransactionId + "\"}");
    }
}