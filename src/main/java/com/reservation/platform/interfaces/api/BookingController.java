package com.reservation.platform.interfaces.api;

import com.reservation.platform.booking.application.BookingFacade;
import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.booking.application.dto.BookingResponse;
import com.reservation.platform.booking.application.dto.CheckoutResponse;
import com.reservation.platform.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacade bookingFacade;

    // 주문서 진입 - 상품 정보 + 가용 포인트 조회
    @GetMapping("/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @RequestParam Long productId,
            @RequestParam Long userId
    ) {
        CheckoutResponse response = bookingFacade.checkout(productId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 결제 및 예약 완료
    @PostMapping("/bookings")
    public ResponseEntity<ApiResponse<BookingResponse>> book(
            @RequestBody @Valid BookingRequest request,
            @RequestParam Long userId
    ) {
        BookingResponse response = bookingFacade.book(request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}