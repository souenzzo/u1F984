(ns u1F984.telegram-test
  (:require [clojure.test :refer [deftest]]
            [u1F984.telegram :as telegram]
            [midje.sweet :refer :all]))

(deftest ->type
  (let [message {:message_id 194,
                 :from       {:id         614
                              :is_bot     true
                              :first_name "My Name"
                              :username   "my-name"},
                 :chat       {:id         116
                              :first_name "Target"
                              :username   "target-name"
                              :type       "private"},
                 :date       1527638244,
                 :text       "Hello!"}]
    (fact
      (telegram/->type ::telegram/message message)
      => {:telegram.message/chat {:telegram.chat/first-name "Target"
                                  :telegram.chat/id         116
                                  :telegram.chat/type       :telegram.chat.type/private
                                  :telegram.user/username   "target-name"}
          :telegram.message/date #inst"2018-05-29T23:57:24.000-00:00"
          :telegram.message/from {:telegram.user/bot?       true
                                  :telegram.user/first-name "My Name"
                                  :telegram.user/id         614
                                  :telegram.user/username   "my-name"}
          :telegram.message/id   194})))
