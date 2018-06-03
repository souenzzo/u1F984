(ns u1F984.core-test
  (:require [clojure.test :refer [deftest]]
            [midje.sweet :refer :all]
            [u1F984.core :as core]
            [u1F984.telegram :as telegram]
            [clojure.string :as string]
            [io.pedestal.interceptor.chain :as chain]))


(deftest my-first-test
  (let [ctx (core/make-ctx)]
    (fact
      (->> (chain/execute (assoc ctx
                            :event {:event/type              :event.type/update
                                    :telegram.update/id      123
                                    :telegram.update/message {:telegram.message/id   194
                                                              :telegram.message/chat {:telegram.chat/id 222}
                                                              :telegram.message/text "alpha"}}))
           :api.telegram/dispatch)
      => `[{(telegram/received ~{::telegram/message_id 194})
            [{(telegram/send-message ~{::telegram/chat_id 222
                                       ::telegram/text    (string/join "\n" ["latin small letter alpha: ɑ (593)"
                                                                             "latin small letter turned alpha: ɒ (594)"
                                                                             "greek capital letter alpha with tonos: Ά (902)"])})
              [(telegram/sent ~{::telegram/message_id 194})]}]}])
    (fact
      (->> (chain/execute (assoc ctx
                            :event {:event/type              :event.type/update
                                    :telegram.update/id      123
                                    :telegram.update/message {:telegram.message/id   194
                                                              :telegram.message/chat {:telegram.chat/id 222}
                                                              :telegram.message/text "zzzzzzzz"}}))
           :api.telegram/dispatch)
      => `[{(telegram/received ~{::telegram/message_id 194})
            [{(telegram/send-message ~{::telegram/chat_id 222
                                       ::telegram/text    "404"})
              [(telegram/sent ~{::telegram/message_id 194})]}]}])))
