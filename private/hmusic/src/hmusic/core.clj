(ns hmusic.core
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as ch])
  (:import java.util.Base64)
  (:gen-class))

(def spotify-credential (slurp "credential"))
(def rss-header "<?xml version=\"1.0\" ?>\n<rss version=\"2.0\">\n<channel>\n")
(def rss-footer "</channel>\n</rss>")
(def rss-channel-info "  <title>Recomendações Musicais do Horacio</title>\n  <link>musica.yagoteixeira.xyz</link>\n  <description></description>\n")
(def rss-filename "./rss.xml")
(def json-filename "./tracks.json")

(defn encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(defn spotify-token []
  (-> "https://accounts.spotify.com/api/token"
      (client/post  {:headers {"Authorization", (str "Basic "(encode spotify-credential))}
                     :content-type :x-www-form-urlencoded
                     :body "grant_type=client_credentials"})
      :body
      (str/split #",")
      first
      (str/split #":")
      second
      (str/replace "\"" "")))

(defn spotify-track-info [token track-id]
  (-> "https://api.spotify.com/v1/tracks/"
      (str track-id)
       (client/get  {:headers {"Authorization", (str "Bearer " token)}})
       :body
       (ch/parse-string true)))

(defn spotify-track-data [info]
  {:name (:name info)
   :artist (:name (first (:artists info)))
   :image (:url (first (:images (:album info))))
   :url (:href info)})

(defn rss-items [items]
  (str/join ""
   (map
    (fn [item]
      (str "  <item>\n    <title>" (:artist item) " - " (:name item) "</title>\n    <link>" (:url item) "</link>\n    <description></description>\n  </item>\n"))
    items)))

(defn gen-rss-content [items]
  (str rss-header rss-channel-info items rss-footer))

(defn -main
  ([& args]
   (let [token (spotify-token)
         track-info (spotify-track-data (spotify-track-info token (first args)))
         old-tracks (ch/parse-stream (clojure.java.io/reader json-filename) true)
         all-tracks (into old-tracks (list track-info))]
     
     (with-open [w (clojure.java.io/writer json-filename)]
       (.write w (ch/generate-string all-tracks)))
     
     (with-open [w (clojure.java.io/writer rss-filename)]
       (.write w (gen-rss-content (rss-items all-tracks))))))
  
  ([]
   (let [all-tracks (ch/parse-stream (clojure.java.io/reader json-filename) true)]
     (with-open [w (clojure.java.io/writer rss-filename)]
       (.write w (gen-rss-content (rss-items all-tracks)))))))
