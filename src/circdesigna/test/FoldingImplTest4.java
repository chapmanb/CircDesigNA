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
package circdesigna.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;


import circdesigna.DomainSequence;
import circdesigna.config.CircDesigNAConfig;
import circdesigna.energy.CircDesigNAMCSFolder;
import circdesigna.energy.UnafoldFolder;

public class FoldingImplTest4 {
	public static void main(String[] args) throws FileNotFoundException{
		File outFile = new File("output.txt");
		CircDesigNAConfig config = new CircDesigNAConfig();
		System.out.println(outFile.getAbsolutePath());
		//System.setOut(new PrintStream(new FileOutputStream(outFile)));
		//Error in this file. third column represents pairing of seq1 with seq1! Embarrasing.
		//Scanner in = new Scanner(new File("C:\\Users\\Benjamin\\CLASSWORK\\002. UT UNDERGRADUATE GENERAL\\EllingtonLab\\Circ_DesigNAPaper\\AssemblaRepo\\circdesignapaper_w_figures\\scoresComparison_SS.txt"));
		Scanner in = new Scanner(new File(System.getProperty("inFile")));
		in.nextLine();
		CircDesigNAMCSFolder fl = new CircDesigNAMCSFolder(config);
		UnafoldFolder fl_unafold = new UnafoldFolder(config);
		int[][] domain = new int[2][];
		int[][] domain_markings = new int[2][];

		DomainSequence ds1 = new DomainSequence();
		ds1.setDomains(0, null);
		DomainSequence ds2 = new DomainSequence();
		ds2.setDomains(1, null);
		while(in.hasNextLine()){
			String line2 = in.nextLine();
			String[] line = line2.split(" ");
			int seqLength = line[1].length();
			for(int j = 0; j < 1; j++){
				domain[j] = new int[seqLength];
				domain_markings[j] = new int[seqLength];
				for(int k = 0; k < seqLength; k++){
					domain[j][k] = config.monomer.decodeConstraintChar(line[j+1].charAt(k));
				}
			}
			double resultLocal = fl.mfe(ds1, domain, domain_markings);
			System.out.println(Arrays.toString(domain_markings[0]));
			Arrays.fill(domain_markings[0],0);
			//double result = fl.mfeHybridDeltaG_viaUnafold(ds1, ds1, domain, domain_markings);
			double result = fl_unafold.mfe(ds1, domain, domain_markings);
			System.out.println(Arrays.toString(domain_markings[0]));
			System.out.println(line2+" "+resultLocal+" "+(result));
		}
		System.out.flush();
	}
}
