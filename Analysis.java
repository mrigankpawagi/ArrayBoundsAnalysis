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
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;
import soot.Value;
import soot.jimple.Constant;
import soot.jimple.IntConstant;
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
import soot.Type;
import soot.ArrayType;
import soot.jimple.NewArrayExpr;
import soot.jimple.internal.JArrayRef;

import pav.Pair;
import pav.LatticeElement;
import pav.IntervalElement;
import pav.IntegerArrayPointer;

public class Analysis{
    public static String targetDirectory;
    public static String tClass;
    public static String tMethod;

    private DotGraph dot = new DotGraph("callgraph");
    private static HashMap<String, Boolean> visited = new HashMap<String, Boolean>();

    // Trace will store details necessary for File 2 (fulloutput.txt)
    private static List<Pair<Integer, LatticeElement>> trace = new ArrayList<>();

    // Analysis function
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

        Map<Integer, LatticeElement> resultIntervalAnalysis = runKildall(initialElement, flowPoints,
                enclosingUnit, trueBranches);

        // get all integer arrays in the method
        List<Local> integerArrays = new ArrayList<>();
        for (Local local : body.getLocals()) {
            Type type = local.getType();
    
            // Check if the type is an ArrayType and the base type is int
            if (type instanceof ArrayType) {
                ArrayType arrayType = (ArrayType) type;
                if (arrayType.getElementType().toString().equals("int")) {
                    integerArrays.add(local);
                }
            }
        }

