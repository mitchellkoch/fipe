# Fipe

Fipe is a simple Clojure library for representing and running file pipelines. It is similar to tools like [GNU Make](http://www.gnu.org/software/make/) and [Factual's Drake](https://github.com/Factual/drake), but allows defining targets with simple Clojure macros, allowing flexibility similar to that of [Rake](https://github.com/jimweirich/rake), but for Clojure, not Ruby.

Note that dependency resolution is not yet implemented, but this library can still be very useful by using `fipe-glob`, sometimes combined with `doseq`, to produce many targets at once.

**This is an alpha release. The API is subject to change (especially for globbing and dependencies). Comments and contributions are much appreciated.**

## Installation

Fipe release are [published on Clojars](https://clojars.org/mitchellkoch/fipe).

To use this version with Leiningen, add the following dependency to your project
definition:

```clojure
[mitchellkoch/fipe "0.1.0"]
```

## Usage

Use the `deftarget` macro to define how a target file should be produced. All targets are relative to `fipe.core/data-dir`, which by default is `data/`. The function `fipe-rel` gives files relative to this path.

```clojure
(ns example
  (:use fipe.core :only [deftarget dep fipe]))

(deftarget "hello.txt"
  "hello")

(defn x4 [f]
  (let [s (str/trim-newline (slurp f))]
    (vec (repeat 4 s))))

(deftarget "hellos.edn"
  (x4 (dep "hello.txt")))
```

Here, running `(fipe "hello.txt")` will produce `hello.txt` (containing `hello`) or `(fipe-glob "hello*")` will produce both `hello.txt` and `hellos.edn`, (where `hellos.edn` contains `["hello" "hello" "hello" "hello"]`).

Fipe uses the file's extension to determine the output format for writing the files (see `fipe.util/write-to-file`). It currently supports `.txt`, a naive `.tsv` and `.tsv.gz`, `csv` using `clojure.data.csv`, `json` using `cheshire`, `edn` with pretty printing, and `.ser.gz` for serialized Java objects.

Additionally, `deftarget!` can be used to just run for side effects, in which case Fipe does not write the value to the target file itself.

## License

Copyright © 2014 Mitchell Koch. Distributed under the Eclipse Public License, the same as Clojure.
