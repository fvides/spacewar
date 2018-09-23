(ns vector-test
  (:require [midje.sweet :refer :all]
            [spacewar.vector :as v]
            [midje.experimental :refer [for-all]]
            [clojure.spec.alpha :as s]))

(facts
  "about vectors"
  (fact
    "vector add"
    (for-all
      [[x1 y1 :as v1] (s/gen ::v/vector)
       [x2 y2 :as v2] (s/gen ::v/vector)]
      (let [sum (v/add v1 v2)]
        sum => #(s/valid? ::v/vector %)
        sum => [(+ x1 x2) (+ y1 y2)]
        )))

  (fact
    "from-angular properties"
    (for-all
      [angle (s/gen ::v/number)
       length (s/gen ::v/number)]
      (v/from-angular length angle) => #(s/valid? ::v/vector %)))


  )