
(require '[lambdaisland.uri :refer [uri join]])

(def get-uri-domain (comp :host uri))
