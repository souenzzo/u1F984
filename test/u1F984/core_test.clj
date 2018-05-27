(ns u1F984.core-test
  (:require [clojure.test :refer [deftest]]
            [midje.sweet :refer :all]
            [u1F984.core :as core]
            [clojure.string :as string]
            [io.pedestal.interceptor.chain :as chain]))


(deftest my-first-test
  (let [ctx (core/make-ctx)]
    (fact
      (->> (chain/execute (assoc ctx
                            :event {:event/type :event.type/update
                                    :message    {:chat {:id 222}
                                                 :text "alpha"}}))
           :api.telegram/dispatch)
      => [{:method  :sendMessage
           :chat_id 222
           :text    (string/join "\n" ["latin small letter alpha: ɑ"
                                       "latin small letter turned alpha: ɒ"
                                       "greek capital letter alpha with tonos: Ά"])}])
    (fact
      (->> (chain/execute (assoc ctx
                            :event {:event/type :event.type/update
                                    :message    {:chat {:id 222}
                                                 :text "zzzzz"}}))
           :api.telegram/dispatch)
      => [{:method  :sendMessage
           :chat_id 222
           :text    "404"}])))
