package com.group8.evcoownership.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Annotation để validate CreateGroupWithVehicleRequest với validation động
 * dựa trên loại xe (xe máy hoặc xe ô tô)
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CreateGroupWithVehicleDynamicValidator.class)
@Documented
public @interface ValidCreateGroupWithVehicleDynamic {
    String message() default "Invalid vehicle data";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
