(ns u1F984.core
  (:gen-class)
  (:require [u1F984.telegram :as telegram]
            [io.pedestal.interceptor.chain :as chain]
            [clojure.string :as string]
            [clojure.core.async :as async])
  (:import (java.text Normalizer$Form Normalizer)))

(defn distinct-by
  [f]
  (fn [rf]
    (let [seen (volatile! #{})]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [x (f input)]
           (if (contains? @seen x)
             result
             (do (vswap! seen conj x)
                 (rf result input)))))))))



(def token (System/getenv "bot_token"))

(def ^:dynamic limit 3)

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
   :enter (fn [{{{{:keys [id]} :chat
                  :keys        [text]} :message} :event
                ::keys                           [index]
                :as                              ctx}]
            (let [response (->> (normalize text)
                                (match index)
                                (take limit)
                                (map (fn [[k v]]
                                       (format "%s: %s (%s)" v k (int k))))
                                (string/join "\n"))]
              (assoc ctx
                :api.telegram/dispatch `[(telegram/send-message ~{::telegram/chat_id id
                                                                  ::telegram/text    (if (string/blank? response)
                                                                                       "404"
                                                                                       response)})])))})

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
        index (make-index)]
    (chain/enqueue {::index  index
                    ::routes routes} interceptors)))

(def watch-state (atom nil))

(defn start-watch!
  [state chan timeout]
  (swap! state (fn [fut]
                 (when fut
                   (future-cancel fut))
                 (future (loop []
                           (Thread/sleep timeout)
                           (doseq [msg-update (->> {:method :getUpdates}
                                                   (telegram/telegram->http token)
                                                   telegram/request!)]
                             (async/>!! chan msg-update))
                           (recur))))))

(defn start-process!
  [ctx chan]
  (async/go-loop []
    (let [msg-update (async/<! chan)
          ctx* (assoc ctx :event
                          (assoc msg-update
                            :event/type :event.type/update))
          {:keys [api.telegram/dispatch]} (chain/execute ctx*)]
      (prn [:msg msg-update dispatch])
      (telegram/parser {::telegram/token token} dispatch)
      (recur))))

(defonce updates
         (async/chan 100 (distinct-by :update_id)))

(defn -main
  [& args]
  (start-process! (make-ctx) updates)
  (start-watch! watch-state updates 1000))
