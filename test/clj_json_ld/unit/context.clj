(ns clj-json-ld.unit.context
  "
  Test the context processing as defined here: http://www.w3.org/TR/json-ld-api/#context-processing-algorithm
  "
  (:require [midje.sweet :refer :all]
            [clj-json-ld.context :refer (update-with-local-context)]
            [clojurewerkz.urly.core :as u]))

(def active-context {
  "@base" fcms-iri
  "@vocab" "http://vocab.com/"
  "@foo" :bar
})

(def fcms-iri "http://falkland-cms.com/")
(def falklandsophile-iri "http://falklandsophile.com/")
(def snootymonkey-iri "http://snootymonkey.com")
(def relative-iri "/foo/bar")

(def vocab-iri "http://vocab.org/")
(def another-vocab-iri "http://vocab.net/")

(def blank-node-identifier "_:foo")

(facts "about updating active context with local contexts"

  (facts "about invalid local contexts"

    (facts "as a scalar"
      (update-with-local-context active-context 1) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil 1]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context 1.1) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil 1.1]) => (throws clojure.lang.ExceptionInfo))

    (facts "as a keyword"
      (update-with-local-context active-context :foo) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil :foo]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context :foo) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil :foo]) => (throws clojure.lang.ExceptionInfo))

    (facts "as a sequential"
      (update-with-local-context active-context [[]]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil []]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [()]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil ()]) => (throws clojure.lang.ExceptionInfo)))

  (facts "about nil local contexts"

    (fact "result in a newly-initialized active context"
      (update-with-local-context active-context nil) => {}
      (update-with-local-context active-context [nil]) => {}
      (update-with-local-context active-context [{:blat :bloo} nil]) => {}))

  (future-facts "about local contexts as remote strings")
        
  (facts "about @base in local contexts"

    (facts "@base of nil removes the @base"
      (update-with-local-context active-context {"@base" nil}) => (dissoc active-context "@base")
      (update-with-local-context active-context {"@base2" nil}) => active-context)

    (facts "@base of an absolute IRI makes the IRI the @base"
      
      (doseq [local-context [
                {"@base" falklandsophile-iri}     
                [{} {"@base" falklandsophile-iri}]   
                [{"@base" falklandsophile-iri} {}]
                [{"@base" snootymonkey-iri} {"@base" falklandsophile-iri} {}]
                [{"@base" snootymonkey-iri} {"@base" fcms-iri} {} {"@base" nil} {"@base" falklandsophile-iri}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@base" falklandsophile-iri)))
      
    (facts "@base of an relative IRI merges with the @base of the active-context"

      (update-with-local-context active-context {"@base" "foo/bar"}) =>
        (assoc active-context "@base" (u/resolve fcms-iri "foo/bar"))

      (update-with-local-context active-context [{} {"@base" "foo/bar"} {}]) =>
        (assoc active-context "@base" (u/resolve fcms-iri "foo/bar"))

      (update-with-local-context active-context [{"@base" "foo/bar"} {"@base" "bloo/blat"}]) =>
        (assoc active-context "@base" (-> fcms-iri (u/resolve "foo/bar") (u/resolve "bloo/blat"))))

    (facts "@base of a relative IRI without an @base in the active-context is an invalid base IRI error"

      (doseq [local-context [
                {"@base" 42}
                {"@base" 3.14}
                {"@base" "foo/bar"}
                [{"@base" nil} {"@base" "foo/bar"}]
                [{} {"@base" "foo/bar"}]
                [{"@base" "foo/bar"} {"@base" falklandsophile-iri}]
              ]]
        (update-with-local-context {} local-context) => (throws clojure.lang.ExceptionInfo))))

  (facts "about @vocab in local contexts"

    (facts "@vocab of nil removes the @vocab"
      (update-with-local-context active-context {"@vocab" nil}) => (dissoc active-context "@vocab")
      (update-with-local-context active-context {"@vocab2" nil}) => 
        active-context)

    (facts "@vocab of an absolute IRI makes the IRI the @vocab"

      (doseq [local-context [
                {"@vocab" vocab-iri}
                [{} {"@vocab" vocab-iri}]
                [{"@vocab" vocab-iri} {}]
                [{"@vocab" another-vocab-iri} {"@vocab" vocab-iri} {}]
                [{"@vocab" another-vocab-iri} {} {"@vocab" nil} {"@vocab" vocab-iri}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@vocab" vocab-iri)))

    (facts "@vocab of a blank node identifier makes the blank node identifier the @vocab"
      (doseq [local-context [
                {"@vocab" blank-node-identifier}
                [{} {"@vocab" blank-node-identifier}]
                [{"@vocab" blank-node-identifier} {}]
                [{"@vocab" another-vocab-iri} {"@vocab" vocab-iri} {"@vocab" blank-node-identifier} {}]
                [{"@vocab" another-vocab-iri} {"@vocab" vocab-iri} {} {"@vocab" nil} {"@vocab" blank-node-identifier}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@vocab" blank-node-identifier)))

    (facts "@vocab of anything else is an invalid vocab mapping"

      (doseq [local-context [
                {"@vocab" 42}
                {"@vocab" 3.14}
                {"@vocab" "foo"}
                {"@vocab" "foo/bar"}
                [{"@vocab" nil} {"@vocab" "foo/bar"}]
                [{} {"@vocab" "foo/bar"}]
                [{"@base" vocab-iri} {"@vocab" "foo/bar"} {"@base" another-vocab-iri}]
              ]]
        (update-with-local-context {} local-context) => (throws clojure.lang.ExceptionInfo))))

  (future-facts "about @language in local contexts"))