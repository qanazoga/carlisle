(ns carlisle.repl.command
  (:gen-class)
  (:use [carlisle.config :only [config]] 
        [carlisle.utils]
        [clojure.java.javadoc]
        [clojure.java.shell]
        [clojure.repl])
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml])
  (:import [java.io ByteArrayOutputStream PrintStream PrintWriter OutputStreamWriter]
           [net.dv8tion.jda.api.events.message MessageReceivedEvent]
           [net.dv8tion.jda.api.entities ChannelType Message$MentionType]
           [net.dv8tion.jda.api.utils MarkdownSanitizer]))

;; Mostly here for repl sessions and debugging
;; being able to look at javadocs quickly is real nice
(add-remote-javadoc "net.dv8tion.jda." "https://ci.dv8tion.net/job/JDA/javadoc/")

;; we need this to expose things to eval
;; when we move eval command to it's own file we should take this too
(declare ^:dynamic event)
(declare ^:dynamic bot)
(declare ^:dynamic author)
(declare ^:dynamic channel)
(declare ^:dynamic msg)
(declare ^:dynamic txt)
(declare ^:dynamic guild)
(declare ^:dynamic *last*)

(defn reply 
  "shortcut to reply in current channel
  don't use outside of an eval command" 
  [msg]
  (let [fmt (if (<= 2000 (count msg))
              (if (or (str/starts-with? "```" msg) (str/starts-with? "```clj" msg))
                (trunc msg 2000)
                (str (trunc msg 1994) "...```"))
              msg)]
    (.queue (.. channel (sendMessage fmt)))
    fmt))

(defn reply-embed
  [embed]
  (.. channel (sendMessage embed) queue))

(defn shell 
  "shortcut to `sh` but with pretty formatting"
  [commands]
  (let [{code :exit sysout :out syserr :err} (apply sh (str/split commands #" "))
        out (if-not (str/blank? sysout) 
              sysout
              "")
        err (if-not (str/blank? syserr) 
              syserr
              "")]
    (println out err)))

(defn restart!
  "updates and restarts the bot, use :update? false to just restart"
  [& {:keys [update?]
      :or {update? true}}]
  (let [result (if update? 
                 (format "👋%n```bash%n%s```"
                         (:out (sh "git" "pull")))
                 "👋")]  
    (async/thread    
      (.. channel (sendMessage result) queue)
      (Thread/sleep 2500)
      (.shutdown bot)
      (Thread/sleep 2500)
      (System/exit 0)))
  "restarting...")

(defn safe-to-eval? 
  "Ensures that an eval is intended, and it is sent by owner"
  [event]
  (let [guild (if (.. event (isFromType ChannelType/TEXT))
                         (.. event getGuild)
                         nil)
        msg (.. event getMessage)
        names [(if guild (.. guild getBotRole getId))
               (.. event getJDA getSelfUser getId)]]

    (println names)
    
    (and 
     
     (.. msg (isMentioned
              (.. event getJDA getSelfUser)
              (into-array Message$MentionType [Message$MentionType/USER Message$MentionType/ROLE])))

     (-> (.getContentRaw msg)
         (str/split #" ")
         (first)
         (str/replace #"\D" ""))

     (=
      (.. event getAuthor getId)
      (config :owner)))))

(defn- eval-to-map [txt]
  (with-out-result-map 
    (try (eval (read-string  (format "(do %s)"( MarkdownSanitizer/sanitize txt))))
         (catch Exception e (format "%nException: %s%nCause: %s"
                                    (.getMessage e)
                                    (.getCause e))))))

(defn- format-response [out-result-map]
  (let [result (out-result-map :result)
        out (out-result-map :out)]
    (str/join "\n"
              [(->> [(str result) "nil"]
                    (filter #(not (str/blank? %)))
                    (first)
                    (format "Return: ```clj%n%s```"))
               (if (str/blank? out)
                 ""
                 (format "Out: ```bash%n%s```" out))])))
  
(defn eval-command 
  "Evaluate arbitrary code.
  The following variables have been assigned for your convenience:
  event   - The MessageReceivedEvent
  bot     - The bot
  author  - You
  channel - The channel the command was sent in
  msg     - The Message object
  txt     - The message, minus the !e
  guild   - The guild the command was sent in
  *last*  - The result of the last evaluation"
  [_event] 
  (->> (binding [*ns* (find-ns 'carlisle.repl.command)
                 event _event
                 bot (.. _event getJDA)
                 author (.. _event getAuthor)
                 channel (.. _event getChannel)
                 msg (.. _event getMessage)
                 txt (str/replace-first (.. _event getMessage getContentRaw) 
                                        #"^\S*\s"
                                        "")
                 guild (if (.. _event (isFromType ChannelType/TEXT))
                         (.. _event getGuild)
                         nil)]
         (println txt)
         (let [result-map (eval-to-map txt)
               result (result-map :result)
               out (result-map :out)
               response (format-response result-map)]
           
           (reply response)
           result))
       (def ^:dynamic *last*)))
