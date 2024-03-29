(ns carlisle.listeners.interaction
  (:gen-class)
  (:use [carlisle.config :only [config]] 
        [carlisle.logging :only [log-message]]
        [carlisle.commands.ask]
        [carlisle.commands.info]
        [carlisle.commands.move]
        [carlisle.commands.mtg]
        [carlisle.commands.repl]
        [carlisle.commands.role-signup]
        [carlisle.commands.roll]
        [carlisle.commands.warframe]
        [carlisle.utils])
  (:import [net.dv8tion.jda.api.events.message MessageReceivedEvent]
           [net.dv8tion.jda.api.hooks ListenerAdapter]
           [net.dv8tion.jda.api.interactions InteractionType]
	   [net.dv8tion.jda.api.interactions.components.buttons Button])
  (:require [clojure.tools.logging :as log]))

;; TODO: seperate interaction types out
;; 

(def command-listener
      (proxy [ListenerAdapter] []
      
        (onSlashCommandInteraction [event]
          (do (log/info (str "recieved " (.getCommandType event) " command " (.getName event) " from " (.. event getUser getAsTag)))
          
              (try
                (case (.getName event)
                  "ask"              (ask-command event)
                  "info"             (info-command event)
                  "move"             (move-command event)
                  "mtg"              (mtg-command event)
                  "role-signup"      (role-signup-command event)
                  "roll"             (roll-command event)
                  "warframe"         (warframe-command event))
            
                (catch Exception e  
                  (println (.getStackTrace e))
                  (.. event
                      (reply (format "Something went wrong!%nClick one of the links below, describe what you were trying to do, and provide this error code: `%s%n%s`"
                                     (.getCause e)
                                     (.getMessage e)))
                      (addActionRow #{(Button/link (str "https://discord.com/invite/" (config :server-invite)) 
                                                   "Join the support server")
                                      (Button/link (str (config :repo) "/issues/new") 
                                                   "Leave an issue on GitHub")})
                      (setEphemeral true)
                      (queue))))))))
