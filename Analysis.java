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

public class Analysis {
    public interface LatticeElement {
        // Join operation
        LatticeElement join(LatticeElement other);

        // Application of transfer functions (based on statements)
        LatticeElement tf_assignment(Stmt stmt, boolean isTrueBranch);

        // Equals method
        boolean equals(Object o);

        LatticeElement getBot();
    }

    public static class IntervalElement implements LatticeElement {
        private Map<Local, Pair<Float, Float>> intervalMap;
        public static final IntervalElement bot = new IntervalElement();
        public static final Pair<Float, Float> topPair = new Pair<>(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

        public LatticeElement getBot() {
            return bot.clone();
        }

        // lowerBound and upperBound for the intervals
        public static float lowerBound = Float.NEGATIVE_INFINITY;
        public static float upperBound = Float.POSITIVE_INFINITY;

        public IntervalElement(Map<Local, Pair<Float, Float>> givenIntervalMap) {
            // check that all intervals are valid
            for (Local local : givenIntervalMap.keySet()) {
                Pair<Float, Float> interval = givenIntervalMap.get(local);
                if (interval.first > interval.second) {
                    throw new IllegalArgumentException(
                            "Invalid interval for " + local + ": " + givenIntervalMap.get(local));
                }
            }
            this.intervalMap = givenIntervalMap;

            // contract intervals to integers
            // round-up lower limits and round-down upper limits (unless they are -inf or
            // +inf)
            for (Local local : intervalMap.keySet()) {
                Pair<Float, Float> interval = intervalMap.get(local);
                float newLower = interval.first == Float.NEGATIVE_INFINITY ? Float.NEGATIVE_INFINITY
                        : (float) Math.ceil(interval.first);
                float newUpper = interval.second == Float.POSITIVE_INFINITY ? Float.POSITIVE_INFINITY
                        : (float) Math.floor(interval.second);
                if (newLower > newUpper) {
                    throw new IllegalArgumentException(
                            "Invalid interval for " + local + ": " + givenIntervalMap.get(local));
                }
                intervalMap.put(local, new Pair<>(newLower, newUpper));
            }

            // sanity check
            if (lowerBound > upperBound) {
                throw new IllegalArgumentException("Invalid bounds: [" + lowerBound + ", " + upperBound + "]");
            }

            // check all intervals and replace lower limit by -inf if it is less than
            // lowerBound
            // and upper limit by +inf if it is greater than upperBound
            for (Local local : intervalMap.keySet()) {
                Pair<Float, Float> interval = intervalMap.get(local);
                float newLower = interval.first >= lowerBound ? interval.first : Float.NEGATIVE_INFINITY;
                float newUpper = interval.second <= upperBound ? interval.second : Float.POSITIVE_INFINITY;
                intervalMap.put(local, new Pair<>(newLower, newUpper));
            }
        }

        // Private constructor for bot
        private IntervalElement() {
            this.intervalMap = null;
        }

        // check if the IntervalElement is bot
        private boolean isBot() {
            return this.intervalMap == null;
        }

        public LatticeElement join(LatticeElement other) {
            if (!(other instanceof IntervalElement)) {
                throw new IllegalArgumentException("Incompatible types for join");
            }
            IntervalElement otherIntervalElement = (IntervalElement) other;
            if (this.equals(bot)) {
                return otherIntervalElement;
            }
            if (otherIntervalElement.equals(bot)) {
                return this;
            }
            Map<Local, Pair<Float, Float>> newIntervalMap = new HashMap<>();
            for (Local local : this.intervalMap.keySet()) {
                Pair<Float, Float> thisInterval = this.intervalMap.get(local);
                Pair<Float, Float> otherInterval = otherIntervalElement.intervalMap.get(local);
                float newLower = Math.min(thisInterval.first, otherInterval.first);
                float newUpper = Math.max(thisInterval.second, otherInterval.second);
                newIntervalMap.put(local, new Pair<>(newLower, newUpper));
            }
            return new IntervalElement(newIntervalMap);
        }

        private Pair<Float, Float> getIntervalFromBinOp(String opSymbol, Pair<Float, Float> interval1,
                Pair<Float, Float> interval2) {
            float newLower, newUpper;
            float p1, p2, p3, p4, p5, p6, p7, p8;
            switch (opSymbol) {
                case "+":
                    newLower = interval1.first + interval2.first;
                    newUpper = interval1.second + interval2.second;
                    break;
                case "-":
                    newLower = interval1.first - interval2.second;
                    newUpper = interval1.second - interval2.first;
                    break;
                case "*":
                    p1 = interval1.first * interval2.first;
                    p2 = interval1.first * interval2.second;
                    p3 = interval1.second * interval2.first;
                    p4 = interval1.second * interval2.second;
                    newLower = Math.min(Math.min(p1, p2), Math.min(p3, p4));
                    newUpper = Math.max(Math.max(p1, p2), Math.max(p3, p4));
                    break;
                case "/":
                    if (interval2.first == 0 && interval2.second == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    if (interval2.first == 0) {
                        // assume the lower limit of interval2 to be 1
                        p1 = interval1.first / interval2.second;
                        p2 = interval1.second / interval2.second;
                        p3 = interval1.first / 1;
                        p4 = interval1.second / 1;
                        newLower = Math.min(Math.min(p1, p2), Math.min(p3, p4));
                        newUpper = Math.max(Math.max(p1, p2), Math.max(p3, p4));
                    } else if (interval2.second == 0) {
                        // assume the upper limit of interval2 to be -1
                        p1 = interval1.first / interval2.first;
                        p2 = interval1.second / interval2.first;
                        p3 = interval1.first / -1;
                        p4 = interval1.second / -1;
                        newLower = Math.min(Math.min(p1, p2), Math.min(p3, p4));
                        newUpper = Math.max(Math.max(p1, p2), Math.max(p3, p4));
                    } else if (interval2.first < 0 && 0 < interval2.second) {
                        // assume interval2 to be union of [interval2.first, -1] and [1,
                        // interval2.second] and then take the "join" of the two results
                        p1 = interval1.first / interval2.first;
                        p2 = interval1.second / interval2.first;
                        p3 = interval1.first / interval2.second;
                        p4 = interval1.second / interval2.second;
                        p5 = interval1.first / -1;
                        p6 = interval1.second / -1;
                        p7 = interval1.first / 1;
                        p8 = interval1.second / 1;
                        newLower = Math.min(Math.min(Math.min(p1, p2), Math.min(p3, p4)),
                                Math.min(Math.min(p5, p6), Math.min(p7, p8)));
                        newUpper = Math.max(Math.max(Math.max(p1, p2), Math.max(p3, p4)),
                                Math.max(Math.max(p5, p6), Math.max(p7, p8)));
                    } else {
                        // interval2 does not contain 0
                        p1 = interval1.first / interval2.first;
                        p2 = interval1.second / interval2.first;
                        p3 = interval1.first / interval2.second;
                        p4 = interval1.second / interval2.second;
                        newLower = Math.min(Math.min(p1, p2), Math.min(p3, p4));
                        newUpper = Math.max(Math.max(p1, p2), Math.max(p3, p4));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid operator: " + opSymbol);
            }
            return new Pair<>(newLower, newUpper);
        }

        private Pair<Pair<Float, Float>, Pair<Float, Float>> getIntervalsAfterComparison(String opSymbol,
                Pair<Float, Float> interval1, Pair<Float, Float> interval2) {
            Float lower1, upper1, lower2, upper2;
            switch (opSymbol) {
                case "<":
                    if (interval1.first >= interval2.second) {
                        throw new ArithmeticException("Unreachable code");
                    }
                    lower1 = interval1.first;
                    upper1 = Math.min(interval1.second, interval2.second - 1);
                    lower2 = Math.max(interval1.first + 1, interval2.first);
                    upper2 = interval2.second;
                    return new Pair<>(new Pair<>(lower1, upper1), new Pair<>(lower2, upper2));
                case ">":
                    return getIntervalsAfterComparison("<", interval2, interval1);
                case "<=":
                    if (interval1.first > interval2.second) {
                        throw new ArithmeticException("Unreachable code");
                    }
                    lower1 = interval1.first;
                    upper1 = Math.min(interval1.second, interval2.second);
                    lower2 = Math.max(interval1.first, interval2.first);
                    upper2 = interval2.second;
                    return new Pair<>(new Pair<>(lower1, upper1), new Pair<>(lower2, upper2));
                case ">=":
                    return getIntervalsAfterComparison("<=", interval2, interval1);
                case "==":
                    if (interval1.first > interval2.second || interval1.second < interval2.first) {
                        throw new ArithmeticException("Unreachable code");
                    }
                    lower1 = Math.max(interval1.first, interval2.first);
                    upper1 = Math.min(interval1.second, interval2.second);
                    lower2 = lower1;
                    upper2 = upper1;
                    return new Pair<>(new Pair<>(lower1, upper1), new Pair<>(lower2, upper2));
                case "!=":
                    if (interval1.first == interval2.first && interval1.second == interval2.second
                            && interval1.first == interval1.second) {
                        throw new ArithmeticException("Unreachable code");
                    }
                    lower1 = interval1.first;
                    upper1 = interval1.second;
                    lower2 = interval2.first;
                    upper2 = interval2.second;

                    if (interval1.second == interval2.first) {
                        upper1 = interval1.second - 1;
                        lower2 = interval2.first + 1;
                    } else if (interval1.first == interval2.second) {
                        lower1 = interval1.first + 1;
                        upper2 = interval2.second - 1;
                    }
                    return new Pair<>(new Pair<>(lower1, upper1), new Pair<>(lower2, upper2));
                default:
                    throw new IllegalArgumentException("Invalid operator: " + opSymbol);
            }
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
                Local leftVar = (Local) assignStmt.getLeftOp();
                Value rightOp = assignStmt.getRightOp();

                if (this.intervalMap.containsKey(leftVar)) {
                    Map<Local, Pair<Float, Float>> newIntervalMap = new HashMap<>(this.intervalMap);

                    if (rightOp instanceof IntConstant || rightOp instanceof FloatConstant) {
                        // var = constant
                        float value = rightOp instanceof IntConstant
                                ? (float) ((IntConstant) rightOp).value
                                : (float) ((FloatConstant) rightOp).value;
                        newIntervalMap.put(leftVar, new Pair<>(value, value));
                    } else if (rightOp instanceof Local) {
                        // var = var2
                        Local rightVar = (Local) rightOp;
                        if (this.intervalMap.containsKey(rightVar)) {
                            Pair<Float, Float> rightInterval = this.intervalMap.get(rightVar);
                            newIntervalMap.put(leftVar, rightInterval);
                        } else {
                            // If rightVar is not in the interval map, set the interval to [-inf, +inf]
                            newIntervalMap.put(leftVar, topPair);
                        }
                    } else if (rightOp instanceof NegExpr) {
                        // var = -var2
                        NegExpr negExpr = (NegExpr) rightOp;
                        if (negExpr.getOp() instanceof Local) {
                            Local var = (Local) negExpr.getOp();
                            if (this.intervalMap.containsKey(var)) {
                                Pair<Float, Float> interval = this.intervalMap.get(var);
                                newIntervalMap.put(leftVar, new Pair<>(-interval.second, -interval.first));
                            } else {
                                newIntervalMap.put(leftVar, topPair);
                            }
                        }
                    } else if (rightOp instanceof BinopExpr) {
                        // var = var2 op var3
                        BinopExpr binopExpr = (BinopExpr) rightOp;
                        String opSymbol = binopExpr.getSymbol();

                        if (binopExpr.getOp1() instanceof Local && binopExpr.getOp2() instanceof Local) {
                            Local var1 = (Local) binopExpr.getOp1();
                            Local var2 = (Local) binopExpr.getOp2();
                            if (this.intervalMap.containsKey(var1) && this.intervalMap.containsKey(var2)) {
                                Pair<Float, Float> interval1 = this.intervalMap.get(var1);
                                Pair<Float, Float> interval2 = this.intervalMap.get(var2);
                                try {
                                    Pair<Float, Float> newInterval = getIntervalFromBinOp(opSymbol, interval1,
                                            interval2);
                                    newIntervalMap.put(leftVar, newInterval);
                                } catch (ArithmeticException e) {
                                    return getBot();
                                } catch (IllegalArgumentException e) {
                                    newIntervalMap.put(leftVar, topPair); // unhandled operator
                                }
                            } else {
                                newIntervalMap.put(leftVar, topPair);
                            }
                        } else if (binopExpr.getOp1() instanceof Local && binopExpr.getOp2() instanceof Constant) {
                            // var = var2 op constant
                            Local var = (Local) binopExpr.getOp1();
                            float constant = ((FloatConstant) binopExpr.getOp2()).value;
                            if (this.intervalMap.containsKey(var)) {
                                Pair<Float, Float> interval = this.intervalMap.get(var);
                                try {
                                    Pair<Float, Float> newInterval = getIntervalFromBinOp(opSymbol, interval,
                                            new Pair<>(constant, constant));
                                    newIntervalMap.put(leftVar, newInterval);
                                } catch (ArithmeticException e) {
                                    return getBot();
                                } catch (IllegalArgumentException e) {
                                    newIntervalMap.put(leftVar, topPair); // unhandled operator
                                }
                            } else {
                                newIntervalMap.put(leftVar, topPair);
                            }
                        } else if (binopExpr.getOp1() instanceof Constant && binopExpr.getOp2() instanceof Local) {
                            // var = constant op var2
                            Local var = (Local) binopExpr.getOp2();
                            float constant = ((FloatConstant) binopExpr.getOp1()).value;
                            if (this.intervalMap.containsKey(var)) {
                                Pair<Float, Float> interval = this.intervalMap.get(var);
                                try {
                                    Pair<Float, Float> newInterval = getIntervalFromBinOp(opSymbol,
                                            new Pair<>(constant, constant), interval);
                                    newIntervalMap.put(leftVar, newInterval);
                                } catch (ArithmeticException e) {
                                    return getBot();
                                } catch (IllegalArgumentException e) {
                                    newIntervalMap.put(leftVar, topPair); // unhandled operator
                                }
                            } else {
                                newIntervalMap.put(leftVar, topPair);
                            }
                        }
                    }
                    // do a sanity check and see if newLower > newUpper
                    if (newIntervalMap.get(leftVar).first > newIntervalMap.get(leftVar).second) {
                        return getBot();
                    }
                    return new IntervalElement(newIntervalMap);
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                ConditionExpr condition = (ConditionExpr) ifStmt.getCondition();

                Map<Local, Pair<Float, Float>> newIntervalMap = new HashMap<>(this.intervalMap);

                Value op1 = condition.getOp1();
                Value op2 = condition.getOp2();
                String opSymbol = null;

                if (condition instanceof LtExpr) {
                    opSymbol = isTrueBranch ? "<" : ">=";
                } else if (condition instanceof GtExpr) {
                    opSymbol = isTrueBranch ? ">" : "<=";
                } else if (condition instanceof LeExpr) {
                    opSymbol = isTrueBranch ? "<=" : ">";
                } else if (condition instanceof GeExpr) {
                    opSymbol = isTrueBranch ? ">=" : "<";
                } else if (condition instanceof EqExpr) {
                    opSymbol = isTrueBranch ? "==" : "!=";
                } else if (condition instanceof NeExpr) {
                    opSymbol = isTrueBranch ? "!=" : "==";
                }

                if (opSymbol != null) {
                    if (op1 instanceof Local && op2 instanceof Constant) {
                        Local var = (Local) op1;
                        float constant = ((FloatConstant) op2).value;
                        if (this.intervalMap.containsKey(var)) {
                            Pair<Float, Float> interval1 = this.intervalMap.get(var);
                            Pair<Float, Float> interval2;
                            if (opSymbol.equals("<") || opSymbol.equals("<=")) {
                                interval2 = new Pair<>(Float.NEGATIVE_INFINITY, constant);
                            } else if (opSymbol.equals(">") || opSymbol.equals(">=")) {
                                interval2 = new Pair<>(constant, Float.POSITIVE_INFINITY);
                            } else {
                                interval2 = new Pair<>(constant, constant);
                            }
                            try {
                                Pair<Pair<Float, Float>, Pair<Float, Float>> newIntervals = getIntervalsAfterComparison(
                                        opSymbol, interval1, interval2);
                                newIntervalMap.put(var, newIntervals.first);
                            } catch (ArithmeticException e) {
                                return getBot();
                            } catch (IllegalArgumentException e) {
                                return this.clone(); // unhandled operator
                            }
                        }
                    } else if (op1 instanceof Constant && op2 instanceof Local) {
                        Local var = (Local) op2;
                        float constant = ((FloatConstant) op1).value;
                        if (this.intervalMap.containsKey(var)) {
                            Pair<Float, Float> interval2 = this.intervalMap.get(var);
                            Pair<Float, Float> interval1;
                            if (opSymbol.equals("<") || opSymbol.equals("<=")) {
                                interval1 = new Pair<>(constant, Float.POSITIVE_INFINITY);
                            } else if (opSymbol.equals(">") || opSymbol.equals(">=")) {
                                interval1 = new Pair<>(Float.NEGATIVE_INFINITY, constant);
                            } else {
                                interval1 = new Pair<>(constant, constant);
                            }
                            try {
                                Pair<Pair<Float, Float>, Pair<Float, Float>> newIntervals = getIntervalsAfterComparison(
                                        opSymbol, interval1, interval2);
                                newIntervalMap.put(var, newIntervals.second);
                            } catch (ArithmeticException e) {
                                return getBot();
                            } catch (IllegalArgumentException e) {
                                return this.clone(); // unhandled operator
                            }
                        }
                    } else if (op1 instanceof Local && op2 instanceof Local) {
                        Local var1 = (Local) op1;
                        Local var2 = (Local) op2;
                        if (this.intervalMap.containsKey(var1) && this.intervalMap.containsKey(var2)) {
                            Pair<Float, Float> interval1 = this.intervalMap.get(var1);
                            Pair<Float, Float> interval2 = this.intervalMap.get(var2);
                            try {
                                Pair<Pair<Float, Float>, Pair<Float, Float>> newIntervals = getIntervalsAfterComparison(
                                        opSymbol, interval1, interval2);
                                newIntervalMap.put(var1, newIntervals.first);
                                newIntervalMap.put(var2, newIntervals.second);
                            } catch (ArithmeticException e) {
                                return getBot();
                            } catch (IllegalArgumentException e) {
                                return this.clone(); // unhandled operator
                            }
                        }
                    }
                }

                // sanity check (easy way out is to just check for every variable)
                for (Local local : newIntervalMap.keySet()) {
                    if (newIntervalMap.get(local).first > newIntervalMap.get(local).second) {
                        return getBot();
                    }
                }
                return new IntervalElement(newIntervalMap);
            } else if (stmt instanceof IdentityStmt) {
                IdentityStmt identityStmt = (IdentityStmt) stmt;
                Local leftVar = (Local) identityStmt.getLeftOp();

                // always send leftVar to topInterval
                Map<Local, Pair<Float, Float>> newIntervalMap = new HashMap<>(this.intervalMap);
                newIntervalMap.put(leftVar, topPair);
                return new IntervalElement(newIntervalMap);
            }
            // check for identity statements (like @p)

            return this.clone(); // unhandled statements
        }

        public LatticeElement tf_assignment(Stmt stmt) {
            return tf_assignment(stmt, true);
        }

        public boolean equals(Object o) {
            if (o instanceof IntervalElement) {
                IntervalElement other = (IntervalElement) o;
                if (this.isBot() && other.isBot()) {
                    return true;
                }
                if (this.isBot() || other.isBot()) {
                    return false;
                }
                return this.intervalMap.equals(other.intervalMap);
            }
            return false;
        }

        public String toString() {
            return isBot() ? "bot" : intervalMap.toString();
        }

        public IntervalElement clone() {
            return new IntervalElement(new HashMap<>(intervalMap));
        }
    }

    private DotGraph dot = new DotGraph("callgraph");
    private static HashMap<String, Boolean> visited = new HashMap<String, Boolean>();

    public Analysis() {
    }

    public static class Pair<A, B> {
        public A first;
        public B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public boolean equals(Object o) {
            if (o instanceof Pair) {
                Pair p = (Pair) o;
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
    }

    public static void doAnalysis(SootMethod targetMethod) {
        Body body = targetMethod.retrieveActiveBody();

        // Get integer variables (considering byte, short, int, long)
        List<Local> integerLocals = new ArrayList<>();
        for (Local local : body.getLocals()) {
            if (local.getType() instanceof IntType ||
                    local.getType() instanceof LongType ||
                    local.getType() instanceof ByteType ||
                    local.getType() instanceof ShortType) {
                integerLocals.add(local);
            }
        }

        // Get statements that use integer variables
        List<Unit> intStatements = new ArrayList<>();
        for (Unit unit : body.getUnits()) {
            for (ValueBox box : unit.getUseAndDefBoxes()) {
                if (box.getValue() instanceof Local && integerLocals.contains((Local) box.getValue())) {
                    intStatements.add(unit);
                    break;
                }
            }
        }

        // Create the CFG for the method
        UnitGraph graph = new BriefUnitGraph(body);
        Map<Unit, Set<Unit>> flow = new HashMap<>();
        Unit entry = graph.getHeads().get(0);
        for (Unit u : graph) {
            flow.put(u, new HashSet<Unit>());
            List<Unit> succs = graph.getSuccsOf(u);
            for (Unit succ : succs) {
                flow.get(u).add(succ);
            }
        }

        // Create a CFG based on program-points
        Map<Integer, Set<Integer>> flowPoints = new HashMap<>();
        int entryPoint = 0;

        // Map from pairs of program-points to enclosing unit
        Map<Pair<Integer, Integer>, Unit> enclosingUnit = new HashMap<>();

        // Maintain a set of true branches
        Set<Pair<Integer, Integer>> trueBranches = new HashSet<>();

        // Add a point before every unit
        Map<Unit, Integer> pointBeforeUnit = new HashMap<>();
        pointBeforeUnit.put(entry, entryPoint);
        int i = 1;
        for (Unit u : graph) {
            if (u != entry) {
                pointBeforeUnit.put(u, i);
                i++;
            }
        }

        // populate flowPoints and enclosingUnit
        for (Unit u : flow.keySet()) {
            int uPoint = pointBeforeUnit.get(u);
            Set<Integer> succPoints = new HashSet<>();
            for (Unit succ : flow.get(u)) {
                int succPoint = pointBeforeUnit.get(succ);
                succPoints.add(succPoint);
                enclosingUnit.put(new Pair<>(uPoint, succPoint), u);

                // if u was an if statement, check if succ is the true-descendant
                if (u instanceof IfStmt) {
                    IfStmt ifStmt = (IfStmt) u;
                    Unit trueTarget = ifStmt.getTarget();
                    boolean isTrueBranch = succ.equals(trueTarget);
                    if (isTrueBranch) {
                        trueBranches.add(new Pair<>(uPoint, succPoint));
                    }
                }
            }
            flowPoints.put(uPoint, succPoints);
        }

        // Run the Kildall's algorithm
        Map<Local, Pair<Float, Float>> initialIntervalMap = new HashMap<>();
        for (Local local : integerLocals) {
            initialIntervalMap.put(local, new Pair<>(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY));
        }
        IntervalElement initialElement = new IntervalElement(initialIntervalMap);

        Map<Integer, LatticeElement> result = runKildall(IntervalElement.class, initialElement, flowPoints, enclosingUnit, trueBranches);
    }

    public static <T> Map<Integer, LatticeElement> runKildall(Class<? extends LatticeElement> latticeElementClass,
            LatticeElement initialElement,
            Map<Integer, Set<Integer>> flowPoints, Map<Pair<Integer, Integer>, Unit> enclosingUnit,
            Set<Pair<Integer, Integer>> trueBranches) {

        // facts: {0: initialElement, 1: latticeElementClass.getBot(), 2:
        // latticeElementClass.getBot(), ...}

        // Flesh out the details
        // while(marked) {
        // current = marked.pop();
        // result = tranferfunction(current);
        // if(result != successorFact(current)) {
        // newSuccessorFact = union(successorFact(current), result);
        // setFact(successors(current), newSuccessorFact);
        // marked.push(successors(current));
        // }
        // }

        // marked => Set of marked program points
        // flowPoints: map from program points to successors
        // enclosingUnit[pair(m, n)] => statement between program points m and n
        // trueBranches: set of pairs of program points that are true branches

        // element.tf_assignment(enclosingUnit[pair(m, n)], pair(m,n) in trueBranches?)
        // bot: latticeElementClass.getBot()

        return null;
    }

    public static void main(String[] args) {

        String targetDirectory = args[0];
        String mClass = args[1];
        String tClass = args[2];
        String tMethod = args[3];
        float upperBound = Float.parseFloat(args[4]);
        boolean methodFound = false;

        List<String> procDir = new ArrayList<String>();
        procDir.add(targetDirectory);

        // Set Soot options
        soot.G.reset();
        Options.v().set_process_dir(procDir);
        // Options.v().set_prepend_classpath(true);
        Options.v().set_src_prec(Options.src_prec_only_class);
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("cg.spark", "verbose:false");

        Scene.v().loadNecessaryClasses();

        SootClass entryClass = Scene.v().getSootClassUnsafe(mClass);
        SootMethod entryMethod = entryClass.getMethodByNameUnsafe("main");
        SootClass targetClass = Scene.v().getSootClassUnsafe(tClass);
        SootMethod targetMethod = entryClass.getMethodByNameUnsafe(tMethod);

        Options.v().set_main_class(mClass);
        Scene.v().setEntryPoints(Collections.singletonList(entryMethod));

        // System.out.println (entryClass.getName());
        System.out.println("tclass: " + targetClass);
        System.out.println("tmethod: " + targetMethod);
        System.out.println("tmethodname: " + tMethod);
        Iterator mi = targetClass.getMethods().iterator();
        // targetClass.getMethods() retrieves all the methods in targetClass
        while (mi.hasNext()) {
            SootMethod sm = (SootMethod) mi.next();
            if (sm.getName().equals(tMethod)) {
                methodFound = true;
                break;
            }
            // Not sure why this loop and check is required
        }

        if (methodFound) {
            drawMethodDependenceGraph(targetMethod);

            IntervalElement.lowerBound = 0;
            IntervalElement.upperBound = upperBound;
            doAnalysis(targetMethod);
        } else {
            System.out.println("Method not found: " + tMethod);
        }
    }

    private static void drawMethodDependenceGraph(SootMethod method) {
        if (!method.isPhantom() && method.isConcrete()) {
            Body body = method.retrieveActiveBody();
            ExceptionalUnitGraph graph = new ExceptionalUnitGraph(body);

            CFGToDotGraph cfgForMethod = new CFGToDotGraph();
            cfgForMethod.drawCFG(graph);
            DotGraph cfgDot = cfgForMethod.drawCFG(graph);
            cfgDot.plot(method.getName() + "cfg.dot");
        }
    }

    public static void printUnit(int lineno, Body b, Unit u) {
        UnitPrinter up = new NormalUnitPrinter(b);
        u.toString(up);
        String linenostr = String.format("%02d", lineno) + ": ";
        System.out.println(linenostr + up.toString());
    }

    private static void printInfo(SootMethod entryMethod) {
        if (!entryMethod.isPhantom() && entryMethod.isConcrete()) {
            Body body = entryMethod.retrieveActiveBody();
            // `body' refers to the code body of entryMethod
            int lineno = 0;
            for (Unit u : body.getUnits()) {
                if (!(u instanceof Stmt)) {
                    continue;
                }
                Stmt s = (Stmt) u;
                printUnit(lineno, body, u);
                lineno++;
            }
        }
    }
}
