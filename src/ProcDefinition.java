import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class JSTypePairB {
    DataType left, right;
    JSTypePairB(DataType left, DataType right) {
        this.left = left;
        this.right = right;
    }
}


class TypeDispatchDefinitionBuilder {

    class Condition {}
    enum ConditionalOperator { OR, AND, NOT }
    class CompoundCondition extends Condition {
        ConditionalOperator op;
        Condition cond1, cond2;
        CompoundCondition(ConditionalOperator op, Condition c1, Condition c2) {
            this.op = op;
            this.cond1 = c1;
            this.cond2 = c2;
        }
        public String toString() {
            return "(" + op + "," + cond1 + "," + cond2 + ")";
        }
    }
    class AtomCondition extends Condition {
        int varIdx;
        DataType t;
        AtomCondition(int varIdx, String jsType) {
            this.varIdx = varIdx;
            this.t = DataType.get(jsType);
        }
        public String toString() {
            return "(" + varIdx + ":" + t + ")";
        }
    }

    int numOfVars;  // number of variables which will be checked jsType ( 2 or less )
    String[] vnames;  // variables which will be checked jsType

    TypeDispatchDefinitionBuilder(String[] vnames) {
        numOfVars = vnames.length;
        this.vnames = vnames;
    }

    List<String> cProgram;


