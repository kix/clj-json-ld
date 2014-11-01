(ns clj-json-ld.expansion
  (:require [midje.sweet :refer :all]
            [cheshire.core :refer (parse-string)]
            [clj-json-ld.lib.spec-test-suite :refer :all]
            [clj-json-ld.core :as json-ld]))

(def manifest "expand-manifest.jsonld")

(facts "Expansion Evaluation Tests"

  (doseq [test-case (take 1 (tests-from-manifest manifest))]

    (println "\n" (:name test-case))

    ;; Possible variation:
    ;; input - JSON, map, remote file
    ;; output - JSON, map
    
    ;; Combinatorial: 3 x 2 = 6 total cases

    ;; 1,"JSON","JSON"
    ;; 2,"JSON", "map"
    ;; 3,"map", "JSON"
    ;; 4,"map","map"
    ;; 5,"remote","JSON"
    ;; 6,"remote","map"

    ;; 1,"JSON","JSON"
    (parse-string (json-ld/expand (:input test-case))) =>
      (parse-string (:expect test-case))
  )
)