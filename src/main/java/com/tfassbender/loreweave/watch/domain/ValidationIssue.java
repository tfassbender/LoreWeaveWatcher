package com.tfassbender.loreweave.watch.domain;

import java.nio.file.Path;

public record ValidationIssue(
        ValidationCategory category,
        Path filePath,
        String message) {

    public static ValidationIssue error(ValidationCategory category, Path filePath, String message) {
        if (!category.isError()) {
            throw new IllegalArgumentException("Not an error category: " + category);
        }
        return new ValidationIssue(category, filePath, message);
    }

    public static ValidationIssue warning(ValidationCategory category, Path filePath, String message) {
        if (category.isError()) {
            throw new IllegalArgumentException("Not a warning category: " + category);
        }
        return new ValidationIssue(category, filePath, message);
    }

    public boolean isError() {
        return category.isError();
    }
}
