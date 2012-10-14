(ns circdesigna.core
  "Simple Clojure wrapper around high CircDesigNA functionality"
  (:import [circdesigna DomainSequence]
           [circdesigna.config CircDesigNAConfig]
           [circdesigna.energy ConstraintsNAFoldingImpl]))

(defn- to-int-matrix
  [& xs]
  (into-array (vec (map int-array xs))))

(defn min-free-energy
  "deltaG of minimum free energy for secondary structures formed by a sequence.
  Problematic secondary structure will have larger negative delta Gs, indicating
  more stable dimers and loops."
  [seq]
  (let [config (CircDesigNAConfig.)
        domain-seq (doto (DomainSequence.)
                     (.setDomains 0 nil))
        domain (map #(.decodeConstraintChar (.monomer config) %) seq)
        domain-mark (repeat (count seq) 0)]
    (-> (doto (ConstraintsNAFoldingImpl. config)
          (.setScoringModel 1))
        (.mfe domain-seq (to-int-matrix domain) (to-int-matrix domain-mark)))))