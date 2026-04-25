package org.example.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CypherDeleteParser {

    private static final Pattern REMOVE_PATTERN =
            Pattern.compile(
                    "MATCH \\(\\w+:(\\w+) \\{(\\w+):\\s*([^}]+)\\}\\).*REMOVE \\w+\\.(\\w+)",
                    Pattern.CASE_INSENSITIVE
            );

    public boolean isDeleteQuery(String query) {

        String upper =
                query.toUpperCase();

        return upper.contains("REMOVE")
                || upper.contains("DELETE");
    }

    public ParsedDelete parse(
            String query
    ) {

        Matcher matcher =
                REMOVE_PATTERN.matcher(query);

        if (!matcher.find()) {

            throw new RuntimeException(
                    "Unsupported delete query format"
            );
        }

        ParsedDelete parsed =
                new ParsedDelete();

        parsed.node = matcher.group(1);

        parsed.keyName = matcher.group(2);

        parsed.keyValue = matcher.group(3);

        parsed.property = matcher.group(4);

        return parsed;
    }
}