#!/usr/bin/env bb
;; Structural check of a proposal only; not a runtime, resource or board validator.
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(defn require-check! [valid? message]
  (when-not valid?
    (throw (ex-info message {}))))

(defn check-plan! [proposal-path]
  (let [plan (edn/read-string (slurp proposal-path))
        deps (edn/read-string (slurp "deps.edn"))
        points (:integration/points plan)]
    (require-check! (= :proposed (:proposal/status plan)) "Proposal must remain proposed")
    (require-check! (false? (:activation/enabled? plan)) "Proposal must not activate a service")
    (require-check! (re-matches #"[0-9a-f]{40}" (:source/revision plan)) "Full source SHA required")
    (require-check! (= "330aa63f-f697-5bd6-9fc0-3f19fbea2be4" (:parent/story plan))
                    "Expected Foresight parent story")
    (require-check! (= :eta-mu (get-in plan [:authority :publication])) "Publisher owner drift")
    (require-check! (false? (get-in plan [:review-policy :auto-merge?])) "Planning auto-merge must stay off")
    (require-check! (seq (:dependencies plan)) "Pinned dependencies required")
    (doseq [[lib pin] (:dependencies plan)]
      (require-check! (= pin (select-keys (get-in deps [:deps lib]) (keys pin)))
                      (str "Dependency pin drift: " lib)))
    (require-check! (seq points) "Integration points required")
    (require-check! (= (count points) (count (set (map :path points)))) "Duplicate source paths")
    (doseq [{:keys [path namespace anchor]} points]
      (require-check! (.isFile (io/file path)) (str "Missing integration file: " path))
      (let [source (slurp path)
            ns-form (binding [*read-eval* false]
                      (read (java.io.PushbackReader. (java.io.StringReader. source))))]
        (require-check! (and (= 'ns (first ns-form))
                             (= (symbol namespace) (second ns-form)))
                        (str "Namespace drift: " path))
        (require-check! (str/includes? source anchor) (str "Anchor drift: " path))))
    {:proposal (:proposal/id plan) :status :proposed
     :dependency-pins (count (:dependencies plan)) :integration-files (count points)
     :runtime-validated? false :board-validated? false :deployment-validated? false}))

(try
  (prn (check-plan! (or (first *command-line-args*) "docs/design/persistent-review-workers.edn")))
  (catch Exception error
    (binding [*out* *err*] (println "Proposal check failed:" (.getMessage error)))
    (System/exit 1)))
