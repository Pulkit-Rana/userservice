package com.syncnest.userservice.Validators;

import jakarta.validation.Constraint;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordMatchValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMatch {
    String message() default "Passwords does not match";

    Class<?>[] groups() default {};

    Class<?>[] payload() default {};

    String passwordField();

    String passwordConfirmationField();
}
