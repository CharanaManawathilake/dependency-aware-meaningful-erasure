package org.example.config;

import org.example.dependency.Property;
import org.example.dependency.Rule;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleLoader {

    public void loadRules(
            String ruleFile,
            HashMap<Property, ArrayList<Rule>> propertyInHead,
            HashMap<Property, ArrayList<Rule>> propertyInTail
    ) throws Exception {

        var parser = CSVFormat.DEFAULT.parse(
                Files.newBufferedReader(Paths.get(ruleFile))
        );

        for (var record : parser) {

            var rule = parseRule(record);

            propertyInHead
                    .computeIfAbsent(
                            rule.head,
                            a -> new ArrayList<>()
                    )
                    .add(rule);

            for (var tail : rule.tail) {

                propertyInTail
                        .computeIfAbsent(
                                tail,
                                a -> new ArrayList<>()
                        )
                        .add(rule);
            }
        }
    }

    public void loadSchema(
            String schemaFile,
            HashMap<String, String> nodeName2Key
    ) throws IOException {

        var parser = CSVFormat.DEFAULT.parse(
                Files.newBufferedReader(Paths.get(schemaFile))
        );

        for (var record : parser) {

            nodeName2Key.put(
                    record.get(0),
                    record.get(1)
            );
        }
    }

    public Rule parseRule(CSVRecord record)
            throws Exception {

        if (
                !record.get(0).equals("MATCH") ||
                        !record.get(2).equals("RETURN")
        ) {
            throw new Exception("Invalid rule format");
        }

        Rule rule = new Rule();

        rule.condition = record.get(1);

        Pattern pattern = Pattern.compile(
                "\\(([A-Za-z0-9_]+):([A-Za-z0-9_]+)\\)"
        );

        Matcher matcher =
                pattern.matcher(record.get(1));

        while (matcher.find()) {

            String left = matcher.group(1);
            String right = matcher.group(2);

            rule.node2Alias.put(left, right);
            rule.node2Alias.put(right, left);

            rule.nodes.add(right);
        }

        for (int i = 3; i < record.size(); i++) {

            String s = record.get(i);

            int first = s.indexOf('.');

            String node =
                    s.substring(0, first);

            String property =
                    s.substring(first + 1);

            if (i == 3) {

                rule.head = new Property(
                        rule.node2Alias.get(node),
                        property
                );

            } else {

                rule.tail.add(
                        new Property(
                                rule.node2Alias.get(node),
                                property
                        )
                );
            }
        }

        return rule;
    }
}
