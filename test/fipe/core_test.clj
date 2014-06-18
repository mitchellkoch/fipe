(ns fipe.core-test
  (:require [clojure.test :refer :all]
            [fipe.core :refer :all]
            [fipe.util :refer :all]
            [me.raynes.fs :as fs]
            [clojure.string :as str]))

(set-fipe-dir! "data-test")

(deftarget "hello.txt"
  "hello")

(defn x4 [f]
  (let [s (str/trim-newline (slurp f))]
    (vec (repeat 4 s))))

(deftarget "hellos.edn"
  (x4 (dep "hello.txt")))

(deftest fipe-test
  (testing "simple deftarget and fipe"
    (fs/delete "data-test/hello.txt")
    (fipe "hello.txt")
    (is (= "hello\n" (slurp "data-test/hello.txt")))))

(deftest fipe-glob-test
  (testing "simple deftarget and fipe"
    (fs/delete "data-test/hello.txt")
    (fs/delete "data-test/hellos.edn")
    (fipe-glob "hello*")
    (is (= "hello\n" (slurp "data-test/hello.txt")))
    (is (= ["hello" "hello" "hello" "hello"] (read-from-file "data-test/hellos.edn")))))