    List<Pair<Condition,String>> actList = new LinkedList<Pair<Condition,String>>();
    Condition tmpCond = null;
    String csrc = null;
    void readB(String line) {
        if (line.matches("^\\\\when .*")) {
            Condition c = parseCondition(line.substring(6), new int[1], true);
            convertConditionToDNF(c);
            if (csrc != null) endWhenScopeB();
            startWhenScopeB(c);
        } else if (line.matches("^\\\\otherwise\\s*")) {
            if (csrc != null) endWhenScopeB();
            startWhenScopeB(null);
        } else {
            csrc = csrc + line + "\n";
        }
    }
    void endWhenScopeB() {
        actList.add(new Pair<Condition,String>(tmpCond, csrc));
    }
    void startWhenScopeB(Condition c) {
        tmpCond = c;
        csrc = "";
    }
    void endB() {
        endWhenScopeB();
    }
    ProcDefinition.TypeDispatchDefinition build() {
        List<Pair<JSTypePairB,String>> raw = actListToParts(actList);
        List<Pair<JSTypePairB,String>> twoOp = new LinkedList<Pair<JSTypePairB,String>>();
        List<Pair<JSTypePairB,String>> oneOpL = new LinkedList<Pair<JSTypePairB,String>>();
        List<Pair<JSTypePairB,String>> oneOpR = new LinkedList<Pair<JSTypePairB,String>>();
        Pair<JSTypePairB,String> otherwisep = new Pair<JSTypePairB,String>(null, null);
        splitByOpType(raw, twoOp, oneOpL, oneOpR, otherwisep);

        String otherwise = otherwisep.second();
        List<Pair<JSTypePairB,String>> result = new LinkedList<Pair<JSTypePairB,String>>();
        if (this.vnames.length == 1) {
            result.addAll(oneOpL);
            if (otherwise != null) {
                for (DataType dt : DataType.all()) {
                    boolean b = true;
                    for (Pair<JSTypePairB,String> e : result) {
                        JSTypePairB jtp = e.first();
                        if (jtp.left == dt) {
                            b = false;
                            break;
                        }
                    }
                    if (b) result.add(new Pair<JSTypePairB,String>(new JSTypePairB(dt, null), otherwise));
                }
            }
        } else if (this.vnames.length == 2) {
            result.addAll(twoOp);
            for (Pair<JSTypePairB,String> el : oneOpL) {
                DataType left = el.first().left;
                for (DataType right : DataType.all()) {
                    boolean willBeAdded = true;
                    for (Pair<JSTypePairB,String> etwo : twoOp) {
                        if (etwo.first().left == left && etwo.first().right == right) {
                            willBeAdded = false;
                            break;
                        }
                    }
                    if (!willBeAdded) break;
                    for (Pair<JSTypePairB,String> er : oneOpR) {
                        if (er.first().right == right) {
                            willBeAdded = false;
                            break;
                        }
                    }
                    if (!willBeAdded) break;
                    result.add(new Pair<JSTypePairB,String>(new JSTypePairB(left, right), el.second()));
                }
            }
            for (Pair<JSTypePairB,String> er : oneOpR) {
                DataType right = er.first().right;
                for (DataType left : DataType.all()) {
                    boolean willBeAdded = true;
                    for (Pair<JSTypePairB,String> etwo : twoOp) {
                        if (etwo.first().left == left && etwo.first().right == right) {
                            willBeAdded = false;
                            break;
                        }
                    }
                    if (!willBeAdded) break;
                    for (Pair<JSTypePairB,String> el : oneOpL) {
                        if (el.first().left == left) {
                            willBeAdded = false;
                            break;
                        }
                    }
                    if (!willBeAdded) break;
                    result.add(new Pair<JSTypePairB,String>(new JSTypePairB(left, right), er.second()));
                }
            }
            if (otherwise != null) {
                for (DataType left : DataType.all()) {
                    for (DataType right : DataType.all()) {
                        boolean willBeAdded = true;
                        for (Pair<JSTypePairB,String> e : result) {
                            if (e.first().left == left && e.first().right == right) {
                                willBeAdded = false;
                                break;
                            }
                        }
                        if (willBeAdded) {
                            result.add(new Pair<JSTypePairB,String>(new JSTypePairB(left, right), otherwise));
                        }
                    }
                }
            }
        }
        return new ProcDefinition.TypeDispatchDefinition(this.vnames, partsToRules(result));
    }
    private Set<Plan.Rule> partsToRules(List<Pair<JSTypePairB,String>> parts) {
        List<Pair<JSTypePairB,String>> _parts = new LinkedList<Pair<JSTypePairB,String>>(parts);
        Set<Plan.Rule> rules = new HashSet<Plan.Rule>();
        for (Pair<JSTypePairB,String> e : parts) {
            if (_parts.contains(e)) {
                Set<Plan.Condition> conditions = new HashSet<Plan.Condition>();
                List<Pair<JSTypePairB,String>> rmList = new LinkedList<Pair<JSTypePairB,String>>();
                for (Pair<JSTypePairB,String> _e : _parts) {
                    if (e.second() == _e.second()) {
                        if (this.vnames.length == 1) {
                            conditions.add(new Plan.Condition(_e.first().left.getName()));
                        } else if (this.vnames.length == 2) {
                            conditions.add(new Plan.Condition(_e.first().left.getName(), _e.first().right.getName()));
                        }
                        rmList.add(_e);
                    }
                }
                _parts.removeAll(rmList);
                rules.add(new Plan.Rule(e.second(), conditions));
            }
        }
        return rules;
    }
    private void conditionIntoParts(List<JSTypePairB> result, Condition arg) {
        if (arg instanceof CompoundCondition) {
            CompoundCondition cond = (CompoundCondition) arg;
            if (cond.op == ConditionalOperator.AND) {
                if (cond.cond1 instanceof AtomCondition && cond.cond2 instanceof AtomCondition) {
                    AtomCondition c1 = (AtomCondition) cond.cond1;
                    AtomCondition c2 = (AtomCondition) cond.cond2;
                    if (c1.varIdx == 0 && c2.varIdx == 1) {
                        result.add(new JSTypePairB(c1.t, c2.t));
                    } else if (c2.varIdx == 0 && c1.varIdx == 1) {
                        result.add(new JSTypePairB(c2.t, c1.t));
                    } else {
                        // error
                        System.out.println("error: " + c1.varIdx + ", " + c2.varIdx);
                    }
                } else {
                    // error
                    System.out.println("error: cond1 and cond2 must be AtomCondition.");
                }
            } else if (cond.op == ConditionalOperator.OR) {
                conditionIntoParts(result, cond.cond1);
                conditionIntoParts(result, cond.cond2);
            }
        } else if (arg instanceof AtomCondition) {
            AtomCondition atom = (AtomCondition) arg;
            if (atom.varIdx == 0) {
                result.add(new JSTypePairB(atom.t, null));
            } else if (atom.varIdx == 1) {
                result.add(new JSTypePairB(null, atom.t));
            } else {
                // error
                System.out.println("error: AtomCondition.varIdx must be '0' or '1'.");
            }
        }
    }
    private List<Pair<JSTypePairB,String>> actListToParts(List<Pair<Condition,String>> actList) {
        List<Pair<JSTypePairB,String>> result = new LinkedList<Pair<JSTypePairB,String>>();
        actList.forEach(act -> {
            if (act.first() == null) {
                result.add(new Pair<JSTypePairB,String>(null, act.second()));
            } else {
                List<JSTypePairB> jtps = new LinkedList<JSTypePairB>();
                conditionIntoParts(jtps, act.first());
                for (JSTypePairB j1 : jtps) {
                    boolean b = true;
                    for (Pair<JSTypePairB,String> e : result) {
                        JSTypePairB j2 = e.first();
                        if (j1.left == j2.left && j1.right == j2.right) {
                            // error
                            b = false;
                            System.out.println("error: same condition (" + j1 + ", " + j2 + ")");
                        }
                    }
                    if (b) result.add(new Pair<JSTypePairB, String>(j1, act.second()));
                }
            }
        });
        return result;
    }
    private void splitByOpType(List<Pair<JSTypePairB,String>> raw,
            List<Pair<JSTypePairB,String>> twoOp,
            List<Pair<JSTypePairB,String>> oneOpL,
            List<Pair<JSTypePairB,String>> oneOpR,
            Pair<JSTypePairB,String> otherwise) {
        for (Pair<JSTypePairB,String> e : raw) {
            JSTypePairB j = e.first();
            if (j == null) {
                otherwise.t = e.second();
            } else if (j.left != null) {
                if (j.right != null) {
                    twoOp.add(e);
                } else {
                    oneOpL.add(e);
                }
            } else {
                if (j.right != null) {
                    oneOpR.add(e);
                } else {
                    otherwise.t = e.second();
                }
            }
        }
    }

