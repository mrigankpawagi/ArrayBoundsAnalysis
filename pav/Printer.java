package pav;

import java.util.*;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.Body;
import soot.UnitPrinter;
import soot.NormalUnitPrinter;

public class Printer{
    // Generate Array safety output as mentioned in the requirements
    public static void ArraySafety(String targetDirectory, String tClass, String tMethod, Map<Integer, String> safetyMap) {
        // Create a file Output_tclass_tmethod.txt
        String outputFileName = targetDirectory + "/Output_" + tClass + "_" + tMethod + ".txt";
        try {
            java.io.FileWriter fw = new java.io.FileWriter(outputFileName);
            java.io.PrintWriter pw = new java.io.PrintWriter(fw);

            for (Map.Entry<Integer, String> safetyEntry : safetyMap.entrySet()) {
                pw.println(tClass + "." + tMethod + ": " + String.format("%02d", safetyEntry.getKey()) + ": " + safetyEntry.getValue());
            }

            pw.close();
            fw.close();
        } catch (java.io.IOException e) {
            System.out.println("Error writing to file " + outputFileName);
        }
    }

    // Generate Points-to-Analysis output as mentioned in the requirements
    public static void PointerAnalysis(String targetDirectory, String tClass, String tMethod, Map<Integer, LatticeElement> result) {
        // Create a file Output_tclass_points_to_analysis_tmethod.txt
        String outputFileName = targetDirectory + "/Output_" + tClass + "_points_to_analysis_" + tMethod + ".txt";
        Set<Unit> nullset = new HashSet<Unit>(); 
        nullset.add((Unit) null);
  
        try {
            java.io.FileWriter fw = new java.io.FileWriter(outputFileName);
            java.io.PrintWriter pw = new java.io.PrintWriter(fw);

            for (Integer point: result.keySet()) {
                if (point == 0) {
                    continue;
                }
                String prefix = tClass + "." + tMethod + ": in" + String.format("%02d", point) + ": ";
                LatticeElement latticeElement = result.get(point);
                if (latticeElement instanceof IntegerArrayPointer) {
                    IntegerArrayPointer intArrayPointer = (IntegerArrayPointer) latticeElement;
                    Map<Local, Set<Unit>> pointerMap = new HashMap<>(intArrayPointer.pointerMap);
                    Map<Unit, Integer> assignStmtMap = new HashMap<Unit, Integer>();

                    // Sort variable names
                    List<Local> locals = new ArrayList<Local>(pointerMap.keySet());
                    Collections.sort(locals, new Comparator<Local>() {
                        public int compare(Local l1, Local l2) {
                            return l1.getName().compareTo(l2.getName());
                        }
                    });

                    for (Local local : locals) {
                        // Skip singleton [null] sets
                        if(pointerMap.get(local).equals(nullset)) {
                            continue;
                        }
                        // Create pointerSet from pointerMap using assignStmtMap
                        Set<String> pointerSet = new HashSet<String>();
                        for(Unit stmt: pointerMap.get(local)) {
                            if(stmt == (Unit) null) {
                                pointerSet.add("null");
                            }
                            else {
                                if(! assignStmtMap.containsKey(stmt)) {
                                    if (assignStmtMap.size() == 0) {
                                        assignStmtMap.put(stmt, 0);
                                    } else {
                                        assignStmtMap.put(stmt, Collections.max(assignStmtMap.values()) + 1);
                                    }
                                }
                                pointerSet.add("new" + String.format("%02d", assignStmtMap.get(stmt)));
                            }
                        }
                        pw.println(prefix + local.getName() + ":" + pointerSet.toString());
                    }
                    pw.println();
                }
            }

            pw.close();
            fw.close();
        } catch (java.io.IOException e) {
            System.out.println("Error writing to file " + outputFileName);
        }
    }

    // Generate File 1 output as mentioned in the requirements
    public static void IntervalAnalysis(String targetDirectory, String tClass, String tMethod, Map<Integer, LatticeElement> result) {
        // Create a file Output_tclass_interval_analysis_tmethod.txt
        String outputFileName = targetDirectory + "/Output_" + tClass + "_interval_analysis_" + tMethod + ".txt";
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
                pw.println();
            }

            pw.close();
            fw.close();
        } catch (java.io.IOException e) {
            System.out.println("Error writing to file " + outputFileName);
        }
    }

    public static void printUnit(int lineno, Body b, Unit u){
        UnitPrinter up = new NormalUnitPrinter(b);
        u.toString(up);
        String linenostr = String.format("%02d", lineno) + ": ";
        System.out.println(linenostr + up.toString());
    }

    public static void Info(SootMethod entryMethod) {
        if (!entryMethod.isPhantom() && entryMethod.isConcrete()) {
            Body body = entryMethod.retrieveActiveBody();

            int lineno = 0;
            for (Unit u : body.getUnits()) {
                if (!(u instanceof Stmt)) {
                    continue;
                }
                printUnit(lineno, body, u);
                lineno++;
            }
        }
    }
}