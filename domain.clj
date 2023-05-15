
(defn parse-public-suffix-list []
  (->> (slurp "https://publicsuffix.org/list/public_suffix_list.dat")
       str/split-lines
       (remove #(or (str/starts-with? % "//") (= % "")))))

(defn format-psl-rules
  [psls]
  (reduce (fn [res v]
            (cond
              (str/starts-with? v "*")
              (let [comps (str/split v #"\.")]
                (update res :all  #(conj % (str/join "." (rest comps)))))

              (str/starts-with? v "!")
              (update res :exclude  #(conj % (subs v 1)) )

              :else
              (update res :match  #(conj % v) )))
          {:exclude #{}
           :all #{}
           :match #{}}
          psls))

(defonce psl-rules (-> (parse-public-suffix-list)
                       (format-psl-rules)))

(defn get-tld
  "获取域名的公共后缀 public suffix"
  [domain]
  (loop [dc (str/split domain #"\.")]
    (let [top-d (str/join "." dc)
          second-d (str/join "." (rest dc))]
      (cond
        (empty? dc) nil

        (and ((:match psl-rules) top-d)
             (not ((:exclude psl-rules) top-d)))
        top-d

        ((:all psl-rules) second-d)
        (if ((:exclude psl-rules) top-d)
          second-d
          top-d)

        :else
        (recur (rest dc))))))

(comment

  (assert (= (get-tld "www.baidu.com") "com"))
  (assert (= (get-tld "example.my-load-balancer.elb.amazonaws.com") "my-load-balancer.elb.amazonaws.com"))
  (assert (= (get-tld "example.my-load-balancer.elb.amazonaws.com") "my-load-balancer.elb.amazonaws.com"))
  (assert (= (get-tld "www.city.kawasaki.jp") "kawasaki.jp"))
  (assert (= (get-tld "b.d.c.a.test.ck") "test.ck"))
  (assert (= (get-tld "a.www.ck") "ck"))
  (assert (= (get-tld "www.ck") "ck"))
  (assert (= (get-tld "com") "com"))

  )

(defn get-tpd
  "获取一个域名的顶级私有域（Top Private Domain）,
  如果是public suffix，抛出异常"
  [domain]
  (when-let [tld (get-tld domain)]
    (when (= domain tld)
      (throw (ex-info "public suffix" {:domain domain})))
    (let [prefix (subs domain 0 (- (count domain)
                                   (count tld)))
          ps (str/split prefix #"\.")]
      (str (last ps) "." tld))))

(comment
  (assert (= (get-tpd "www.baidu.com") "baidu.com"))
  (assert (= (get-tpd "www.storage.googleapis.com") "storage.googleapis.com"))
  (assert (= (get-tpd "www.cn.google.com") "google.com"))
  (assert (= (get-tpd "b.example.my-load-balancer.elb.amazonaws.com") "example.my-load-balancer.elb.amazonaws.com"))
  (assert (= (get-tpd "example.my-load-balancer.elb.amazonaws.com") "example.my-load-balancer.elb.amazonaws.com"))
  (assert (= (try (get-tpd "my-load-balancer.elb.amazonaws.com" )
                  (catch Exception _ :exp)) :exp))
  (assert (= (get-tpd "a.test.ck") "a.test.ck"))
  (assert (= (get-tpd "c.a.test.ck") "a.test.ck"))
  (assert (= (get-tpd "b.d.c.a.test.ck") "a.test.ck"))
  (assert (= (get-tpd "a.www.ck") "www.ck"))
  (assert (= (get-tpd "www.ck") "www.ck"))
  )