    private boolean eatSpaces(final String rawCond, int[] idx) {
        for (; idx[0] < rawCond.length(); idx[0]++) {
            if (rawCond.charAt(idx[0]) != ' ') break;
        }
        return idx[0] >= rawCond.length();
    }

    private Condition parseParenthesizedCondition(final String rawCond, int[] idx, boolean last) {
        // System.out.println("parseParenthesizedCondition: " + rawCond + " # " + idx[0]);
        int tmp = idx[0];
        eatSpaces(rawCond, idx);
        if (idx[0] + 1 >=  rawCond.length()) return null;
        if (rawCond.charAt(idx[0]) != '(')
            return null;
        idx[0]++;
        Condition ret = parseCondition(rawCond, idx, false);
        if (eatSpaces(rawCond, idx)) {
            idx[0] = tmp;
            return null;
        }
        if (rawCond.charAt(idx[0]) != ')') {
            idx[0] = tmp;
            return null;
        }
        idx[0]++;
        eatSpaces(rawCond, idx);
        if (last && idx[0] < rawCond.length()) {
            idx[0] = tmp;
            return null;
        }
        // System.out.println("Parenthesized ret is " + ret);
        return ret;
    }

    private Condition parseAtomCondition(final String rawCond, int[] idx, boolean last) {
        // System.out.println("parseAtomCondition: " + rawCond + " # " + idx[0]);
        eatSpaces(rawCond, idx);
        final Pattern ptnAtom = Pattern.compile("^(\\$\\w+)\\s*:\\s*(\\w+)\\s*");
        Matcher m = ptnAtom.matcher(rawCond.substring(idx[0]));
        if (!m.find()) {
            return null;
        }
        int vidx = -1;
        if      (vnames[0] != null && vnames[0].equals(m.group(1))) vidx = 0;
        else if (vnames[1] != null && vnames[1].equals(m.group(1))) vidx = 1;
        Condition ret = new AtomCondition(vidx, m.group(2));
        int tmp = idx[0];
        idx[0] = idx[0] + m.end();
        eatSpaces(rawCond, idx);
        // System.out.println("Atom ret is " + ret + " " + idx[0]);
        return ret;
    }

