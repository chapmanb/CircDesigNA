## CircDesigNA
   
Provide build instructions and small Clojure wrapper around CircDesigNA
([website][1], code[0]). Available from the [Clojars maven repository][3].

### Build

Using [Leiningen][2]:

    lein jar
    
### Usage

    > (use '[circdesigna.core])
    > (min-free-energy "GATCGATC")

[0]: https://github.com/taifunbrowser/CircDesigNA
[1]: http://cssb.utexas.edu/circdesigna/
[2]: http://leiningen.org/
[3]: https://clojars.org/org.clojars.chapmanb/circdesigna
