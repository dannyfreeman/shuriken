(ns shuriken.associative
  "### Operations on associative structures")

(defn- flatten-keys* [acc ks m]
  (if (and (map? m)
           (not (empty? m)))
    (reduce into
            (map (fn [[k v]]
                   (flatten-keys* acc (conj ks k) v))
                 m))
    (assoc acc ks m)))

(defn flatten-keys
  "Transforms a nested map into a map where keys are paths through
  the original map and values are leafs these paths lead to.

  ```clojure
  (flatten-keys {:a {:b {:c :x
                         :d :y}}})
  => {[:a :b :c] :x
      [:a :b :d] :y}
  ```
  
      (flatten-keys {:a {:b {:c :x
                             :d :y}}})
      => {[:a :b :c] :x
          [:a :b :d] :y}"
  [m]
  (if (empty? m)
    m
    (flatten-keys* {} [] m)))

(defn deflatten-keys
  "Builds a nested map out of a map obtained from [[flatten-keys]].
  
  ```clojure
  (deflatten-keys {[:a :b :c] :x
                   [:a :b :d] :y})
  => {:a {:b {:c :x
              :d :y}}}
  ```"
  [m]
  (reduce (fn [acc [ks v]]
            (update-in acc ks
                       (fn [x]
                         (if x
                           (if (every? map? [x v])
                             (merge v x)
                             x)
                           v))))
          {} m))

(defn- deep-merge* [m1f & [m2 & more]]
  (if (not m2)
    m1f
    (let [m2f (flatten-keys m2)
          m1m2f (merge m1f m2f)]
      (apply deep-merge* m1m2f (or more [])))))

(defn deep-merge
  "Deep merges two or more nested maps.
  
  ```clojure
  (deep-merge {:x {:a :a  :b :b  :c :c}}
              {:x {:a :aa :b :bb}}
              {:x {:a :aaa}})

  => {:x {:a :aaa  :b :bb  :c :c}}
  ```"
  [m & more]
  (deflatten-keys (apply deep-merge*
                         (flatten-keys m)
                         more)))

(defn- raise-error-index-strategy [key entries]
  (if (not= 1 (count entries))
    (throw
      (ex-info (pr-str "Can't index key " key " because of duplicate "
                       "entries " (map :name entries))
               {:type :index-by-duplicate-entries
                :entries (map :name entries)
                :key key}))
    (first entries)))

(defn index-by
  "Like `group-by` except it applies a strategy to each grouped
  collection.
  A strategy is a function with signature `key, entries) -> entry`
  where `entry` is the one that will be indexed.
  The default strategy asserts there is only one entry for the given
  key and returns it.
  
  ```clojure
  (def ms [{:a 1 :b 2} {:a 3 :b 4} {:a 5 :b 4}])

  (index-by :a ms)
  => {1 {:a 1 :b 2}
      3 {:a 3 :b 4}
      5 {:a 5 :b 4}}

  (index-by :b ms)
  => ; clojure.lang.ExceptionInfo (Duplicate entries for key 4)

  (index-by :b (fn [key entries]
               (last entries))
          ms)
  => {2 {:a 1 :b 2}
      4 {:a 5 :b 4}}
  ```"
  ([f coll]
   (index-by f raise-error-index-strategy coll))
  ([f strategy coll]
   (->> (group-by f coll)
        (map (fn [[k vs]]
               [k (strategy k vs)]))
        (into {}))))

(def unindex
  "Alias of `vals`."
  vals)

(defn merge-with-plan
  "Like `merge-with` except that the combination fn of a specific pair
  of entries is determined by looking up their key in `plan`. If not
  found, falls back to the function found under key `:else` or if not
  provided to a function that returns the value in the right-most map,
  thus providing the behavior of `merge`."
  [plan & maps]
    (when (some identity maps)
      (let [merge-entry (fn [m e]
                          (let [k (key e) v (val e)]
                            (if (contains? m k)
                              (let [else-f (get plan :else #(identity %2))
                                    f (get plan k else-f)]
                                (assoc m k (f (get m k) v)))
                              (assoc m k v))))
            merge2 (fn [m1 m2]
                     (reduce merge-entry (or m1 {}) (seq m2)))]
        (reduce merge2 maps))))
