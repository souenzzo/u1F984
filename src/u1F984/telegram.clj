(ns u1F984.telegram
  (:require [clj-http.client :as client]
            [clojure.data.json :as data.json]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [clojure.spec.alpha :as s]
            [clojure.java.jdbc :as j])
  (:import (java.util Date)
           (clojure.lang Atom PersistentArrayMap)))



(defprotocol IDb
  (msg-received? [this message-id])
  (received-msgs [this]))

(defn mem-db
  []
  (atom {:received #{}
         :sent     #{}}))

(defprotocol IConn
  (msg-received! [this message-id])
  (msg-sent! [this message-id])
  (conn->db [this]))

(extend-type PersistentArrayMap
  IDb
  (msg-received? [this message-id]
    (contains? (received-msgs this) message-id))
  (received-msgs [this]
    (:received this)))

(extend-type Atom
  IConn
  (conn->db [this] @this)
  (msg-received! [this message-id]
    (swap! this (fn [db] (if (msg-received? db message-id)
                           (throw (ex-info "Duped" {}))
                           (update db :received conj message-id)))))
  (msg-sent! [this message-id]
    (swap! this update :sent conj message-id)))

(defn request!
  [req]
  (-> (client/request req)
      :body
      (data.json/read-str :key-fn keyword)
      :result))

(defn telegram->http
  ([token {:keys [method] :as params}]
   (telegram->http token method (dissoc params :method)))
  ([token method params]
   {:method  :post
    :url     (format "https://api.telegram.org/bot%s/%s" token (name method))
    :headers {"Content-Type" "application/json"}
    :body    (data.json/write-str params)}))

(defmulti api :method)
(defmethod api :getMe
  [_]
  (s/keys :req-un [::method]))

(s/def :sendMessage/chat_id (s/or :nick string?
                                  :id number?))
(s/def :sendMessage/text string?)

(s/def :sendMessage/parse_mode string?)

(s/def :sendMessage/disable_web_page_preview boolean?)

(s/def :sendMessage/disable_notification boolean?)

(s/def :sendMessage/reply_to_message_id number?)


(defmethod api :sendMessage
  [_]
  (s/keys :req-un [::method
                   :sendMessage/chat_id
                   :sendMessage/text]
          :opt-un [:sendMessage/parse_mode
                   :sendMessage/disable_web_page_preview
                   :sendMessage/disable_notification
                   :sendMessage/reply_to_message_id]))

(s/def :getUpdates/offset integer?)
(s/def :getUpdates/limit integer?)
(s/def :getUpdates/timeout integer?)

(s/def :getUpdates/allowed_updates (s/coll-of string?))

(defmethod api :getUpdates
  [_]
  (s/keys :opt-un [:getUpdates/offset
                   :getUpdates/limit
                   :getUpdates/timeout
                   :getUpdates/allowed_updates]))


(s/def ::event (s/multi-spec api :method))

(def types
  {::update     {:update_id :telegram.update/id
                 :message   [:telegram.update/message ::message]}
   ::message    {:message_id :telegram.message/id
                 :from       [:telegram.message/from ::user]
                 :chat       [:telegram.message/chat ::chat]
                 :date       [:telegram.message/date ::date]
                 :text       :telegram.message/text}
   ::chat       {:id                             :telegram.chat/id
                 :type                           [:telegram.chat/type ::enum]
                 :title                          :telegram.chat/title
                 :username                       :telegram.user/username
                 :first_name                     :telegram.chat/first-name
                 :last_name                      :telegram.chat/last-name
                 :all_members_are_administrators :telegram.chat/all-members-are-admin?
                 :photo                          [:telegram.chat/photo ::chat-photo]
                 :description                    :telegram.chat/description
                 :invite_link                    :telegram.chat/invite-link
                 :pinned_message                 [:telegram.chat/pinned-message ::message]
                 :sticker_set_name               :telegram.chat/sticker-set-name
                 :can_set_sticker_set            :telegram.chat/can-set-sticker-set?}
   ::chat-photo {:small_file_id :telegram.chat-photo/small-file-id
                 :big_file_id   :telegram.chat-photo/big-file-id}
   ::user       {:id            :telegram.user/id
                 :is_bot        :telegram.user/bot?
                 :first_name    :telegram.user/first-name
                 :last_name     :telegram.user/last-name
                 :username      :telegram.user/username
                 :language_code :telegram.user/language-code}
   ::enum       #(keyword (format "%s.%s" (namespace %1) (name %1)) %2)
   ::date       #(new Date (* %2 1000))})

(defn ->type
  ([k v] (->type k k v))
  ([K k v]
   (let [desc (get types K)
         many? (sequential? v)]
     (cond-> (map (fn [v]
                    (cond
                      (fn? desc) (desc k v)
                      (map? desc) (into {} (for [[k v] v]
                                             (let [kdesc (get desc k)]
                                               (cond
                                                 (keyword? kdesc) [kdesc v]
                                                 (coll? kdesc) [(first kdesc) (->type (second kdesc) (first kdesc) v)]))))))
                  (if many? v [v]))
             (not many?) first))))


(def indexes (atom {}))

(defmulti mutation-fn pc/mutation-dispatch)
(defmulti resolver-fn pc/resolver-dispatch)
(def defmutation
  (pc/mutation-factory mutation-fn indexes))
(def defresolver
  (pc/resolver-factory resolver-fn indexes))


(defmutation `send-message
             {::pc/args   [::chat_id ::text]
              ::pc/output [:telegram.message/chat [:telegram.chat/first-name
                                                   :telegram.chat/id
                                                   :telegram.chat/type
                                                   :telegram.user/username]
                           :telegram.message/date
                           :telegram.message/from [:telegram.user/bot?
                                                   :telegram.user/first-name
                                                   :telegram.user/id
                                                   :telegram.user/username]
                           :telegram.message/id]}
             (fn [{::keys [token]} {::keys [chat_id text]}]
               (->> (telegram->http token "sendMessage" {:chat_id chat_id
                                                         :text    text})
                    request!
                    (->type ::message))))



(defmutation `received
             {::pc/args [::message_id]}
             (fn [{::keys [conn]} {::keys [message_id]}]
               (msg-received! conn message_id)))

(defmutation `sent
             {::pc/args [::message_id]}
             (fn [{::keys [conn]} {::keys [message_id]}]
               (msg-sent! conn message_id)))


(defresolver `received-msgs
             {::pc/args   []
              ::pc/output [::msg-ids]}
             (fn [{::keys [db]} _]
               {::msg-ids (received-msgs db)}))

(defresolver `get-updates
             {::pc/args   []
              ::pc/output [{::updates [:telegram.update/id
                                       #:telegram.update{:message [#:telegram.message{:chat [:telegram.chat/first-name
                                                                                             :telegram.chat/id
                                                                                             :telegram.chat/type
                                                                                             :telegram.user/username]}
                                                                   :telegram.message/date
                                                                   #:telegram.message{:from [:telegram.user/bot?
                                                                                             :telegram.user/first-name
                                                                                             :telegram.user/id
                                                                                             :telegram.user/language-code
                                                                                             :telegram.user/username]}
                                                                   :telegram.message/id
                                                                   :telegram.message/text]}]}]}
             (fn [{::keys [token]} _]
               (->> (telegram->http token "getUpdates" {})
                    request!
                    (->type ::update)
                    (hash-map ::updates))))



(def parser (p/parser {::p/env {::p/reader             [p/map-reader pc/all-readers]
                                ::pc/mutate-dispatch   mutation-fn
                                ::pc/resolver-dispatch resolver-fn
                                ::pc/indexes           @indexes}
                       :mutate pc/mutate}))


(s/fdef telegram->http
        :args (s/cat :url string?
                     :args ::event))
