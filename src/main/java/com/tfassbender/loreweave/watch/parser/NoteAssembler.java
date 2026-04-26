package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.Link;
import com.tfassbender.loreweave.watch.domain.Note;
import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Composes the per-file parse pipeline into a {@link ParseResult}. Collects
 * errors and warnings along the way. Forward links are emitted unresolved;
 * resolution to target notes happens in the index builder, which has the
 * global vault context this class intentionally doesn't.
 */
public class NoteAssembler {

    private final FrontmatterParser frontmatterParser;
    private final MarkdownBodyParser bodyParser;
    private final WikiLinkExtractor wikiLinkExtractor;
    private final HashtagExtractor hashtagExtractor;
    private final TitleResolver titleResolver;

    public NoteAssembler() {
        this(new FrontmatterParser(), new MarkdownBodyParser(), new WikiLinkExtractor(),
                new HashtagExtractor(), new TitleResolver());
    }

    public NoteAssembler(FrontmatterParser frontmatterParser,
                         MarkdownBodyParser bodyParser,
                         WikiLinkExtractor wikiLinkExtractor,
                         HashtagExtractor hashtagExtractor,
                         TitleResolver titleResolver) {
        this.frontmatterParser = frontmatterParser;
        this.bodyParser = bodyParser;
        this.wikiLinkExtractor = wikiLinkExtractor;
        this.hashtagExtractor = hashtagExtractor;
        this.titleResolver = titleResolver;
    }

    public ParseResult assemble(Path sourcePath, String rawContent) {
        List<ValidationIssue> issues = new ArrayList<>();

        FrontmatterParser.Split split = frontmatterParser.split(rawContent == null ? "" : rawContent);
        FrontmatterParser.Parsed parsed = frontmatterParser.parse(split.yamlText(), sourcePath);
        issues.addAll(parsed.issues());
        if (parsed.issues().stream().anyMatch(ValidationIssue::isError)) {
            return new ParseResult.Failure(issues);
        }

        Map<String, Object> fm = parsed.fields();
        String type = asString(fm.get("type"));

        if (type == null || type.isBlank()) {
            issues.add(ValidationIssue.error(ValidationCategory.MISSING_REQUIRED_FIELDS, sourcePath,
                    "required field 'type' is missing"));
            return new ParseResult.Failure(issues);
        }

        String fmTitle = asString(fm.get("title"));
        String fmSummary = asString(fm.get("summary"));
        Integer schemaVersion = asInt(fm.get("schema_version"));
        List<String> aliases = asStringList(fm.get("aliases"));
        Map<String, Object> metadata = asMap(fm.get("metadata"));

        String filenameWithoutExt = filenameWithoutExt(sourcePath);
        TitleResolver.Resolved title = titleResolver.resolve(fmTitle, filenameWithoutExt);
        if (title.missingTitleWarning()) {
            issues.add(ValidationIssue.warning(ValidationCategory.MISSING_TITLE, sourcePath,
                    "no 'title' field; resolved to '" + title.title() + "'"));
        }
        if (fmSummary == null || fmSummary.isBlank()) {
            issues.add(ValidationIssue.warning(ValidationCategory.MISSING_SUMMARY, sourcePath,
                    "no 'summary' field; /search results will omit a summary"));
        }
        if (schemaVersion == null) {
            issues.add(ValidationIssue.warning(ValidationCategory.MISSING_SCHEMA_VERSION, sourcePath,
                    "no 'schema_version' field; defaulting to 1"));
            schemaVersion = 1;
        }

        var ast = bodyParser.parse(split.body());
        List<Link> links = wikiLinkExtractor.extract(ast);
        List<String> tags = hashtagExtractor.extract(ast);

        Note note = new Note(
                type, title.title(),
                fmSummary == null ? "" : fmSummary,
                schemaVersion,
                aliases, metadata, split.body(), links, tags, sourcePath);
        return new ParseResult.Success(note, issues);
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> asStringList(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of(String.valueOf(o));
    }

    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return Map.of();
    }

    private static String filenameWithoutExt(Path path) {
        if (path == null) return null;
        Path name = path.getFileName();
        if (name == null) return null;
        String s = name.toString();
        int dot = s.lastIndexOf('.');
        return dot < 0 ? s : s.substring(0, dot);
    }
}
