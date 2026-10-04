(ns open-hax.sol.clio-native-probe
  "Test-only compiled ESM probe of Sol's actual Clio transport, never inference."
  (:require [open-hax.sol.infra.agent.clio-store :as store]
            [open-hax.sol.infra.agent.episode-ledger :as episode]
            [open-hax.sol.domain.node.path :as path]))

(defn- require-result!
  [predicate message]
  (when-not predicate (throw (ex-info message {}))))

(defn ^:async probe!
  [root]
  (let [options {:ledger-file (path/join root "epoch" "events.edn")
                 :schema-directory (path/join root "epoch" "schemas")}
        canonical-store (store/open! (assoc options :initialize? true))
        attempts* (atom [])
        e (episode/create-episode
           {:clio/store canonical-store
            :clio/append! (fn [event]
                            (let [verdict (store/append! canonical-store event)]
                              (swap! attempts* conj event)
                              (if (= 1 (count @attempts*))
                                (throw (ex-info "probe lost acknowledgement" {}))
                                verdict)))}
           {:run-id "probe-run" :session-id "probe-session"})]
    (try
      (await (episode/emit! e :sol.run/started {:status "running"}))
      (throw (ex-info "Lost acknowledgement fixture did not reject" {}))
      (catch :default error
        (require-result! (= "probe lost acknowledgement" (.-message error))
                         "Unexpected first append error")))
    (require-result! (= 0 @(:sequence* e)) "Sequence advanced without acknowledgement")
    (let [first-event (await (episode/emit! e :sol.run/started {:status "running"}))
          second-event (await (episode/emit! e :sol.run/completed {:status "completed"}))
          reopened (store/open! options)
          events (store/events reopened)]
      (require-result! (= (first @attempts*) (second @attempts*) first-event)
                       "Retry did not preserve exact event identity/data")
      (require-result! (= [first-event second-event] events) "Complete replay changed accepted data")
      (require-result! (= [1 2] (mapv :event/seq events)) "Stream revisions changed")
      (require-result! (= [[] [(:event/id first-event)]] (mapv :event/causes events))
                       "Explicit causal predecessor was lost")
      {:status :passed :events (count events) :identical-retry true
       :reopen-replay true :stream-sequences (mapv :event/seq events)})))

(defn ^:async probe-edn!
  "JS export returns printable evidence; all assertions run through real stores."
  [root]
  (pr-str (await (probe! root))))
