#!/usr/bin/env bb

(load-file "util.clj")

(defn parse-http-info
  [line]
  (when-some [info (re-find #"(http.*)\s\[(\d+)\]" line)]
    {:host (get-uri-domain (second info))
     :hash (last info)}))

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
                      (take keep-num)
                      (map :host))))))

(comment
  (defn hamming-distance [str1 str2]
    (count (filter #(not= (first %) (second %)) (map vector str1 str2))))

  (hamming-distance "17399485081927508932" "16202653481372169916")

  (def rs (run-httpx "./paypal/venn2.txt"))

  (filter-sim-domains 5 rs)

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
                  ;; 保留内容相似的前k的域名
                  :keep {:default 5
                         :alias :k
                         :coerce :long
                         }
                  :help {:coerce :boolean
                         :alias :h}})

(defn print-opts
  []
  (println)
  (println "check domain http probe:")
  (println "  options:")
  (println (cli/format-opts {:spec cli-options})))

(defn -main [& args]
  (let [{:keys [path out keep help]} (cli/parse-opts args {:spec cli-options})]
    (when help
      (print-opts)
      (System/exit 0))

    (when-not (fs/exists? out)
      (fs/create-dirs out))

    (doseq [f1 (-> (fs/expand-home path)
                   (fs/glob "*.txt"))]
      (println "http probe process" (str f1))
      (let [hosts (->> (run-httpx f1)
                       (filter-sim-domains keep))]
        (when (seq hosts)
          (spit
           (str (fs/file out (fs/file-name f1)))
           (str/join "\n" (set hosts))))))))

(try
  (apply -main *command-line-args*)
  (catch Exception e
    (println "check domain http probe")
    (println "error: " (ex-message e) e)
    (print-opts)
    (System/exit 1)))
