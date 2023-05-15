#!/usr/bin/env bb

(load-file "util.clj")

(defn run-httpx [file]
  (println :run-httpx (str file))
  (some->> (shell/sh "httpx" "-silent" "-l" (str file))
           :out
           str/split-lines
           (map get-uri-domain)))

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
                  :help {:coerce :boolean
                         :alias :h}})

(defn print-opts
  []
  (println)
  (println "check domain http probe:")
  (println "  options:")
  (println (cli/format-opts {:spec cli-options})))

(defn -main [& args]
  (let [{:keys [path out help]} (cli/parse-opts args {:spec cli-options})]
    (when help
      (print-opts)
      (System/exit 0))

    (when-not (fs/exists? out)
      (fs/create-dirs out))

    (doseq [f1 (-> (fs/expand-home path)
                   (fs/glob "*.txt"))]
      (println "http probe process" (str f1))
      (let [hosts (run-httpx f1)]
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
