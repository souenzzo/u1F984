(ns u1F984.core
  (:gen-class)
  (:require [u1F984.telegram :as telegram]
            [io.pedestal.interceptor.chain :as chain]
            [clojure.string :as string]
            [clojure.java.jdbc :as j]
            [clojure.core.async :as async])
  (:import (java.text Normalizer$Form Normalizer)))

(def token (System/getenv "bot_token"))

(def ^:dynamic limit
  3)

(def update-timeout
  1000)


(defn distinct-by
  ([f] (distinct-by f #{}))
  ([f seen]
   (fn [rf]
     (let [seen (volatile! seen)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [x (f input)]
            (if (contains? @seen x)
              result
              (do (vswap! seen conj x)
                  (rf result input))))))))))


(defn normalize
  [s]
  (-> (Normalizer/normalize s Normalizer$Form/NFD)
      (string/replace #"[^\p{ASCII}]" "")
      (string/lower-case)
      (string/split #"[\s]+")
      ((partial string/join " "))
      #_(set)))


(defn make-index
  []
  (->> (for [i (range)]
         (try
           [(char i) (Character/getName i)]
           (catch Throwable _ nil)))
       (take-while coll?)
       (keep (fn [[k v]]
               (when (string? v)
                 (let [nv (normalize v)]
                   (when-not (or (string/blank? nv)
                                 (string/blank? (str k)))
                     [k nv])))))
       (into (sorted-map))))

(defn match
  [index text]
  (for [[k v] index
        :when (string/includes? v text)]
    [k v]))

(def handler
  {:name  ::handler
   :enter (fn [{{{{chat-id :telegram.chat/id} :telegram.message/chat
                  :telegram.message/keys      [text id]} :telegram.update/message
                 :as                                     event} :event
                ::keys                                          [index]
                :as                                             ctx}]
            (let [response (->> (normalize text)
                                (match index)
                                (take limit)
                                (map (fn [[k v]]
                                       (format "%s: %s (%s)" v k (int k))))
                                (string/join "\n"))]
              (assoc ctx
                :api.telegram/dispatch `[{(telegram/received ~{::telegram/message_id id})
                                          [{(telegram/send-message ~{::telegram/chat_id chat-id
                                                                     ::telegram/text    (if (string/blank? response)
                                                                                          "404"
                                                                                          response)})
                                            [(telegram/sent ~{::telegram/message_id id})]}]}])))})

(def routes
  {:event.type/update [handler]})

(def router
  {:name  ::router
   :enter (fn [{{:keys [event/type]} :event
                ::keys               [routes]
                :as                  ctx}]
            (chain/enqueue ctx (or (get routes type)
                                   (throw (ex-info "404" {:routes (keys routes)
                                                          :event  type})))))})

(defn make-ctx
  []
  (let [interceptors [router]
        index (make-index)
        conn (telegram/mem-db)]
    (chain/enqueue {::telegram/conn  conn
                    ::telegram/db    (telegram/conn->db conn)
                    ::telegram/token token
                    ::index          index
                    ::update-timeout update-timeout
                    ::routes         routes} interceptors)))

(defn start-watch!
  [state chan {::keys [update-timeout]
               :as    ctx}]
  (swap! state (fn [fut]
                 (when (future? fut)
                   (future-cancel fut))
                 (future (loop []
                           (Thread/sleep update-timeout)
                           (doseq [update (::telegram/updates (telegram/parser ctx [::telegram/updates]))]
                             (async/>!! chan update))
                           (recur))))))

(defn start-process!
  [ctx chan]
  (async/go-loop []
    (when-let [msg-update (async/<! chan)]
      (let [ctx* (assoc ctx :event
                            (assoc msg-update
                              :event/type :event.type/update))
            {:keys [api.telegram/dispatch]} (chain/execute ctx*)]
        (telegram/parser ctx dispatch)
        (recur)))))

(defonce update-chan (atom nil))
(defonce watch-state (atom nil))
(def buffer-len 100)
(defn -main
  [& args]
  (let [ctx (make-ctx)
        {::telegram/keys [msg-ids]} (telegram/parser ctx [::telegram/msg-ids])
        updates (->> (distinct-by :telegram.update/id msg-ids)
                     (async/chan (async/sliding-buffer buffer-len))
                     (reset! update-chan))]
    (start-process! ctx updates)
    (start-watch! watch-state updates ctx)))

(defn stop!!
  []
  (async/close! @update-chan)
  (future-cancel @watch-state))