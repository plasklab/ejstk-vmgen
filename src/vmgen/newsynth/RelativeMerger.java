/*
   RelativeMerger.java

   eJS Project
     Kochi University of Technology
     the University of Electro-communications

     Tomoharu Ugawa, 2016-18
     Hideya Iwasaki, 2016-18
 */
package vmgen.newsynth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;

import vmgen.newsynth.DecisionDiagram.HTNode;
import vmgen.newsynth.DecisionDiagram.Leaf;
import vmgen.newsynth.DecisionDiagram.Node;
import vmgen.newsynth.DecisionDiagram.TagNode;

public class RelativeMerger {
    static class LayerGatherVisitor extends NodeVisitor<Void> {
        ArrayList<Node> nodes = new ArrayList<Node>();
        int depth;

        LayerGatherVisitor(int depth) {
            this.depth = depth;
        }

        ArrayList<Node> get() {
            return nodes;
        }

        @Override
        Void visitLeaf(Leaf node) {
            if (depth != 0)
                throw new Error("depth too large");
            nodes.add(node);
            return null;
        }

        @Override
        <T> Void visitTagNode(TagNode<T> node) {
            if (depth == 0) {
                nodes.add(node);
                return null;
            }
            depth--;
            for (Node child: node.getChildren())
                child.accept(this);
            depth++;
            return null;
        }
    }

    static class ReplaceVisitor extends NodeVisitor<Void> {
        int depth;
        TreeMap<Node, Node> replace;

        ReplaceVisitor(int depth, TreeMap<Node, Node> replace) {
            this.depth = depth;
            this.replace = replace;
        }

        @Override
        Void visitLeaf(Leaf node) {
            throw new Error("attempt to replace children of Leaf");
        }

        @Override
        <T> Void visitTagNode(TagNode<T> node) {
            if (depth == 1) {
                if (node instanceof HTNode) {
                    HTNode htnode = (HTNode) node;
                    if (htnode.isNoHT()) {
                        Node before = htnode.getChild();
                        Node after = replace.get(before);
                        if (after != null)
                            htnode.replaceChild(after);
                        return null;
                    }
                }
                for (T tag: node.getEdges()) {
                    Node before = node.getChild(tag);
                    Node after = replace.get(before);
                    if (after != null)
                        node.replaceChild(tag, after);
                }
                return null;
            }
            depth--;
            for (Node child: node.getChildren())
                child.accept(this);
            depth++;
            return null;
        }
    }

    protected TreeMap<Node, Node> mergeNodes(ArrayList<Node> nodes) {
        TreeMap<Node, Node> replace = new TreeMap<Node, Node>();
        boolean[] hasMerged = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            TreeSet<Node> subjects = new TreeSet<Node>();
            if (hasMerged[i])
                continue;
            Node ni = nodes.get(i);
            Node merged = ni;
            subjects.add(ni);
            hasMerged[i] = true;
            for (int j = i + 1; j < nodes.size(); j++) {
                if (hasMerged[j])
                    continue;
                Node nj = nodes.get(j);
                if (!DecisionDiagram.isCompatible(merged, nj))
                    continue;
                merged = merged.merge(nj);
                subjects.add(nj);
                hasMerged[j] = true;
            }
            if (subjects.size() > 1)
                for (Node before: subjects)
                    replace.put(before, merged);
        }
        return replace;
    }

    void mergeRelative(Node root) {
        for (int i = root.depth() - 1; i >= 1; i--) {
            LayerGatherVisitor gv = new LayerGatherVisitor(i);
            root.accept(gv);
            ArrayList<Node> nodes = gv.get();

            
            /* sort */
            /*
            nodes.sort(new Comparator<Node>() {
                @Override
                public int compare(Node ax, Node bx) {
                    NodeVisitor<Integer> v = new NodeVisitor<Integer>() {
                        @Override
                        Integer visitLeaf(Leaf n) {
                            return 0;
                        }
                        @Override
                        <T> Integer visitTagNode(TagNode<T> n) {
                            int sz = n.getChildren().size() > 1 ? 1 : 0;
                            for (Node child: n.getChildren())
                                sz += child.accept(this);
                            return sz;
                        }
                    };
                    int a = ax.accept(v);
                    int b = bx.accept(v);
                    
                    return a - b;
                }
            });
            */
            nodes.sort(new Comparator<Node>() {
                @Override
                public int compare(Node o1, Node o2) {
                    return o1.compareTo(o2);
                }
            });

            /* merge */
            TreeMap<Node, Node> replace = mergeNodes(nodes);

            /* do replace */
            ReplaceVisitor rv = new ReplaceVisitor(i, replace);
            root.accept(rv);
        }
    }
}