    private Condition parseCompoundConditionNOT(final String rawCond, int[] idx, boolean last) {
        // System.out.println("parseCompoundConditionNOT: " + rawCond + " # " + idx[0]);
        eatSpaces(rawCond, idx);
        int tmp = idx[0];
        if (idx[0] + 1 >= rawCond.length()) return null;
        if (rawCond.charAt(idx[0]) != '!') return null;
        idx[0]++;
        Condition cond1 = parseCondition(rawCond, idx, last);
        eatSpaces(rawCond, idx);
        return new CompoundCondition(ConditionalOperator.NOT, cond1, null);
    }

    private Condition parseCompoundConditionANDOR(final String rawCond, int[] idx, boolean last, ConditionalOperator op) {
        // System.out.println("parseCompoundCondition" + op + ": " + rawCond + " # " + idx[0]);
        eatSpaces(rawCond, idx);
        int tmp = idx[0];
        Condition left = parseParenthesizedCondition(rawCond, idx, false);
        if (left == null) {
            idx[0] = tmp;
            left = parseAtomCondition(rawCond, idx, false);
        }
        eatSpaces(rawCond, idx);
        if (left != null && idx[0] + 1 < rawCond.length()) {
            if (op == ConditionalOperator.AND) {
                if (!(rawCond.charAt(idx[0]) == '&' && rawCond.charAt(idx[0] + 1) == '&')) {
                    idx[0] = tmp;
                    return null;
                }
            } else if (op == ConditionalOperator.OR) {
                if (!(rawCond.charAt(idx[0]) == '|' && rawCond.charAt(idx[0] + 1) == '|')) {
                    idx[0] = tmp;
                    return null;
                }
            }
            idx[0] += 2;
        } else {
            idx[0] = tmp;
            return null;
        }
        eatSpaces(rawCond, idx);
        Condition right = parseCondition(rawCond, idx, last);
        if (right == null) {
            idx[0] = tmp;
            return null;
        }
        // System.out.println("CompoundCondition: " + op +", " + left + ", " + right);
        return new CompoundCondition(op, left, right);
    }

    private Condition parseCompoundCondition(final String rawCond, int[] idx, boolean last) {
        // System.out.println("parseCompoundCondition: " + rawCond + " # " + idx[0]);
        int tmp = idx[0];
        Condition cond = parseCompoundConditionNOT(rawCond, idx, last);
        if (cond != null)
            return cond;
        idx[0] = tmp;
        cond = parseCompoundConditionANDOR(rawCond, idx, last, ConditionalOperator.AND);
        if (cond != null) {
            return cond;
        }
        idx[0] = tmp;
        cond = parseCompoundConditionANDOR(rawCond, idx, last, ConditionalOperator.OR);
        if (cond != null)
            return cond;
        return null;
    }

    private Condition parseCondition(final String rawCond, int[] idx, boolean last) {
        // System.out.println("parseCondition: " + rawCond + " # " + idx[0]);
        int tmp = idx[0];
        Condition cond = null;
        cond = parseCompoundCondition(rawCond, idx, last);
        if (cond != null) return cond;
        idx[0] = tmp;
        cond = parseParenthesizedCondition(rawCond, idx, last);
        if (cond != null) return cond;
        idx[0] = tmp;
        cond = parseAtomCondition(rawCond, idx, last);
        if (cond != null) return cond;
        idx[0] = tmp;
        return null;
    }

    private void convertConditionToDNFStep1(Condition condition) {
        if (condition instanceof AtomCondition) return;
        // condition instance of CompoundCondition
        CompoundCondition cond = (CompoundCondition) condition;
        if (cond.op == ConditionalOperator.NOT) {
            convertConditionToDNFStep1(cond.cond1);
            if (cond.cond1 instanceof AtomCondition) return;
            CompoundCondition cond1 = (CompoundCondition) cond.cond1;
            if (cond1.op == ConditionalOperator.NOT) {
                cond.cond1 = cond1.cond1;
            } else if (cond1.op == ConditionalOperator.AND) {
                cond.cond1 = new CompoundCondition(ConditionalOperator.NOT, cond1.cond1, null);
                cond.cond2 = new CompoundCondition(ConditionalOperator.NOT, cond1.cond1, null);
                cond.op = ConditionalOperator.OR;
            } else if (cond1.op == ConditionalOperator.OR) {
                cond.cond1 = new CompoundCondition(ConditionalOperator.NOT, cond1.cond1, null);
                cond.cond2 = new CompoundCondition(ConditionalOperator.NOT, cond1.cond1, null);
                cond.op = ConditionalOperator.AND;
            }
        } else {
            convertConditionToDNFStep1(cond.cond1);
            convertConditionToDNFStep1(cond.cond2);
        }
    }

