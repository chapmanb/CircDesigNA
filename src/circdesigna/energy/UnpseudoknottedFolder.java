package circdesigna.energy;

import java.lang.reflect.Array;
import java.util.Arrays;

import circdesigna.DomainSequence;
import circdesigna.config.CircDesigNAConfig;
import circdesigna.config.CircDesigNASystemElement;

/**
 * Uses an N^3 DP algorithm to compute the MFE among all unpseudoknotted folded structures
 * of one or two sequences.
 */
public class UnpseudoknottedFolder extends CircDesigNASystemElement implements ConstraintsNAFolding{
	private ExperimentalDuplexParams eParams;
	private int[][][] memo_shared;
	private FoldingConstraints constraints_shared;
	private int algorithmicOrder = 3;

	public UnpseudoknottedFolder(CircDesigNAConfig System) {
		super(System);
		eParams = new ExperimentalDuplexParams(System);
	}
	
	private boolean memoLocked = false;
	private boolean constraintsLocked = false;
	private int[][][] getMemo(int N){
		if (memoLocked){
			throw new RuntimeException("Attempt to multithread nonmultithreadsafe method MFE");
		}
		memoLocked = true;
		if (memo_shared == null || memo_shared[0].length < N){
			memo_shared = new int[EXTERNAL_EQ1_0S+1][N][N];
		}
		return memo_shared;
	}
	private void returnMemo(){
		memoLocked = false;
	}
	private FoldingConstraints getConstraints(int N){
		if (constraintsLocked){
			throw new RuntimeException("Attempt to multithread nonmultithreadsafe method MFE");
		}
		constraintsLocked = true;
		if (constraints_shared == null || constraints_shared.preventPairing[0].length < N){
			constraints_shared = new FoldingConstraints(N);
		}
		return constraints_shared;
	}
	private void returnConstraints(){
		constraintsLocked = false;
	}
	
	public void setOrder(int i) {
		algorithmicOrder = i;
	}
	

	public double mfe(DomainSequence seq1, DomainSequence seq2, int[][] domain, int[][] domain_markings) {
		return mfe(seq1, seq2, domain, domain_markings, false);
	}
	/**
	 * Computes the minimum free energy of the minimum free energy structure of all structures which are
	 * nonpseudoknotted and which do not make any allowed pairs (so complementary domains will not pair.)
	 * 
	 * If order is 2, the optimization searches only over all structures which have the nested loops property.
	 * (So, if a is paired to b, c is paired to d, either a < c < d < b or c < a < b < d.)
	 */
	public double mfe(DomainSequence seq1, DomainSequence seq2, int[][] domain, int[][] domain_markings, boolean onlyIllegalPairing) {
		int N1 = seq1.length(domain);
		int N2 = seq2.length(domain);
		int N = N1 + N2;
		
		int[] nicks;
		if (seq1.isCircular() && seq2.isCircular()){
			//TODO
			throw new RuntimeException();
		} else {
			if (seq2.isCircular()){
				//swap it with seq1
				DomainSequence tmp = seq1;
				seq1 = seq2;
				seq2 = tmp;
			}
			if (seq1.isCircular()){
				//1 is circular, to is not. TODO
				throw new RuntimeException();
			} else {
				//Neither are circular
				nicks = new int[]{N1-1,N-1};
			}
		}
			
		int[] seq = new int[N];
		int[][] seq_origin = new int[N][2];
		for(int k = 0; k < N1; k++){
			seq[k] = seq1.base(k, domain, Std.monomer);
			seq_origin[k][0] = seq1.domainAt(k, domain);
			seq_origin[k][1] = seq1.offsetInto(k, domain, true);
		}
		for(int k = 0; k < N2; k++){
			seq[N1+k] = seq2.base(k, domain, Std.monomer);
			seq_origin[N1+k][0] = seq2.domainAt(k, domain);
			seq_origin[N1+k][1] = seq2.offsetInto(k, domain, true);
		}

		int[][][] memo2 = getMemo(N);
		FoldingConstraints constraints = getConstraints(N);
		for(boolean[] row : constraints.preventPairing){
			Arrays.fill(row, false);
		}
		if (onlyIllegalPairing){
			for(int i = 0; i < N; i++){
				for(int j = 0; j < N; j++){
					if (Arrays.equals(seq_origin[i], seq_origin[j])){
						constraints.preventPairing[i][j] = true;
					}
				}
			}
		}
		
		NXFold(memo2, seq, N, nicks, true, constraints);

		int nStrands = 2;
		double toRet = getQe(memo2, 0, N-1, 0, 0) / 100.0 - .51 * (nStrands - 1);
		
		returnMemo();
		returnConstraints();
		
		return toRet;
	}
	public double mfe(DomainSequence domainSequence, int[][] domain, int[][] domain_markings) {
		return mfe(domainSequence, domain, domain_markings, false);
	}

	public double mfe(DomainSequence domainSequence, int[][] domain, int[][] domain_markings, boolean onlyIllegalPairing) {
		int N = domainSequence.length(domain);
		int[] nicks;
		if (domainSequence.isCircular()){
			nicks = new int[]{};
		} else {
			nicks = new int[]{N-1};
		}	
			
		int[] seq = new int[N];
		int[][] seq_origin = new int[N][2];
		for(int k = 0; k < N; k++){
			seq[k] = domainSequence.base(k, domain, Std.monomer);
			seq_origin[k][0] = domainSequence.domainAt(k, domain);
			seq_origin[k][1] = domainSequence.offsetInto(k, domain, true);
		}

		int[][][] memo2 = getMemo(N);
		FoldingConstraints constraints = getConstraints(N);
		for(boolean[] row : constraints.preventPairing){
			Arrays.fill(row, false);
		}
		if (onlyIllegalPairing){
			for(int i = 0; i < N; i++){
				for(int j = 0; j < N; j++){
					if (Arrays.equals(seq_origin[i], seq_origin[j])){
						constraints.preventPairing[i][j] = true;
					}
				}
			}
		}
		
		double toRet;

		if (domainSequence.isCircular()){
			NXFold(memo2, seq, N, nicks, false, constraints);
			int[][] Qb = memo2[PAIRED];
			//If the circular DNA has no pairs
			int best = 0;
			//If the circular DNA has one pair 
			for(int i = 0; i <= N-2; i++){
				for(int j = i+1; j <= N-1; j++){
					best = alt(best, combine(Qb[i][j],Qb[j][i]));
				}
			}
			toRet = best / 100.0;
		} else {
			//N3Fold(memo2, seq, N, nicks, true);
			NXFold(memo2, seq, N, nicks, true, constraints);
			toRet = getQe(memo2, 0, N-1, 0, 0) / 100.0;
		}
		
		returnMemo();
		returnConstraints();
		return toRet;
	}
	
	private void NXFold(int[][][] memo2, int[] seq, int N, int[] nicks, boolean onlyUpperTriangle, FoldingConstraints constraints) {
		switch(algorithmicOrder){
		case 3:
			N3Fold(memo2, seq, N, nicks, onlyUpperTriangle);
			return;
		case 2:
			N2Fold(memo2, seq, N, nicks, onlyUpperTriangle, constraints);
			return;
		}
		throw new RuntimeException("No algorithm for time complexity order: "+algorithmicOrder);
	}

