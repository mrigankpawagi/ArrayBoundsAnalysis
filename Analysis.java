// This program will plot a CFG for a method using soot
// [ExceptionalUnitGraph feature].
// Arguements : <ProcessOrTargetDirectory> <MainClass> <TargetClass> <TargetMethod>

// Ref:
// 1) https://gist.github.com/bdqnghi/9d8d990b29caeb4e5157d7df35e083ce
// 2) https://github.com/soot-oss/soot/wiki/Tutorials


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

public class Analysis {
    private DotGraph dot = new DotGraph("callgraph");
    private static HashMap<String, Boolean> visited = new
        HashMap<String, Boolean>();

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



    public static void doAnalysis(SootMethod targetMethod){
        Body body = targetMethod.retrieveActiveBody();

        // Get integer variables (considering byte, short, int, long)
        List<Local> integerLocals = new ArrayList<>();
        for (Local local : body.getLocals()) {
            if (
                    local.getType() instanceof IntType || 
                    local.getType() instanceof LongType || 
                    local.getType() instanceof ByteType || 
                    local.getType() instanceof ShortType
                ) {
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

        // create a CFG based on program-points
        Map<Integer, Set<Integer>> flowPoints = new HashMap<>();
        int entryPoint = 0;
        
        // map from pairs of program-points to enclosing unit
        Map<Pair<Integer, Integer>, Unit> enclosingUnit = new HashMap<>();

        // maintain a set of true branches
        Set<Pair<Integer, Integer>> trueBranches = new HashSet<>();

        // add a point before every unit
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

        // nicely print flowPoints and enclosingUnit
        for (int j : flowPoints.keySet()) {
            System.out.println("Program-point: " + j + " -> " + flowPoints.get(j));
        }
        for (Pair<Integer, Integer> pair : enclosingUnit.keySet()) {
            System.out.println("Pair: " + pair + " -> " + enclosingUnit.get(pair));
        }
        for (Pair<Integer, Integer> pair : trueBranches) {
            System.out.println("True branch: " + pair);
        }
        // ^ these three data structures are what we will need :)
    }

    public static void main(String[] args) {

        String targetDirectory=args[0];
        String mClass=args[1];
        String tClass=args[2];
        String tMethod=args[3];
        String upperBound=args[4];
        boolean methodFound=false;


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
	    // A SootClass is Soot's representation of a class in the target program.
	    // A SootMethod is Soot's representation fo a method in the target program.
	    // Pay attention to the code above, as you may need to use the methods used
	    // above at other places in your code as well to find a desired class or a desired method.
	
        Options.v().set_main_class(mClass);
        Scene.v().setEntryPoints(Collections.singletonList(entryMethod));

        // System.out.println (entryClass.getName());
        System.out.println("tclass: " + targetClass);
        System.out.println("tmethod: " + targetMethod);
        System.out.println("tmethodname: " + tMethod);
        Iterator mi = targetClass.getMethods().iterator();
	    // targetClass.getMethods() retrieves all the methods in targetClass
        while (mi.hasNext()) {
            SootMethod sm = (SootMethod)mi.next();
            if(sm.getName().equals(tMethod)) {
                methodFound = true;
                break;
            }
	    // Not sure why this loop and check is required
        }

        if(methodFound) {
            printInfo(targetMethod);
	    // the call above prints the Soot IR (Intermediate Represenation) of the targetMethod.
	    // You can use the same printInfo method appropriately
	    // to view the IR of any method.

	    // Note: Your analysis will work not on the source code of the target program, but on
            // the Soot IR only. Soot automatically generates the IR of every method from the compiled
	    // .class files. In other words, any Soot-based analysis needs only the .class files, and does
	    // not need or use the source .java files. The IR is similar to a three-address code. 

	    /*****************************************************************
             * XXX The function doAnalysis is the entry point for the Kildall's 
             * fix-point algorithm over the LatticeElement.
             ******************************************************************/

            drawMethodDependenceGraph(targetMethod);
	    // the call above generates the control-flow graph of the targetMethod.
	    // Since you are going to implement a flow-insensitive analysis, you
	    // may not need to know about control-flow graphs at all. 

	        doAnalysis(targetMethod);
        } else {
            System.out.println("Method not found: " + tMethod);
        }
    }


    private static void drawMethodDependenceGraph(SootMethod method){
        if (!method.isPhantom() && method.isConcrete())
        {
            Body body = method.retrieveActiveBody();
            ExceptionalUnitGraph graph = new ExceptionalUnitGraph(body);

            CFGToDotGraph cfgForMethod = new CFGToDotGraph();
            cfgForMethod.drawCFG(graph);
            DotGraph cfgDot =  cfgForMethod.drawCFG(graph);
            cfgDot.plot(method.getName() + "cfg.dot");
        }
    }

    public static void printUnit(int lineno, Body b, Unit u){
        UnitPrinter up = new NormalUnitPrinter(b);
        u.toString(up);
        String linenostr = String.format("%02d", lineno) + ": ";
        System.out.println(linenostr + up.toString());
    }


    private static void printInfo(SootMethod entryMethod) {
        if (!entryMethod.isPhantom() && entryMethod.isConcrete())
        {
            Body body = entryMethod.retrieveActiveBody();
	        // `body' refers to the code body of entryMethod

            int lineno = 0;
            for (Unit u : body.getUnits()) {
        		// .getUnits retrieves all the Units in the code body of the method, as a list. 
        		// A Unit basically represent a single statement or conditional in the Soot IR.
        		// You should fully understand the structure of a Unit, the subtypes of Unit, etc., 
        		// to make progress with your analysis.
        		// Objects of type `Value' in Soot represent  local variables, constants and expressions.
        		// Expressions may be BinopExp, InvokeExpr, and so on.
        		// Boxes: References in Soot are called boxes. There are two types – Unitboxes, ValueBoxes.

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