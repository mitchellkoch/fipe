(ns fipe.core
  "File pipelines"
  (:use fipe.util
        flatland.ordered.set)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [clj-time.core]
            [clj-progress.core :as progress])
  (:import [org.joda.time.format PeriodFormatterBuilder]
           org.joda.time.Interval))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Paths

(def fipe-dir "data")

(defn set-fipe-dir! [dir]
  (def fipe-dir dir))

(defn dep [filename]
  (io/file fipe-dir filename))

(defn fipe-rel [& filenames]
  (apply io/file fipe-dir filenames))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Timing Runs

(defn interval->hms [^Interval interval]
  (let [builder (doto (PeriodFormatterBuilder.)
                  (.printZeroAlways)
                  (.printZeroRarelyLast)
                  (.appendHours)
                  (.appendSuffix "h")
                  (.appendMinutes)
                  (.appendSuffix "m")
                  (.appendSeconds)
                  (.appendSuffix "s")
                  (.appendMillis)
                  (.appendSuffix "ms"))
        formatter (.toFormatter builder)]
    (.print formatter (.toPeriod interval))))

(progress/set-progress-bar! ":header [:bar] :percent :done/:total :etas")

(defn run 
  ([f] (run f {}))
  ([f {:keys [with-progress]}]
     (when with-progress
       (progress/init (first with-progress) ((second with-progress))))
     (let [start-time (clj-time.core/now)
           result (f)]
       (when with-progress
         (progress/done))
       (println "--- done in" 
                (interval->hms (clj-time.core/interval 
                                start-time 
                                (clj-time.core/now)))
                "\n")
       result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; File pipeline definitions

(def fipe-target-names-all (atom (ordered-set)))
(def fipe-targets-glob (atom {}))
(def fipe-target!s (atom {}))
(def fipe-targets (atom {}))

(defn target-glob
  "Find all targets that match the glob pattern."
  [glob-str]
  (filter (partial glob-match? glob-str) @fipe-target-names-all))

(defn target-match
  "Find all targets that match the regex pattern."
  [regex]
  (filter #(re-find regex %) @fipe-target-names-all))

(defn split-glob-str [glob-str]
  (str/split glob-str #"\*"))

(defmacro deftarget 
  ([target body] `(deftarget ~target {} ~body))
  ([target opts body]
     `(let [^String target# ~target
            ;; Convert :from-same-dir to :from
            opts# (if (contains? ~opts :from-same-dir)
                    (dissoc 
                     (assoc ~opts :from (str (first (split-glob-str target#))
                                             (:from-same-dir ~opts)))
                     :from-same-dir)
                    ~opts)
            ftatom# (if (.contains target# "*") fipe-targets-glob fipe-targets)]
        (swap! ftatom#
               #(assoc % 
                  target#
                  (assoc opts#
                    :target-fn (fn [] ~body))))
        (swap! fipe-target-names-all #(conj % target#)))))

(def ^:dynamic *target* nil)
(def ^:dynamic *basename-noext* nil)
(def ^:dynamic *target-basename* nil)
(def ^:dynamic *source* nil)

(defmacro deftarget! [target & body]
  `(do (swap! fipe-target!s
              #(assoc % 
                 ~target
                 {:target-fn (fn [] ~@body)}))
       (swap! fipe-target-names-all #(conj % ~target))))

(defn fipe-glob-match-single [target]
  (first
   (for [[glob-str target-map] @fipe-targets-glob
         :when (glob-match? glob-str target)]
     (assoc target-map :deftarget-str glob-str))))

(defn fipe [target]
  (fs/mkdirs (fs/parent (fipe-rel target)))
  ;; fipe called with a glob
  (if-let [{:keys [target-fn from write-as with-progress]} (get @fipe-targets-glob target)]
    (doseq [source (fs/glob (fipe-rel from))]
      (let [[_ source-ext] (split-glob-str from)
            [target-parent-relpath target-ext] (split-glob-str target)]
        (binding [*source* source]
          (binding [*basename-noext* (fs/base-name *source* source-ext)]
            (binding [*target-basename* (str *basename-noext* target-ext)]
              (binding [*target* (io/file (fs/parent (fipe-rel target)) *target-basename*)]
                (println "---" (str target-parent-relpath *target-basename*))
                (run #(write-to-file *target* (target-fn) :as write-as)
                     {:with-progress with-progress})))))))

    (do
      (println "---" target)
      (binding [*target* (fipe-rel target)
                *target-basename* (fs/base-name target)]
        ;; single file matches a glob target case
        (if-let [{:keys [target-fn from write-as with-progress deftarget-str]} 
                 (fipe-glob-match-single target)]
          (let [[source-parent-relpath source-ext] (split-glob-str from)
                [_ target-ext] (split-glob-str deftarget-str)]
            (binding [*basename-noext* (fs/base-name target target-ext)]
              (binding [*source* (io/file (fipe-rel source-parent-relpath)
                                          (str *basename-noext* source-ext))]
                (run #(write-to-file *target* (target-fn) :as write-as)
                     {:with-progress with-progress}))))

          ;; deftarget! case
          (if-let [{:keys [target-fn with-progress]} (get @fipe-target!s target)]
            (run target-fn
                 {:with-progress with-progress})

            ;; plain deftarget case
            (if-let [{:keys [target-fn write-as with-progress]} (get @fipe-targets target)]
              (run #(write-to-file *target* (target-fn) :as write-as)
                   {:with-progress with-progress}))))))
    
    ))

(defn fipe-glob [glob-str]
  (doseq [target (target-glob glob-str)]
    (fipe target)))
