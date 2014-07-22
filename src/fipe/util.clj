(ns fipe.util
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.reader.edn :as edn]
            [clojure.data.csv :as csv]
            [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [clojure.pprint :as pprint]
            [wharf.core :as wharf])
  (:import [java.io FileOutputStream ObjectOutputStream ObjectInputStream FileInputStream BufferedInputStream InputStream Writer]
           [java.util.zip GZIPInputStream GZIPOutputStream]))

(defn glob-match?
  "Does glob-str match s?"
  [glob-str s]
  (let [regex (#'fs/glob->regex glob-str)]
    (re-find regex s)))

(defn split-ext
  "Gives [prefix ext] with no . in between. Selects the longest possible extension, combining multiple ones into one."
  [file]
  (let [filename (str file)
        match (re-find #"(.*?)\.(.*)" filename)]
    (when match
      (subvec match 1))))

(defn write-to-file [file data & {:keys [as]}]
  (let [filename (str file)
        [prefix ext] (split-ext file)
        ^String ext (or as ext)]
    (cond 
     (.endsWith ext "txt")
     (with-open [wrtr (io/writer filename)]
       (doseq [row (if (coll? data) data [data])]
         (.write wrtr (str row "\n"))))

     (.endsWith ext "tsv")
     (with-open [wrtr (io/writer filename)]
       (doseq [row data]
         (.write wrtr (str (str/join "\t" row) "\n"))))

     (.endsWith ext "csv")
     (with-open [wrtr (io/writer filename)]
       (csv/write-csv wrtr data))
     
     (.endsWith ext "json")
     (with-open [wrtr (io/writer filename)]
       (.write wrtr (json/generate-string 
                     (wharf/transform-keys (comp wharf/dash->underscore name) data)
                     {:pretty true})))

     (.endsWith ext "tsv.gz")
     (with-open [wrtr (-> filename
                          io/output-stream
                          GZIPOutputStream.
                          io/writer)]
       (doseq [row data]
         (.write wrtr (str (str/join "\t" row) "\n"))))

     (.endsWith ext "edn")
     (binding [*print-length* nil]
       (with-open [^Writer wrtr (io/writer filename)]
         (doseq [entry (if (map? data) [data] data)]
           (pprint/pprint entry wrtr))))

     (.endsWith ext "ser.gz")
     (with-open [^ObjectOutputStream wrtr 
                 (-> filename
                     io/output-stream
                     GZIPOutputStream.
                     ObjectOutputStream.)]
       (.writeObject wrtr data))

     :else (throw+ (str "no match for ext: " ext)))))

(defn read-from-file 
  [file & {:keys [as]}]
  (let [[prefix ext] (split-ext file)
        ext (or as ext)
        exts (str/split ext #"\.")
        in (-> file io/file io/input-stream)
        [in exts] (if (= "gz" (last exts))
                    [(GZIPInputStream. in) (drop-last exts)]
                    [in exts])]
    (if (= "ser" (last exts))
      (with-open [rdr (ObjectInputStream. in)] ; TODO don't use with-open here to be consistent with others but need to change usages of read-from-file, maybe have slurp-from-file
        (.readObject rdr))
      ;; Else use character-input:
      (let [in (io/reader in)]
        (condp = (last exts)
            "json" (wharf/transform-keys (comp keyword wharf/underscore->dash) (json/parsed-seq in))
            "csv" (csv/read-csv in)
            "edn" (let [in (java.io.PushbackReader. in)
                        edn-seq (repeatedly (partial edn/read {:eof :eof} in))]
                    (take-while (partial not= :eof) edn-seq))
            ;; else use line-based
            (let [in (line-seq in)]
              (condp = (last exts)
                "txt" in
                "tsv" (->> in
                           (map #(vec (str/split % #"\t")))
                           (map #(map (fn [s] (str/trim s)) %)))
                :else (throw+ "couldn't match extension" exts))))))))

