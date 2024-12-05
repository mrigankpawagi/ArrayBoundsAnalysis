package pav;

import soot.jimple.Stmt;

public interface LatticeElement {
    // Join operation
    LatticeElement join(LatticeElement other);

    // Application of transfer functions (based on statements)
    LatticeElement tf_assignment(Stmt stmt, boolean isTrueBranch);

    // Equals method
    boolean equals(Object o);
    
    LatticeElement getBot();

    public boolean isBot();
}