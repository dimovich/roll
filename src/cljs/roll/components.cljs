(ns roll.components
  (:require [taoensso.timbre :refer [info]]
            [reagent.core :as r]))



(defn editable [tag {:keys [state error on-change] :as props}]
  [tag
   (-> props
       (dissoc :state :error)
       (merge (cond-> {:value @state
                       :on-change #(do (reset! state (.. % -target -value))
                                       (when on-change (on-change)))}
                error (merge {:class :error}))))])





(defn search-tag [opts tag]
  [:div.search-tag opts (str "#" tag)])



(defn search-box [{:keys [update-tags tags]}]
  (let [text (r/atom nil)
        tags (r/atom (or tags []))
        add-tag (fn [tag]
                  (when-not (empty? tag)
                    (swap! tags conj tag)
                    (update-tags @tags)))
               
        remove-tag (fn [idx]
                     (swap! tags #(vec (concat
                                        (subvec % 0 idx)
                                        (subvec % (inc idx)))))
                     (update-tags @tags))

        pop-tag (fn []
                  (swap! tags #(vec (butlast %)))
                  (update-tags @tags))]

    (fn []
      [:div.search-box
       
       ;; Search input
       [editable :input {:state text
                         :auto-focus true
                         :class :search-input-field
                         :on-key-down (fn [e]
                                        (condp = (.. e -key)
                                          " "  (do (add-tag @text)
                                                   (reset! text nil)
                                                   (.preventDefault e))
                                          
                                          "Enter"     (do
                                                        (add-tag @text)
                                                        (reset! text nil))
                                          "Backspace" (when (empty? @text)
                                                        (pop-tag))
                                          false))}]
       ;; Tags
       (reverse
        (map-indexed
         (fn [idx tag]
           [search-tag {:on-click #(remove-tag idx)
                        :key idx}
            tag])
         @tags))])))

