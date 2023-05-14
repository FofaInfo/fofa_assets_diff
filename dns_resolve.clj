#!/usr/bin/env bb

(defn run-dnsx [file]
  (println :run-dnsx (str file))
  (some->> (shell/sh "dnsx" "-a" "-silent" "-resp" "-l" (str file))
           :out
           str/split-lines
           (map #(let [[domain ip] (str/split % #"\s+")]
                   (try
                     [domain (str/replace ip #"\[|\]" "")]
                     (catch Exception e
                       (println "process" domain ":" ip "error" (ex-message e))
                       nil))))
           (filter identity)
           seq ;; fix lazy-seq apply
           (apply map vector)))


(require '[babashka.cli :as cli])

(def cli-options {:path {:default "/tmp/paypal2"
                         :validate fs/directory?
                         :alias :p
                         :coerce fs/file
                         }
                  :help {:coerce :boolean
                         :alias :h}})

(defn print-opts
  []
  (println)
  (println "  options:")
  (println (cli/format-opts {:spec cli-options})))

(defn -main [& args]
  (let [{:keys [path help]} (cli/parse-opts args {:spec cli-options})]
    (when help
      (print-opts)
      (System/exit 0))
    (doseq [f1 (-> (fs/expand-home path)
                   (fs/glob "*.txt"))]
      (println "dns process" (str f1))
      (let [[hosts ips] (run-dnsx f1)]
        (spit
         (str (fs/strip-ext f1) ".json")
         (json/generate-string {:domain (fs/strip-ext (fs/file-name f1))
                                :hosts hosts
                                :ips ips}
                               {:pretty {:indent-arrays? true
                                         :indent-objects? true
                                         :line-break "\n"
                                         :indentation "  "
                                        }}))
        ))))

(try
  (apply -main *command-line-args*)
  (catch Exception e
    (println "domain dns resolver:")
    (println "error: " (ex-message e) e)
    (print-opts)
    (System/exit 1)))

