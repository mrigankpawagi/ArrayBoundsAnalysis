package pav;

import java.util.*;

import soot.Unit;
import soot.jimple.Stmt;
import soot.Local;
import soot.jimple.IfStmt;
import soot.jimple.AssignStmt;
import soot.Value;
import soot.jimple.ConditionExpr;
import soot.jimple.NewArrayExpr;
import soot.jimple.EqExpr;
import soot.jimple.NeExpr;
import soot.jimple.NullConstant;

import pav.LatticeElement;

public class IntegerArrayPointer implements LatticeElement{
    public Map<Local, Set<Unit>> pointerMap;
    public final Set<Unit> allocUnits;
    public static final IntegerArrayPointer bot = new IntegerArrayPointer();

    public LatticeElement getBot() {
        return bot.clone();
    }

    public IntegerArrayPointer(Map<Local, Set<Unit>> givenPointerMap, Set<Unit> givenAllocUnits) {
        // givenPointerMap should not be null
        if (givenPointerMap == null) {
            throw new IllegalArgumentException("Invalid pointer map.");
        }
        // check that all pointers are valid
        for (Local local : givenPointerMap.keySet()) {
            Set<Unit> units = givenPointerMap.get(local);
            // units should not be empty or null
            if (units == null || units.isEmpty()) {
                throw new IllegalArgumentException("Invalid pointer map.");
            }
            // each unit should be null or a member of givenAllocUnits
            for (Unit unit : units) {
                if (unit != null && !givenAllocUnits.contains(unit)) {
                    throw new IllegalArgumentException("Invalid pointer map.");
                }
            }
        }
        this.pointerMap = givenPointerMap;
        this.allocUnits = givenAllocUnits;
    }

    // Private constructor for bot
    private IntegerArrayPointer() {
        this.pointerMap = null;
        this.allocUnits = null;
    }

    // check if the IntegerArrayPointer is bot
    public boolean isBot() {
        return this.pointerMap == null;
    }

    // Join operation with another LatticeElement
    public LatticeElement join(LatticeElement other) {
        if (!(other instanceof IntegerArrayPointer)) {
            throw new IllegalArgumentException("Incompatible types for join");
        }
        IntegerArrayPointer otherIntgerArrayPointer = (IntegerArrayPointer) other;
        if (this.equals(bot)) {
            return otherIntgerArrayPointer;
        }
        if (otherIntgerArrayPointer.equals(bot)) {
            return this;
        }

        // check for common allocUnits and pointerMap keys
        if (!this.allocUnits.equals(otherIntgerArrayPointer.allocUnits)) {
            throw new IllegalArgumentException("Incompatible pointer elements for join.");
        }
        if (!this.pointerMap.keySet().equals(otherIntgerArrayPointer.pointerMap.keySet())) {
            throw new IllegalArgumentException("Incompatible pointer elements for join.");
        }

        Map<Local, Set<Unit>> newPointerMap = new HashMap<>();
        for (Local local : this.pointerMap.keySet()) {
            Set<Unit> thisUnits = this.pointerMap.get(local);
            Set<Unit> otherUnits = otherIntgerArrayPointer.pointerMap.get(local);
            Set<Unit> newUnits = new HashSet<>();
            newUnits.addAll(thisUnits);
            newUnits.addAll(otherUnits);
            newPointerMap.put(local, newUnits);
        }

        return new IntegerArrayPointer(newPointerMap, this.allocUnits);
    }

