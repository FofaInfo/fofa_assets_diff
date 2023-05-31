#!/usr/bin/env bb

(load-file "util.clj")

(defn get-parent-domain
  [domain]
  (->> (str/split domain #"\.")
       rest
       (str/join ".")))

(defn parse-http-info
  [line]
  (when-some [info (re-find #"(http.*)\s\[(\d+)\]" line)]
    (let [domain (get-uri-domain (second info))]
      {:host domain
       :parent (get-parent-domain domain)
       :hash (last info)})))

(comment
  (parse-http-info "https://apacheckouthy.beta.venmo.com [15074220298716777414]")

  )

(defn run-httpx [file]
  (println :run-httpx (str file))
  (some->> (shell/sh "httpx" "-silent" "-nc" "-hash" "simhash" "-l" (str file))
           :out
           str/split-lines
           (map parse-http-info)
           ))

(defn filter-sim-domains
  "过滤内容相似的域名,只保留指定数量的相似域名"
  [keep-num rs]
  (->> (group-by :hash rs)
       (mapcat (fn [[k vs]]
                 (->> (sort-by :host vs)
                      (take keep-num))))))

(defn filter-same-parent
  "相同子域的域名保留keep-num个"
  [keep-num rs]
  (->> (group-by :parent rs)
       (mapcat (fn [[p-domain vs]]
                 (filter-sim-domains keep-num vs)))))

(comment
  (defn hamming-distance [str1 str2]
    (count (filter #(not= (first %) (second %)) (map vector str1 str2))))

  (hamming-distance "17399485081927508932" "16202653481372169916")

  (def rs (run-httpx "./paypal/venn2.txt"))

  (filter-sim-domains 5 rs)

  (def rps (map #(assoc % :parent (get-parent-domain (:host %))) rs))

  (filter-same-parent 5 rps)

  )


(require '[babashka.cli :as cli])

(def cli-options {:path {:default "./paypal/chaos"
                         :validate fs/directory?
                         :alias :p
                         :coerce fs/file
                         }
                  :out {:default "./paypal/chaos-http"
                        :alias :o
                        :coerce fs/file
                        }
                  ;; 不过滤内容相同的域名
                  :no-filter {:default false
                              :alias :n
                              :coerce :boolean
                              }
                  ;; 保留parent相同的子域名, 默认为true,如果为false则过滤所有内容相似的子域名
                  :keep-parent {:default true
                                :alias :e
                                :coerce :boolean
                                }
                  ;; 保留内容相似的前k的域名, 如果:keep-parent为true，则每个层级的子域名，保留前k个相似内容的域名
                  :keep {:default 5
                         :alias :k
                         :coerce :long
                         }
                  :help {:coerce :boolean
                         :alias :h}})

(require '[clojure.tools.logging :as log])

(defn print-opts
  []
  (println)
  (println "check domain http probe:")
  (println "  options:")
  (println (cli/format-opts {:spec cli-options})))

(defn -main [& args]
  (let [{:keys [path out no-filter keep-parent keep help]} (cli/parse-opts args {:spec cli-options})]
    (when help
      (print-opts)
      (System/exit 0))

    (when-not (fs/exists? out)
      (fs/create-dirs out))

    (cond
      no-filter
      (log/info "Disable HTTP content similarity filter.")

      keep-parent
      (log/info "Keep" keep "domains with similar HTTP content for each parent domain.")

      (pos? keep)
      (log/info "Keep" keep "domains with similar HTTP content.")

      :else
      (log/info "Disable HTTP content similarity filter."))

    (doseq [f1 (-> (fs/expand-home path)
                   (fs/glob "*.txt"))]
      (println "http probe process" (str f1))
      (let [hosts (run-httpx f1)]
        (when (seq hosts)
          (let [hosts (->> (cond
                             no-filter hosts
                             keep-parent (filter-same-parent keep hosts)
                             (pos? keep) (filter-sim-domains keep hosts)
                             :else hosts)
                           (map :host))]
            (spit
             (str (fs/file out (fs/file-name f1)))
             (str/join "\n" (set hosts)))))))))

(try
  (apply -main *command-line-args*)
  (catch Exception e
    (println "check domain http probe")
    (println "error: " (ex-message e) e)
    (print-opts)
    (System/exit 1)))
