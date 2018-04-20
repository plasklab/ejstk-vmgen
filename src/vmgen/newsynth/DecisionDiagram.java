package vmgen.newsynth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import vmgen.RuleSet;
import vmgen.RuleSet.Rule;
import vmgen.newsynth.LLRuleSet.LLRule;
import vmgen.type.VMRepType;
import vmgen.type.VMRepType.HT;
import vmgen.type.VMRepType.PT;

public class DecisionDiagram {
	public static final boolean DEBUG_COMMENT = true;
	public static final int MERGE_LEVEL = 2; // 0-2: 0 is execution spped oriendted, 2 is size oriented
	
	static final int DISPATCH_TAGPAIR = 0;
	static final int DISPATCH_PT_BASE = 10;
	static final int DISPATCH_HT_BASE = 20;
	static final int[] DISPATCH_PLAN = {
			DISPATCH_TAGPAIR,
			DISPATCH_PT_BASE + 0,
			DISPATCH_PT_BASE + 1,
			DISPATCH_HT_BASE + 0,
			DISPATCH_HT_BASE + 1
	};
	
	static abstract class Node {
		abstract Object accept(NodeVisitor visitor);		
		int depth() {
			int max = 0;
			for (Node child: getChildren()) {
				int d = child.depth();
				if (d > max)
					max = d;
			}
			return max + 1;
		}
		abstract ArrayList<Node> getChildren();
		
		// returns a merged node
		// other should be compatible with this
		// this method does not mutate this object
		abstract Node merge(Node other);
	}
	
	static class Leaf extends Node {
		LLRuleSet.LLRule rule;
		Leaf(LLRuleSet.LLRule rule) {
		 	this.rule = rule;
		}
		LLRuleSet.LLRule getRule() {
			return rule;
		}
		@Override
		Object accept(NodeVisitor visitor) {
			return visitor.visitLeaf(this);
		}
		@Override
		ArrayList<Node> getChildren() {
			return new ArrayList<Node>();
		}
		boolean hasSameHLRule(Leaf other) {
			return getRule().getHLRule() == other.getRule().getHLRule();
		}
		@Override
		Node merge(Node otherx) {
			return this;
		}
	}
	static abstract class TagNode<T> extends Node {
		int opIndex;
		HashMap<T, Node> branches = new HashMap<T, Node>();
		
		TagNode(int opIndex) {
			this.opIndex = opIndex;
		}
		void addBranch(TreeDigger digger, T tag) {
			Node child = branches.get(tag);
			child = digger.dig(child);
			branches.put(tag, child);
		}
		@Override
		Object accept(NodeVisitor visitor) {
			return visitor.visitTagNode(this);
		}
		@Override
		ArrayList<Node> getChildren() {
			LinkedHashSet<Node> s = new LinkedHashSet<Node>();
			for (T tag: branches.keySet())
				s.add(branches.get(tag));
			return new ArrayList<Node>(s);
		}
		Set<T> getEdges() {
			return branches.keySet();
		}
		void replaceChild(T tag, Node child) {
			branches.replace(tag, child);
		}
		Node getChild(T tag) {
			return branches.get(tag);
		}
		int getOpIndex() {
			return opIndex;
		}
		void makeMergedNode(TagNode<T> n1, TagNode<T> n2) {
			LinkedHashSet<T> union = new LinkedHashSet<T>(n1.branches.keySet());
			union.addAll(n2.branches.keySet());
			for (T tag: union) {
				Node c1 = n1.branches.get(tag);
				Node c2 = n2.branches.get(tag);
				if (c1 == null)
					branches.put(tag, c2);
				else if (c2 == null)
					branches.put(tag, c1);
				else {
					Node child = c1.merge(c2);
					branches.put(tag, child);
				}
			}
			mergeChildren(this);
		}
		HashMap<Node, LinkedHashSet<T>> getChildToTagsMap() {
			HashMap<Node, LinkedHashSet<T>> childToTags = new HashMap<Node, LinkedHashSet<T>>();
			for (T tag: branches.keySet()) {
				Node child = branches.get(tag);
				LinkedHashSet<T> tags = childToTags.get(child);
				if (tags == null) {
					tags = new LinkedHashSet<T>();
					childToTags.put(child, tags);
				}
				tags.add(tag);
			}
			return childToTags;
		}
	}
	static class TagPairNode extends TagNode<TagPairNode.TagPair> {
		static class TagPair {
			@Override
			public int hashCode() {
				return (op1.getValue() << 8) + op2.getValue();
			}
			@Override
			public boolean equals(Object obj) {
				if (obj == null)
					return false;
				if (!(obj instanceof TagPair))
					return false;
				TagPair other = (TagPair) obj;
				return op1 == other.op1 && op2 == other.op2;
			}
			PT op1;
			PT op2;
			TagPair(PT op1, PT op2) {
				this.op1 = op1;
				this.op2 = op2;
			}
		};
		TagPairNode() {
			super(-1);
		}
		@Override
		Object accept(NodeVisitor visitor) {
			return visitor.visitTagPairNode(this);
		}
		@Override
		Node merge(Node otherx) {
			throw new Error("merge for TagPairNode is called");
		}
	}
	static class PTNode extends TagNode<PT> {
		PTNode(int opIndex) {
			super(opIndex);
		}
		@Override
		Object accept(NodeVisitor visitor) {
			return visitor.visitPTNode(this);
		}
		@Override
		Node merge(Node otherx) {
			PTNode other = (PTNode) otherx;
			PTNode merged = new PTNode(opIndex);
			merged.makeMergedNode(this, other);
			return merged;
		}
	}
	static class HTNode extends TagNode<HT> {
		boolean noHT;
		Node child;
		HTNode(int opIndex) {
			super(opIndex);
			noHT = false;
		}
		@Override
		void addBranch(TreeDigger digger, HT tag) {
			if (tag == null) {
				if (branches.size() != 0)
					throw new Error("invalid tag assignment");
				noHT = true;
				child = digger.dig(child);
			} else
				super.addBranch(digger, tag);
		}
		@Override
		Object accept(NodeVisitor visitor) {
			return visitor.visitHTNode(this);
		}
		@Override
		ArrayList<Node> getChildren() {
			if (noHT) {
				ArrayList<Node> r = new ArrayList<Node>(1);
				r.add(child);
				return r;
			}
			return super.getChildren();
		}
		boolean isNoHT() {
			return noHT;
		}
		Node getChild() {
			return child;
		}
		@Override
		Node merge(Node otherx) {
			HTNode other = (HTNode) otherx;
			if (noHT) {
				HTNode merged = new HTNode(opIndex);
				merged.noHT = true;
				merged.child = child.merge(other.getChildren().get(0));
				return merged;
			}
			HTNode merged = new HTNode(opIndex);
			merged.makeMergedNode(this, other);
			return merged;
		}
	}
	
