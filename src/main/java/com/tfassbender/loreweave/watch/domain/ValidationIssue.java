package com.tfassbender.loreweave.watch.domain;

import java.nio.file.Path;

public record ValidationIssue(
        ValidationCategory category,
        Path filePath,
        String message) {

    public static ValidationIssue error(ValidationCategory category, Path filePath, String message) {
        if (category.severity() != ValidationCategory.Severity.ERROR) {
            throw new IllegalArgumentException("Not an error category: " + category);
        }
        return new ValidationIssue(category, filePath, message);
    }

    public static ValidationIssue warning(ValidationCategory category, Path filePath, String message) {
        if (category.severity() != ValidationCategory.Severity.WARNING) {
            throw new IllegalArgumentException("Not a warning category: " + category);
        }
        return new ValidationIssue(category, filePath, message);
    }

    /**
     * Watcher divergence: informational issues (currently only {@code #todo} tag
     * notices). Treated as a third severity tier — they don't fail the headless
     * check command and render in their own UI section.
     */
    public static ValidationIssue info(ValidationCategory category, Path filePath, String message) {
        if (category.severity() != ValidationCategory.Severity.INFO) {
            throw new IllegalArgumentException("Not an info category: " + category);
        }
        return new ValidationIssue(category, filePath, message);
    }

    public ValidationCategory.Severity severity() {
        return category.severity();
    }

    public boolean isError() {
        return category.isError();
    }

    public boolean isWarning() {
        return category.isWarning();
    }

    public boolean isInfo() {
        return category.isInfo();
    }
}