	//Indeces in n^2 memo
	private static final int 
	EXTERNAL_00 = 0,
	EXTERNAL_01 = EXTERNAL_00+1,
	EXTERNAL_10 = EXTERNAL_01+1,
	EXTERNAL_11 = EXTERNAL_10+1,
	PAIRED = EXTERNAL_11 + 1,
	LOOP_EXTENSION_J = PAIRED+1,
	LOOP_EXTENSION_JMIN1 = LOOP_EXTENSION_J+1,
	LOOP_EXTENSION_JMIN2 = LOOP_EXTENSION_JMIN1+1,
	RIGHT_BULGE_EXTENSION_J = LOOP_EXTENSION_JMIN2+1,
	RIGHT_BULGE_EXTENSION_JMIN1 = RIGHT_BULGE_EXTENSION_J+1,
	LEFT_BULGE_EXTENSION_J = RIGHT_BULGE_EXTENSION_JMIN1+1,
	LEFT_BULGE_EXTENSION_JMIN1 = LEFT_BULGE_EXTENSION_J+1,
	MULTIBRANCH_EQ1_00 = LEFT_BULGE_EXTENSION_JMIN1+1,
	MULTIBRANCH_EQ1_01 = MULTIBRANCH_EQ1_00+1,
	MULTIBRANCH_EQ1_10 = MULTIBRANCH_EQ1_01+1,
	MULTIBRANCH_EQ1_0S = MULTIBRANCH_EQ1_10+1,
	MULTIBRANCH_EQ1_S0 = MULTIBRANCH_EQ1_0S+1,
	MULTIBRANCH_EQ2COAX_01 = MULTIBRANCH_EQ1_S0+1,
	MULTIBRANCH_EQ2COAX_11 = MULTIBRANCH_EQ2COAX_01+1,
	MULTIBRANCH_EQ2COAX_S1 = MULTIBRANCH_EQ2COAX_11+1,
	MULTIBRANCH_EQ2COAX_0S = MULTIBRANCH_EQ2COAX_S1+1,
	MULTIBRANCH_EQ2COAX_1S = MULTIBRANCH_EQ2COAX_0S+1,
	MULTIBRANCH_EQ2COAX_SS = MULTIBRANCH_EQ2COAX_1S+1,
	MULTIBRANCH_GE2_00 = MULTIBRANCH_EQ2COAX_SS+1,
	MULTIBRANCH_GE2_01 = MULTIBRANCH_GE2_00+1,
	MULTIBRANCH_GE2_10 = MULTIBRANCH_GE2_01+1,
	MULTIBRANCH_GE2_11 = MULTIBRANCH_GE2_10+1,
	EXTERNAL_EQ2COAX_01 = MULTIBRANCH_GE2_11+1,
	EXTERNAL_EQ2COAX_11 = EXTERNAL_EQ2COAX_01+1,
	EXTERNAL_EQ2COAX_S1 = EXTERNAL_EQ2COAX_11+1,
	EXTERNAL_EQ2COAX_0S = EXTERNAL_EQ2COAX_S1+1,
	EXTERNAL_EQ2COAX_1S = EXTERNAL_EQ2COAX_0S+1,
	EXTERNAL_EQ2COAX_SS = EXTERNAL_EQ2COAX_1S+1,
	EXTERNAL_EQ1_01 = EXTERNAL_EQ2COAX_SS+1,
	EXTERNAL_EQ1_0S = EXTERNAL_EQ1_01+1
	;

