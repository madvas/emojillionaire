(ns emojillionaire.components.main-panel
  (:require
    [cljs-react-material-ui.core :refer [get-mui-theme color]]
    [cljs-react-material-ui.icons :as icons]
    [cljs-react-material-ui.reagent :as ui]
    [clojure.set :as set]
    [emojillionaire.components.about-page :refer [about-page]]
    [emojillionaire.components.code-page :refer [code-page]]
    [emojillionaire.components.contact-page :refer [contact-page]]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.home-page :refer [home-page]]
    [emojillionaire.components.how-to-play-page :refer [how-to-play-page]]
    [emojillionaire.components.inactive-state-page :refer [inactive-state-page]]
    [emojillionaire.components.layout :refer [grid row col]]
    [emojillionaire.components.player-profile-page :refer [player-profile-page]]
    [emojillionaire.components.sponsor-page :refer [sponsor-page]]
    [emojillionaire.styles :as st]
    [emojillionaire.utils :as u]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    ))

(def pages
  {:home home-page
   :player-profile player-profile-page
   :sponsor sponsor-page
   :code code-page
   :about about-page
   :how-to-play how-to-play-page
   :contact contact-page})

(def menu-pages
  [[:home "Home" icons/action-home]
   [:sponsor "Sponsor" icons/editor-monetization-on]
   [:how-to-play "How To Play" icons/action-help]
   [:about "About" icons/action-info]
   [:code "Code" icons/action-code]
   [:contact "Contact" icons/communication-contact-mail]])

(defn- menu-link [[route title icon]]
  [ui/list-item
   {:left-icon (icon)
    :href (u/path-for route)
    :key route} title])

(defn main-panel []
  (let [drawer-open? (subscribe [:db/drawer-open?])
        current-page (subscribe [:db/current-page])
        my-addresses (subscribe [:db/my-addresses])
        active-state? (subscribe [:contract/active-state?])
        snackbar (subscribe [:db/snackbar])]
    (fn []
      {:fluid true}
      [ui/mui-theme-provider
       {:mui-theme (get-mui-theme {:palette {:primary1-color (color :deep-purple500)}})}
       [:div
        [ui/app-bar {:title (r/as-element
                              [row {:middle "xs" :style {:height "100%"}}
                               [:a {:href (u/path-for :home)
                                    :style {:display :inherit}}
                                [:img {:src "/images/emojillionaire.svg"
                                       :style st/main-logo}]]])
                     :on-left-icon-button-touch-tap #(dispatch [:drawer/toggle])
                     :icon-element-right (r/as-element
                                           [row {:middle "xs"}
                                            [u/new-window-link "https://www.ethereum.org/"
                                             [:img {:src "/images/ethereum.svg"
                                                    :style st/eth-logo}]]])}]
        [ui/drawer {:open @drawer-open?
                    :docked false
                    :on-request-change #(dispatch [:drawer/toggle])}
         [ui/app-bar {:title (r/as-element [row {:middle "xs" :center "xs"}
                                            [col {:xs 12}
                                             (for [emoji-key [:wink :stuck-out-tongue-closed-eyes :heart-eyes]]
                                               ^{:key emoji-key}
                                               [emoji {:style {:margin-top 10}
                                                       :on-touch-tap #(dispatch [:drawer/toggle])} emoji-key 45 5])]])
                      :show-menu-icon-button false}]
         (menu-link (first menu-pages))
         [ui/list-item
          {:primary-toggles-nested-list true
           :initially-open true
           :left-icon (icons/action-account-balance-wallet)
           :nested-items (for [address @my-addresses]
                           (r/as-element
                             [ui/list-item
                              {:key address
                               :href (u/path-for :player-profile :address address)}
                              (u/truncate address 20)]))}
          "Accounts"]
         (for [menu-page (rest menu-pages)]
           (menu-link menu-page))]
        [grid {:fluid true
               :style st/main-grid}
         (if @active-state?
           (when-let [page (pages (:handler @current-page))]
             [page])
           [inactive-state-page])]
        [ui/snackbar (-> @snackbar
                       (set/rename-keys {:open? :open})
                       (update :message #(r/as-element %))
                       (update :action #(if % (r/as-element %) nil)))]]
       ])))