	static class TreeDigger {
		final LLRuleSet.LLRule rule;
		final VMRepType[] rts;
		final int arity;
		int planIndex;
		
		TreeDigger(LLRuleSet.LLRule r) {
			rule = r;
			rts = r.getVMRepTypes();
			arity = rts.length;
			planIndex = 0;
		}
		
		Node dig(Node nodex) {
			if (planIndex == DISPATCH_PLAN.length)
				return new Leaf(rule);
			
			int dispatchType = DISPATCH_PLAN[planIndex++];
			if (dispatchType == DISPATCH_TAGPAIR && arity == 2) {
				TagPairNode node = nodex == null ? new TagPairNode() : (TagPairNode) nodex;
				node.addBranch(this, new TagPairNode.TagPair(rts[0].getPT(), rts[1].getPT()));
				return node;
			} else if (DISPATCH_PT_BASE <= dispatchType &&
					   dispatchType < DISPATCH_HT_BASE &&
					   dispatchType - DISPATCH_PT_BASE < arity) {
				int opIndex = dispatchType - DISPATCH_PT_BASE;
				PTNode node = nodex == null ? new PTNode(opIndex) : (PTNode) nodex;
				node.addBranch(this, rts[opIndex].getPT());
				return node;
			} else if (DISPATCH_HT_BASE <= dispatchType &&
					   dispatchType - DISPATCH_HT_BASE < arity) {
				int opIndex = dispatchType - DISPATCH_HT_BASE;
				HTNode node = nodex == null ? new HTNode(opIndex) : (HTNode) nodex;
				node.addBranch(this, rts[opIndex].getHT());
				return node;
			} else
				throw new Error("invalid dispatch plan:"+dispatchType);
		}
	}
	
	Node root;
	
	public DecisionDiagram(LLRuleSet rs) {
		if (rs.getRules().size() == 0)
			return;
		for (LLRuleSet.LLRule r : rs.getRules()) {
			TreeDigger digger = new TreeDigger(r);
			root = digger.dig(root);
		}
		
//		System.out.println(generateCode(new String[] {"b1", "b2"}));

		mergeChildren(root);
		
//		System.out.println(generateCode(new String[] {"a1", "a2"}));
		
		root = skipNoChoice(root);

	}
	
	public String generateCode(String[] varNames) {
		return generateCodeForNode(root, varNames);
	}
	
	static String generateCodeForNode(Node node, String[] varNames) {
		CodeGenerateVisitor gen = new CodeGenerateVisitor(varNames);
		node.accept(gen);
		return gen.toString();
	}

	static boolean isCompatible(Node a, Node b) {
		IsCompatibleVisitor v = new IsCompatibleVisitor(a);
		return (Boolean) b.accept(v);
	}
	
	static void mergeChildren(Node node) {
		MergeChildrenVisitor v = new MergeChildrenVisitor();
		node.accept(v);
	}
	
	static Node skipNoChoice(Node node) {
		SkipNoChoiceVisitor v = new SkipNoChoiceVisitor();
		return (Node) node.accept(v);
	}
	
	public LLRule search(VMRepType[] rts) {
		SearchVisitor v = new SearchVisitor(rts);
		return (LLRule) root.accept(v);
	}
}
