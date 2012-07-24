package circdesigna.energy;

import circdesigna.GSFR;

public interface ConstraintsNAFolding extends NAFolding{
	public double mfe(GSFR seq1, GSFR seq2, int[][] domain, int[][] domain_markings, boolean onlyIllegalPairing);
	public double mfe(GSFR seq, int[][] domain, int[][] domain_markings, boolean onlyIllegalPairing);
}
