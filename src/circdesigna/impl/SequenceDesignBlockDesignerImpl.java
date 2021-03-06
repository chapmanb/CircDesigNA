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
package circdesigna.impl;

import static circdesigna.GSFR.DNAMARKER_DONTMUTATE;

import java.util.ArrayList;
import java.util.Arrays;

import circdesigna.CircDesigNA;
import circdesigna.CircDesigNA.ScorePenalty;
import circdesigna.DesignerCode;
import circdesigna.DomainDefinitions;
import circdesigna.abstractDesigner.SingleMemberDesigner;



/**
 * Implementation of BlockDesigner
 */
public class SequenceDesignBlockDesignerImpl extends SingleMemberDesigner<CircDesigNAPMemberImpl>{
	public SequenceDesignBlockDesignerImpl(int[] mutableDomains, DesignerCode[] mutators, DomainDefinitions dsd, CircDesigNA domainDesigner, CircDesigNAPMemberImpl backupPMember){
		//Temporary backup member for performing reversions
		this.defaultBackupCache = backupPMember;
		this.dsd = dsd;
		this.mutators = mutators;
		this.dd = domainDesigner;
		this.mutableDomains = mutableDomains;
		mutation_shared = new Mutation();
		
		PROB_MUT_DOMAIN = .9;
		PROB_MUT_BASE = .9; 
	}
	private DesignerCode[] mutators;
	private CircDesigNAPMemberImpl defaultBackupCache;
	private Mutation mutation_shared;
	int[] mutableDomains;
	private CircDesigNA dd;
	private DomainDefinitions dsd;
	private double PROB_MUT_BASE;
	private double PROB_MUT_DOMAIN;
	
	public void setMutationProbabilities(double PROB_MUT_DOMAIN, double PROB_MUT_BASE) {
		this.PROB_MUT_BASE = PROB_MUT_BASE;
		this.PROB_MUT_DOMAIN = PROB_MUT_DOMAIN;
	}
	public double getOverallScore(CircDesigNAPMemberImpl q) {
		double current_score = 0;
		for(ScorePenalty s : q.penalties){
			//Sanity check
			if (s.old_score!=s.cur_score){
				throw new RuntimeException("Get overallScore called in the middle of mutation");
			}
			current_score += s.old_score; 
		}
		return current_score;
	}
	
	private class Mutation {
		private ArrayList<Integer> mut_domains = new ArrayList();
		private boolean revert_mutation, newPointReached;
		
		public void Mutate(CircDesigNAPMemberImpl q, CircDesigNAPMemberImpl backup, boolean fullBackup){
			mut_domains.clear();
			
			if (fullBackup){
				backup.seedFromOther(q);
			}
			
			//System.out.print("#ds="+num_domains_mut+" ");

			int total_mutated = 0;
			while(total_mutated == 0) { //mutate at least one base
				for(int mut_domain : mutableDomains){
					//Mutate this domain?
					if (Math.random() < PROB_MUT_DOMAIN){
						if (!fullBackup){
							//Backup
							System.arraycopy(q.domain[mut_domain],0,backup.domain[mut_domain],0,q.domain[mut_domain].length);
							System.arraycopy(q.domain_markings[mut_domain],0,backup.domain_markings[mut_domain],0,q.domain[mut_domain].length);
						}
						//Mutate
						int mutated = dd.mutateDomain(mut_domain, q.domain, q.domain_markings, mutators[mut_domain], PROB_MUT_BASE);
						total_mutated += mutated;
						if (mutated > 0){
							mut_domains.add(mut_domain);
						}						
					}
				}
			}
			//System.out.print("#ns="+total_mutated+" ");
		}
		
