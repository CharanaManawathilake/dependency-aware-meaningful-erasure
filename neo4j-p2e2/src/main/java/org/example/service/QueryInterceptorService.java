package org.example.service;

import org.example.config.DependencyConfig;
import org.example.config.Neo4jProperties;
import org.example.dependency.*;
import org.example.parser.CypherDeleteParser;
import org.example.parser.ParsedDelete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
public class QueryInterceptorService {

    @Autowired
    private CypherDeleteParser parser;

    @Autowired
    private Neo4jForwardingService forwardingService;

    @Autowired
    private DependencyConfig dependencyConfig;

    @Autowired
    private Neo4jProperties neo4jProperties;

    @Autowired
    private DeletionEngine deletionEngine;

    public Object process(String query)
            throws Exception {

        /*
         * NON DELETE QUERY
         */

        if (!parser.isDeleteQuery(query)) {

            return forwardingService.execute(query);
        }

        /*
         * DELETE QUERY
         */

        ParsedDelete parsed =
                parser.parse(query);

        Property property =
                new Property(
                        parsed.node,
                        parsed.property
                );

        Cell deletedCell =
                new Cell(
                        property,
                        parsed.keyValue
                );

        System.out.println(
                "DELETE QUERY DETECTED"
        );

        System.out.println(
                "Target: " + deletedCell
        );

        Instantiator instantiator =
                new Instantiator(
                        dependencyConfig.propertyInHead,
                        dependencyConfig.propertyInTail,
                        dependencyConfig.nodeName2Key,
                        neo4jProperties
                );

        /*
         * fetch actual value + insertion time
         */

        instantiator.completePropVal(
                deletedCell
        );

        /*
         * build dependency graph
         */

        InstantiatedModel model =
                new InstantiatedModel(
                        deletedCell,
                        instantiator
                );

        /*
         * compute cascade delete set
         */

        HashSet<Cell> toDelete =
                deletionEngine.optimalDelete(
                        model,
                        deletedCell
                );

        System.out.println(
                "CASCADE DELETE SET:"
        );

        toDelete.forEach(System.out::println);

        /*
         * PERMANENT delete
         */

        instantiator.deleteCells(toDelete);

        return "Deleted "
                + toDelete.size()
                + " dependent cells";
    }
}