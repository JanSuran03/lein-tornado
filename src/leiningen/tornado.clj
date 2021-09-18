(ns leiningen.tornado
  (:require [me.raynes.fs :as fs]
            [leiningen.core.main :as lein]
            [clojure.java.io :as io]
            [leiningen.core.project :as project]
            [leiningen.core.eval :refer [eval-in-project]]
            [leiningen.help :as help]))

(defn- all-builds
  "Returns a sequence of Tornado builds."
  [project]
  (-> project :tornado :builds))

(defn- find-builds
  "For each of the given build ids, if each of the builds exists, returns a complete
  build map of the build. Otherwise, throws an exception."
  [project build-ids]
  (let [all-builds (all-builds project)]
    (for [build-id build-ids]
      (if-let [build (some (fn [{:keys [id] :as build}]
                             (when (= (name build-id) (name id))
                               build)) all-builds)]
        build
        (throw (Exception. (str "Unknown build id: " build-id)))))))

(defn- validate-builds
  "For each build, validates id, stylesheet and source paths. Throws an exception if some
  of the builds is not valid.."
  [project]
  (doseq [{:keys [id stylesheet source-paths compiler]} (all-builds project)
          :let [{:keys [output-to]} compiler]]
    (cond (not ((some-fn keyword? string?) id)) (throw
                                                  (Exception. (str "The build id must be a string or a keyword: " id)))
          (nil? source-paths) (throw
                                (Exception. (str "No source paths specified for a build: " (name id))))
          (nil? stylesheet) (throw
                              (Exception. (str "No stylesheet specified for a build: " (name id))))
          (not (symbol? stylesheet)) (throw
                                       (Exception. (str "Stylesheet value must be a symbol: " stylesheet " in a build: " (name id))))
          (nil? output-to) (throw
                             (Exception. (str "No specified output path (:output-to) for a compiled CSS build: " (name id)))))))

(defn- load-namespaces [stylesheets]
  `(require ~@(for [stylesheet stylesheets]
                `'~(-> stylesheet namespace symbol))))

(defn- create-output-dir-if-not-exists
  "If the output path directory does not exist, tries to create it. If it does not
  succeed, throws an exception."
  [{:keys [compiler]}]
  (let [output-to-dir (-> compiler :output-to
                          io/file
                          fs/absolute
                          fs/parent)]
    (when-not (fs/exists? output-to-dir)
      (when-not (fs/mkdirs output-to-dir)                   ;; Returns true on success, false on fail
        (lein/info "Failed creating a directory: " output-to-dir)
        (lein/abort)))))

(defn- compile-builds
  "Automatically recompiles modified builds when needed, or once if watch? is not truthy."
  [{:keys [tornado-source-paths]} builds watch?]
  (let [all-stylesheet-namespaces (mapv #(-> % :stylesheet symbol namespace) builds) ;;initial compilation of all stylesheets
        tornado-source-paths (vec tornado-source-paths)]
    `(let [modified-namespaces# (ns-tracker/ns-tracker ~tornado-source-paths)]
       (loop [modified-nss# ~all-stylesheet-namespaces]
         (when (seq modified-nss#)
           (println "Reloading modified namespaces...")
           (doseq [ns# modified-nss#]
             (require (symbol ns#) :reload))
           (println "Namespaces reloaded.")
           (try (doseq [build# ~builds]
                  (let [{stylesheet# :stylesheet
                         id#         :id
                         flags#      :compiler} build#]
                    (println "   Compiling build" (name id#) "...")
                    (compiler/css flags# stylesheet#)
                    (println "   Successful.")))
                (println "All builds were successfully recompiled.")
                (catch Exception e#
                  (println "Error: " (.getMessage e#)))))
         (when ~watch? nil
                       (Thread/sleep 500)
                       ;; A funciton to return a set of modified namespaces is called, called every 500 millieconds.
                       (recur (modified-namespaces#)))))))

(defn- run-compiler
  "Starts the compilation process."
  [project args watch?]
  (let [builds (if (seq args)
                 (find-builds project args)
                 (all-builds project))
        stylesheets (map :stylesheet builds)
        build-paths (mapcat :source-paths builds)
        modified-project (assoc project :tornado-source-paths build-paths
                                        :tornado-stylesheets stylesheets)
        required-nss (load-namespaces stylesheets)]
    (when (seq builds)
      (doseq [build builds] (create-output-dir-if-not-exists build))
      (lein/info "Compiling Tornado...")
      (eval-in-project modified-project
                       `(do ~required-nss
                            ~(compile-builds modified-project builds watch?))
                       '(require '[tornado.compiler :as compiler]
                                 '[ns-tracker.core :as ns-tracker])))))

(defn- once
  "Compiles Tornado stylesheets once. Args are optional specific builds to be compiled."
  [project args]
  (run-compiler project args false))

(defn- auto
  "Compiles Tornado stylesheets once and recompiles them whenever they are modified.
  Args are optional specific builds to be compiled."
  [project args]
  (run-compiler project args true))

(def ^:private tornado-profile {:dependencies '[^:displace [ns-tracker "0.4.0"]
                                                ^:displace [org.clojars.jansuran03/tornado "0.1.4"]
                                                ^:displace [me.raynes/fs "1.4.6"]]})

(defn tornado
  "Somethingggg"
  {:help-arglists '([once] [auto])
   :subtasks      [#'once #'auto]}
  [project command & builds?]
  (let [project (project/merge-profiles project [tornado-profile])]
    (validate-builds project)
    (case command "once" (once project builds?)
                  "auto" (auto project builds?)
                  (do (lein/info (when command (str "Unknown command: " command))
                                 (help/subtask-help-for *ns* #'tornado))
                      (lein/abort)))))