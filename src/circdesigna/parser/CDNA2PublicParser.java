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
package circdesigna.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.ListIterator;

import beaver.Parser.Exception;
import circdesigna.DomainDefinitions;

public class CDNA2PublicParser {
	public static class ParseResult {
		public String moleculeName;
		public ArrayList parse;
		public String toString(){
			StringBuffer sb = new StringBuffer();
			sb.append(moleculeName);
			sb.append(" ");
			for(Object k : parse){
				sb.append(k.toString()+" ");
			}
			return sb.toString();
		}
	}
	public static ParseResult parse(String info, DomainDefinitions domains){
		CDNA2Parser cp = new CDNA2Parser();
		ParseResult res = new ParseResult();
		try {
			String[] info2 = info.split("\\s+",2);
			if (info2.length!=2){
				throw new RuntimeException("Correct molecule format: <name> <molecule>");
			}
			ArrayList parse = (ArrayList) cp.parse(new CDNA2Scanner(new StringReader(info2[1])));
			res.moleculeName = (String) info2[0];
			for(ListIterator oi = parse.listIterator(); oi.hasNext();){
				Object o = oi.next();
				if (o instanceof CDNA2Token.Domain){
					CDNA2Token.Domain co = ((CDNA2Token.Domain)o);
					co.validate(domains);
					
					//Special: expand combined domains
					String comboNameL = co.name;
					if (comboNameL.endsWith("*")){
						comboNameL = comboNameL.substring(0, comboNameL.length()-1);
					}
					String[] combinedDomains = domains.expandCombinationName(comboNameL);
					if (combinedDomains != null){
						oi.remove();
						
						if (co.name.endsWith("*")){
							combinedDomains = complement(combinedDomains);
						}
						for(String n : combinedDomains){
							CDNA2Token.Domain no = new CDNA2Token.Domain(n);
							no.close = co.close;
							no.open = co.open;
							no.ss = co.ss;
							oi.add(no);
						}
					}
				}
			}
			
			res.parse = new ArrayList();
			res.parse.addAll(parse);
			
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (Exception e) {
			if (e.getCause().equals("")){
				throw new RuntimeException(e.getMessage());
			}
			throw new RuntimeException(e.getCause());
		}
		return res;
	}
	private static String[] complement(String[] a) {
		String[] b = new String[a.length];
		for(int i = 0; i < a.length; i++){
			b[i] = a[a.length-1-i];
			if (b[i].endsWith("*")){
				b[i] = b[i].substring(0,b[i].length()-1);
			} else {
				b[i] += "*";
			}
		}
		return b;
	}
}
