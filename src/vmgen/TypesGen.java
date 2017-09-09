package vmgen;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TypesGen {
	String definePT() {
		StringBuilder sb = new StringBuilder();
		for (PT pt: VMRepType.allPT()) {
			sb.append(String.format("#define %s %d\n", pt.name, pt.value));
			sb.append(String.format("#define %s_MASK 0x%x\n", pt.name, (1 << pt.bits) - 1));
		}
		return sb.toString();
	}

	String defineHT() {
		StringBuilder sb = new StringBuilder();
		for (HT ht: VMRepType.allHT())
			sb.append(String.format("#define %s %d\n",  ht.name, ht.value));
		return sb.toString();
	}

	String minimumRepresentation(Collection<VMRepType> dts, Collection<VMRepType> among) {
		if (!among.containsAll(dts))
			throw new Error("Internal error");
		
		if (among.size() == 1)
			return "1";

		Collection<PT> unique = new HashSet<PT>();
		Collection<PT> common = new HashSet<PT>();
		Collection<HT> hts = new ArrayList<HT>();
		for (VMRepType rt: dts) {
			if (rt.hasUniquePT(among))
				unique.add(rt.getPT());
			else {
				common.add(rt.getPT());
				hts.add(rt.getHT());
			}
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("(((0");
		for (PT pt : common)
			sb.append(" || ")
			  .append(String.format("(((x) & %s_MASK) == %s)", pt.name, pt.name));
		sb.append(") && (0");
		for (HT ht : hts)
			sb.append(" || ")
			  .append(String.format("(obj_header_tag(x) == %s)", ht.name));
		sb.append("))");
		for (PT pt : unique)
			sb.append(" || ")
			  .append(String.format("(((x) & %s_MASK) == %s)", pt.name, pt.name));
		sb.append(")");
		
		return sb.toString();
	}

	String defineTypePredicates() {
		StringBuilder sb = new StringBuilder();

		sb.append("/* VM-DataTypes */\n");
		for (VMDataType dt: VMDataType.all()) {
			sb.append("#define is_").append(dt.name).append("(x) ");
			if (dt.getVMRepTypes().isEmpty())
				sb.append("0  /* not used */\n");
			else
				sb.append(minimumRepresentation(dt.getVMRepTypes(), VMRepType.all()))
				  .append("\n");
		}

		sb.append("/* VM-RepTypes */\n");
		for (VMDataType dt: VMDataType.all()) {
			for (VMRepType rt: dt.getVMRepTypes()) {
				Set<VMRepType> rtSingleton = new HashSet<VMRepType>(1);
				rtSingleton.add(rt);
				sb.append("#define is_").append(rt.name).append("(x) ");
				sb.append(minimumRepresentation(rtSingleton, dt.getVMRepTypes()));
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	void appendDataTypeFamilyPredicates(StringBuilder sb, String name, String[] dtNames) {
		Set<VMRepType> rts = new HashSet<VMRepType>();
		for (String dtName: dtNames)
			rts.addAll(VMDataType.get(dtName).getVMRepTypes());
		sb.append("#define is_").append(name).append("(x) ")
		  .append(minimumRepresentation(rts, VMRepType.all()))
		  .append("\n");
	}

	String defineDTFamilyPredicates() {
		StringBuilder sb = new StringBuilder();
		appendDataTypeFamilyPredicates(sb,  "object", new String[] {
			"simple_object",
			"array",
			"function",
			"builtin",
			"iterator",
			"regexp",
			"string_object",
			"number_object",
			"boolean_object"
		});
		appendDataTypeFamilyPredicates(sb, "number", new String[]{
			"fixnum",
			"flonum"
		});
		return sb.toString();
	}

	String defineNeed() {
		StringBuilder sb = new StringBuilder();
		sb.append("/* VM-DataTypes */\n");
		for (VMDataType dt: VMDataType.all())
			if (!dt.getVMRepTypes().isEmpty())
				sb.append("#define need_").append(dt.name).append(" 1\n");
		sb.append("/* customised types */\n");
		for (VMDataType dt: VMDataType.all()) {
			for (VMRepType rt: dt.getVMRepTypes())
				sb.append("#define need_"+rt.name+" 1\n");
			if (dt.getVMRepTypes().size() > 1)
				sb.append("#define customised_"+dt.name+" 1\n");
		}
		return sb.toString();
	}
	
	String defineTagOperations() {
		StringBuilder sb = new StringBuilder();

		/* leaf types */
		sb.append("/* leaf types */\n");
		for (VMRepType rt: VMRepType.all()) {
			String ptName = rt.getPT().name;
			String cast = rt.getStruct() == null ? "" : ("("+rt.getStruct()+" *)");
			sb.append("#define put_"+rt.name+"_tag(p) ")
			  .append("(put_tag((p), "+ptName+"))\n");
			sb.append("#define remove_"+rt.name+"_tag(p) ")
			  .append("("+cast+"remove_tag((p), "+ptName+"))\n");
		}

		return sb.toString();
	}

	public static void main(String[] args) throws FileNotFoundException {
		if (args.length == 1)
			TypeDefinition.load(args[0]);
		else
			TypeDefinition.load("datatype/new.dtdef"); // debug
		TypesGen tg = new TypesGen();
		System.out.println(tg.definePT());
		System.out.println(tg.defineHT());
		System.out.println(tg.defineTypePredicates());
		System.out.println(tg.defineDTFamilyPredicates());
		System.out.println(tg.defineTagOperations());
		System.out.println(tg.defineNeed());
		System.out.println(TypeDefinition.quoted);
	}
}