        // get all the statements with "new int[...]"
        Set<Unit> newArrayStatements = new HashSet<>();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) unit;
                Value rhs = assignStmt.getRightOp();
    
                // Check if the right-hand side is a NewArrayExpr
                if (rhs instanceof NewArrayExpr) {
                    NewArrayExpr newArrayExpr = (NewArrayExpr) rhs;
    
                    // Check if the array type is int
                    if (newArrayExpr.getBaseType().toString().equals("int")) {
                        newArrayStatements.add(unit);
                    }
                }
            }
        }

        // Run the Kildall's algorithm for Pointer Analysis with integer arrays
        Map<Local, Set<Unit>> initialPointerMap = new HashMap<>();

        // make all integer array variables point to {null}
        for (Local local : integerArrays) {
            initialPointerMap.put(local, new HashSet<Unit>());
            // add null to this new hashset
            initialPointerMap.get(local).add(null);
        }

        IntegerArrayPointer initialIntegerArrayPointer = new IntegerArrayPointer(initialPointerMap, newArrayStatements);
        
        Map<Integer, LatticeElement> resultPointerAnalysis = runKildall(initialIntegerArrayPointer, flowPoints,
                enclosingUnit, trueBranches);

        // record the size of each allocated array
        Map<Unit, Pair<Float, Float>> arraySizeMap = new HashMap<>();
        for (Unit unit : newArrayStatements) {
            NewArrayExpr newArrayExpr = (NewArrayExpr) ((AssignStmt) unit).getRightOp();
            Value sizeValue = newArrayExpr.getSize();
            
            // check whether the array size is a constant
            if (sizeValue instanceof Constant) {
                float size = (float) ((IntConstant) sizeValue).value;
                arraySizeMap.put(unit, new Pair<>(size, size));
            } 
            // check whether the array size is a variable
            else if (sizeValue instanceof Local) {
                Pair<Float, Float> size = ((IntervalElement) resultIntervalAnalysis.get(pointBeforeUnit.get(unit))).intervalMap.get(sizeValue);
                arraySizeMap.put(unit, size);
            }
        }

        // now check all the array accesses
        Map<Integer, String> safetyMap = new HashMap<>();
        int lineno = -1;
        for (Unit unit : body.getUnits()) {
            lineno++;
            boolean safe = false;

            // get all JArrayRefs in the unit
            List<JArrayRef> arrayRefs = new ArrayList<>();
            for (ValueBox box : unit.getUseBoxes()) {
                if (box.getValue() instanceof JArrayRef) {
                    Local base = (Local) ((JArrayRef) box.getValue()).getBase();
                    if (integerArrays.contains(base)) {
                        arrayRefs.add((JArrayRef) box.getValue());
                    }
                }
            }
            for (ValueBox box : unit.getDefBoxes()) {
                if (box.getValue() instanceof JArrayRef) {
                    Local base = (Local) ((JArrayRef) box.getValue()).getBase();
                    if (integerArrays.contains(base)) {
                        arrayRefs.add((JArrayRef) box.getValue());
                    }
                }
            }

            if (arrayRefs.isEmpty()) {
                continue;
            }

            for (JArrayRef arrayRef : arrayRefs) {
                Local base = (Local) arrayRef.getBase();
                Value index = arrayRef.getIndex();

                // check if the index is a constant
                Pair<Float, Float> indexInterval = null;
                if (index instanceof Constant) {
                    int indexValue = ((IntConstant) index).value;
                    indexInterval = new Pair<>((float) indexValue, (float) indexValue);
                } else if (index instanceof Local) {
                    indexInterval = ((IntervalElement) resultIntervalAnalysis.get(pointBeforeUnit.get(unit))).intervalMap.get(index);
                }

                if (indexInterval != null) {
                    // check what the base points to in the resultPointerAnalysis
                    Map<Local, Set<Unit>> basePointsToMap = ((IntegerArrayPointer) resultPointerAnalysis.get(pointBeforeUnit.get(unit))).pointerMap;
                    if (basePointsToMap == null) {
                        continue; // unreachable code. ignore.
                    }
                    Set<Unit> basePointsTo = basePointsToMap.get(base);
                    if (basePointsTo.contains(null) && basePointsTo.size() == 1) {
                        continue; // base points to null... must be unsafe!
                    }

                    // check all the array allocations that base points to
                    boolean safeForThisAccess = true;
                    for (Unit newArrayStmt : basePointsTo) {
                        if (newArrayStmt == null) continue; 
                        Pair<Float, Float> arraySize = arraySizeMap.get(newArrayStmt);
                        if (arraySize != null) {
                            if (indexInterval.second >= arraySize.first) {
                                safeForThisAccess = false;
                                break;
                            }
                        }
                    }
                    safe = safe || safeForThisAccess;
                }
            }

            safetyMap.put(lineno, safe ? "Safe" : "Potentially Unsafe");
        }

        // Print the output (to STDOUT for now) from safetyMap
        for (Map.Entry<Integer, String> safetyEntry : safetyMap.entrySet()) {
            System.out.println(tClass + "." + tMethod + ": " + String.format("%02d", safetyEntry.getKey()) + ": " + safetyEntry.getValue());
        }
    }

    // Running Kildall's algorithm
    public static Map<Integer, LatticeElement> runKildall(LatticeElement initialElement, Map<Integer, Set<Integer>> flowPoints,
            Map<Pair<Integer, Integer>, Unit> enclosingUnit, Set<Pair<Integer, Integer>> trueBranches) {
        // facts will store details necessary for output.txt
        // trace will store details necessary for fulloutput.txt
        Map<Integer, LatticeElement> facts = new HashMap<>();
        
        // Initialize facts with initial lattice elements
        for (Integer point : flowPoints.keySet()) {
            facts.put(point, initialElement.getBot());
        }
        facts.put(0, initialElement);
        trace.add(new Pair<Integer, LatticeElement>(0, initialElement));
        trace.add(new Pair<Integer, LatticeElement>(-1, initialElement.getBot()));  // Spacer representing newline

        // Initialize worklist with all nodes in the flowPoints
        Queue<Integer> worklist = new LinkedList<>(flowPoints.keySet());

        // Process the worklist
        while (!worklist.isEmpty()) {
            Integer current = worklist.poll();
            LatticeElement oldFact = facts.get(current);

            // Compute the current fact to all successors
            for (Integer succ : flowPoints.get(current)) {
                Pair<Integer, Integer> transition = new Pair<>(current, succ);
                LatticeElement newFact = oldFact.tf_assignment((Stmt) enclosingUnit.get(transition), trueBranches.contains(transition));
                LatticeElement oldSuccFact = facts.get(succ);
                LatticeElement newSuccFact = oldSuccFact.join(newFact);
                facts.put(succ, newSuccFact);
                trace.add(new Pair<Integer, LatticeElement>(succ, newSuccFact));
                if (!newSuccFact.equals(oldSuccFact)) {
                    worklist.add(succ);
                }
            }
            trace.add(new Pair<Integer, LatticeElement>(-1, initialElement.getBot()));
        }

        // Return the final facts map
        return facts;
    }

    public static void main(String[] args) {
        String targetDirectory = args[0];
        String mClass = args[1];
        String tClass = args[2];
        String tMethod = args[3];
        float upperBound = Float.parseFloat(args[4]);
        boolean methodFound = false;

        Analysis.targetDirectory = targetDirectory;
        Analysis.tClass = tClass;
        Analysis.tMethod = tMethod;

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

        SootClass entryClass = Scene.v().getSootClass(mClass);
        //SootMethod entryMethod = entryClass.getMethodByNameUnsafe("main");
        SootClass targetClass = Scene.v().getSootClass(tClass);
        SootMethod targetMethod = entryClass.getMethodByName(tMethod);

        Options.v().set_main_class(mClass);
        //Scene.v().setEntryPoints(Collections.singletonList(entryMethod));

        //System.out.println(entryClass.getName());
        System.out.println("tclass: " + targetClass);
        System.out.println("tmethod: " + targetMethod);
        System.out.println("tmethodname: " + tMethod);
        // mi iterates over all methods in the targetClass
        Iterator mi = targetClass.getMethods().iterator();
        // targetClass.getMethods() retrieves all the methods in targetClass
        while (mi.hasNext()) {
            SootMethod sm = (SootMethod) mi.next();
            if (sm.getName().equals(tMethod)) {
                methodFound = true;
                break;
            }
        }

        // If tMethod is found in targetClass 
        if (methodFound) {
            drawMethodDependenceGraph(targetMethod);

            IntervalElement.lowerBound = 0;
            IntervalElement.upperBound = upperBound;
            doAnalysis(targetMethod);
        } else {
            System.out.println("Method not found: " + tMethod);
            System.exit(1);
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

    // Generate File 1 ouput as mentioned in the requirements
    private static void printOutput(Map<Integer, LatticeElement> result) {
        // Create a file tclass.tmethod.output.txt
        String outputFileName = targetDirectory + "/" + tClass + "." + tMethod + ".output.txt";
        try {
            java.io.FileWriter fw = new java.io.FileWriter(outputFileName);
            java.io.PrintWriter pw = new java.io.PrintWriter(fw);

            for (Integer point : result.keySet()) {
                // Skip the entry point (0)
                if (point == 0) {
                    continue;
                }
                // Pad the point number so it is always 2 digits
                String statementNumber = String.format("%02d", point);
                if (((IntervalElement) result.get(point)).intervalMap == null) {
                    // skip printing bot
                    continue;
                }
                // Sort variables by name
                List<Local> locals = new ArrayList<>(((IntervalElement) result.get(point)).intervalMap.keySet());
                Collections.sort(locals, new Comparator<Local>() {
                    public int compare(Local l1, Local l2) {
                        return l1.getName().compareTo(l2.getName());
                    }
                });
                for (Local local : locals) {
                    Pair<Float, Float> interval = ((IntervalElement) result.get(point)).intervalMap.get(local);

                    String lower = interval.first == Float.NEGATIVE_INFINITY ? "-inf" : String.valueOf(Math.round(interval.first));
                    String upper = interval.second == Float.POSITIVE_INFINITY ? "inf" : String.valueOf(Math.round(interval.second));
                    pw.print(tClass + "." + tMethod + ": in" + statementNumber + ": ");
                    pw.println(local.getName() + ":[" + lower + ", " + upper + "]");
                } 
            }

            pw.close();
            fw.close();
        } catch (java.io.IOException e) {
            System.out.println("Error writing to file " + outputFileName);
        }
    }

    // Generate File 2 ouput as mentioned in the requirements
    private static void printTrace() {
        // Create a file tclass.tmethod.fulloutput.txt
        String outputFileName = targetDirectory + "/" + tClass + "." + tMethod + ".fulloutput.txt";
        try {
            java.io.FileWriter fw = new java.io.FileWriter(outputFileName);
            java.io.PrintWriter pw = new java.io.PrintWriter(fw);
            boolean wasLastLineNewline = false;

            for (Pair<Integer,LatticeElement> point : trace) {
                if (point.first() == -1) {
                    if (!wasLastLineNewline) {
                        pw.println("");
                        wasLastLineNewline = true;
                    }
                    continue;
                }
                // Pad the point number so it is always 2 digits
                String statementNumber = String.format("%02d", point.first());
                if (((IntervalElement) point.second()).intervalMap == null) {
                    // skip printing bot
                    continue;
                }
                // Sort variables by name
                List<Local> locals = new ArrayList<>(((IntervalElement) point.second()).intervalMap.keySet());
                Collections.sort(locals, new Comparator<Local>() {
                    public int compare(Local l1, Local l2) {
                        return l1.getName().compareTo(l2.getName());
                    }
                });
                for (Local local : locals) {
                    Pair<Float, Float> interval = ((IntervalElement) point.second()).intervalMap.get(local);

                    String lower = interval.first == Float.NEGATIVE_INFINITY ? "-inf" : String.valueOf(Math.round(interval.first));
                    String upper = interval.second == Float.POSITIVE_INFINITY ? "inf" : String.valueOf(Math.round(interval.second));
                    pw.print(tClass + "." + tMethod + ": in" + statementNumber + ": ");
                    pw.println(local.getName() + ":[" + lower + ", " + upper + "]");
                    wasLastLineNewline = false;
                } 
            }

            pw.close();
            fw.close();
        } catch (java.io.IOException e) {
            System.out.println("Error writing to file " + outputFileName);
        }
    }
}
