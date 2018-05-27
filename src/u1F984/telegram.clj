(ns u1F984.telegram
  (:require [clj-http.client :as client]
            [clojure.data.json :as data.json]
            [clojure.spec.alpha :as s]))

(defn request
  [req]
  (-> (client/request req)
      :body
      (data.json/read-str :key-fn keyword)
      :result))

(defn telegram->http
  [token {:keys [method] :as params}]
  {:method  :post
   :url     (format "https://api.telegram.org/bot%s/%s" token (name method))
   :headers {"Content-Type" "application/json"}
   :body    (data.json/write-str params)})

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

(s/fdef telegram->http
        :args (s/cat :url string?
                     :args ::event))