    private void convertConditionToDNFStep2(Condition condition) {
        if (condition instanceof AtomCondition) return;
        CompoundCondition cond = (CompoundCondition) condition;
        if (cond.op == ConditionalOperator.NOT) return;
        else if (cond.op == ConditionalOperator.AND) {
            boolean b = true;
            if (cond.cond1 instanceof CompoundCondition) {
                CompoundCondition cond1 = (CompoundCondition) cond.cond1;
                if (cond1.op == ConditionalOperator.OR) {
                    Condition c = cond.cond2;
                    cond.cond1 = new CompoundCondition(ConditionalOperator.AND, cond1.cond1, c);
                    cond.cond2 = new CompoundCondition(ConditionalOperator.AND, cond1.cond2, c);
                    cond.op = ConditionalOperator.OR;
                }
            }
            if (cond.cond2 instanceof CompoundCondition) {
                CompoundCondition cond2 = (CompoundCondition) cond.cond2;
                if (b && cond2.op == ConditionalOperator.OR) {
                    Condition c = cond.cond1;
                    cond.cond1 = new CompoundCondition(ConditionalOperator.AND, c, cond2.cond1);
                    cond.cond2 = new CompoundCondition(ConditionalOperator.AND, c, cond2.cond2);
                    cond.op = ConditionalOperator.OR;
                }
            }
        }
        convertConditionToDNFStep2(cond.cond1);
        convertConditionToDNFStep2(cond.cond2);
    }

    private void convertConditionToDNF(Condition condition) {
        convertConditionToDNFStep1(condition);
        convertConditionToDNFStep2(condition);
    }
}


interface DefinitionBuilder {
    void read(String line);
    void start();
    void end();
    ProcDefinition.Definition build();
}

class InstDefinitionBuilder implements DefinitionBuilder {
    String instName;
    String[] dispatchVars, otherVars;
    TypeDispatchDefinitionBuilder tdDef;
    InstDefinitionBuilder(String instName, String[] dispatchVars, String[] otherVars, TypeDispatchDefinitionBuilder tdDef) {
        this.instName = instName;
        this.dispatchVars = dispatchVars;
        this.otherVars = otherVars;
        this.tdDef = tdDef;
    }
    InstDefinitionBuilder(String instDefLine) {
        Pattern ptnInst = Pattern.compile("^\\s*(\\w+)(\\s*\\[\\s*((\\$?\\w+)\\s*(,\\s*(\\$?\\w+)\\s*)?)?\\])?(\\s*\\((\\s*(\\$?\\w+)(\\s*,\\s*(\\$?\\w+)(\\s*,\\s*((\\$?\\w+)))?)?)?\\s*\\))?\\s*$");
        Matcher m = ptnInst.matcher(instDefLine);
        if (m.find()) {
            // \inst INST_NAME [ x1 , x2 ] ( y1 , y2 , y3 )
            // (2, INST_NAME)  (4, x1)  (6, x2)  (9, y1)  (11, y2)  (13, y3)
            String instName = m.group(1);
            String x1 = m.group(4), x2 = m.group(6);
            String y1 = m.group(9), y2 = m.group(11), y3 = m.group(13);
            String[] xs, ys;
            if (x1 != null) {
                if (x2 != null) {
                    xs = new String[2];
                    xs[1] = x2;
                } else xs = new String[1];
                xs[0] = x1;
            } else xs = new String[0];
            if (y1 != null) {
                if (y2 != null) {
                    if (y3 != null) {
                        ys = new String[3];
                        ys[2] = y3;
                    } else ys = new String[2];
                    ys[1] = y2;
                } else ys = new String[1];
                ys[0] = y1;
            } else ys = new String[0];
            this.instName = instName;
            this.dispatchVars = xs;
            this.otherVars = ys;
            // this.tdDef = new TypeDispatchDefinition(this.dispatchVars);
        } else {
            // error
            System.out.println("parsing error: inst statement");
        }
    }
    public void read(String line) {
        tdDef.readB(line);
    }
    public void start() {
        tdDef = new TypeDispatchDefinitionBuilder(dispatchVars);
    }
    public void end() {
        tdDef.endB();
    }
    //public Set<Plan.Rule> toRules() {
        //return tdDef.toRules();
    //}
    public String toString() {
        String ret = "Instruction name: " + instName + "\n";
        ret += "dispatch vars:";
        for (String v : dispatchVars) {
            ret += " " + v;
        }
        ret += "\nother vars: ";
        for (String v : otherVars) {
            ret += " " + v;
        }
        ret += "\n";
        ret += tdDef.toString();
        ret += "\n";
        return ret;
    }
    @Override
    public ProcDefinition.InstDefinition build() {
        ProcDefinition.TypeDispatchDefinition def = this.tdDef.build();
        return new ProcDefinition.InstDefinition(this.instName, this.dispatchVars, this.otherVars, def);
    }
}

