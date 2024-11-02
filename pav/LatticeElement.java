package pav;

import java.util.*;
import soot.options.Options;

import soot.Unit;
import soot.Scene;
import soot.Body;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.UnitPrinter;
import soot.NormalUnitPrinter;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.ExceptionalBlockGraph;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;
import soot.Local;
import soot.IntType;
import soot.LongType;
import soot.ByteType;
import soot.ShortType;
import soot.ValueBox;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.BriefUnitGraph;
import soot.jimple.IfStmt;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.FloatConstant;
import soot.jimple.ConditionExpr;
import soot.jimple.BinopExpr;
import soot.jimple.NegExpr;
import soot.jimple.LtExpr;
import soot.jimple.GeExpr;
import soot.jimple.GtExpr;
import soot.jimple.LeExpr;
import soot.jimple.EqExpr;
import soot.jimple.NeExpr;
import soot.jimple.Constant;

public interface LatticeElement {
    // Join operation
    LatticeElement join(LatticeElement other);

    // Application of transfer functions (based on statements)
    LatticeElement tf_assignment(Stmt stmt, boolean isTrueBranch);

    // Equals method
    boolean equals(Object o);

    LatticeElement getBot();
}