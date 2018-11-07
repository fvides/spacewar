(ns spacewar.game-logic.bases
  (:require [clojure.spec.alpha :as s]
            [spacewar.geometry :refer :all]
            [spacewar.game-logic.config :refer :all]))

(s/def ::x number?)
(s/def ::y number?)
(s/def ::type #{:antimatter-factory :dilithium-factory :weapon-factory})
(s/def ::antimatter number?)
(s/def ::dilithium number?)
(s/def ::kinetics int?)
(s/def ::torpedos int?)
(s/def ::age number?)
(s/def ::transport-readiness number?)

(s/def ::base (s/keys :req-un [::x ::y ::type ::antimatter ::dilithium ::age ::transport-readiness]
                      :opt-un [::kinetics ::torpedos]))
(s/def ::bases (s/coll-of ::base))

(defn- random-base-type []
  (nth [:antimatter-factory :weapon-factory :dilithium-factory]
       (rand-int 3)))

(defn make-base [[x y] type]
  {:x x
   :y y
   :age 0
   :type type
   :antimatter 0
   :dilithium 0
   :torpedos 0
   :kinetics 0
   :transport-readiness 0})

(s/def ::velocity (s/tuple number? number?))
(s/def ::commodity #{:antimatter :dilithium})
(s/def ::amount number?)
(s/def ::destination (s/tuple number? number?))

(s/def ::transport (s/keys :req-un [::x ::y ::velocity ::commodity ::amount ::destination]))
(s/def ::transports (s/coll-of ::transport))

(defn make-transport [commodity amount destination]
  {:x 0
   :y 0
   :velocity [0 0]
   :commodity commodity
   :amount amount
   :destination destination})

(defn make-random-base []
  (let [x (int (rand known-space-x))
        y (int (rand known-space-y))]
    (make-base [x y] (random-base-type))))

(defn- age-base [ms base]
  (let [age (:age base)
        age (min base-maturity-age (+ age ms))]
    (assoc base :age age)))

(defn age-bases [ms bases]
  (map #(age-base ms %) bases))

(defn- manufacture [base ms commodity rate maximum]
  (let [inventory (commodity base)
        deficit (max 0 (- maximum inventory))
        production (* ms rate)
        increase (min deficit production)]
    (update base commodity + increase)))

(defn- update-base-manufacturing [ms base]
  (if (>= (:age base) base-maturity-age)
    (condp = (:type base)
      :antimatter-factory (manufacture base ms :antimatter antimatter-factory-production-rate base-antimatter-maximum)
      :dilithium-factory (manufacture base ms :dilithium dilithium-factory-production-rate base-dilithium-maximum)
      :weapon-factory (-> base
                          (manufacture ms :torpedos weapon-factory-torpedo-production-rate base-torpedos-maximum)
                          (manufacture ms :kinetics weapon-factory-kinetic-production-rate base-kinetics-maximum)))
    base))

(defn update-bases-manufacturing [ms bases]
  (map #(update-base-manufacturing ms %) bases)
  )

(defn update-transport-readiness-for [ms base]
  (let [readiness (:transport-readiness base)
        deficit (- transport-ready readiness)
        adjustment (min deficit ms)]
    (update base :transport-readiness + adjustment)))

(defn- update-transport-readiness [ms bases]
  (map #(update-transport-readiness-for ms %) bases))

(defn- transportable-target? [source-base target-base]
  (let [dist (distance [(:x source-base) (:y source-base)]
                       [(:x target-base) (:y target-base)])]
    (and (< dist trade-route-limit) (> dist 0))))

(defn find-transport-targets-for [base bases]
  (filter #(transportable-target? base %) bases))

(defn transport-ready? [base]
  (= (:transport-readiness base) transport-ready))

(defn- sufficient-antimatter [type]
  (condp = type
    :antimatter-factory antimatter-factory-sufficient-antimatter
    :dilithium-factory dilithium-factory-sufficient-antimatter
    :weapon-factory weapon-factory-sufficient-antimatter))

(defn- antimatter-reserve [type]
  (condp = type
      :antimatter-factory antimatter-factory-antimatter-reserve
      :dilithium-factory dilithium-factory-antimatter-reserve
      :weapon-factory weapon-factory-antimatter-reserve))

(defn- sufficient-dilithium [type]
  (condp = type
    :antimatter-factory antimatter-factory-sufficient-dilithium
    :dilithium-factory dilithium-factory-sufficient-dilithium
    :weapon-factory weapon-factory-sufficient-dilithium))

(defn- dilithium-reserve [type]
  (condp = type
      :antimatter-factory antimatter-factory-dilithium-reserve
      :dilithium-factory dilithium-factory-dilithium-reserve
      :weapon-factory weapon-factory-dilithium-reserve))

(defn- get-promised-commodity [commodity dest transports]
  (let [transports (filter #(= commodity (:commodity %)) transports)
        transports (filter #(= [(:x dest) (:y dest)] (:destination %)) transports)]
    (reduce + (map :amount transports))))

(defn should-transport-antimatter? [source dest transports]
  (let [source-type (:type source)
        dest-type (:type dest)
        source-antimatter (:antimatter source)
        dest-antimatter (:antimatter dest)
        promised-antimatter (get-promised-commodity :antimatter dest transports)]
    (and
      (< (+ promised-antimatter dest-antimatter) (sufficient-antimatter dest-type))
      (>= source-antimatter (+ antimatter-cargo-size (antimatter-reserve source-type))))))

(defn should-transport-dilithium? [source dest transports]
  (let [source-type (:type source)
        dest-type (:type dest)
        source-dilithium (:dilithium source)
        dest-dilithium (:dilithium dest)
        promised-dilithium (get-promised-commodity :dilithium dest transports)]
    (and
      (< (+ promised-dilithium dest-dilithium) (sufficient-dilithium dest-type))
      (>= source-dilithium (+ dilithium-cargo-size (dilithium-reserve source-type))))))


(defn update-bases [ms world]
  (let [bases (:bases world)
        bases (->> bases
                   (age-bases ms)
                   (update-bases-manufacturing ms)
                   (update-transport-readiness ms))]
    (assoc world :bases bases)))



