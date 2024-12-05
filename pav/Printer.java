package pav;

import java.util.*;

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
        try {
            java.io.FileWriter fw = new java.io.FileWriter(outputFileName);
            java.io.PrintWriter pw = new java.io.PrintWriter(fw);

            for (Integer point: result.keySet()) {
                pw.println(point + " : " + result.get(point));
            }

            pw.close();
            fw.close();
        } catch (java.io.IOException e) {
            System.out.println("Error writing to file " + outputFileName);
        }
    }
/*
    // Generate File 2 ouput as mentioned in the requirements
    public static void printTrace(String targetDirectory, String tClass, String tMethod) {
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
*/
}