    // isTrueBranch is False if an alternate branch is taken (like the false branch
    // of an if statement)
    public LatticeElement tf_assignment(Stmt stmt, boolean isTrueBranch) {
        if (this.equals(bot)) {
            return bot; // bot is always transformed to bot
        }
        if (stmt instanceof AssignStmt) {
            // Handle assignment statements
            AssignStmt assignStmt = (AssignStmt) stmt;

            // check if the left side of the assignment is in the pointerMap
            if (pointerMap.containsKey(assignStmt.getLeftOp())) {
                Local leftVar = (Local) assignStmt.getLeftOp();

                // check if the right side of the assignment is in the pointerMap
                if (pointerMap.containsKey(assignStmt.getRightOp())) {
                    Local rightVar = (Local) assignStmt.getRightOp();
                    
                    // x = y
                    Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                    newPointerMap.put(leftVar, new HashSet<>(pointerMap.get(rightVar)));
                    return new IntegerArrayPointer(newPointerMap, allocUnits);
                }

                // check if the right side of the assignment is 'null'
                if (assignStmt.getRightOp() instanceof NullConstant) {
                    // x = null
                    Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                    newPointerMap.put(leftVar, new HashSet<>());
                    newPointerMap.get(leftVar).add(null);
                    return new IntegerArrayPointer(newPointerMap, allocUnits);
                }
                
                // check if the assignment is an allocation in allocUnits
                if (allocUnits.contains(assignStmt)) {
                    // x = new int[...]
                    Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                    Set<Unit> newUnits = new HashSet<>();
                    newUnits.add(assignStmt);
                    newPointerMap.put(leftVar, newUnits);
                    return new IntegerArrayPointer(newPointerMap, allocUnits);
                }
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            ConditionExpr condition = (ConditionExpr) ifStmt.getCondition();

            // check that both operands are either keys in the pointerMap or null
            if ((pointerMap.containsKey(condition.getOp1()) || condition.getOp1() instanceof NullConstant)
                    && (pointerMap.containsKey(condition.getOp2()) || condition.getOp2() instanceof NullConstant)) {

                if ((condition instanceof EqExpr && isTrueBranch) || (condition instanceof NeExpr && !isTrueBranch)) {
                    // x == y, T or x != y, F

                    // left operand is null and right operand is a key in the pointerMap
                    if (condition.getOp1() instanceof NullConstant && pointerMap.containsKey(condition.getOp2())) {
                        Local rightVar = (Local) condition.getOp2();

                        // check if rightVar can be null
                        if (pointerMap.get(rightVar).contains(null)) {
                            Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                            newPointerMap.put(rightVar, new HashSet<>());
                            newPointerMap.get(rightVar).add(null);
                            return new IntegerArrayPointer(newPointerMap, allocUnits);
                        } else {
                            return getBot();
                        }
                    }

                    // right operand is null and left operand is a key in the pointerMap
                    if (condition.getOp2() instanceof NullConstant && pointerMap.containsKey(condition.getOp1())) {
                        Local leftVar = (Local) condition.getOp1();

                        // check if leftVar can be null
                        if (pointerMap.get(leftVar).contains(null)) {
                            Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                            newPointerMap.put(leftVar, new HashSet<>());
                            newPointerMap.get(leftVar).add(null);
                            return new IntegerArrayPointer(newPointerMap, allocUnits);
                        } else {
                            return getBot();
                        }
                    }

                    // both operands are keys in the pointerMap
                    Local leftVar = (Local) condition.getOp1();
                    Local rightVar = (Local) condition.getOp2();

                    Set<Unit> leftUnits = pointerMap.get(leftVar);
                    Set<Unit> rightUnits = pointerMap.get(rightVar);

                    Set<Unit> commonUnits = new HashSet<>(leftUnits);
                    commonUnits.retainAll(rightUnits);

                    // if the commonUnits set is empty, then return bot
                    if (commonUnits.isEmpty()) {
                        return getBot();
                    } else {
                        Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                        newPointerMap.put(leftVar, new HashSet<>(commonUnits));
                        newPointerMap.put(rightVar, new HashSet<>(commonUnits));
                        return new IntegerArrayPointer(newPointerMap, allocUnits);
                    }
                }
                if ((condition instanceof EqExpr && !isTrueBranch) || (condition instanceof NeExpr && isTrueBranch)) {
                    // x == y, F or x != y, T

                    // left operand is null and right operand is a key in the pointerMap
                    if (condition.getOp1() instanceof NullConstant && pointerMap.containsKey(condition.getOp2())) {
                        Local rightVar = (Local) condition.getOp2();

                        // check if rightVar can be null
                        if (pointerMap.get(rightVar).contains(null) && pointerMap.get(rightVar).size() > 1) {
                            Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                            newPointerMap.put(rightVar, new HashSet<>(pointerMap.get(rightVar)));
                            newPointerMap.get(rightVar).remove(null);
                            return new IntegerArrayPointer(newPointerMap, allocUnits);
                        } else if (!pointerMap.get(rightVar).contains(null)) {
                            return this.clone();
                        } else {
                            return getBot(); // rightVar can only be null
                        }
                    }

                    // right operand is null and left operand is a key in the pointerMap
                    if (condition.getOp2() instanceof NullConstant && pointerMap.containsKey(condition.getOp1())) {
                        Local leftVar = (Local) condition.getOp1();

                        // check if leftVar can be null
                        if (pointerMap.get(leftVar).contains(null) && pointerMap.get(leftVar).size() > 1) {
                            Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);
                            newPointerMap.put(leftVar, new HashSet<>(pointerMap.get(leftVar)));
                            newPointerMap.get(leftVar).remove(null);
                            return new IntegerArrayPointer(newPointerMap, allocUnits);
                        } else if (!pointerMap.get(leftVar).contains(null)) {
                            return this.clone();
                        } else {
                            return getBot(); // leftVar can only be null
                        }
                    }

                    // both operands are keys in the pointerMap
                    Local leftVar = (Local) condition.getOp1();
                    Local rightVar = (Local) condition.getOp2();

                    Set<Unit> leftUnits = pointerMap.get(leftVar);
                    Set<Unit> rightUnits = pointerMap.get(rightVar);

                    Map<Local, Set<Unit>> newPointerMap = new HashMap<>(pointerMap);

                    // if leftVar is {null} and rightVar - {null} is not empty
                    if ((leftUnits.contains(null) && leftUnits.size() == 1) && (!rightUnits.contains(null) || rightUnits.size() > 1)) {
                        newPointerMap.get(rightVar).remove(null);
                        return new IntegerArrayPointer(newPointerMap, allocUnits);
                    }

                    // if rightVar is {null} and leftVar - {null} is not empty
                    if ((rightUnits.contains(null) && rightUnits.size() == 1) && (!leftUnits.contains(null) || leftUnits.size() > 1)) {
                        newPointerMap.get(leftVar).remove(null);
                        return new IntegerArrayPointer(newPointerMap, allocUnits);
                    }

                    // if leftVar is {null} and rightVar is {null}
                    if (leftUnits.contains(null) && leftUnits.size() == 1 && rightUnits.contains(null) && rightUnits.size() == 1) {
                        return this.getBot();
                    }

                    return this.clone(); // imprecise... but this is what we discussed in class.
                }
            }
        }

        return this.clone(); // unhandled statements
    }

    public LatticeElement tf_assignment(Stmt stmt) {
        return tf_assignment(stmt, true);
    }

    public boolean equals(Object o) {
        if (o instanceof IntegerArrayPointer) {
            IntegerArrayPointer other = (IntegerArrayPointer) o;
            if (this.isBot() && other.isBot()) {
                return true;
            }
            if (this.isBot() || other.isBot()) {
                return false;
            }
            return this.pointerMap.equals(other.pointerMap) && this.allocUnits.equals(other.allocUnits);
        }
        return false;
    }

    public String toString() {
        return isBot() ? "bot" : pointerMap.toString();
    }

    public IntegerArrayPointer clone() {
        if (isBot()) {
            return new IntegerArrayPointer();
        }
        return new IntegerArrayPointer(new HashMap<>(pointerMap), new HashSet<>(allocUnits));
    }
}
