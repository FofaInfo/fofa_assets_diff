#!/usr/bin/env bb


(defn read-bom-file
  "兼容BOM"
  [file]
  (let [content (slurp file :encoding "UTF-8")]
    (if (.startsWith content "\uFEFF")
      (-> content (.substring 1))
      content)))

(defn read-csv-headline
  [file]
  (let  [data (csv/read-csv (read-bom-file file))
         d-keys (map keyword (first data))]
    (mapv #(zipmap d-keys %) (rest data))))

(load-file "util.clj")

(defn parse-fofa-hosts
  [fofa-path]
  (->> (fs/expand-home fofa-path)
       str
       read-csv-headline
       (group-by :domain)
       (filter #(not= "" (first %)))
       (map (fn [[k v]] [k (->> v
                                (map (comp get-uri-domain :host))
                                (filter identity)
                                set
                                )]))
       (into {})))

(load-file "domain.clj")

(defn parse-fofa-domains
  [fofa-domain-file]
  (->> (fs/expand-home fofa-domain-file)
       str
       read-csv-headline
       (filter (comp seq :domain))
       (reduce  (fn [res {:keys [domain]}]
                  (update res
                          (get-tpd domain)
                          #(conj % domain))) {})))

(require '[babashka.cli :as cli])

(def cli-options {:out-dir {:default "./paypal/fofa/"
                            :alias :o
                            :coerce fs/file
                            }
                  :fofa-file {:default "./paypal/fofa/all.csv"
                              :validate fs/exists?
                              :alias :f
                              :coerce fs/file
                              }
                  :fofa-type {:default :all
                              :validate #{:all :domain}
                              :alias :t
                              :coerce keyword
                              }
                  :help {:coerce :boolean
                         :alias :h}})

(defn print-opts
  []
  (println)
  (println "format fofa domains:")
  (println "  options:")
  (println (cli/format-opts {:spec cli-options})))

(defn -main [& args]
  (let [{:keys [out-dir fofa-file fofa-type help]} (cli/parse-opts args {:spec cli-options})]
    (when help
      (print-opts)
      (System/exit 0))

    (when-not (fs/exists? out-dir)
      (fs/create-dirs out-dir))

    (let [hosts (case fofa-type
                  :all (parse-fofa-hosts fofa-file)
                  (parse-fofa-domains fofa-file))]
      (doseq [[root-domain domains] hosts]
        (when (seq domains)
          (spit (str (fs/file out-dir (str root-domain ".txt")))
                (str/join "\n" (set domains))))))))

(try
  (apply -main *command-line-args*)
  (catch Exception e
    (println "error: " (ex-message e) e)
    (print-opts)
    (System/exit 1)))
