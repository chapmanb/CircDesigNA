/*
  Part of the CircDesigNA Project - http://cssb.utexas.edu/circdesigna
  
  Copyright (c) 2010-11 Ben Braun
  
  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/
package circdesigna;
import static circdesigna.DomainSequence.NA_COMPLEMENT_FLAG;
import static circdesigna.DomainSequence.NA_COMPLEMENT_FLAGINV;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import circdesigna.config.CircDesigNAConfig;
import circdesigna.config.CircDesigNASystemElement;


/**
 * The information parsed from the DomainDefs block is encoded in an object of this class.
 */
public class DomainDefinitions extends CircDesigNASystemElement{
	public DomainDefinitions(CircDesigNAConfig Std){
		super(Std);
	}
	/**
	 * Invertible.
	 */
	private Map<String, Integer> nameMap = new TreeMap();
	private Map<String, String[]> combinedDomains = new TreeMap();
	//keys are domains converted to numbers.
	public String getDomainName(int domain){
		String postpend = ((domain & NA_COMPLEMENT_FLAG)!=0?"*":"");
		for(Entry<String, Integer> q : nameMap.entrySet()){
			if (q.getValue()==(domain & NA_COMPLEMENT_FLAGINV)){
				return q.getKey()+postpend;
			}
		}
		return ""+(domain & NA_COMPLEMENT_FLAGINV)+postpend;
	}
	public int getDomainLength(int domain) {
		domain &= NA_COMPLEMENT_FLAGINV;
		return domainLengths[domain];
	}
	/**
	 * Throws an exception if the domainName is not registered, and returns a negative value
	 * if the domain is registered, but isn't given a number (i.e., domains of length 0)
	 */
	public int lookupDomainName(String domainName) {
		if (!nameMap.containsKey(domainName)){
			return -1;
		}
		return nameMap.get(domainName);
	}
	public int[] domainLengths;
	private Map<Integer, String> domainConstraints = new TreeMap();
	private Map<Integer, String> compositionConstraints = new TreeMap();
	private Map<Integer, String> domainInitialSeqs = new TreeMap();
	private static final int FLAG_CONSERVEAMINOS = 2;
	private Map<Integer, Integer> otherRuleFlags = new TreeMap();
	private Map<Integer, String> reprintArguments = new TreeMap();

	public String getArguments(int k) {
		return reprintArguments.get(k);
	}
	/**
	 * Returns the constraint for the NONCOMPLEMENTED version of this domain.
	 * You must handle complementation yourself in handling the constraints!
	 */
	public String getConstraint(int domain){
		domain &= NA_COMPLEMENT_FLAGINV;
		if (domainConstraints.containsKey(domain)){
			return domainConstraints.get(domain);
		}
		StringBuffer wT = new StringBuffer();
		for(int k = 0; k < domainLengths[domain]; k++){
			wT.append("N");
		}
		return wT.toString();
	}
	public String getInitialSequence(int domain) {
		domain &= NA_COMPLEMENT_FLAGINV;
		if (domainInitialSeqs.containsKey(domain)){
			return domainInitialSeqs.get(domain);
		}
		StringBuffer wT = new StringBuffer();
		for(int k = 0; k < domainLengths[domain]; k++){
			wT.append("N");
		}
		return wT.toString();
	}

	/** 
	 * Pass in whole block, please
	 **/
	public static void readDomainDefs(String domainDefsBlock, DomainDefinitions out){
		if (out.domainLengths!=null){
			Arrays.fill(out.domainLengths,0);
		}
		out.nameMap.clear();
		out.domainConstraints.clear();
		out.compositionConstraints.clear();
		out.domainInitialSeqs.clear();
		out.otherRuleFlags.clear();
		ArrayList<Integer> domainLengths = new ArrayList<Integer>();
		Scanner in = new Scanner(domainDefsBlock);
		int k = 0;
		while(in.hasNextLine()){
			parseDomainDefLine(in.nextLine(),out,domainLengths,k);
			k++;
		}
		
		//Remove
		
		
		out.domainLengths = new int[domainLengths.size()];
		for(k = 0; k < domainLengths.size(); k++){
			out.domainLengths[k] = domainLengths.get(k);
			//Test the seq constraints
			DesignSequenceConstraints dsc = new DesignSequenceConstraints(out.Std);
			out.loadConstraints(k, dsc, true);
		}
	}

