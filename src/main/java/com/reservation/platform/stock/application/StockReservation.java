package com.reservation.platform.stock.application;

public record StockReservation(
        Long productId,
        boolean redisReserved,
        boolean dbConfirmed
) {

    public static StockReservation redisReserved(Long productId) {
        return new StockReservation(productId, true, false);
    }

    public static StockReservation dbConfirmed(Long productId) {
        return new StockReservation(productId, false, true);
    }

    public StockReservation confirmDb() {
        return new StockReservation(productId, redisReserved, true);
    }
}