		public void Evaluate(CircDesigNAPMemberImpl q, boolean ShortcircuitOnRegression){
			//Reset markers.
			for(int mut_domain : mut_domains){
				Arrays.fill(q.domain_markings[mut_domain], DNAMARKER_DONTMUTATE);
			}
			double deltaScoreSum = 0;
			
			int priority;
			priorityLoop: for(priority = 0; priority <= 2; priority++){
				for(int mut_domain : mut_domains){
					for(int sd : q.scoredElements[mut_domain]){
						ScorePenalty s = q.penalties.get(sd);
						if (s.in_intermediate_state){
							continue; //Already scored this penalty (perhaps it uses more than one domain)
						}
						if (s.getPriority()==priority){
							s.evalScore(q.domain,q.domain_markings); //STATE CHANGE
							s.in_intermediate_state = true; //Set scored flag.
						}
					}
				}

				//Decide whether we improved.

				double deltaScore = 0;
				for(ScorePenalty s : q.penalties){
					if (s.getPriority()==priority){
						deltaScore += s.getCDelta();
					}
				}

				revert_mutation = false;
				newPointReached = false;

				if (deltaScore > 0){
					revert_mutation = true;
				}
				
				/*
				if (bestWorstPenaltyScore[priority-1]<worstPenaltyScore){
					revert_mutation = true;
				} else if (bestWorstPenaltyScore[priority-1]==worstPenaltyScore){
					//Very often, there is a "wall" at a problem spot - allow the rest of the system
					//to also optimize in the background.
					if (deltaScore > 0){
						revert_mutation = true;
					}
				} else {
					newPointReached = true;
				}
				 */

				deltaScoreSum += deltaScore;
				if (ShortcircuitOnRegression){
					if (revert_mutation){
						//Short circuit! Get out of there.
						priority ++;

						break priorityLoop;
					} else {
						//keep the mutations
					}
				}
			}

			if (deltaScoreSum > 0){
				revert_mutation = true;
			}
			if (deltaScoreSum < 0){
				newPointReached = true;
			}
			
			if (!revert_mutation && priority < 3){
				throw new RuntimeException("Not all penalties ran!");
			}
		}

		public void Revert(CircDesigNAPMemberImpl q) {
			for(int mut_domain : mut_domains){
				//Revert ALL scores.
				for(int sd : q.scoredElements[mut_domain]){
					ScorePenalty s = q.penalties.get(sd);
					s.revert();
				}
				//Have to go back to old sequences..
				System.arraycopy(defaultBackupCache.domain[mut_domain],0,q.domain[mut_domain],0,q.domain[mut_domain].length);
				System.arraycopy(defaultBackupCache.domain_markings[mut_domain],0,q.domain_markings[mut_domain],0,q.domain[mut_domain].length);
			}
		}
	}
	