	private static void parseDomainDefLine(String nextLine, DomainDefinitions out, List<Integer> domainLengths, int lineNumber) {
		//Hack to simulate tokenizing 
		nextLine = nextLine.replaceAll("\\s*,\\s*",",");
		nextLine = nextLine.replaceAll("\\s*\\(\\s*","(");
		nextLine = nextLine.replaceAll("\\s*\\)",")");
		nextLine = nextLine.replaceAll("\\s*\\+\\s*","+");
		
		String[] line = nextLine.trim().split("\\s+");
		if (line.length<=1){ //0 is sort of impossible though.
			return;
		}
		String domainID = line[0];
		if (domainID.length()==0){
			throw new RuntimeException("'Empty' Domain ID: on line "+(lineNumber+1));
		}
		if (Character.isWhitespace(nextLine.charAt(0))){
			throw new RuntimeException("'Empty' Domain ID: on line "+(lineNumber+1)+". (Leading whitespace?)");
		}
		if (out.nameMap.containsKey(domainID)){
			throw new RuntimeException("Listed Domain "+domainID+" twice. (on line "+(lineNumber+1)+")");
		}
		

		int domain = domainLengths.size();
		
		int term = 1;
		int seqLen = -1;
		String argsCumulativeString = "";
		int flags = 0;
		if (line[1].matches("\\d+")){
			//We have length map (optional)
			term = 2;
			seqLen = new Integer(line[1]);
		}
		//Sequence constraints...
		String positionalConstraints = null;
		String compositionConstraint = null;
		String initialSeq = null;
		if (line.length>term){
			//Parse the position-specific constraints block, if we have one.
			if (line[term].charAt(0)!='-'){
				argsCumulativeString += line[term]+" "; //Add the constraints as an "argument"
				StringBuffer constraintParsed = new StringBuffer();
				for(int e = 0; e < line[term].length(); e++){
					char kc = line[term].charAt(e);
					if (Character.toLowerCase(kc)=='u'){
						kc = 'T'; // rna / dna treated equally.
					}
					//Capital means mutable.
					kc = Character.toUpperCase(kc);
					constraintParsed.append(kc);
				}
				line[term] = constraintParsed.toString();
				//Ok! load the constraint. Default to "unconstrained".
				if (line[term].equalsIgnoreCase("TBD")){
					//Lol, silly format.
				} else {
					positionalConstraints = line[term];
					//Test the constraint out:
					for(char u : line[term].toCharArray()){
						out.Std.monomer.decodeConstraintChar(u);
					}
					seqLen = line[term].length();
				}
				term++;
			}
			//Parse arguments.
			//Do we have flags?
			Pattern decodeArg = Pattern.compile("\\-([^\\(]*)(\\((.*)\\))?");
			for(int flag = term; flag < line.length; flag++){
				Matcher m = decodeArg.matcher(line[flag]);
				if(m.find()){
					String paramName = m.group(1);
					try {
						if (!paramName.equalsIgnoreCase("init")){
							//All args BUT init.
							argsCumulativeString += line[flag]+" ";
						}
						
						String args = m.group(3);
						if (paramName.equalsIgnoreCase("p")){
							if (seqLen%3!=0){
								throw new RuntimeException("Domain "+domainID+" not a valid protein sequence - length not a multiple of 3");
							}
							flags |= FLAG_CONSERVEAMINOS;
						} else  
						if (paramName.equalsIgnoreCase("seq")){
							String cc = compositionConstraint;
							if (cc==null){
								cc = "";
							}
							while(cc.endsWith(",")){
								cc = cc.substring(0,cc.length()-1);
							}
							if (cc.contains(",")){
								cc+=",";
							}
							if (args==null){
								throw new RuntimeException(String.format("Invalid argument to parameter %s. Missing parenthesis?",paramName));
							}
							compositionConstraint = cc + args;
						} else 
						if (paramName.equalsIgnoreCase("init")){
							if (args==null){
								throw new RuntimeException(String.format("Invalid argument to parameter %s. Missing parenthesis?",paramName));
							}
							if (args.length() != seqLen){
								throw new RuntimeException(String.format("Initialization is not length %d, as it should be. %s",seqLen,args));
							}
							if (initialSeq!=null){
								throw new RuntimeException("Multiple initialization blocks are not allowed.");
							}
							//Test the initialization out:
							for(char u : args.toCharArray()){
								int b = out.Std.monomer.decodeInitializationChar(u);
							}
							initialSeq = args;
						} else
						if (paramName.equalsIgnoreCase("combine")){
							//Ignore all other parameters and exit.
							out.addCombinedDomains(domainID, args);
							return;
						} else {
							throw new RuntimeException("No such option");
						}
					} catch (Throwable e){
						throw new RuntimeException("Invalid args to '-"+paramName+"': "+e.getMessage());
					}
				}
			}
		}
		if (seqLen==-1){
			throw new RuntimeException("Assertion error - no length for domain '"+domainID+"'");
		}
		domainLengths.add(-1);
		if (positionalConstraints != null){
			out.domainConstraints.put(domain,positionalConstraints);
		}
		if (compositionConstraint != null){
			out.compositionConstraints.put(domain, compositionConstraint);
		}
		if (initialSeq != null){
			out.domainInitialSeqs.put(domain,initialSeq);
		}
		domainLengths.set(domain, seqLen);//initialize length
		out.nameMap.put(domainID,domain);
		out.reprintArguments.put(domain,argsCumulativeString);
		out.otherRuleFlags.put(domain,flags);
	}

