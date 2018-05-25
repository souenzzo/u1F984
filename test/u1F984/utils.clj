(ns u1F984.utils
  (:require [io.pedestal.interceptor.chain :as chain]))

(defn dispatch-event
  [ctx event]
  (chain/execute (assoc ctx :event event)))
