package org.example.GraphDependencyRules;

public class Attribute {
    public String node;
    public String parameter;

    public Attribute(String node, String attribute) {
        this.node = node;
        this.parameter = attribute;
    }

    @Override
    public String toString() {
        return node + " " + parameter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Attribute attribute1 = (Attribute) o;

        if (!node.equals(attribute1.node)) return false;
        return parameter.equals(attribute1.parameter);
    }

    @Override
    public int hashCode() {
        return 31 * node.hashCode() + parameter.hashCode();
    }
}
