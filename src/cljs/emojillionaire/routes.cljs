(ns emojillionaire.routes)

(def routes
  ["/" {"about" :about
        "code" :code
        "contact" :contact
        "sponsor" :sponsor
        "how-to-play" :how-to-play
        ["players/" :address] :player-profile
        true :home}])
