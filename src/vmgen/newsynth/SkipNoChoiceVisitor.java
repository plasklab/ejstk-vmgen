/*
   SkipNoChoiceVisitor.java

   eJS Project
     Kochi University of Technology
     the University of Electro-communications

     Tomoharu Ugawa, 2016-18
     Hideya Iwasaki, 2016-18
 */
package vmgen.newsynth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import vmgen.newsynth.DecisionDiagram.Leaf;
import vmgen.newsynth.DecisionDiagram.Node;
import vmgen.newsynth.DecisionDiagram.TagNode;

public class SkipNoChoiceVisitor extends NodeVisitor<Node> {

    @Override
    Node visitLeaf(Leaf node) {
        return node;
    }

    @Override
    <T> Node visitTagNode(TagNode<T> node) {
        ArrayList<Node> children = node.getChildren();
        if (children.size() == 1)
            return children.get(0).accept(this);
        TreeMap<Node, Node> replace = new TreeMap<Node, Node>();
        for (Node before: children) {
            Node after = (Node) before.accept(this);
            replace.put(before, after);
        }
        for (T tag: node.getEdges()) {
            Node before = node.getChild(tag);
            Node after = replace.get(before);
            node.replaceChild(tag, after);
        }
        return node;
    }

}
