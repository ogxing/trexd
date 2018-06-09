(ns trexd.setting
  (:require [reagent.core :as r]
            [soda-ash.core :as sa]
            [ajax.core :refer [GET POST]]
            [trexd.util :as u])) 

(defn setup-setting-page []
  (let [pubkey       (r/atom "")
        prikey       (r/atom "")
        email-user   (r/atom "")
        email-pass   (r/atom "")
        email-target (r/atom "")
        server-pass  (r/atom "")
        current-pass (r/atom "")
        save         (r/atom "save")]
    (fn []
      (let [active (r/atom (and
                                (not (u/blank? @current-pass))
                                (or
                                   (not (u/blank? @server-pass))
                                   (not (u/blank? @email-target))
                                   (and (not (u/blank? @pubkey)) (not (u/blank? @prikey)))
                                   (and (not (u/blank? @email-user)) (not (u/blank? @email-pass))))))]
        [sa/SegmentGroup
         [sa/Segment
          [sa/Header {:as "h3"} "Setting"]
          [:div [:b "New Bittrex Public/Private key: "]]
          [:div [sa/Input {:placeholder "New Public Key" :value @pubkey :onChange #(reset! pubkey (.-value %2))}]]
          [:div [sa/Input {:placeholder "New Private Key" :value @prikey :onChange #(reset! prikey (.-value %2))}]]
          [:div [:b "Notification Email: "]]
          [:div [sa/Input {:placeholder "Sender Address" :value @email-user :onChange #(reset! email-user (.-value %2))}]]
          [:div [sa/Input {:placeholder "Sender Password" :value @email-pass :onChange #(reset! email-pass (.-value %2))}]]
          [:div [sa/Input {:placeholder "Receiver Address" :value @email-target :onChange #(reset! email-target (.-value %2))}]]
          [:div [:b "Server-password : "]]
          [:div [sa/Input {:placeholder "Password" :value @server-pass :onChange #(reset! server-pass (.-value %2))}]]
          [:div [:b "*Current-password : "]]
          [:div [sa/Input {:placeholder "*Required* Password" :value @current-pass :onChange #(reset! current-pass (.-value %2))}]]
          [:div [sa/Button {:toggle   true 
                            :active   @active
                            :disabled (or (not @active) (not= @save "save"))
                            :loading  (= @save "saving")
                            :onClick  (fn []
                                        (reset! save "saving")
                                        (POST "/"
                                          {:params (str {:cookies "cookies"
                                                         :data    (u/func->string "update-setting" ["admin"
                                                                                                    @pubkey 
                                                                                                    @prikey 
                                                                                                    @email-user
                                                                                                    @email-pass
                                                                                                    @email-target
                                                                                                    @server-pass
                                                                                                    @current-pass])})
                                           :handler #(reset! save "done")
                                           :error-handler #(println %1)}))}
                 (cond
                   (= @save "save")   "Update"
                   (= @save "saving") "Updating"
                   (= @save "done")   "Done")]]]]))))

