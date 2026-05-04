package com.reservation.platform.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("예약/결제 플랫폼")
                        .description("특정 시간(00시)에 오픈되는\n" +
                                "'초특가 숙소 상품(10개 한정)'에 대한 선착순 예약\n" +
                                "시스템")
                        .version("v1.0.0"));
    }
}
