/*
   IsCompatibleVisitor.java

   eJS Project
     Kochi University of Technology
     the University of Electro-communications

     Tomoharu Ugawa, 2016-18
     Hideya Iwasaki, 2016-18
 */
package vmgen.newsynth;

import java.util.ArrayList;
import java.util.TreeSet;

import vmgen.InsnGen.Option;
import vmgen.newsynth.DecisionDiagram.HTNode;
import vmgen.newsynth.DecisionDiagram.Leaf;
import vmgen.newsynth.DecisionDiagram.Node;
import vmgen.newsynth.DecisionDiagram.TagNode;

class IsCompatibleVisitor extends NodeVisitor<Boolean> {
    Option option;
    Node root;
    Node currentNodex;

    IsCompatibleVisitor(Node root, Option option) {
        this.option = option;
        this.root = root;
        currentNodex = root;
    }

    <T> boolean hasCompatibleBranches(TagNode<T> currentNode, TagNode<T> other) {
        TreeSet<T> union = new TreeSet<T>(currentNode.branches.keySet());
        union.addAll(other.branches.keySet());
        for (T tag: union) {
            Node thisChild = currentNode.branches.get(tag);
            Node otherChild = other.branches.get(tag);
            if (thisChild != null && otherChild != null) {
                currentNodex = thisChild;
                if (!otherChild.accept(this))
                    return false;
            }
        }
        return true;
    }

    @Override
    Boolean visitLeaf(Leaf other) {
        if (currentNodex instanceof Leaf) {
            Leaf currentNode = (Leaf) currentNodex;
            return currentNode.hasSameHLRule(other);
        }
        return false;
    }

    @Override
    <T> Boolean visitTagNode(TagNode<T> other) {
        if (currentNodex.getClass() == other.getClass()) {
            TagNode<T> currentNode = (TagNode<T>) currentNodex;
            if (currentNode.getOpIndex() != other.getOpIndex())
                throw new Error("opIndex mismatch");

            if (!option.getOption(Option.AvailableOptions.CMP_SIZE_INCREASING_MERGE, false)) {
                int currentNChildren = currentNode.getChildren().size();
                int otherNChildren = other.getChildren().size();
                // branch increasing
                if ((currentNChildren == 1 && otherNChildren > 1) ||
                    (currentNChildren > 1 && otherNChildren == 1))
                    return false;
                if (currentNChildren == 1 && otherNChildren == 1) {
                    if (option.getOption(Option.AvailableOptions.CMP_CORRECT_COMPATIBILITY, true)) {
                        currentNodex = currentNode.getChildren().get(0);
                        return other.getChildren().get(0).accept(this);
                    } else {
                        T currentTag = currentNode.branches.keySet().iterator().next();
                        T otherTag = other.branches.keySet().iterator().next();
                        if (currentTag != otherTag)
                            return false;
                    }
                }
            }

            return hasCompatibleBranches(currentNode, other);
        }
        return false;
    }

    @Override
    Boolean visitHTNode(HTNode other) {
        if (currentNodex instanceof HTNode) {
            HTNode currentNode = (HTNode) currentNodex;
            if (currentNode.getOpIndex() != other.getOpIndex())
                throw new Error("opIndex mismatch");

            // if each node has a single child, they are compatible iff their children are compatible,
            // regardless of existence of HT.
            if (currentNode.isNoHT() && other.isNoHT()) {
                currentNodex = currentNode.getChild();
                return other.getChild().accept(this);
            } else if (currentNode.isNoHT()) {
                ArrayList<Node> otherChildren = other.getChildren();
                if (otherChildren.size() == 1) {
                    currentNodex = currentNode.getChild();
                    return otherChildren.get(0).accept(this);
                } else
                    return false;
            } else if (other.isNoHT()) {
                ArrayList<Node> currentNodeChildren = currentNode.getChildren();
                if (currentNodeChildren.size() == 1) {
                    currentNodex = currentNodeChildren.get(0);
                    return other.getChild().accept(this);
                } else
                    return false;
            } else
                return visitTagNode(other);
        }
        return false;
    }
}