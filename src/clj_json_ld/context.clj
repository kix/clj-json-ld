(ns clj-json-ld.context
  "
  Implement the context processing as defined in the
  [Context Processing Algorithms section](http://www.w3.org/TR/json-ld-api/#context-processing-algorithm).
  "
  (:require [defun :refer (defun defun-)]
            [clojure.core.match :refer (match)]
            [clojure.string :as s]
            [clojurewerkz.urly.core :as u]
            [clj-json-ld.iri :refer (blank-node-identifier?)]))

;; 3.4) If context has an @base key and remote contexts is empty,
;; i.e., the currently being processed context is not a remote context: 
(defun- process-base-key 

  ; currently being processed context IS a remote context, so do nothing
  ([result context remote-contexts :guard #(not (empty? %))]
    result) 

  ; currently being processed context has a @base key
  ([result context :guard #(contains? % "@base") remote-contexts]
    
    ;; 3.4.1) Initialize value to the value associated with the @base key.
    (let [value (get context "@base")]
      
      (match [value]
        
        ;; 3.4.2) If value is null, remove the base IRI of result.
        [value :guard #(not %)] (dissoc result "@base")
        
        ;; 3.4.3) Otherwise, if value is an absolute IRI, the base IRI of result is set to value.
        [value :guard #(u/absolute? %)] (assoc result "@base" value)

        ;; 3.4.4) Otherwise, if value is a relative IRI and the base IRI of result is not null,
        ;; set the base IRI of result to the result of resolving value against the current base IRI
        ;; of result.
        [value :guard #(and (string? %) (not (s/blank? (get result "@base"))))]
          (assoc result "@base" (u/resolve (get result "@base") value))

        ;; 3.4.5) Otherwise, an invalid base IRI error has been detected and processing is aborted.
        [_] (throw (ex-info "JSONLDError" {:code "invalid base IRI"
            :message "local context @base has a relative IRI, and there is no absolute @base IRI in the active context"})))))

  ; context has no @base key, so do nothing
  ([result _ _] result))

;; 3.5) If context has an @vocab key: 
(defun- process-vocab-key
  ; currently being processed context has a @vocab key
  ([result context :guard #(contains? % "@vocab")]
    ;; 3.5.1) Initialize value to the value associated with the @vocab key.
    (if-let [vocab (get context "@vocab")]
      ;; 3.5.3) Otherwise, if value is an absolute IRI or blank node identifier,
      ;; the vocabulary mapping of result is set to value. If it is not an absolute
      ;; IRI or blank node identifier, an invalid vocab mapping error has been detected
      ;; and processing is aborted.
      (or (u/absolute? vocab) (blank-node-identifier? vocab)
        (assoc result "@vocab" vocab)
        (throw (ex-info "JSONLDError" {:code "invalid vocab mapping"
              :message "local context @vocab but it's not an absolute IRI or a blank node identifier"})))
      ;; 3.5.2) If value is null, remove any vocabulary mapping from result.
      (dissoc result "@vocab")))
  ; context has no @vocab key, so do nothing
  ([result _] result))


;; 3.6) If context has an @language key: 
(defn- process-language-key [result context]
  result)

;; 3.4, 3.5, and 3.6) If context IS a JSON object, process the context.
(defn- process-local-context [result context remote-contexts]
  (-> result
    ;; 3.4) If context has an @base key and remote contexts is empty, i.e., the currently being processed context is not a remote context: 
    (process-base-key context remote-contexts)
    ;; 3.5) If context has an @vocab key: 
    (process-vocab-key context)
    ;; 3.6) If context has an @language key: 
    (process-language-key context)))

(defun update-with-local-context 
  "Update an active context with a local context."
  
  ;; If remote contexts is not passed, it is initialized to an empty array.
  ([active-context local-context]
    (update-with-local-context active-context local-context []))
  
  ;; 2) If local context is not an array, set it to an array containing only local context.
  ([active-context local-context :guard #(not (sequential? %)) remote-contexts]
    (update-with-local-context active-context [local-context] remote-contexts))

  ;; 1) Initialize result to the result of cloning active context.
  ;; Our recursion accumulator starts as a "clone" of the active-context
  ([active-context local-context remote-contexts]
    (update-with-local-context active-context active-context local-context remote-contexts))

  ;; The next two patterns provide the recursion for:
  ;; 3) For each item context in local context

  ;; stop condition of the recursion; return the accumulated result of merging with the local
  ;; context(s) as the new active context
  ([result active-context local-context :guard #(empty? %) remote-contexts]
    result)

  ([result active-context local-context remote-contexts]
    (let [context (first local-context)]
      (match [context]

        ;; 3.1) If context is null, set result to a newly-initialized active context and continue
        ;; with the next context. The base IRI of the active context is set to the IRI of the
        ;; currently being processed document (which might be different from the currently being
        ;; processed context), if available; otherwise to null. If set, the base option of a JSON-LD
        ;; API Implementation overrides the base IRI.
        [nil] 
          (recur {} active-context (rest local-context) remote-contexts)

        ;; 3.2) If context is a string, it's a remote context, retrieve it, parse it and recurse.
        [string-context :guard #(string? %)]
          (do (println "Remote Context! Do good things here.")
            (recur active-context (rest local-context) remote-contexts))

        ;; TODO Use a real Java exception here, not ex-info
        ;; 3.3) If context is NOT a JSON object, an invalid local context error has been detected and
        ;; processing is aborted.
        [map-context :guard #(not (map? %))] 
          (throw (ex-info "JSONLDError" {:code "invalid local context"
            :message "local context is not a JSON object"}))

        ;; 3.4, 3.5, and 3.6) If context IS a JSON object, process the context.
        [_] 
          (recur (process-local-context result (first local-context) remote-contexts)
                 (rest local-context)
                 remote-contexts)))))