package org.example.GraphDependencyRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class Rule {
    public Property head;
    public ArrayList<Property> tail = new ArrayList<>(2);
    public ArrayList<String> nodes = new ArrayList<>(1);
    public HashMap<String, String> node2Alias = new HashMap<>(2);
    public String condition;

    @Override
    public String toString() {
        return head.property + " <- " + tail.stream().map(a -> a.property).collect(Collectors.joining());
    }
}