class FuncDefinition {
    List<String> cSrcLines = new LinkedList<String>();
}


public class ProcDefinition {

    static class TypeDispatchDefinition {
        String[] vars;
        Set<Plan.Rule> rules;
        TypeDispatchDefinition(String[] vars, Set<Plan.Rule> rules) {
            this.vars = vars;
            this.rules = rules;
        }
    }

    interface Definition {
        void gen(Synthesiser synthesiser);
    }

    static class InstDefinition implements Definition {
        String name;
        String[] dispatchVars, otherVars;
        TypeDispatchDefinition tdDef;
        InstDefinition(String name, String[] dispatchVars, String[] otherVars, TypeDispatchDefinition tdDef) {
            this.name = name;
            this.dispatchVars = dispatchVars;
            this.otherVars = otherVars;
            this.tdDef = tdDef;
        }
        public void gen(Synthesiser synthesiser) {
            StringBuilder sb = new StringBuilder();
            sb.append(name + "_HEAD:\n");
            Plan p = new Plan(dispatchVars, tdDef.rules);
            sb.append(synthesiser.synthesise(p));
            try {
                File file = new File(OUT_DIR + "/" + name + ".c");
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
                pw.print(sb.toString());
                pw.close();
            }catch(IOException e){
                System.out.println(e);
            }
        }
    }

    List<InstDefinition> instDefs = new LinkedList<InstDefinition>();

    private void addDef(Definition def) {
        if (def instanceof InstDefinition) {
            instDefs.add((InstDefinition) def);
        }
    }

    void load(String fname) throws FileNotFoundException {
        Scanner sc = new Scanner(new FileInputStream(fname));
        DefinitionBuilder defb = null;
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.matches("^\\\\inst .+")) {
                if (defb != null) {
                    defb.end();
                    addDef(defb.build());
                }
                defb = new InstDefinitionBuilder(line.substring(6));
                defb.start();
            } else if (line.matches("^\\\\func ")) {
                if (defb != null) {
                    defb.end();
                    addDef(defb.build());
                }
                // func
            } else {
                defb.read(line);
            }
        }
        if (defb != null) {
            defb.end();
            addDef(defb.build());
        }
    }

    public String toString() {
        String ret = "InstDefinition:\n";
        for (InstDefinition def : instDefs) {
            ret += def.toString() + "\n";
        }
        return ret;
    }

    static final String OUT_DIR = "./generated";

    public static void main(String[] args) throws FileNotFoundException {
        TypeDefinition td = new TypeDefinition();
        td.load("datatype/ssjs_origin.dtdef");
        ProcDefinition procDef = new ProcDefinition();
        procDef.load("datatype/insts.idef");
        SimpleSynthesiser ss = new SimpleSynthesiser();
        if (!(new File(OUT_DIR).exists())) {
            File dir = new File(OUT_DIR);
            dir.mkdir();
        }
        for (InstDefinition instDef : procDef.instDefs) {
            System.out.println(instDef.name);
            instDef.gen(ss);
        }
    }
}
