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

public class Analysis {
    private static ArrayList<ArrayList<Integer>> in = new ArrayList<ArrayList<Integer>>();
    private static List<Unit> unitList = new ArrayList<Unit>();
    private static Queue<Unit> workList = new LinkedList<Unit>();
    private static Body body = null;
    private static ExceptionalUnitGraph graph = null;
    private DotGraph dot = new DotGraph("callgraph");
    private static HashMap<String, Boolean> visited = new HashMap<String, Boolean>();
    private static SootMethod targetMethod = null;

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
        targetMethod = entryClass.getMethodByNameUnsafe(tMethod);
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
        }

        if(methodFound) {
            printInfo(targetMethod);
            drawMethodDependenceGraph(targetMethod);
            body = targetMethod.retrieveActiveBody();
            graph = new ExceptionalUnitGraph(body);
            unitList = getUnits();

            // Construct the initial Flowfacts
            for (int i = 0; i < unitList.size(); i++) {
                    in.add(new ArrayList<Integer>());
            }

            workList = new LinkedList<Unit>(reverse(unitList));
	        doAnalysis(targetMethod);
        }
        else {
            System.out.println("Method not found: " + tMethod);
        }
    }

    public static void doAnalysis(SootMethod targetMethod) {
        while (!workList.isEmpty()) {
            Unit curr = workList.poll();
            for (Unit pred : graph.getPredsOf(curr)) {
                // Get union of flowfact(pred) and transfer function applied to (flowfact(curr), pred)
                ArrayList<Integer> value = union( in.get(unitList.indexOf(pred)), transferFunction(in.get(unitList.indexOf(curr)), pred));

                // If union is not equal to existing flowfact(pred), set flowfact(pred) to union value
                if (!IntervalEquals(in.get(unitList.indexOf(pred)), value)) {
                    in.set(unitList.indexOf(pred), value);
                    // Add pred back to worklist if flowfact has changed
                    if (!workList.contains(pred)) {
                      workList.add(pred);
                    }
                }
            }
        }
    }

    // Transfer functions
    public static ArrayList<Integer> transferFunction(ArrayList<Integer> out, Unit stmt) {
        ArrayList<Integer> in = new ArrayList<Integer>();



        return in;
    }

    // Union
    public static ArrayList<Integer> union(ArrayList<Integer> i1, ArrayList<Integer> i2) {
        ArrayList<Integer> intervals = new ArrayList<Integer>();



        return intervals;
    }

    // Check if both intervals are the same
    public static boolean IntervalEquals(ArrayList<Integer> i1, ArrayList<Integer> i2) {





        return false;
    }

    // Reverse a list
    public static List<Unit> reverse(List<Unit> inp) {
        List<Unit> rev = inp;


        return rev;
    }

    // Return Jimple code as a list of statements
    private static List<Unit> getUnits() {
        List<Unit> units = new ArrayList<Unit>();
        body = targetMethod.retrieveActiveBody();
        for (Unit u : body.getUnits()) {
            units.add(u);
        }
        return units;
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