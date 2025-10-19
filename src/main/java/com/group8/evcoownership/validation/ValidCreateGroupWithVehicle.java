package com.group8.evcoownership.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CreateGroupWithVehicleValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCreateGroupWithVehicle {
    String message() default "Invalid group and vehicle data";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
