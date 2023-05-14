#!/usr/bin/env bb


(require '[lambdaisland.uri :refer [uri join]])

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

(def get-uri-domain (comp :host uri))

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


(defn read-chaos-infos
  [chaos-root]
  (-> (fs/expand-home chaos-root)
      (fs/glob "*.json")
      (->> (map #(-> (slurp (str %))
                     (json/decode  keyword))))))

;; 直接读取文件
(defn read-chaos-hosts
  [chaos-root]
  (-> (fs/expand-home chaos-root)
      (fs/glob "*.txt")
      (->> (map #(vector (fs/strip-ext (fs/file-name %))
                         (-> (slurp (str %))
                             str/split-lines
                             set)))
           (into {}))))

(defn diff-set
  [s1 s2]
  (let [ss1 (set s1)
        ss2 (set s2)]
    {:same (set/intersection ss1 ss2)
     :add (set/difference ss2 ss1)
     :miss (set/difference ss1 ss2)}))

(comment
  (diff-set ["test"] nil)
  (diff-set nil  ["test"])

  (diff-set '("t1" "test") ["test" "taa"])

  )

(require '[hiccup.core :refer [html]])

(defn generate-html [data-list]
  (html [:html
    [:head
     [:title "Diff Comparison Report"]
     [:style "
        table { 
          width: 100%;
          border-collapse: collapse;
          margin-bottom: 1rem;
          color: #363636;
          max-width: 100%;
          background-color: white;
          border-radius: 4px;
          box-shadow: 0 0.5em 1em -0.125em rgb(10 10 10 / 10%), 0 0px 0 1px rgb(10 10 10 / 2%);
          overflow: hidden;
          border-spacing: 0;
          font-size: 1rem;
          text-indent: initial;
          border-color: grey;
        }
        td, th { 
          border: 1px solid #dbdbdb;
          border-width: 0 0 1px;
          vertical-align: middle;
          padding: 0.5em 0.75em;
        }
        th { 
          color: #363636;
          text-align: left;
          border-width: 0 0 2px;
          border-color: #dbdbdb;
        }
        tr:nth-child(even) {
          background-color: #f2f2f2;
        }
        tr:hover {
          background-color: #ddd;
        }
        .add { color: green; } .miss { color: red; } .same { color: black; } 
        .data { display: none; }
      "]
     [:script {:src "https://ajax.aspnetcdn.com/ajax/jQuery/jquery-3.5.1.min.js"}]]
    [:body
     [:h1 "Diff Comparison Report"]
     [:table 
      [:thead
       [:tr 
        [:th "Diff"]
        [:th {:class "add"} "Add"]
        [:th {:class "same"} "Same"]
        [:th {:class "miss"} "Miss"]]]
      [:tbody
       (for [data data-list]
         (let [{:keys [title diff]} data
               {:keys [add same miss]} diff]
           (let [row-id (str title "-row")]
             [:tr {:class "row" :id row-id}
              [:td {:class "domain"}
               [:span.toggle-sign "+ "]
               (if (= title "root-domains")
                 [:h7 {:style "color: blue;"} "root-domains"]
                 title)]
              [:td {:style "color: green;"}
               [:div {:class "count-wrapper"}
                [:div {:class "count" :id (str title "-addCount")} (count add)]]
               [:div {:class "data-wrapper"}
                [:div {:class "data" :id (str title "-addData")}
                 (for [url add]
                   [:p [:a {:href (str "https://" url)
                            :rel "noopener noreferrer"
                            :onclick "(function(event) { event.preventDefault(); window.open(event.target.href, '_blank'); })(event);"
                            :target "_blank"
                            } url]])]]]
              [:td
               [:div {:class "count-wrapper"}
                [:div {:class "count" :id (str title "-sameCount")} (count same)]]
               [:div {:class "data-wrapper"}
                [:div {:class "data" :id (str title "-sameData")}
                 (for [url same]
                   [:p [:a {:href (str "https://" url)
                            :rel "noopener noreferrer"
                            :onclick "window.open(this.href, '_blank'); return false;"
                            :target "_blank"
                            } url]])]]]
              [:td {:style "color: red;"}
               [:div {:class "count-wrapper"}
                [:div {:class "count" :id (str title "-missCount")} (count miss)]]
               [:div {:class "data-wrapper"}
                [:div {:class "data" :id (str title "-missData")}
                 (for [url miss]
                   [:p [:a {:href (str "https://" url)
                            :rel "noopener noreferrer"
                            :onclick "window.open(this.href, '_blank'); return false;"
                            :target "_blank"
                            } url]])]]]])))]]
     [:script
      "$(document).ready(function(){
          var table = $('table');
          var tbody = table.find('tbody');
          var rows = tbody.find('.row');
          var sortedRows = rows.sort(function(a, b) {
            var diffA = $(a).find('.domain').text();
            var diffB = $(b).find('.domain').text();
            return diffA.localeCompare(diffB);
          });
          tbody.append(sortedRows);

        tbody.on('click', '.row', function(event) {
          var isLinkClicked = $(event.target).is('a');
          if (isLinkClicked) { return; };

          var row = $(this);
          row.find('.data').toggle();
          row.toggleClass('expanded');
          var sign = row.find('.toggle-sign');
          if (row.hasClass('expanded')) {
            sign.text('- ');
          } else {
            sign.text('+ ');
          }
         });


          $('th').click(function() {
            var index = $(this).index();
            var isAscending = true;
            if ($(this).hasClass('ascending')) {
              $(this).removeClass('ascending');
              $(this).addClass('descending');
              isAscending = false;
            } else {
              $(this).removeClass('descending');
              $(this).addClass('ascending');
            }
            var sortedRows = rows.sort(function(a, b) {
              var countA = parseInt($(a).find('.count').eq(index - 1).text());
              var countB = parseInt($(b).find('.count').eq(index - 1).text());
              if (isAscending) {
                return countA - countB;
              } else {
                return countB - countA;
              }
            });
            tbody.html(sortedRows);
          });

      });"]]]))


(require '[babashka.cli :as cli])

(def cli-options {:path {:default "/tmp/paypal2"
                         :validate fs/directory?
                         :alias :p
                         :coerce fs/file
                         }
                  :resolved-dns {:default false
                                 :alias :a
                                 :coerce :boolean}
                  :fofa-file {:default "/tmp/paypal.csv"
                              :validate fs/exists?
                              :alias :f
                              :coerce fs/file
                              }
                  :out-file {:default "/tmp/diff.html"
                             :alias :o
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
  (let [{:keys [path resolved-dns fofa-file out-file help]} (cli/parse-opts args {:spec cli-options})]
    (when help
      (print-opts)
      (System/exit 0))
    (let [chaos-hosts (if resolved-dns
                        (->> (read-chaos-infos path)
                             (map #(vector (:domain %) (set (:hosts %))))
                             (into {}))
                        (read-chaos-hosts path))
          fofa-hosts (parse-fofa-hosts fofa-file)
          fofa-root-domains (keys fofa-hosts)
          chaos-root-domains (keys chaos-hosts)
          all-domains (set (concat fofa-root-domains chaos-root-domains))]
      (->> all-domains
           (map (fn [d]
                  {:title d
                   :diff (diff-set (get fofa-hosts d) (get chaos-hosts d))}))
           (concat [{:title "root-domains"
                     :diff (diff-set fofa-root-domains chaos-root-domains)}])
           generate-html
           (spit out-file)))))

(try
  (apply -main *command-line-args*)
  (catch Exception e
    (println "fofa data diff:")
    (println "error: " (ex-message e) e)
    (print-opts)
    (System/exit 1)))
