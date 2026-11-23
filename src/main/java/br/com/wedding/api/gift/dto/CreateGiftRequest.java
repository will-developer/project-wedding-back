package br.com.wedding.api.gift.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
public class CreateGiftRequest {

    @NotBlank
    @Size(max = 160)
    private String name;

    private String description;

    @Size(max = 2048)
    @URL(protocol = "https")
    private String imageUrl;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Min(1)
    private Integer totalQuantity;
}
