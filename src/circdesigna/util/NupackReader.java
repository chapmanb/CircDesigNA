package circdesigna.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

import circdesigna.CircDesigNA_SharedUtils;
import circdesigna.DomainDefinitions;
import circdesigna.DomainSequence;
import circdesigna.config.CircDesigNAConfig;
import circdesigna.config.CircDesigNASystemElement;

/**
 * Converts the multiobjective design specification of Nupack to the CircDesigNA domain specs / molecule specs syntax.
 */
public class NupackReader extends CircDesigNASystemElement{
	private String NupackSpecs;
	public NupackReader(CircDesigNAConfig cfg, String NupackSpecs){
		super(cfg);
		this.NupackSpecs = NupackSpecs;
	}
	public static void main(String[] args) throws IOException{
		CircDesigNAConfig cfg = new CircDesigNAConfig();
		String NupackSpecs = null;
		String outputFile = null;
		int i = 0;
		for(; i < args.length; i++){
			if (args[i].equals("--material")){
				i++;
				if (args[i].equalsIgnoreCase("dna")){
					cfg.setMode(CircDesigNAConfig.DNA_MODE);
				} else
				if (args[i].equalsIgnoreCase("rna")){
					cfg.setMode(CircDesigNAConfig.RNA_MODE);
				} else {
					throw new RuntimeException("Unrecognized material: " + args[i]);
				}
			} else if (args[i].equals("-i")){
				i++;
				NupackSpecs = readCompletely(args[i]);
			} else if (args[i].equals("-o")){
				i++;
				outputFile = args[i];
			}
		}
		
		if (NupackSpecs == null || outputFile == null){
			printUsage();
			System.exit(1);
		}
		NupackReader converter = new NupackReader(cfg, NupackSpecs);
		PrintWriter out = new PrintWriter(new File(outputFile));
		converter.toCircDesigNA(out);
		out.close();
	}
	private static void printUsage() {
		System.out.println("Usage: java "+NupackReader.class+" [--material <dna / rna>] -d <domains> -m <molecules> -o <outputfile>");
		System.out.println("\t This class converts a system specified in Nupack's Multiobjective Design syntax to CircDesigNA syntax");
		System.out.println("\t-i : Specify a file containing the Nupack multiobjective design specification");
		System.out.println("\t-o : Specify a file to hold the output CircDesigNA specification");
	}
	private static String readCompletely(String filename) {
		try {
			Scanner in = new Scanner(new File(filename));
			StringBuffer sb = new StringBuffer();
			while(in.hasNextLine()){
				sb.append(in.nextLine());
				sb.append("\n");
			}
			return sb.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Outputs the nupack specification to printwriter out
	 */
	private void toCircDesigNA(PrintWriter out) {
		Scanner in = new Scanner(NupackSpecs);
		StringBuffer domainDefs = new StringBuffer();
		DomainDefinitions dsd = new DomainDefinitions(Std);
		List<String> molecules = new ArrayList();
		Map<String, String> structures = new HashMap();
		while(in.hasNext()){
			String term = in.next();
			if (term.startsWith("#")){
				in.nextLine();
			} else if (term.equals("material")){
				in.next();
				/*
				On second thought, I don't care.
				if (!in.next().equals(Std.monomer.toString())){
					throw new RuntimeException("Material in Nupack file disagrees with --material flag");
				}
				*/
				in.nextLine();
			} else if (term.equals("domain")){
				String domainName = in.next();
				in.next();
				String constraint = toCDNAConstraint(in.nextLine());
				domainDefs.append(domainName);
				domainDefs.append("\t");
				domainDefs.append(constraint);
				domainDefs.append("\n");
				//Update dsd
				DomainDefinitions.readDomainDefs(domainDefs.toString(), dsd);
			} else if (term.equals("structure")){
				String molName = in.next();
				in.next();
				String line = in.nextLine();
				structures.put(molName, line);
			} else if (term.endsWith(".seq")){
				String molName = term.substring(0,term.length()-4);
				String structure = structures.get(molName);
				in.next();
				molecules.add(toCDNAMolecule(molName, structure, in.nextLine(), dsd));
			} else 
			{
				//Ignore unrecognized lines
				in.nextLine();
			}
		}
		
		out.println("# Domains");
		for(int i = 0; i < dsd.domainLengths.length; i++){
			out.println(dsd.getDomainName(i)+" "+dsd.getConstraint(i));
		}

		out.println("");
		out.println("# Molecules");
		for(String k : molecules){
			out.println(k);
		}
	}
	private String toCDNAMolecule(String molName, String structure, String domains, DomainDefinitions dsd) {
		structure = structure.trim();
		domains = domains.trim();
		StringBuffer cdna = new StringBuffer();
		
		Queue<String> structureTerms = new LinkedList();
		Queue<String> domainTerms = new LinkedList();
		{
			Scanner in = new Scanner(structure);
			while(in.hasNext()){
				structureTerms.add(in.next());
			}
			in = new Scanner(domains);
			while(in.hasNext()){
				domainTerms.add(in.next());
			}
		}
		
		while(!structureTerms.isEmpty()){
			toCDNAMolecule_(structureTerms, domainTerms, dsd, cdna);
		}
		if (!domainTerms.isEmpty()){
			throw new RuntimeException("Invalid DU+ notation: ");
		}
		
		return molName+" ["+cdna.toString()+"}";
	}
	/**
	 * recursively convert DU+ notation to dot parens with domains 
	 */
	private void toCDNAMolecule_(Queue<String> structureTerms, Queue<String> domainTerms, DomainDefinitions dsd, StringBuffer cdna) {
		String structureTerm = structureTerms.poll();
		if (structureTerm.startsWith("D")){
			int length = new Integer(structureTerm.substring(1));
			int wroteLeft = 0;
			while(wroteLeft < length){
				DomainSequence ds = new DomainSequence();
				ds.setDomains(domainTerms.poll(), dsd, null);
				cdna.append(ds.toString(dsd)+"( ");
				wroteLeft += ds.length(dsd);
			}
			if (wroteLeft != length){
				throw new RuntimeException("Duplex length and domains don't match up." + structureTerm);
			}
			//Recurse into interior of duplex (next term, or more if parens used)
			toCDNAMolecule_(structureTerms, domainTerms, dsd, cdna);
			int wroteRight = 0;
			while(wroteRight < length){
				DomainSequence ds = new DomainSequence();
				ds.setDomains(domainTerms.poll(), dsd, null);
				cdna.append(ds.toString(dsd)+") ");
				wroteRight += ds.length(dsd);
			}
			if (wroteRight != length){
				throw new RuntimeException("Duplex length and domains don't match up." + structureTerm);
			}
		} else if (structureTerm.startsWith("U")){
			int length = new Integer(structureTerm.substring(1));
			int wrote = 0;
			while(wrote < length){
				DomainSequence ds = new DomainSequence();
				ds.setDomains(domainTerms.poll(), dsd, null);
				cdna.append(ds.toString(dsd)+" ");
				wrote += ds.length(dsd);
			}
			if (wrote != length){
				throw new RuntimeException("Unpaired length and domains don't match up." + structureTerm);
			}
		} else if (structureTerm.equals("(")){
			//Read blocks until an ) is encountered on this level
			while(!structureTerms.isEmpty()){
				toCDNAMolecule_(structureTerms, domainTerms, dsd, cdna);
				if (structureTerms.peek().equals(")")){
					structureTerms.poll();
					return;
				}
			}
		} else if (structureTerm.equals("+")){
			cdna.append("} [");
		} else {
			throw new RuntimeException("Invalid DU+ notation: " + structureTerm);
		}
	}
	private String toCDNAConstraint(String line) {
		//Converts a constraint of the form N3 T4 AA Y2 to NNNTTTTAAYY
		StringBuffer out = new StringBuffer();
		line = line.replaceAll("\\s+","");
		for(int k = 0; k < line.length(); k++){
			int mult = 1;
			char now = line.charAt(k);
			if (k + 1 < line.length()){
				int end = k+1;
				for(; end < line.length(); end++){
					if (!Character.isDigit(line.charAt(end))){
						break;
					}
				}
				if (end == k+1){
					mult = 1;
				} else {
					mult = new Integer(line.substring(k+1, end));
					k = end;
				}
			}
			for(int i = 0; i < mult; i++){
				out.append(now);
			}
		}
		return out.toString();
	}
}