	/**
	 * Performs the recursion to compute the MFE energy over all foldings of the given strands which are
	 * connected foldings and which obey the folding constraints.
	 * 
	 * If onlyUpperTriangle is true, then only recursions on subsequences [i,j], where i <= j, are computed.
	 * This is essentially always on, with the exception of circular folding.
	 */
	private void N3Fold(int[][][] memo2, int[] seq, int N, int[] nicks, boolean onlyUpperTriangle) {
		//Initialization: all is impossible.
		deepFill3(memo2, Integer.MAX_VALUE);

		//Initialization: A single, external base has 0 free energy.
		int[][] Qe00 = memo2[EXTERNAL_00];
		int[][] Qe01 = memo2[EXTERNAL_01];
		int[][] Qe10 = memo2[EXTERNAL_10];
		int[][] Qe11 = memo2[EXTERNAL_11];
		for(int i = 0; i <= N-1; i++){
			Qe00[i][i] = 0;
		}
		
		int[][] Qb = memo2[PAIRED];
		int[][] QbNoStk = Qb; 
		int[][] QbStk = Qb; 

		int a1 = eParams.getMultibranchBase_deci();
		int a2 = eParams.getMultibranchBranch_deci();
		int a3 = eParams.getMultibranchUnpairedBase_deci();
		
		//Multibranch terms
		int[][] QmEq1_00 = memo2[MULTIBRANCH_EQ1_00];
		int[][] QmEq1_01 = memo2[MULTIBRANCH_EQ1_01];
		int[][] QmEq1_10 = memo2[MULTIBRANCH_EQ1_10];
		int[][] QmEq1_0S = memo2[MULTIBRANCH_EQ1_0S];
		int[][] QmEq1_S0 = memo2[MULTIBRANCH_EQ1_S0];
		
		int[][] QmEq2Coax_01 = memo2[MULTIBRANCH_EQ2COAX_01];
		int[][] QmEq2Coax_11 = memo2[MULTIBRANCH_EQ2COAX_11];
		int[][] QmEq2Coax_S1 = memo2[MULTIBRANCH_EQ2COAX_S1];
		int[][] QmEq2Coax_0S = memo2[MULTIBRANCH_EQ2COAX_0S];
		int[][] QmEq2Coax_1S = memo2[MULTIBRANCH_EQ2COAX_1S];
		int[][] QmEq2Coax_SS = memo2[MULTIBRANCH_EQ2COAX_SS];
		
		int[][] QmGe2_00 = memo2[MULTIBRANCH_GE2_00];
		int[][] QmGe2_01 = memo2[MULTIBRANCH_GE2_01];
		int[][] QmGe2_10 = memo2[MULTIBRANCH_GE2_10];
		int[][] QmGe2_11 = memo2[MULTIBRANCH_GE2_11];

		//External coaxial stacking
		int[][] QeEq2Coax_01 = memo2[EXTERNAL_EQ2COAX_01];
		int[][] QeEq2Coax_11 = memo2[EXTERNAL_EQ2COAX_11];
		int[][] QeEq2Coax_S1 = memo2[EXTERNAL_EQ2COAX_S1];
		int[][] QeEq2Coax_0S = memo2[EXTERNAL_EQ2COAX_0S];
		int[][] QeEq2Coax_1S = memo2[EXTERNAL_EQ2COAX_1S];
		int[][] QeEq2Coax_SS = memo2[EXTERNAL_EQ2COAX_SS];
	
		int[][] QeEq1_01 = memo2[EXTERNAL_EQ1_01];
		int[][] QeEq1_0S = memo2[EXTERNAL_EQ1_0S];

		//Build up to larger subproblems:
		for( int L = 2; L <= N; L++ ){
			//Swap the Qx buffers
			swap(memo2, LOOP_EXTENSION_JMIN2, LOOP_EXTENSION_JMIN1);
			swap(memo2, LOOP_EXTENSION_JMIN1, LOOP_EXTENSION_J);
			
			int[][] Qxj = memo2[LOOP_EXTENSION_J];
			int[][] Qxj1 = memo2[LOOP_EXTENSION_JMIN1];
			int[][] Qxj2 = memo2[LOOP_EXTENSION_JMIN2];
			for(int[] row : Qxj){
				Arrays.fill(row, Integer.MAX_VALUE);
			}
			
			for( int i = 0; i <= N-1; i++){
				int j = (i + L - 1) % N;
				if (onlyUpperTriangle && j < i){
					break;
				}

				//Structure of the recursion:
				//001) i and j are paired with eachother
					//001) They form an interior structure
					//001.5) They form an exterior structure
				//002) [i,j] is inside a multiloop (or exterior)
					//002) [i,j] contains exactly one pair
					//002.25) [i,j] contains 2 pairs exactly, coaxially stacked, and j is paired or j unpaired and j-1 paired
					//002.5) External loop general case (>= 0 pairs)
					//002.75) Multibranch general case (>=2 pairs)
								
				//001)
				//if i and j are paired to one another, and they close an interior region
				if (L >= 4 && !(containsNick(i, (i+1)%N, nicks) && containsNick((j-1+N)%N,j,nicks))){
					//if the region contains no pairs, i.e. hairpin
					if (!containsNick(i, j, nicks)){
						QbNoStk[i][j] = alt(QbNoStk[i][j],eParams.getHairpinLoopDeltaG_deci(seq, N, i, L));
					}
					
					//if the region contains 1 pair
					//Step 1: Find the optimal interior loop and store it in Qxj
					//If L1 > 4 and L2 > 4, there is no sequence dependence and hence we use memo 
					for(int s = 10; s <= L - 4; s++){
						//symmetry term is unmodified. However, the length of the interior loop is increased.
						Qxj[i][s] = combine(Qxj2[(i+1)%N][s-2], eParams.getInteriorLoopSizeTerm_deci(s) - eParams.getInteriorLoopSizeTerm_deci(s-2));
					}
					//If L2 >= 4 and L1 = 4, 
					for(int L2 = 4, L1 = 4; L1 + L2 <= L - 4; L2++){
						int d = (i + L1 + 1)%N;
						int e = (j - L2 - 1 + N)%N;
						int s = L1 + L2;
						if (containsNick(i, d, nicks) || containsNick(e, j, nicks)){
							break;
						}
						int closingPair = eParams.getInteriorNNTerminal_deci(seq[e], seq[d], seq[(e+1)%N], seq[(d-1+N)%N]);
						Qxj[i][s] = alt(Qxj[i][s], 
								combine(Qb[d][e], 
										combine(eParams.getInteriorLoopSizeTerm_deci(s) + eParams.getNINIOAssymetry(L1, L2),
												closingPair
												)));
					}
					//If L1 > 4 and L2 = 4, 
					for(int L1 = 5, L2 = 4; L1 + L2 <= L - 4; L1++){
						int d = (i + L1 + 1)%N;
						int e = (j - L2 - 1 + N)%N;
						int s = L1 + L2;
						if (containsNick(i, d, nicks) || containsNick(e, j, nicks)){
							break;
						}
						int closingPair = eParams.getInteriorNNTerminal_deci(seq[e], seq[d], seq[(e+1)%N], seq[(d-1+N)%N]);
						Qxj[i][s] = alt(Qxj[i][s], 
								combine(Qb[d][e], 
										combine(eParams.getInteriorLoopSizeTerm_deci(s) + eParams.getNINIOAssymetry(L1, L2),
												closingPair
												)));
					}
					//Step 2: Translate the value in Qxj as a possibility for Qb
					int internalLoopLeftClosing = eParams.getInteriorNNTerminal_deci(seq[i], seq[j], seq[(i+1)%N], seq[(j-1+N)%N]);
					for(int s = 2; s <= L - 4; s++){
						QbNoStk[i][j] = alt(QbNoStk[i][j], combine(internalLoopLeftClosing, Qxj[i][s]));
					}
					
					//If L1 < 4.
					for(int L1 = 0; L1 < 4; L1++){
						for(int L2 = 0; L1 + L2 <= L - 4; L2++){
							int d = (i+L1+1)%N;
							int e = (j-L2-1+N)%N;
							if (containsNick(i, d, nicks) || containsNick(e,j, nicks)){
								break; //Only inner loop!
							}
							
							int stack = eParams.getNN_deci(seq[i], seq[j], seq[d], seq[e]);
							if (L1 == 0 && L2 == 0){
								//Continue a stack
								QbStk[i][j] = alt(QbStk[i][j], combine(QbStk[d][e], stack));
								//Possibly terminate stack as well?
								QbStk[i][j] = alt(QbStk[i][j], combine(QbNoStk[d][e], stack));
							} 
							//In the standard model, bulge loops have no sequence dependence on the unpaired bases. 
							if (L1 == 0 && L2 > 0){
								int bulge = eParams.getBulgeLoop_deci(L2); 
								//For L2 == 1 we consider that the stack is not terminated
								if (L2 == 1){
									bulge = combine(bulge, stack);
									QbStk[i][j] = alt(QbStk[i][j], combine(QbStk[d][e], bulge));
									//Possibly terminate stack
									QbStk[i][j] = alt(QbStk[i][j], combine(QbNoStk[d][e], bulge));
								} else {
									int ATPenalty = eParams.getATPenalty_deci(seq[d], seq[e]) + eParams.getATPenalty_deci(seq[i], seq[j]);
									QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], bulge + ATPenalty));
								}
							}
							if (L2 == 0 && L1 > 0){
								//For L1 == 1 we consider that the stack is not terminated
								int bulge = eParams.getBulgeLoop_deci(L1);
								if (L1 == 1){
									bulge = combine(bulge, stack);
									QbStk[i][j] = alt(QbStk[i][j], combine(QbStk[d][e], bulge));
									//Possibly terminate stack
									QbStk[i][j] = alt(QbStk[i][j], combine(QbNoStk[d][e], bulge));
								} else {
									int ATPenalty = eParams.getATPenalty_deci(seq[d], seq[e]) + eParams.getATPenalty_deci(seq[i], seq[j]);
									QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], bulge + ATPenalty));
								}
							}
							if (L1 > 0 && L2 > 0){
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], eParams.getInteriorLoop_deci(seq, N, i, j, L1, L2)));
							}
						}
					} //end L1 < 4
					
					//If L1 >= 4 but L2 < 4
					for(int L2 = 0; L2 < 4; L2++){
						for(int L1 = 4; L1 + L2 <= L - 4; L1++){
							int d = (i+L1+1)%N;
							int e = (j-L2-1+N)%N;
							if (containsNick(i, d, nicks) || containsNick(e,j, nicks)){
								break; //only inner loop!
							}
							if (L2 == 0 && !containsNick(i, d, nicks) && !containsNick(e,j, nicks)){
								int bulge = eParams.getBulgeLoop_deci(L1);
								int ATPenalty = eParams.getATPenalty_deci(seq[d], seq[e]) + eParams.getATPenalty_deci(seq[i], seq[j]);
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], bulge + ATPenalty));
							}
							if (L2 > 0){
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], eParams.getInteriorLoop_deci(seq, N, i, j, L1, L2)));
							}
						}
					}
				
					//Check: the above cases have checked
					//(L1 > 4, L2 > 4), (L1 == 4, L2 >= 4), (L2 == 4, L1 > 4), (L1 < 4), (L2 < 4, L1 >= 4).
					
					//if the region contains >= 2 pairs (multibranch). 
					//Remark: multi-branch scoring is the most shaky (experimentally) part of the model.
					if (L >= 6){
						//the pair i, j coaxially stacks with an internal loop?
						//Search for 5'-of-i coaxial stacks
						for(int d = (i+1)%N, y = 0; y <= 1; d=(d+1)%N, y++){
							for(int e = (d+1)%N; e!=(y-2+N)%N; e=(e+1)%N){
								int internalDangle = eParams.getDangleTop_deci(seq[e],seq[d],seq[(e+1)%N]);
								int externalDangle = eParams.getDangleBottom_deci(seq[i], seq[j], seq[(j-1+N)%N]);
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(
										getQmGe1(memo2, seq, N, nicks, (e+1)%N,(j-1+N)%N, internalDangle, externalDangle),
										getCoaxialStackBonus(memo2, seq, N, nicks, i, j, d, e, y),
										Qb[d][e], a1+2*a2+a3*y
										));
							}
						}
						//Search for 3'-of-j coaxial stacks
						for(int e = (j-1+N)%N, y = 0; y <= 1; e=(e-1+N)%N, y++){
							for(int d = (i+3)%N; d!=e; d=(d+1)%N){
								int internalDangle = eParams.getDangleBottom_deci(seq[e], seq[d], seq[(d-1+N)%N]);
								int externalDangle = eParams.getDangleTop_deci(seq[i], seq[j], seq[(i+1)%N]);
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(
										getQmGe1(memo2, seq, N, nicks, (i+1)%N, (d-1+N)%N, externalDangle, internalDangle),
										getCoaxialStackBonus(memo2, seq, N, nicks, e, d, j, i, y),
										Qb[d][e], a1+2*a2+a3*y
										));
							}	
						}
						//no coaxial stacking of i,j.
						int ATPenalty = eParams.getATPenalty_deci(seq[i], seq[j]);
						int leftDangle = eParams.getDangleTop_deci(seq[i], seq[j], seq[(i+1)%N]);
						int rightDangle = eParams.getDangleBottom_deci(seq[i], seq[j], seq[(j-1+N)%N]);
						QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, a1 + a2, getQmGe2(memo2, (i+1)%N, (j-1+N)%N, leftDangle, rightDangle)));
					}
				} //end L >= 4
				
				//001.5)
				//if i and j are paired to one another, and their pair is exterior
				if (L == 2 && containsNick(i, j, nicks)){
					int ATPenalty = eParams.getATPenalty_deci(seq[i], seq[j]);
					QbNoStk[i][j] = ATPenalty;  //Blunt end
				}
				//Read this line carefully: We rule out only the case where BOTH (i,i+1) and (j-1, j) are nicked.
				if (L >= 3 && !(containsNick(i, (i+1)%N, nicks) && containsNick((j-1+N)%N,j,nicks))){
					for(int k = 0; k < nicks.length; k++){
						int nick = nicks[k];
						if (containsNick(i,j,nick)){
							int leftloopNoCoax = Integer.MAX_VALUE;
							int leftloopCoax = Integer.MAX_VALUE;
							if (nick==i){
								//empty left loop
							} else { 
								int topDangle = eParams.getDangleTop_deci(seq[i], seq[j], seq[(i+1)%N]);
								leftloopNoCoax = getQe(memo2, (i+1)%N, nick, topDangle, 0);
								//coaxial stack of [i,j] with a stack on the continuous backbone of the i side (the left)
								for(int e = (i+2)%N; e!=(nick+1)%N; e=(e+1)%N){ 
									for(int d = (i+1)%N, y = 0; d!=e && y <= 1; d=(d+1)%N, y++){
										int remainder = 0;
										if (e!=nick){
											int midDangle = eParams.getDangleTop_deci(seq[e], seq[d], seq[(e+1)%N]);
											remainder = getQe(memo2, (e+1)%N, nick, midDangle, 0);
										}
										int pairedScore = Qb[d][e];
										leftloopCoax = alt(leftloopCoax, combine(remainder, pairedScore, getCoaxialStackBonus(memo2, seq, N, nicks, i, j, d, e, y)));
									}
								}
							}
							int rightloopNoCoax = Integer.MAX_VALUE;
							int rightloopCoax = Integer.MAX_VALUE;
							if (nick==(j-1+N)%N){
								//empty right loop
							} else {
								int bottomDangle = eParams.getDangleBottom_deci(seq[i], seq[j], seq[(j-1+N)%N]);
								rightloopNoCoax = getQe(memo2, (nick+1)%N, (j-1+N)%N, 0, bottomDangle);
								//coaxial stack of [i,j] with a stack on the continuous backbone of the j side (the right)
								for(int d = (nick+1)%N; d!=j; d=(d+1)%N){ 
									for(int e = (j-1+N)%N, y = 0; e!=j && y <= 1; e=(e-1+N)%N, y++){
										int remainder = 0;
										if (d!=(nick+1)%N){
											int midDangle = eParams.getDangleBottom_deci(seq[e], seq[d], seq[(d-1+N)%N]);
											remainder = getQe(memo2, (nick+1)%N, (d-1+N)%N, 0, midDangle);
										}
										int pairedScore = Qb[d][e];
										rightloopCoax = alt(rightloopCoax, combine(remainder, pairedScore, getCoaxialStackBonus(memo2, seq, N, nicks, e, d, j, i, y)));
									}
								}
							}

							int ATPenalty = eParams.getATPenalty_deci(seq[i], seq[j]);
							if (nick==i){
								//empty left loop
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, alt(rightloopCoax, rightloopNoCoax)));
							} else
							if (nick==(j-1+N)%N){
								//empty right loop
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, alt(leftloopCoax, leftloopNoCoax)));
							} else {
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, combine(leftloopNoCoax, rightloopNoCoax)));
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, combine(leftloopCoax, rightloopNoCoax)));
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, combine(leftloopNoCoax, rightloopCoax)));
							}
						}
					}
				}
				
				//If i and j are paired to one another, and the pair is either a new stack, or a single pair
				//Qb[i][j] = alt(QbNoStk[i][j], QbStk[i][j]);

				//002) If [i,j] is in a multiloop or is external
				//If [i,j] contains exactly 1 pair
				if (L >= 4){
					//0S
					for(int e = (j-1+N)%N, d = (i+1)%N, unpairedOnLeft = 1; d!=e; d=(d+1)%N, unpairedOnLeft++){
						if (containsNick(i,d,nicks)){
							break;
						}
						int pairedScoreWithDangles = getQbFull(memo2, seq, N, nicks, d, e, 1, 1);
						QmEq1_0S[i][j] = alt(QmEq1_0S[i][j], combine(pairedScoreWithDangles, a2+unpairedOnLeft*a3));
						QeEq1_0S[i][j] = alt(QeEq1_0S[i][j], pairedScoreWithDangles);
					}
					//00, S0
					for(int e = (i+2)%N, unpairedOnRight = L - 3; e!=j; e=(e+1)%N, unpairedOnRight--){
						if (containsNick(e,j,nicks)){
							continue;
						}
						QmEq1_00[i][j] = alt(QmEq1_00[i][j],combine(QmEq1_0S[i][(e+1)%N],unpairedOnRight*a3));
						int d = (i+1)%N;
						int pairedScoreWithDangles = getQbFull(memo2, seq, N, nicks, d, e, 1, 1);
						QmEq1_S0[i][j] = alt(QmEq1_S0[i][j],combine(pairedScoreWithDangles, a2+unpairedOnRight*a3));
					}
				}
				if (L >= 3){
					//01
					for(int e = j, d=(i+1)%N, unpairedOnLeft = 0; d!=e; d=(d+1)%N, unpairedOnLeft++){
						if (containsNick(i,d,nicks)){
							break;
						}
						int pairedScoreWithLeftDangle = getQbFull(memo2, seq, N, nicks, d, e, 1, 0);
						QmEq1_01[i][j] = alt(QmEq1_01[i][j], combine(pairedScoreWithLeftDangle, a2 + unpairedOnLeft*a3));
						QeEq1_01[i][j] = alt(QeEq1_01[i][j], pairedScoreWithLeftDangle);
					}
					//10
					for(int d = i, e =(i+1)%N, unpairedOnRight = L - 2; e!=j; e=(e+1)%N, unpairedOnRight++){
						if (containsNick(e, j,nicks)){
							break;
						}
						int pairedScoreWithRightDangle = getQbFull(memo2, seq, N, nicks, d, e, 0, 1);
						QmEq1_10[i][j] = alt(QmEq1_10[i][j],combine(pairedScoreWithRightDangle, a2 + unpairedOnRight*a3));
					}
				}
				//002.25)
				//if [i,j] contains exactly 2, coaxially stacked, pairs and j is paired or j - 1 is paired and j is unpaired
				if (L >= 4){
					//11
					QmEq2Coax_11[i][j] = getQmEq2Coax(memo2, seq, N, nicks, i, j, 0, 0);
					QeEq2Coax_11[i][j] = getQeEq2Coax(memo2, seq, N, nicks, i, j, 0, 0);
				}
				if(L >= 5){
					//1S
					QmEq2Coax_1S[i][j] = getQmEq2Coax(memo2, seq, N, nicks, i, (j-1+N)%N, 0, 1);
					QeEq2Coax_1S[i][j] = getQeEq2Coax(memo2, seq, N, nicks, i, (j-1+N)%N, 0, 1);
					//S1
					QmEq2Coax_S1[i][j] = getQmEq2Coax(memo2, seq, N, nicks, (i+1)%N, j, 1, 0);
					QeEq2Coax_S1[i][j] = getQeEq2Coax(memo2, seq, N, nicks, (i+1)%N, j, 1, 0);
					//01
					for(int d = (i+1)%N, unpairedOnLeft = 1; d!=(j-1+N)%N; d=(d+1)%N, unpairedOnLeft++){
						QmEq2Coax_01[i][j] = alt(QmEq2Coax_01[i][j], combine(QmEq2Coax_S1[(d-1+N)%N][j], unpairedOnLeft*a3));
						QeEq2Coax_01[i][j] = alt(QeEq2Coax_01[i][j], QeEq2Coax_S1[(d-1+N)%N][j]);
					}
				}
				if (L >= 6){
					//SS
					QmEq2Coax_SS[i][j] = getQmEq2Coax(memo2, seq, N, nicks, (i+1)%N, (j-1+N)%N, 1, 1);
					QeEq2Coax_SS[i][j] = getQeEq2Coax(memo2, seq, N, nicks, (i+1)%N, (j-1+N)%N, 1, 1);
					//0S 
					for(int d = (i+1)%N, e = (j-1+N)%N, unpairedOnLeft = 1; d!=e; d=(d+1)%N, unpairedOnLeft++){
						QmEq2Coax_0S[i][j] = alt(QmEq2Coax_0S[i][j], combine(QmEq2Coax_SS[(d-1+N)%N][(e+1)%N], unpairedOnLeft*a3));
						QeEq2Coax_0S[i][j] = alt(QeEq2Coax_0S[i][j], QeEq2Coax_SS[(d-1+N)%N][(e+1)%N]);
					}
				}
				
				//002.5)
				//external, general case
				//If [i,j] contains 0 pairs
				if (!containsNick(i, j, nicks)){
					Qe00[i][j] = alt(Qe00[i][j], 0);
				}
				//The leftmost pair involves j, and it is not coaxially stacked
				Qe01[i][j] = alt(Qe01[i][j], QeEq1_01[i][j]);
				Qe11[i][j] = alt(Qe11[i][j], getQbFull(memo2, seq, N, nicks, i, j, 0, 0));
				//The leftmost pair does not involve j, and it is not coaxially stacked
				if (L >= 3){
					for(int e = (i+1)%N; e!=j; e=(e+1)%N){
						//e+1 is paired
						Qe00[i][j] = alt(Qe00[i][j], combine(QeEq1_01[i][e], Qe10[(e+1)%N][j]));
						Qe01[i][j] = alt(Qe01[i][j], combine(QeEq1_01[i][e], Qe11[(e+1)%N][j]));
						Qe11[i][j] = alt(Qe11[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 0), Qe11[(e+1)%N][j]));
						Qe10[i][j] = alt(Qe10[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 0), Qe10[(e+1)%N][j]));
						//e+1 is not paired
						Qe00[i][j] = alt(Qe00[i][j], combine(QeEq1_0S[i][(e+1)%N], Qe00[(e+1)%N][j]));
						Qe01[i][j] = alt(Qe01[i][j], combine(QeEq1_0S[i][(e+1)%N], Qe01[(e+1)%N][j]));
						Qe10[i][j] = alt(Qe10[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 1), Qe00[(e+1)%N][j]));
						Qe11[i][j] = alt(Qe11[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 1), Qe01[(e+1)%N][j]));
					}
				}
				//The leftmost 2 pairs are coaxially stacked, and j is involved (i.e. paired as part of the right helix)
				if (L >= 4){
					Qe01[i][j] = alt(Qe01[i][j], QeEq2Coax_01[i][j]);
					Qe11[i][j] = alt(Qe11[i][j], QeEq2Coax_11[i][j]);
				}
				//The leftmost 2 pairs are coaxially stacked, and j is not involved in this coaxial stacking
				if (L >= 5){
					for(int e = (i+3)%N; e!=j; e=(e+1)%N){
						//e+1 is paired
						Qe00[i][j] = alt(Qe00[i][j], combine(QeEq2Coax_01[i][e], Qe10[(e+1)%N][j]));
						Qe01[i][j] = alt(Qe01[i][j], combine(QeEq2Coax_01[i][e], Qe11[(e+1)%N][j]));
						Qe11[i][j] = alt(Qe11[i][j], combine(QeEq2Coax_11[i][e], Qe11[(e+1)%N][j]));
						Qe10[i][j] = alt(Qe10[i][j], combine(QeEq2Coax_11[i][e], Qe10[(e+1)%N][j]));
						//e+1 is not paired
						Qe00[i][j] = alt(Qe00[i][j], combine(QeEq2Coax_0S[i][(e+1)%N], Qe00[(e+1)%N][j]));
						Qe01[i][j] = alt(Qe01[i][j], combine(QeEq2Coax_0S[i][(e+1)%N], Qe01[(e+1)%N][j]));
						Qe10[i][j] = alt(Qe10[i][j], combine(QeEq2Coax_1S[i][(e+1)%N], Qe00[(e+1)%N][j]));
						Qe11[i][j] = alt(Qe11[i][j], combine(QeEq2Coax_1S[i][(e+1)%N], Qe01[(e+1)%N][j]));
					}
				}
				
				//002.75)
				//multibranch, >=2 pairs
				if (L >= 4){
					//the leftmost pair is not coaxially stacked with the second
					for(int e = (i+1)%N; e!=j; e=(e+1)%N){
						//e+1 is paired
						QmGe2_00[i][j] = alt(QmGe2_00[i][j], combine(QmEq1_01[i][e], getQmGe1_10(memo2, seq, N, nicks, (e+1)%N, j)));
						QmGe2_01[i][j] = alt(QmGe2_01[i][j], combine(QmEq1_01[i][e], getQmGe1_11(memo2, seq, N, nicks, (e+1)%N, j)));
						QmGe2_10[i][j] = alt(QmGe2_10[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 0), a2, getQmGe1_10(memo2, seq, N, nicks, (e+1)%N, j)));
						QmGe2_11[i][j] = alt(QmGe2_11[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 0), a2, getQmGe1_11(memo2, seq, N, nicks, (e+1)%N, j)));
						//e+1 is not paired
						QmGe2_00[i][j] = alt(QmGe2_00[i][j], combine(QmEq1_0S[i][(e+1)%N], getQmGe1_00(memo2, seq, N, nicks, (e+1)%N, j)));
						QmGe2_01[i][j] = alt(QmGe2_01[i][j], combine(QmEq1_0S[i][(e+1)%N], getQmGe1_01(memo2, seq, N, nicks, (e+1)%N, j)));
						QmGe2_10[i][j] = alt(QmGe2_10[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 1), a2, getQmGe1_00(memo2, seq, N, nicks, (e+1)%N, j)));
						QmGe2_11[i][j] = alt(QmGe2_11[i][j], combine(getQbFull(memo2, seq, N, nicks, i, e, 0, 1), a2, getQmGe1_01(memo2, seq, N, nicks, (e+1)%N, j)));
					}
					//the leftmost pair is coaxially stacked with the second and j is involved
					QmGe2_01[i][j] = alt(QmGe2_01[i][j], QmEq2Coax_01[i][j]);
					QmGe2_11[i][j] = alt(QmGe2_11[i][j], QmEq2Coax_11[i][j]); 
					//or j is not involved
					if (L >= 5){
						for(int e = (i+3)%N, basesOnRight = L - 4; e!=j; e=(e+1)%N, basesOnRight--){
							//e+1 is paired
							QmGe2_00[i][j] = alt(QmGe2_00[i][j], combine(QmEq2Coax_01[i][e], getQmGe1_10(memo2, seq, N, nicks, (e+1)%N, j)));
							QmGe2_01[i][j] = alt(QmGe2_01[i][j], combine(QmEq2Coax_01[i][e], getQmGe1_11(memo2, seq, N, nicks, (e+1)%N, j)));
							QmGe2_10[i][j] = alt(QmGe2_10[i][j], combine(QmEq2Coax_11[i][e], getQmGe1_10(memo2, seq, N, nicks, (e+1)%N, j)));
							QmGe2_11[i][j] = alt(QmGe2_11[i][j], combine(QmEq2Coax_11[i][e], getQmGe1_11(memo2, seq, N, nicks, (e+1)%N, j)));
							//e+1 is not paired
							QmGe2_00[i][j] = alt(QmGe2_00[i][j], combine(QmEq2Coax_0S[i][(e+1)%N], getQmGe0_00(memo2, seq, N, nicks, (e+1)%N, j, basesOnRight)));
							QmGe2_01[i][j] = alt(QmGe2_01[i][j], combine(QmEq2Coax_0S[i][(e+1)%N], getQmGe1_01(memo2, seq, N, nicks, (e+1)%N, j)));
							QmGe2_10[i][j] = alt(QmGe2_10[i][j], combine(QmEq2Coax_1S[i][(e+1)%N], getQmGe0_00(memo2, seq, N, nicks, (e+1)%N, j, basesOnRight)));
							QmGe2_11[i][j] = alt(QmGe2_11[i][j], combine(QmEq2Coax_1S[i][(e+1)%N], getQmGe1_01(memo2, seq, N, nicks, (e+1)%N, j)));
						}
					}
				} //end multibranch, general case (>=2 pairs)
							
			}//End scoring for [i,j].
		}
	}
	
	/**
	 * Performs the recursion to compute the MFE energy over all foldings of the given strands which are
	 * connected foldings and which do not contain any of the pairs in the constraint matrix.
	 * 
	 * If onlyUpperTriangle is true, then only recursions on subsequences [i,j], where i <= j, are computed.
	 * This is essentially always on, with the exception of circular folding.
	 */
	private void N2Fold(int[][][] memo2, int[] seq, int N, int[] nicks, boolean onlyUpperTriangle, FoldingConstraints constraints) {
		//Initialization: all is impossible.
		deepFill3(memo2, Integer.MAX_VALUE);

		//Initialization: A single, external base has 0 free energy.
		int[][] Qe00 = memo2[EXTERNAL_00];
		int[][] Qe01 = memo2[EXTERNAL_01];
		int[][] Qe10 = memo2[EXTERNAL_10];
		int[][] Qe11 = memo2[EXTERNAL_11];
		for(int i = 0; i <= N-1; i++){
			Qe00[i][i] = 0;
		}
		
		int[][] Qb = memo2[PAIRED];
		int[][] QbNoStk = Qb; 
		int[][] QbStk = Qb; 

		//Build up to larger subproblems:
		for( int L = 2; L <= N; L++ ){
			//Swap the Qx buffers
			swap(memo2, LOOP_EXTENSION_JMIN2, LOOP_EXTENSION_JMIN1);
			swap(memo2, LOOP_EXTENSION_JMIN1, LOOP_EXTENSION_J);

			int maxLoopLen = 30;
			int[][] Qxj = memo2[LOOP_EXTENSION_J];
			int[][] Qxj1 = memo2[LOOP_EXTENSION_JMIN1];
			int[][] Qxj2 = memo2[LOOP_EXTENSION_JMIN2];
			
			for(int[] row : Qxj){
				for(int i = 0; i <= maxLoopLen && i < row.length; i++) row[i] = Integer.MAX_VALUE;
			}

			//Swap the QxBulge buffers
			swap(memo2, RIGHT_BULGE_EXTENSION_J, RIGHT_BULGE_EXTENSION_JMIN1);
			swap(memo2, LEFT_BULGE_EXTENSION_J, LEFT_BULGE_EXTENSION_JMIN1);
			int[][] QxjBulgeR = memo2[RIGHT_BULGE_EXTENSION_J];
			int[][] QxjBulgeR1 = memo2[RIGHT_BULGE_EXTENSION_JMIN1];
			int[][] QxjBulgeL = memo2[LEFT_BULGE_EXTENSION_J];
			int[][] QxjBulgeL1 = memo2[LEFT_BULGE_EXTENSION_JMIN1];
			for(int[] row : QxjBulgeR){
				for(int i = 0; i <= maxLoopLen && i < row.length; i++) row[i] = Integer.MAX_VALUE;
			}
			for(int[] row : QxjBulgeL){
				for(int i = 0; i <= maxLoopLen && i < row.length; i++) row[i] = Integer.MAX_VALUE;
			}
			
			for( int i = 0; i <= N-1; i++){
				int j = (i + L - 1) % N;
				if (onlyUpperTriangle && j < i){
					break;
				}
				
				//Structure of the recursion:
				//001) i and j are paired with eachother
					//001) They form an interior structure
					//001.5) They form an exterior structure
				//002) [i,j] is inside a multiloop (or exterior)
					//002) [i,j] contains exactly one pair
					//002.25) [i,j] contains 2 pairs exactly, coaxially stacked, and j is paired or j unpaired and j-1 paired
					//002.5) External loop general case (>= 0 pairs)
					//002.75) Multibranch general case (>=2 pairs)
								
				//001)
				//s > maxLoopLen
				if (maxLoopLen <= L - 4){
					Qxj[i][maxLoopLen] = alt(alt(Qxj[i][maxLoopLen], Qxj1[i][maxLoopLen]), Qxj1[(i+1)%N][maxLoopLen]);
				}
				if (maxLoopLen <= L - 3){
					QxjBulgeL[i][maxLoopLen] = alt(QxjBulgeL[i][maxLoopLen], QxjBulgeL1[(i+1)%N][maxLoopLen]);
					QxjBulgeR[i][maxLoopLen] = alt(QxjBulgeR[i][maxLoopLen], QxjBulgeR1[i][maxLoopLen]);
				}
				
				//if i and j are paired to one another, and they close an interior region
				if (L >= 4 && !(containsNick(i, (i+1)%N, nicks) && containsNick((j-1+N)%N,j,nicks))){
					//if the region contains no pairs, i.e. hairpin
					if (!containsNick(i, j, nicks)){
						QbNoStk[i][j] = alt(QbNoStk[i][j],eParams.getHairpinLoopDeltaG_deci(seq, N, i, L));
					}
					
					//if the region contains 1 pair
					//Step 1: Find the optimal interior loop and store it in Qxj
					//If L1 > 4 and L2 > 4, there is no sequence dependence and hence we use memo 
					for(int s = 10; s <= L - 4 && s <= maxLoopLen; s++){
						//symmetry term is unmodified. However, the length of the interior loop is increased.
						Qxj[i][s] = combine(Qxj2[(i+1)%N][s-2], eParams.getInteriorLoopSizeTerm_deci(s) - eParams.getInteriorLoopSizeTerm_deci(s-2));
					}
					//If L2 >= 4 and L1 = 4, 
					for(int L2 = 4, L1 = 4; L1 + L2 <= L - 4 && L1 + L2 <= maxLoopLen; L2++){
						int d = (i + L1 + 1)%N;
						int e = (j - L2 - 1 + N)%N;
						int s = L1 + L2;
						if (containsNick(i, d, nicks) || containsNick(e, j, nicks)){
							break;
						}
						int closingPair = eParams.getInteriorNNTerminal_deci(seq[e], seq[d], seq[(e+1)%N], seq[(d-1+N)%N]);
						Qxj[i][s] = alt(Qxj[i][s], 
								combine(Qb[d][e], 
										combine(eParams.getInteriorLoopSizeTerm_deci(s) + eParams.getNINIOAssymetry(L1, L2),
												closingPair
												)));
					}
					//If L1 > 4 and L2 = 4, 
					for(int L1 = 5, L2 = 4; L1 + L2 <= L - 4 && L1 + L2 <= maxLoopLen; L1++){
						int d = (i + L1 + 1)%N;
						int e = (j - L2 - 1 + N)%N;
						int s = L1 + L2;
						if (containsNick(i, d, nicks) || containsNick(e, j, nicks)){
							break;
						}
						int closingPair = eParams.getInteriorNNTerminal_deci(seq[e], seq[d], seq[(e+1)%N], seq[(d-1+N)%N]);
						Qxj[i][s] = alt(Qxj[i][s], 
								combine(Qb[d][e], 
										combine(eParams.getInteriorLoopSizeTerm_deci(s) + eParams.getNINIOAssymetry(L1, L2),
												closingPair
												)));
					}
					
					//Step 2: Translate the value in Qxj as a possibility for Qb
					int internalLoopLeftClosing = eParams.getInteriorNNTerminal_deci(seq[i], seq[j], seq[(i+1)%N], seq[(j-1+N)%N]);
					for(int s = 2; s <= L - 4 && s <= maxLoopLen; s++){
						QbNoStk[i][j] = alt(QbNoStk[i][j], combine(internalLoopLeftClosing, Qxj[i][s]));
					}
					
					//If L1 < 4.
					for(int L1 = 0; L1 < 4; L1++){
						for(int L2 = 0; L1 + L2 <= L - 4 && L1 + L2 <= maxLoopLen; L2++){
							int d = (i+L1+1)%N;
							int e = (j-L2-1+N)%N;
							int s = L1 + L2;
							if (containsNick(i, d, nicks) || containsNick(e,j, nicks)){
								break; //Only inner loop!
							}
							
							int stack = eParams.getNN_deci(seq[i], seq[j], seq[d], seq[e]);
							if (L1 == 0 && L2 == 0){
								//Continue a stack
								QbStk[i][j] = alt(QbStk[i][j], combine(QbStk[d][e], stack));
								//Possibly terminate stack as well?
								QbStk[i][j] = alt(QbStk[i][j], combine(QbNoStk[d][e], stack));
							} 
							//In the standard model, bulge loops have no sequence dependence on the unpaired bases. 
							if (L1 == 0 && L2 > 0){
								int bulge = eParams.getBulgeLoop_deci(L2); 
								//For L2 == 1 we consider that the stack is not terminated
								if (L2 == 1){
									bulge = combine(bulge, stack);
									QbStk[i][j] = alt(QbStk[i][j], combine(QbStk[d][e], bulge));
									//Possibly terminate stack
									QbStk[i][j] = alt(QbStk[i][j], combine(QbNoStk[d][e], bulge));
								} else {
									int ATPenalty = eParams.getATPenalty_deci(seq[d], seq[e]) + eParams.getATPenalty_deci(seq[i], seq[j]);
									int leftBulge = combine(Qb[d][e], bulge + ATPenalty);
									QxjBulgeL[i][s] = alt(QxjBulgeL[i][s], leftBulge);
								}
							}
							if (L2 == 0 && L1 > 0){
								//For L1 == 1 we consider that the stack is not terminated
								int bulge = eParams.getBulgeLoop_deci(L1);
								if (L1 == 1){
									bulge = combine(bulge, stack);
									QbStk[i][j] = alt(QbStk[i][j], combine(QbStk[d][e], bulge));
									//Possibly terminate stack
									QbStk[i][j] = alt(QbStk[i][j], combine(QbNoStk[d][e], bulge));
								} else {
									int ATPenalty = eParams.getATPenalty_deci(seq[d], seq[e]) + eParams.getATPenalty_deci(seq[i], seq[j]);
									int rightBulge = combine(Qb[d][e], bulge + ATPenalty);
									QxjBulgeR[i][s] = alt(QxjBulgeR[i][s], rightBulge);
								}
							}
							//Long bulge as a possibility:
							QbNoStk[i][j] = alt(QbNoStk[i][j], QxjBulgeL[i][s]);
							QbNoStk[i][j] = alt(QbNoStk[i][j], QxjBulgeR[i][s]);
							
							if (L1 > 0 && L2 > 0){
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], eParams.getInteriorLoop_deci(seq, N, i, j, L1, L2)));
							}
						}
					} //end L1 < 4
					
					//If L1 >= 4 but L2 < 4
					for(int L2 = 0; L2 < 4; L2++){
						for(int L1 = 4; L1 + L2 <= L - 4; L1++){
							int d = (i+L1+1)%N;
							int e = (j-L2-1+N)%N;
							if (containsNick(i, d, nicks) || containsNick(e,j, nicks)){
								break; //only inner loop!
							}
							if (L2 == 0 && !containsNick(i, d, nicks) && !containsNick(e,j, nicks)){
								int bulge = eParams.getBulgeLoop_deci(L1);
								int ATPenalty = eParams.getATPenalty_deci(seq[d], seq[e]) + eParams.getATPenalty_deci(seq[i], seq[j]);
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], bulge + ATPenalty));
							}
							if (L2 > 0){
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(Qb[d][e], eParams.getInteriorLoop_deci(seq, N, i, j, L1, L2)));
							}
						}
					}
				
					//Check: the above cases have checked
					//(L1 > 4, L2 > 4), (L1 == 4, L2 >= 4), (L2 == 4, L1 > 4), (L1 < 4), (L2 < 4, L1 >= 4).
					
				} //end L >= 4
				
				//001.5)
				//if i and j are paired to one another, and their pair is exterior
				if (L == 2 && containsNick(i, j, nicks)){
					int ATPenalty = eParams.getATPenalty_deci(seq[i], seq[j]);
					QbNoStk[i][j] = ATPenalty;  //Blunt end
				}
				//Read this line carefully: We rule out only the case where BOTH (i,i+1) and (j-1, j) are nicked.
				if (L >= 3 && !(containsNick(i, (i+1)%N, nicks) && containsNick((j-1+N)%N,j,nicks))){
					for(int k = 0; k < nicks.length; k++){
						int nick = nicks[k];
						if (containsNick(i,j,nick)){
							int leftloopNoCoax = Integer.MAX_VALUE;
							if (nick==i){
								//empty left loop
							} else { 
								int topDangle = eParams.getDangleTop_deci(seq[i], seq[j], seq[(i+1)%N]);
								leftloopNoCoax = getQe(memo2, (i+1)%N, nick, topDangle, 0);
							}
							int rightloopNoCoax = Integer.MAX_VALUE;
							if (nick==(j-1+N)%N){
								//empty right loop
							} else {
								int bottomDangle = eParams.getDangleBottom_deci(seq[i], seq[j], seq[(j-1+N)%N]);
							}

							int ATPenalty = eParams.getATPenalty_deci(seq[i], seq[j]);
							if (nick==i){
								//empty left loop
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, rightloopNoCoax));
							} else
							if (nick==(j-1+N)%N){
								//empty right loop
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, leftloopNoCoax));
							} else {
								QbNoStk[i][j] = alt(QbNoStk[i][j], combine(ATPenalty, combine(leftloopNoCoax, rightloopNoCoax)));
							}
						}
					}
				}
				
				//If i and j are paired to one another, and the pair is either a new stack, or a single pair
				//Qb[i][j] = alt(QbNoStk[i][j], QbStk[i][j]);
				
				//Overrides: Can i and j pair?
				if (constraints.preventPairing[i][j]){
					Qb[i][j] = Integer.MAX_VALUE;
				}
				
				//External:
				Qe00[i][j] = alt(alt(Qe00[(i+1)%N][j], Qe00[i][(j-1+N)%N]), getQbFull(memo2, seq, N, nicks, (i+1)%N, (j-1+N)%N, 1, 1));
				Qe01[i][j] = alt(Qe01[(i+1)%N][j], getQbFull(memo2, seq, N, nicks, (i+1)%N, j, 1, 0));
				Qe10[i][j] = alt(Qe10[i][(j-1+N)%N], getQbFull(memo2, seq, N, nicks, i, (j-1+N)%N, 0, 1));
				Qe11[i][j] = getQbFull(memo2, seq, N, nicks, i, (j-1+N)%N, 1, 1);
							
			}//End scoring for [i,j].
		}
	}


	private int getQmGe1(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j, int bonusIfIUnpaired, int bonusIfJUnpaired) {
		int best = combine(getQmGe1_00(memo2, seq, N, nicks, i, j), bonusIfIUnpaired, bonusIfJUnpaired);
		best = alt(best, combine(getQmGe1_01(memo2, seq, N, nicks, i, j), bonusIfIUnpaired));
		best = alt(best, combine(getQmGe1_10(memo2, seq, N, nicks, i, j), bonusIfJUnpaired));
		best = alt(best, getQmGe1_11(memo2, seq, N, nicks, i, j));
		return best;
	}

	private int getQmGe0_00(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j, int L) {
		int a3 = eParams.getMultibranchUnpairedBase_deci();
		return alt(alt(memo2[MULTIBRANCH_EQ1_00][i][j], memo2[MULTIBRANCH_GE2_00][i][j]), a3*L);
	}
	private int getQmGe1_00(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j) {
		return alt(memo2[MULTIBRANCH_EQ1_00][i][j], memo2[MULTIBRANCH_GE2_00][i][j]);
	}
	private int getQmGe1_10(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j) {
		return alt(memo2[MULTIBRANCH_EQ1_10][i][j], memo2[MULTIBRANCH_GE2_10][i][j]);
	}
	private int getQmGe1_01(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j) {
		return alt(memo2[MULTIBRANCH_EQ1_01][i][j], memo2[MULTIBRANCH_GE2_01][i][j]);
	}
	private int getQmGe1_11(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j) {
		int a2 = eParams.getMultibranchBranch_deci();
		return alt(combine(getQbFull(memo2, seq, N, nicks, i, j, 0, 0), a2), memo2[MULTIBRANCH_GE2_11][i][j]);
	}


	private int getQmEq2Coax(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j, int dangleOnLeft, int dangleOnRight) {
		int[][] Qb = memo2[PAIRED];

		int a2 = eParams.getMultibranchBranch_deci();
		int a3 = eParams.getMultibranchUnpairedBase_deci();
		
		int d = i;
		int g = j;
		if (dangleOnLeft==1){
			if (containsNick((i-1+N)%N, i, nicks)){
				return Integer.MAX_VALUE;
			}
		}
		if (dangleOnRight==1){
			if (containsNick(j, (j+1)%N, nicks)){
				return Integer.MAX_VALUE;
			}
		}
		//d is paired with e, f is paired with g, and the two helixes coaxially stack. Apply dangles.
		int best = Integer.MAX_VALUE;
		for(int e = (i+1)%N; e!=(g-1+N)%N; e=(e+1)%N){
			for(int f = (e+1)%N, y=0; y<=1; f=(f+1)%N, y++){
				int dangles = 0;
				if (dangleOnLeft==1){
					dangles = combine(dangles, eParams.getDangleBottom_deci(seq[e], seq[i], seq[(i-1+N)%N]));
				}
				if (dangleOnRight==1){
					dangles = combine(dangles, eParams.getDangleTop_deci(seq[j], seq[f], seq[(j+1)%N]));
				}
				int coax = getCoaxialStackBonus(memo2, seq, N, nicks, e, d, f, g, y);
				best = alt(best, combine(dangles, coax, Qb[d][e], Qb[f][g], y*a3+2*a2));
			}
		}
		return best;
	}
	private int getQeEq2Coax(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j, int dangleOnLeft, int dangleOnRight) {
		int[][] Qb = memo2[PAIRED];
		
		int d = i;
		int g = j;
		if (dangleOnLeft==1){
			if (containsNick((i-1+N)%N, i, nicks)){
				return Integer.MAX_VALUE;
			}
		}
		if (dangleOnRight==1){
			if (containsNick(j, (j+1)%N, nicks)){
				return Integer.MAX_VALUE;
			}
		}
		//d is paired with e, f is paired with g, and the two helixes coaxially stack. Apply dangles.
		int best = Integer.MAX_VALUE;
		for(int e = (i+1)%N; e!=(g-1+N)%N; e=(e+1)%N){
			for(int f = (e+1)%N, y=0; y<=1; f=(f+1)%N, y++){
				int dangles = 0;
				if (dangleOnLeft==1){
					dangles = combine(dangles, eParams.getDangleBottom_deci(seq[e], seq[i], seq[(i-1+N)%N]));
				}
				if (dangleOnRight==1){
					dangles = combine(dangles, eParams.getDangleTop_deci(seq[j], seq[f], seq[(j+1)%N]));
				}
				int coax = getCoaxialStackBonus(memo2, seq, N, nicks, e, d, f, g, y);
				best = alt(best, combine(dangles, coax, Qb[d][e], Qb[f][g]));
			}
		}
		return best;
	}



	private int getQbFull(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j, int dangleOnLeft, int dangleOnRight) {
		int score = memo2[PAIRED][i][j];
		if (dangleOnLeft==1){
			if (containsNick((i-1+N)%N, i, nicks)){
				return Integer.MAX_VALUE;
			}
			score = combine(score, eParams.getDangleBottom_deci(seq[j], seq[i], seq[(i-1+N)%N]));
		}
		if (dangleOnRight==1){
			if (containsNick(j, (j+1)%N, nicks)){
				return Integer.MAX_VALUE;
			}
			score = combine(score, eParams.getDangleTop_deci(seq[j], seq[i], seq[(j+1)%N]));
		}
		score = combine(score, eParams.getATPenalty_deci(seq[i], seq[j]));
		return score;
	}



	/**
	 * Returns the MFE structure of an exterior loop [i,j], applying appropriate bonuses applied bonusIfIUnpaired (if i is unpaired)
	 * and bonusIfJUnpaired (if j is unpaired)
	 */
	private int getQe(int[][][] memo2, int i, int j, int bonusIfIUnpaired, int bonusIfJUnpaired) {
		int best = combine(memo2[EXTERNAL_00][i][j], bonusIfIUnpaired, bonusIfJUnpaired);
		best = alt(best, combine(memo2[EXTERNAL_01][i][j], bonusIfIUnpaired));
		best = alt(best, combine(memo2[EXTERNAL_10][i][j], bonusIfJUnpaired));
		best = alt(best, memo2[EXTERNAL_11][i][j]);
		return best;
	}
	/**
	 * Returns the MFE structure of an multibranch interior with at least two internal pairs spanning [i,j], applying appropriate bonuses applied bonusIfIUnpaired (if i is unpaired)
	 * and bonusIfJUnpaired (if j is unpaired)
	 */
	private int getQmGe2(int[][][] memo2, int i, int j, int bonusIfIUnpaired, int bonusIfJUnpaired) {
		int best = combine(memo2[MULTIBRANCH_GE2_00][i][j], bonusIfIUnpaired, bonusIfJUnpaired);
		best = alt(best, combine(memo2[MULTIBRANCH_GE2_01][i][j], bonusIfIUnpaired));
		best = alt(best, combine(memo2[MULTIBRANCH_GE2_10][i][j], bonusIfJUnpaired));
		best = alt(best, memo2[MULTIBRANCH_GE2_11][i][j]);
		return best;
	}
	


	/**
	 * [d,e] is coaxial to [i,j], with y bases on the continuous backbone of the coaxial stack, and the left backbone of [i,j] is continuous to d.
	 * In other words, d = (i+y)%N. 
	 */
	private int getCoaxialStackBonus(int[][][] memo2, int[] seq, int N, int[] nicks, int i, int j, int d, int e, int y) {
		if (containsNick(i, d, nicks)){
			return Integer.MAX_VALUE;
		}
		int dangles = 0;
		if (y > 0){
			dangles = combine(eParams.getDangleTop_deci(seq[i], seq[j], seq[(i+1)%N]), 
					eParams.getDangleBottom_deci(seq[e], seq[d], seq[(d-1+N)%N]));
		} else {
			dangles = combine(dangles, 
					eParams.getDangleBottom_deci(seq[i], seq[j], seq[e]), 
					eParams.getDangleTop_deci(seq[e], seq[d], seq[j]));
		}
		return dangles;
	}


	private boolean containsNick(int i, int j, int[] nicks) {
		for(int k = 0; k < nicks.length; k++){
			if (containsNick(i,j,nicks[k])){
				return true;
			}
		}
		return false;
	}
	private boolean containsNick(int i, int j, int nick){
		if (i <= j){
			if (nick >= i && nick < j){
				return true;
			}
		} else {
			if (nick >= i || nick < j){
				return true;
			}
		}
		return false;
	}

	private static void deepFill3(int[][][] memo, int value) {
		for(int[][] c : memo){
			for(int[] d : c){
				Arrays.fill(d, value);
			}
		}	
	}
	private static void swap(Object[] ar, int i, int j) {
		Object tmp = ar[i];
		ar[i] = ar[j];
		ar[j] = tmp;
	}

	/**
	 * Combine two choices, which must be taken together
	 */
	private static int combine(int i, int j) {
		if (i == Integer.MAX_VALUE || j == Integer.MAX_VALUE){
			return Integer.MAX_VALUE;
		}
		return i + j;
	}
	/**
	 * Combine three choices, which must be taken together
	 */
	private static int combine(int i, int j, int k) {
		if (i == Integer.MAX_VALUE || j == Integer.MAX_VALUE || k == Integer.MAX_VALUE){
			return Integer.MAX_VALUE;
		}
		return i + j + k;
	}
	/**
	 * Combine four choices, which must be taken together
	 */
	private static int combine(int i, int j, int k, int m) {
		if (i == Integer.MAX_VALUE || j == Integer.MAX_VALUE || k == Integer.MAX_VALUE || m == Integer.MAX_VALUE){
			return Integer.MAX_VALUE;
		}
		return i + j + k + m;
	}
	/**
	 * Combine five choices, which must be taken together
	 */
	private static int combine(int i, int j, int k, int m, int n) {
		if (i == Integer.MAX_VALUE || j == Integer.MAX_VALUE || k == Integer.MAX_VALUE || m == Integer.MAX_VALUE || n == Integer.MAX_VALUE){
			return Integer.MAX_VALUE;
		}
		return i + j + k + m + n;
	}

	/**
	 * Choose between two alternatives
	 */
	private static int alt(int i, int j) {
		if (i < -1e7 || j < -1e7){
			throw new RuntimeException("UNDERFLOW");
		}
		if (i < j){
			return i;
		}
		return j;
	}
	private static int altALERT(String message, int i, int j) {
		if (i < j){
			System.out.println(message+" : "+i);
			return i;
		}
		return j;
	}

	public double mfeNoDiag(DomainSequence domainSequence,
			DomainSequence domainSequence2, int[][] domain,
			int[][] domain_markings) {
		return 0;
	}

	public double mfeStraight(DomainSequence domainSequence,
			DomainSequence domainSequence2, int[][] domain,
			int[][] domain_markings, int markLeft, int markRight, int joffset) {
		return 0;
	}
}