	private void addCombinedDomains(String domainID, String combine) {
		combinedDomains.put(domainID, combine.replaceAll("\\s+","").split(","));
	}
	
	boolean maintainAminos(int k) {
		Integer integer = otherRuleFlags.get(k);
		if (integer==null){
			return false;
		}
		return (integer & FLAG_CONSERVEAMINOS)!=0;
	}

	/**
	 * Load the constraints associated with domain i into constraints object dsc.
	 */
	public void loadConstraints(int i, DesignSequenceConstraints dsc, boolean crashOnOverwrite) {
		String args = compositionConstraints.get(i);
		if (args!=null){
			String[] array = args.split(",");
			if (array.length%3!=0){
				throw new RuntimeException("Each constraint has 3 parts: base, min, and max");
			}

			{ //Set ceiling: all bases.
				dsc.setMaxConstraint(domainLengths[i], Std.monomer.getMonomers());
			}
			for(int k = 0; k < array.length; k+=3){
				ArrayList<Integer> bases = new ArrayList();
				for(char q : array[k].toCharArray()){
					if (Character.isLetter(q)){
						q = Character.toUpperCase(q);
						int base = Std.monomer.decodeBaseChar(q);
						bases.add(base);
					} else if (q == '+' || Character.isWhitespace(q)){
						//Allowed.
					} else {
						throw new RuntimeException("Invalid character in -seq argument: "+q);
					}
				}
				//pure base.
				int num1 = parsePercent(array[k+1],domainLengths[i],false);
				int num2 = parsePercent(array[k+2],domainLengths[i],true);
				if (num1 < -1 || num2 < -1){
					throw new RuntimeException("Bound values must be >= -1. -1 means no bound.");
				}
				if (num2 !=-1 && num1 != -1 && num2 < num1){
					throw new RuntimeException("Invalid bound: max < min");
				}
				int[] bases2 = new int[bases.size()];
				int count = 0;
				for(int j : bases){
					bases2[count++]=j;
				}
				boolean hadMin = dsc.setMinConstraint(num1, bases2);
				boolean hadMax = dsc.setMaxConstraint(num2, bases2);
				if (crashOnOverwrite && (hadMax||hadMin)){
					throw new RuntimeException("Duplicate bounds for "+array[k]);
				}
			}
		}
	}

	
	private static int parsePercent(String percent, int of, boolean roundUp){
		boolean isP = percent.endsWith("%");
		boolean isF = percent.contains(".");
		if (isP || isF){
			double value;
			if (isP){
				value = new Double(percent.substring(0,percent.length()-1))/100.;	
			} else {
				//isF only
				value = new Double(percent);
			}
			value *= of;
			if (roundUp){
				return (int) Math.ceil(value);
			} else {
				return (int) Math.floor(value);
			}
		}
		return new Integer(percent);
	}
	/**
	 * Returns the combination domains in the format (one per line)
	 * 
	 * <comboname> -combine(<domains>)
	 * 
	 * where <domains> contains the list of domains this combination domain contains.
	 */
	public String getCombinationDomainSpecs() {
		StringBuffer sb = new StringBuffer();
		for(Entry<String, String[]> combo : combinedDomains.entrySet()){
			sb.append(combo.getKey()+" -combine(");
			String[] val = combo.getValue();
			for(int i = 0; i < val.length; i++){
				sb.append(val[i]);
				if (i + 1 < val.length){
					sb.append(", ");
				}
			}
			sb.append(")\n");
		}
		return sb.toString();
	}
	public String[] expandCombinationName(String name) {
		String nameL = name;
		if (name.endsWith("*")){
			nameL = name.substring(0, name.length()-1);
		}
		String[] toRet = combinedDomains.get(nameL);
		if (toRet == null){
			return new String[]{name};
		}
		if (name.endsWith("*")){
			toRet = complement(toRet);
		}
		return toRet;
	}
	private String[] complement(String[] a) {
		String[] b = new String[a.length];
		for(int i = 0; i < b.length; i++){
			b[i] = a[a.length-1-i];
			if (b[i].endsWith("*")){
				b[i] = b[i].substring(0, b[i].length()-1);
			} else {
				b[i] += "*";
			}
		}
		return b;
	}
}
