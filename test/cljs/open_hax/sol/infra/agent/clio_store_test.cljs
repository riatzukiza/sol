(ns open-hax.sol.infra.agent.clio-store-test
  (:require [cljs.test :refer [deftest is]]
            [clio.extern.js.fs :as fs]
            [clio.infra.event :as event]
            [clio.infra.ledger :as ledger]
            [open-hax.sol.clio-native-probe :as probe]
            [open-hax.sol.domain.node.path :as path]
            [open-hax.sol.infra.agent.clio-store :as store]
            [open-hax.sol.infra.agent.episode-ledger :as episode]
            [open-hax.sol.infra.agent.session-store :as sessions]))

(defn scratch [] (path/resolve ".ημ" "sol-clio-tests" (str (random-uuid))))
(defn options [root]
  {:ledger-file (path/join root "new-epoch" "events.edn")
   :schema-directory (path/join root "new-epoch" "schemas")})
(defn candidate [canonical-store sequence causes]
  (event/make-event
   (store/current-revision canonical-store) :sol.run/started
   {:event/stream "test-episode" :event/seq sequence :event/causes causes
    :event/actor "sol.test" :event/subject "test-run"
    :event/data {:run/id "test-run" :session/id "test-session" :turn/id "test-turn"
                 :episode/id "test-episode" :event/from {:actor-id "sol.test"}
                 :delivery/mode "stream" :payload {:status "running"}}}))
(defn error-kind [f]
  (try (f) nil (catch :default error (:clio/error (ex-data error)))))

(deftest ^:async actual-lost-ack-and-reopen-test
  (let [root (scratch)]
    (try
      (let [result (await (probe/probe! root))]
        (is (= {:status :passed :events 2 :identical-retry true
                :reopen-replay true :stream-sequences [1 2]} result)))
      (finally (fs/remove-tree! root)))))

