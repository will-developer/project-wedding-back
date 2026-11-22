package br.com.wedding.api.guest;

import jakarta.persistence.EnumeratedValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RsvpStatus {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    DECLINED("declined");

    @EnumeratedValue
    private final String databaseValue;
}