	public boolean mutateAndTestAndBackup(CircDesigNAPMemberImpl q) {
		//num_domains_mut = int_urn(1,Math.min(Math.min(worstPenalty==null?1:worstPenalty.getNumDomainsInvolved()-1,MAX_MUTATION_DOMAINS),mutableDomains.length-1));
		//System.out.print("M ");
		mutation_shared.Mutate(q, defaultBackupCache, false);
		//System.out.print("E ");
		//Short circuiting constraint evaluation: If score was improved, keep, otherwise, break.
		//Penalties with higher priority are presumably harder to compute, thus the shortcircuiting advantage
		mutation_shared.Evaluate(q, true);
		//System.out.print("D ");

		//Having run some or all of the affected penalty calculations,
		//Revert the states or Dedicate them as need be here.
		if (mutation_shared.revert_mutation){
			//Revert
			/*
			if (ENABLE_MARKINGS){
				for(int[] row : domain_markings){
					for(int q : row){
						System.out.printf("%4d",q);
					}
					System.out.println();
				}
			}
			*/
			mutation_shared.Revert(q);

			//Which priority did we short circuit on?
			/*
			if (priority==2){
				//If we're having trouble with crosstalk (which is really crazy n^2 stuff), 
				//ignore the advice of the self fold server.
				for(int k = 0; k < num_domains_mut; k++){
					mut_domain = mut_domains[k];
					//System.out.println(Arrays.toString(domain_markings[mut_domain]));
					Arrays.fill(q.domain_markings[mut_domain], 1);
				}
			}
			*/
			/*
			for(k = 0; k < num_domains_mut; k++){
				mut_domain = mut_domains[k];
				for(ScorePenalty s : scoredElements[mut_domain]){
					s.evalScore(domain, domain_markings);
					if (s.cur_score!=s.old_score){
						throw new RuntimeException("Assertion failure.");
					}
				}
			}
			*/
			return false;
		} else {
			//Dedicate
			for(ScorePenalty s : q.penalties){
				double l = s.cur_score;
			 	s.dedicate();
			 	
				//Check dedication! Slow!
			 	/*
				if (s.evalScore(q.domain, q.domain_markings)!=0){
					System.out.println(s.getClass());
					throw new RuntimeException("FAIL!");
				}
				*/
			}
			/*
			System.out.println("Current matrix:[");
			for(float[] row : DIR.currentMatrix){
				for(float val : row){
					System.out.printf("%.3f, ",val);
				}
				System.out.println();
			}
			System.out.println("]");
			*/

			/*
			if (OUTPUT_RUNTIME_STATS){
				System.out.println(num_mut_attempts+" , "+best_score);
			}
			//System.out.println(Arrays.toString(worstPenalty.getSeqs()));
			//current_score += deltaScoreSum;
			best_score = current_score;
			displayDomains(domain, false); //Updates public domains

			if (DebugPenaltiesNow){
				printDebugPenalties();
				System.out.println();
				displayDomains(domain, true);
				if (ENABLE_MARKINGS && false){
					for(int[] row : domain_markings){
						System.out.println(Arrays.toString(row));
					}
				}
			}

			double bestWorstPenaltyScoreSum = 0;
			for(double q : bestWorstPenaltyScore){
				//Disallow negative contributions. The overall EndThreshold can still be >0, however.
				bestWorstPenaltyScoreSum += Math.max(q,0);
			}
			if (bestWorstPenaltyScoreSum <= EndThreshold){
				break;
			}
			*/
			//System.out.printf("Components: CX%.3f IN%.3f JX%.3f INT%.3f\n",crosstalkSum,interactionSum,junctionPenalties,selfCrossTalk);

			return true;
		}
	}

	/**
	 * Copies q into into, and then mutates into, evaluating its new penalties.
	 * Allows into to be a regression of q.
	 */
	public boolean mutateAndEval(CircDesigNAPMemberImpl q, CircDesigNAPMemberImpl into) {
		into.seedFromOther(q);
		mutation_shared.Mutate(into, defaultBackupCache, true);
		mutation_shared.Evaluate(into, false);
		for(ScorePenalty s : into.penalties){
			double l = s.cur_score;
		 	s.dedicate();
		}
		return !mutation_shared.revert_mutation;
	}

