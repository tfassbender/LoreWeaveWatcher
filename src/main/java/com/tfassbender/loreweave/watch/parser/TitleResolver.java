package com.tfassbender.loreweave.watch.parser;

/**
 * Resolves a display title using the precedence defined in
 * {@code doc/vault_schema.md}: frontmatter {@code title} → filename without
 * {@code .md}. The fallback to filename emits a {@code missing_title}
 * warning.
 */
public final class TitleResolver {

    public Resolved resolve(String frontmatterTitle, String filenameWithoutExt) {
        if (frontmatterTitle != null && !frontmatterTitle.isBlank()) {
            return new Resolved(frontmatterTitle.strip(), false);
        }
        if (filenameWithoutExt != null && !filenameWithoutExt.isBlank()) {
            return new Resolved(filenameWithoutExt.strip(), true);
        }
        return new Resolved("", true);
    }

    public record Resolved(String title, boolean missingTitleWarning) {}
}
