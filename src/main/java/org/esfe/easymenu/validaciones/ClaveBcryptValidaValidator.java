package org.esfe.easymenu.validaciones;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ClaveBcryptValidaValidator implements ConstraintValidator<ClaveBcryptValida, String> {

    private static final int MAX_BYTES_BCRYPT = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return admite(value);
    }

    public static boolean admite(String value) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES_BCRYPT;
    }
}
