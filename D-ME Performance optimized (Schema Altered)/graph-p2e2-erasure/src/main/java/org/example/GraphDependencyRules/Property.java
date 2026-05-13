package org.example.GraphDependencyRules;

public class Property {
    public String node;
    public String property;

    public Property(String node, String property) {
        this.node = node;
        this.property = property;
    }

    @Override
    public String toString() {
        return node + " " + property;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Property property1 = (Property) o;

        if (!node.equals(property1.node)) return false;
        return property.equals(property1.property);
    }

    @Override
    public int hashCode() {
        return 31 * node.hashCode() + property.hashCode();
    }
}