(deftest actual-admission-and-session-seam-test
  (let [root (scratch)]
    (try
      (let [canonical-store (store/open! (assoc (options root) :initialize? true))
            first-event (candidate canonical-store 1 [])
            session-store (sessions/create-edn-session-store
                           (path/join root "projections") canonical-store)]
        (is (= :appended (store/append! canonical-store first-event)))
        (is (= :already-present (store/append! canonical-store first-event)))
        (is (= :clio.ledger/id-collision
               (error-kind #(store/append! canonical-store
                                           (assoc-in first-event [:event/data :payload :status] "changed")))))
        (is (= :clio.ledger/concurrent-stream-write
               (error-kind #(store/append! canonical-store (candidate canonical-store 1 [])))))
        (is (= [first-event] (store/events canonical-store)))
        (is (thrown-with-msg? cljs.core/ExceptionInfo #"another Sol session"
              (sessions/append-event! session-store "wrong-session" first-event)))
        (is (= [first-event] (store/events canonical-store))))
      (finally (fs/remove-tree! root)))))

(deftest ^:async session-append-and-read-delegate-to-clio-test
  (let [root (scratch)]
    (try
      (let [canonical-store (store/open! (assoc (options root) :initialize? true))
            session-store (sessions/create-edn-session-store (path/join root "views") canonical-store)
            event (candidate canonical-store 1 [])]
        (is (true? (await (sessions/append-event! session-store "test-session" event))))
        (is (true? (await (sessions/append-event! session-store "test-session" event))))
        (is (= [event] (await (sessions/get-events session-store "test-session"))))
        (is (= [] (await (sessions/get-events session-store "other-session")))))
      (finally (fs/remove-tree! root)))))

(deftest explicit-storage-epoch-and-legacy-preservation-test
  (let [root (scratch)]
    (try
      (fs/ensure-dir! root)
      (let [old-ledger (path/join root "legacy.edn")
            old-schema (path/join root "legacy-schema.edn")
            legacy "{:envelope/version 1 :event/id \"old-arbitrary-id\"}\n"
            old-schema-bytes "{:old/schema :untouched}\n"]
        (fs/write-text! old-ledger legacy)
        (fs/write-text! old-schema old-schema-bytes)
        (is (thrown-with-msg? cljs.core/ExceptionInfo #"fresh storage epoch"
              (store/open! {:ledger-file old-ledger
                            :schema-directory (path/join root "new-schemas") :initialize? true})))
        (let [canonical-store (store/open! (assoc (options root) :initialize? true))
              schema-dir (:schema-directory (options root))
              before (mapv #(fs/read-text (path/join schema-dir %)) (fs/list-files schema-dir))]
          (is (= [] (store/events canonical-store)))
          (store/open! (options root))
          (is (= before (mapv #(fs/read-text (path/join schema-dir %)) (fs/list-files schema-dir))))
          (is (= legacy (fs/read-text old-ledger)))
          (is (= old-schema-bytes (fs/read-text old-schema))))
        (is (thrown-with-msg? cljs.core/ExceptionInfo #"not configured"
              (sessions/get-events (sessions/create-edn-session-store root) "test-session"))))
      (finally (fs/remove-tree! root)))))

(deftest missing-path-and-malformed-records-are-not-empty-test
  (let [root (scratch)]
    (try
      (is (= :clio.ledger/missing-file (error-kind #(store/open! (options root)))))
      (let [canonical-store (store/open! (assoc (options root) :initialize? true))]
        (fs/append-text! (:ledger-file canonical-store) "{:broken\n")
        (is (= :clio.ledger/invalid-edn (error-kind #(store/events canonical-store))))
        (is (= :clio.ledger/invalid-edn
               (error-kind #(store/append! canonical-store (candidate canonical-store 1 [])))))
        (fs/delete-if-exists! (:ledger-file canonical-store))
        (is (= :clio.ledger/missing-file (error-kind #(store/events canonical-store)))))
      (finally (fs/remove-tree! root)))))

(deftest missing-schema-directory-is-not-created-on-reopen-test
  (let [root (scratch)]
    (try
      (fs/ensure-dir! (path/dirname (:ledger-file (options root))))
      (ledger/create-ledger! (:ledger-file (options root)))
      (is (thrown-with-msg? cljs.core/ExceptionInfo #"schema history"
            (store/open! (options root))))
      (is (not (fs/exists? (:schema-directory (options root)))))
      (finally (fs/remove-tree! root)))))

(deftest upstream-schema-and-complete-history-refusals-test
  (let [root (scratch)]
    (try
      (let [canonical-store (store/open! (assoc (options root) :initialize? true))
            first-event (candidate canonical-store 1 [])]
        (is (some? (error-kind #(store/append! canonical-store
                                  (assoc-in first-event [:event/schema :schema/root] (apply str (repeat 64 "0")))))))
        ;; Clio append supports partition-local admission, not full causal validation.
        (store/append! canonical-store (candidate canonical-store 2 []))
        (is (some? (error-kind #(store/events canonical-store)))))
      (finally (fs/remove-tree! root)))))

(deftest ^:async pending-write-is-resolved-before-compensation-test
  (let [root (scratch)]
    (try
      (let [canonical-store (store/open! (assoc (options root) :initialize? true))
            attempts* (atom 0)
            e (episode/create-episode
               {:clio/store canonical-store
                :clio/append! (fn [event]
                                (let [result (store/append! canonical-store event)]
                                  (if (= 1 (swap! attempts* inc))
                                    (throw (ex-info "lost ack" {})) result)))}
               {:run-id "test-run" :session-id "test-session"})]
        (try (await (episode/emit! e :sol.run/started {})) (catch :default _ nil))
        (await (episode/emit! e :sol.run/failed {:error "start acknowledgement lost"}))
        (is (= [:sol.run/started :sol.run/failed] (mapv :event/type (store/events canonical-store))))
        (is (= [1 2] (mapv :event/seq (store/events canonical-store)))))
      (finally (fs/remove-tree! root)))))

(deftest hot-reload-reuses-transport-and-refuses-path-change-test
  (let [root (scratch)]
    (try
      (let [config (store/configure!
                    {:clio/ledger-file (:ledger-file (options root))
                     :clio/schema-directory (:schema-directory (options root))
                     :clio/initialize? true})
            reload-config (store/reconfigure! config (dissoc config :clio/store))]
        (is (identical? (:clio/store config) (:clio/store reload-config)))
        (is (thrown-with-msg? cljs.core/ExceptionInfo #"full restart"
              (store/reconfigure! config
                                  (assoc config :clio/ledger-file (path/join root "other.edn")))))
        (is (not (fs/exists? (path/join root "other.edn")))))
      (finally (fs/remove-tree! root)))))
