/*
   SearchVisitor.java

   eJS Project
     Kochi University of Technology
     the University of Electro-communications

     Tomoharu Ugawa, 2016-18
     Hideya Iwasaki, 2016-18
 */
package vmgen.newsynth;

import vmgen.newsynth.DecisionDiagram.HTNode;
import vmgen.newsynth.DecisionDiagram.Leaf;
import vmgen.newsynth.DecisionDiagram.Node;
import vmgen.newsynth.DecisionDiagram.PTNode;
import vmgen.newsynth.DecisionDiagram.TagPairNode;
import vmgen.newsynth.DecisionDiagram.TagPairNode.TagPair;
import vmgen.newsynth.LLRuleSet.LLRule;
import vmgen.type.VMRepType;
import vmgen.type.VMRepType.HT;
import vmgen.type.VMRepType.PT;

public class SearchVisitor extends NodeVisitor<LLRule> {

    VMRepType[] rts;

    SearchVisitor(VMRepType[] rts) {
        this.rts = rts;
    }

    @Override
    LLRule visitLeaf(Leaf node) {
        return node.getRule();
    }

    @Override
    LLRule visitTagPairNode(TagPairNode node) {
        TagPair tag = new TagPair(rts[0].getPT(), rts[1].getPT());
        Node next = node.getChild(tag);
        return next.accept(this);
    }

    @Override
    LLRule visitPTNode(PTNode node) {
        PT tag = rts[node.opIndex].getPT();
        Node next = node.getChild(tag);
        return next.accept(this);
    }

    @Override
    LLRule visitHTNode(HTNode node) {
        if (node.isNoHT())
            return node.getChild().accept(this);
        HT tag = rts[node.opIndex].getHT();
        Node next = node.getChild(tag);
        return next.accept(this);
    }
}
