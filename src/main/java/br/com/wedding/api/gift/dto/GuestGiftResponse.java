package br.com.wedding.api.gift.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GuestGiftResponse {

    private final UUID id;

    private final String name;

    private final String description;

    private final String imageUrl;

    private final BigDecimal price;

    private final Integer availableQuantity;
}
