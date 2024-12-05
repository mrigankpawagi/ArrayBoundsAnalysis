package pav;

public class Pair<A, B> {
    public A first;
    public B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public boolean equals(Object o) {
        if (o instanceof Pair) {
            Pair<?,?> p = (Pair<?,?>) o;
            return first.equals(p.first) && second.equals(p.second);
        }
        return false;
    }

    public int hashCode() {
        return first.hashCode() + second.hashCode();
    }

    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    public A first() {
        return first;
    }

    public B second() {
        return second;
    }
}