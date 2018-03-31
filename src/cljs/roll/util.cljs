(ns roll.util)


(def mobile-breakpoint 768)


(defn match-media? [breakpoint]
  (as-> breakpoint $
    (str "(max-width: " $ "px)")
    (.matchMedia js/window $)
    (aget $ "matches")))



(defn window-size [ratom]
  (let [set-size!
        (fn []
          (let [mobile?      (match-media? mobile-breakpoint)
                inner-width  (some-> js/window .-innerWidth)
                client-width (some-> js/document .-body .-clientWidth)
                page-width   (or inner-width client-width)]

            (swap! ratom  assoc
                   :page-width page-width
                   :mobile? mobile?)))]
    (set-size!)
    (.addEventListener js/window "resize"
                       set-size!
                       true)))



(defn get-width [ratom]
  (let [page-width (get @ratom :page-width)]
    (max (min 900
              (- page-width 100))
         260)))



(defn get-height [ratom]
  (let [width (get-width ratom)]
    (/ width 2)))

