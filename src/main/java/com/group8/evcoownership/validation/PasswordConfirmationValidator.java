package com.group8.evcoownership.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;

public class PasswordConfirmationValidator implements ConstraintValidator<PasswordConfirmation, Object> {

    private String passwordField;
    private String confirmPasswordField;

    @Override
    public void initialize(PasswordConfirmation constraintAnnotation) {
        this.passwordField = constraintAnnotation.passwordField();
        this.confirmPasswordField = constraintAnnotation.confirmPasswordField();
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        try {
            Field passwordFieldObj = obj.getClass().getDeclaredField(passwordField);
            Field confirmPasswordFieldObj = obj.getClass().getDeclaredField(confirmPasswordField);

            passwordFieldObj.setAccessible(true);
            confirmPasswordFieldObj.setAccessible(true);

            String password = (String) passwordFieldObj.get(obj);
            String confirmPassword = (String) confirmPasswordFieldObj.get(obj);

            if (password == null || confirmPassword == null) {
                return false;
            }

            return password.equals(confirmPassword);

        } catch (Exception e) {
            return false;
        }
    }
}
