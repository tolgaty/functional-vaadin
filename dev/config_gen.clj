;;
;; Copyright 2016 Prajna Inc. All Rights Reserved.
;;
;; This work is licensed under the Eclipse Public License v1.0 - https://www.eclipse.org/legal/epl-v10.html
;; Distrubition and use must be done under the terms of this license
;;

(ns config-gen
  "Development-time tools to automatically generate a table of option setters for a given set of Vaadin classes"
  (:require [clojure.string :as str])
  (:use [clojure.set]
        [clojure.java.io]
        [clojure.test])
  (:import (com.vaadin.ui
             Label Embedded Link MenuBar Upload Button Calendar GridLayout Accordion
             TabSheet VerticalSplitPanel HorizontalSplitPanel Slider TextField TextArea PasswordField CheckBox
             RichTextArea InlineDateField PopupDateField Table ComboBox TwinColSelect NativeSelect
             ListSelect OptionGroup Tree TreeTable Panel VerticalLayout HorizontalLayout FormLayout ProgressBar Window LoginForm)
           ))

(def configurable-classes
  [
   Label
   Embedded
   Link
   MenuBar
   Upload
   Button
   Calendar
   GridLayout
   Panel
   Window
   VerticalLayout
   HorizontalLayout
   FormLayout
   TabSheet
   Accordion
   VerticalSplitPanel
   HorizontalSplitPanel
   Slider
   TextField
   TextArea
   PasswordField
   ProgressBar
   CheckBox
   RichTextArea
   InlineDateField
   PopupDateField
   Table
   ComboBox
   TwinColSelect
   NativeSelect
   ListSelect
   OptionGroup
   Tree
   TreeTable
   LoginForm
   ])

(defn format-config-classes []
  (last
    (reduce #(let [old-str (last %1)
                   line-count (first %1)
                   new-str (if (> (quot (+ 1 (count %2) (count old-str)) 120) line-count)
                             (str old-str "\n      " %2 " ")
                             (str old-str %2 " "))]
              [(quot (count new-str) 120) new-str])
            [0 "com.vaadin.ui "]
            (map #(.getSimpleName %1) configurable-classes)))
  )

(def preamble
  (str
    "(ns functional-vaadin.config-table
  \"This is auto-generated by the functions in config-gen
  DO NOT EDIT\"
  (:import
    ("
    (format-config-classes)
    "
    )))

(def config-table
  {\n"))

(def postamble "})")

(defn extract-setters
  "Extract all setters of the form 'setXXX' from the configurable classes list. Return a set of tuples
  {:name XXX :argcount N}. Acc is a transient set"
  [acc cls]
  (let [setters (filter #(= (subs (:name %1) 0 3) "set")
                        (map (fn [m] {:name (.getName m) :argcount (.getParameterCount m)})
                             (.getMethods cls)))]
    (doseq [s setters]
      (conj! acc s)))
  acc)


(defn gen-config-table []
  (let [opt-list
        (sort #(compare (:name %1) (:name %2))
              (persistent!
                (reduce extract-setters (transient #{}) configurable-classes)))]
    (with-open [f (writer "src/functional_vaadin/config_table.clj")]
      (.write f preamble)
      (doseq [opt opt-list]
        (let [arg-count (:argcount opt)
              arg-string (str/join " "
                                   (map #(str "arg" %1) (range 0 arg-count)))
              opt-name (:name opt)]
          (.write f
                  (str "    [:" opt-name " " arg-count "] (fn [obj " arg-string "] (."
                       opt-name
                       " obj " arg-string "))\n"))))
      (.write f postamble))))

(defn all-setters [cls]
  (sort #(compare (first %1) (first %2)) (filter #(= (subs (first %1) 0 3) "set")
                                                 (map (fn [m] [(.getName m) (.getParameterCount m)])
                                                      (.getMethods cls)))))

(defn has-dup-setters? [cls]
  (let [setters (all-setters cls)]
    (not= (count setters) (count (set setters)))))

(defn find-dups [cls]
  (let [s (all-setters cls)]
    (letfn [(update-count
              [acc s]
              (update acc s #(if %1 (inc %1) 1)))]
      (filter #(> (last %1) 1) (reduce update-count {} (all-setters cls))))))