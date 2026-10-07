(ns rheos-sync-receipt
  (:require [eta-mu.receipt-river.api :as api]
            [eta-mu.receipt-river.domain.receipt :as receipt]
            [eta-mu.receipt-river.shape.edn :as edn]
            [clojure.string :as str]
            ["node:fs" :as fs]
            ["node:child_process" :as child]))
(let [[mode input target] *command-line-args*]
  (if (= mode "append")
    (let [now (.toISOString (js/Date.))
          fields (js->clj (js/JSON.parse (.readFileSync fs input "utf8")) :keywordize-keys true)
          payload (receipt/build-payload (assoc fields :kind :decision) (:repo fields) now :decision)
          event (api/build-event {:event-id (str (random-uuid)) :recorded-at now
                                 :component-manifest {:eta-mu/version "1.1.1"}
                                 :command "Sol issue255 full late-control planning local preparation"
                                 :producer {:actor "root/child_prs"}
                                 :subject {:repo (:repo fields)}}
                                payload)
          line (edn/format-line event)
          result (api/validate-line line 1)]
      (when-not (:ok result) (throw (ex-info "Receipt refused" (:errors result))))
      (.appendFileSync fs target (str line "\n"))
      (prn (select-keys result [:ok :source/schema :errors])))
    (let [text (.execFileSync child "git" #js ["show" (str input ":.ημ/receipts.edn")] #js {:encoding "utf8"})
          results (mapv (fn [i line] (assoc (select-keys (api/validate-line line (inc i)) [:ok :source/schema :errors]) :line (inc i)))
                        (range) (str/split-lines text))
          offset (js/parseInt target 10)
          owned (subvec results offset)]
      (prn {:head input :all-count (count results)
            :historical-count offset :historical-refusals (count (remove :ok (subvec results 0 offset)))
            :declared-owned owned})
      (when-not (and (seq owned) (every? :ok owned)) (set! (.-exitCode js/process) 1)))))
