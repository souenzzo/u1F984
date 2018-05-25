(ns u1F984.core-test
  (:require [clojure.test :refer [deftest]]
            [midje.sweet :refer :all]
            [u1F984.utils :as utils]
            [u1F984.core :as core]))


(deftest my-first-test
  (let [ctx {}]
    (fact
      (->> {:event/type :event.type/update
            :update_id  123
            :message    {:message_id 1,
                         :from       {:id 321}
                         :chat       {:id 321}
                         :date       #inst"2018"
                         :text       "lambda"}}
           (utils/dispatch-event ctx)
           :api.telegram/dispatch)
      => (just #{(contains {:method  :sendMessage
                            :chat_id 116632598
                            :text    "λ"})}))))