	/**
	 * Chromosomal-style crossover of solutions. Creates a new member (into) which is
	 * still inside the search space, assuming a and b were.
	 */
	public boolean fourPtCrossoverAndEval(CircDesigNAPMemberImpl a,
			CircDesigNAPMemberImpl b, CircDesigNAPMemberImpl into) {
		
		into.seedFromOther(a);
		
		//is totalBases calculated?
		if (a.totalBases==0 || into.totalBases==0){
			int totalBases = 0;
			for(int[] row : a.domain){
				totalBases += row.length;
			}
			into.totalBases = a.totalBases = b.totalBases = totalBases;
		}
		
		int ptA = (int)(Math.random()*a.totalBases);
		//ptB not calculated
		int windowSize = (int)(Math.random()*(a.totalBases-ptA)) + 1;
		int ptC = (int)(Math.random()*(a.totalBases-windowSize));
		//ptD not calculated.
		
		//for(int domain = 0; domain < a.domain.length; domain++){
		//	System.arraycopy(a.domain[domain], 0, into.domain[domain], 0, a.domain[domain].length);
		//}
		//Keep track of which domains were mutated:
		mutation_shared.mut_domains.clear();
		
		//0..ptA from a :: ptC..ptD from b :: ptB to totalBits from b
		//copy2d(a.domain,0,into.domain,0,ptA);
		copy2d(b.domain,ptC,into.domain,ptA,windowSize,mutation_shared);
		//copy2d(a.domain,ptA+windowSize,into.domain,ptA+windowSize,a.totalBases - (ptA+windowSize));
		
		//copy2d(a.domain_markings,0,into.domain_markings,0,ptA);
		//copy2d(b.domain_markings,ptC,into.domain_markings,ptA,windowSize);
		//copy2d(a.domain_markings,ptA+windowSize,into.domain_markings,ptA+windowSize,a.totalBases - (ptA+windowSize));
		
		//System.out.println(Arrays.deepToString(a.bits));
		//System.out.println(Arrays.deepToString(b.bits));
		//System.out.println(Arrays.deepToString(into.bits));
		
		//Clear markings
		if (false){
			for(int[] row : into.domain_markings){
				Arrays.fill(row,0);
			}
			//Reevaluate ALL penalties
			for(ScorePenalty q : into.penalties){
				q.getScore(into.domain, into.domain_markings);
			}
		} else {
			//Reevaluate only penalties that copy2d marked.
			mutation_shared.Evaluate(into, false);
			for(ScorePenalty q : into.penalties){
				q.dedicate();
			}
		}
		//One of the parents is favored. This appears to be by design.
		//Improvement over both parents?
		double newScore = getOverallScore(into);
		if (newScore < getOverallScore(a) && newScore < getOverallScore(b)){
			return true;
		}
		return false;
	}
	
	/**
	 * Copy exactly end-start+1 bits from bits to bits2, starting at bits.
	 */
	private int copy2d_ai, copy2d_ay, copy2d_bi, copy2d_by;
	private void copy2d_step(int[][] bits){
		copy2d_ay++;
		if (copy2d_ay >= bits[copy2d_ai].length){
			copy2d_ay = 0;
			copy2d_ai++;
		}
		copy2d_by++;
		if (copy2d_by >= bits[copy2d_bi].length){
			copy2d_by = 0;
			copy2d_bi++;
		}
	}
	private void copy2d(int[][] bits, int srcoff, int[][] bits2, int dstoff, int num, Mutation recorder) {
		int i,y = 0, ct = 0;
		big: for(i = 0; i < bits.length; i++){
			for(y = 0; y < bits[i].length; y++){
				if (ct++ == dstoff){
					break big;
				}
			}
		}
		copy2d_bi = i;
		copy2d_by = y; 
		ct = 0;
		big: for(i = 0; i < bits.length; i++){
			for(y = 0; y < bits[i].length; y++){
				if (ct++ == srcoff){
					break big;
				}
			}
		}
		copy2d_ai = i;
		copy2d_ay = y;
		int sucessful = 0;
		for(int mut_domain = 0; mut_domain < bits.length; mut_domain++){
			mutators[mut_domain].init();
		}
		for(i = 0; i < num; i++){
			//Record that we're writing to a domain:
			if (recorder.mut_domains.isEmpty() || recorder.mut_domains.get(recorder.mut_domains.size()-1)!=copy2d_bi){
				recorder.mut_domains.add(copy2d_bi);
			}
			if (bits2[copy2d_bi][copy2d_by] == bits[copy2d_ai][copy2d_ay]){
				sucessful++;
			} else {
				if (mutators[copy2d_bi].mutateToOther(bits2, copy2d_bi, copy2d_by, bits[copy2d_ai][copy2d_ay])){
					sucessful++;
				}
			}
			//bits2[copy2d_bi][copy2d_by] = bits[copy2d_ai][copy2d_ay];
			copy2d_step(bits);
		}
		//System.out.printf("%d of %d crossover bases successfully transferred.\n",sucessful,num);
	}

	
}
