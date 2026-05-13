package org.example.config;

import jakarta.annotation.PostConstruct;
import org.example.dependency.Property;
import org.example.dependency.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;

@Component
public class DependencyConfig {

    public HashMap<Property, ArrayList<Rule>>
            propertyInHead = new HashMap<>();

    public HashMap<Property, ArrayList<Rule>>
            propertyInTail = new HashMap<>();

    public HashMap<String, String>
            nodeName2Key = new HashMap<>();

    @Autowired
    private RuleLoader ruleLoader;

    @PostConstruct
    public void init() throws Exception {

        var rulesResource =
                getClass()
                        .getClassLoader()
                        .getResource("rules_twitter.csv");

        var schemaResource =
                getClass()
                        .getClassLoader()
                        .getResource("schema_twitter.csv");

        if (rulesResource == null) {
            throw new RuntimeException(
                    "rules file not found"
            );
        }

        if (schemaResource == null) {
            throw new RuntimeException(
                    "schema file not found"
            );
        }

        ruleLoader.loadRules(
                java.nio.file.Paths
                        .get(rulesResource.toURI())
                        .toString(),
                propertyInHead,
                propertyInTail
        );

        ruleLoader.loadSchema(
                java.nio.file.Paths
                        .get(schemaResource.toURI())
                        .toString(),
                nodeName2Key
        );

        System.out.println(
                "Dependency rules loaded"
        );
    }
}