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
package circdesigna.abstractDesigner;

import java.util.Iterator;
import java.util.TreeSet;

import circdesigna.CircDesigNA;



/**
 * Implementation of an infinite resource tournament designer, where each member is allowed
 * to consume resources (mutation tests) until it improves. Once every member improves slightly,
 * then the fittest members "reproduce" at some probability, P. To keep the population size constant,
 * for every reproduction, the least fit member is dropped.
 * 
 * Additionally, once a member improves, it is removed from the mutating pool until the end of the
 * "block iteration". This focuses more resources on those that are having the most difficulty improving.
 * 
 * @author Benjamin
 */
public class InfiniteResourceTournament <T extends PopulationDesignMember<T>>  extends TournamentDesigner <T> {
	public InfiniteResourceTournament(SingleMemberDesigner<T> SingleDesigner) {
		super(SingleDesigner);
	}
	private int param_iterationShortcut = 1000;
	private int numElites = 1;
	public void runBlockIteration_(CircDesigNA runner, double endThreshold) {
		TreeSet<T> blockIterationLevel = new TreeSet();
		for(int k = 0; k < populationSize; k++){
			blockIterationLevel.add(population_mutable[k]);
		}
		blockItr: for(int itrCount = 0;; itrCount++){
			Iterator<T> qb = blockIterationLevel.iterator();
			//for(int k = 0; k < 1; k++){ //multiple times for random chance.
				while(qb.hasNext()){
					T q = qb.next();
					boolean mutationSuccessful = SingleDesigner.mutateAndTestAndBackup(q);
					if (mutationSuccessful){
						//An improvement was seen.
						double newScore = SingleDesigner.getOverallScore(q);
						if (newScore <= endThreshold){
							break blockItr;
						}
						qb.remove();
					} else {
						//a backup was performed.
					}
					if(runner != null && runner.abort){
						break;
					}
				}
			//}
			setProgress((itrCount+1), param_iterationShortcut);
			if (blockIterationLevel.size()<populationSize*.5){
				break;
			}
			if (param_iterationShortcut>=0){
				if (itrCount>param_iterationShortcut && populationSize-blockIterationLevel.size()>0){
					//Someone made it through. Break;
					System.err.println("Breaking iteration early.");
					break;
				}
			}
		}
		
		tournamentSelect(numElites);
	}
}
