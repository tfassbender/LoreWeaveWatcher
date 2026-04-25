package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.Note;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;

import java.util.List;

/**
 * Outcome of parsing a single note file. Errors short-circuit index inclusion;
 * warnings ride along with a successfully parsed note.
 */
public sealed interface ParseResult {

    List<ValidationIssue> issues();

    record Success(Note note, List<ValidationIssue> issues) implements ParseResult {
        public Success {
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (issues.stream().anyMatch(ValidationIssue::isError)) {
                throw new IllegalArgumentException("Success must not carry error-severity issues");
            }
        }
    }

    record Failure(List<ValidationIssue> issues) implements ParseResult {
        public Failure {
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (issues.isEmpty() || issues.stream().noneMatch(ValidationIssue::isError)) {
                throw new IllegalArgumentException("Failure must carry at least one error");
            }
        }
    }
}
