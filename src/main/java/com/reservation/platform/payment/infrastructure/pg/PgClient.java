package com.reservation.platform.payment.infrastructure.pg;

import com.reservation.platform.payment.infrastructure.pg.dto.PgRequest;
import com.reservation.platform.payment.infrastructure.pg.dto.PgResponse;

public interface PgClient {

    PgResponse approve(PgRequest request);
    PgResponse cancel(String pgTransactionId);
}