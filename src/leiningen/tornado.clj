(ns leiningen.tornado
  (:refer-clojure :exclude [assert])
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [leiningen.core.main :as lein]
            [clojure.java.io :as io]
            [leiningen.core.project :as project]
            [leiningen.core.eval :refer [eval-in-project]]
            [leiningen.help :as help]))

(defn- exit
  "Prints the contents to the console, then exits."
  [& args]
  (let [[print-exit args] (if (= (first args) :no-print-exit)
                            [false (next args)]
                            [true args])]
    (apply lein/info args)
    (when print-exit
      (lein/info "Exiting lein-tornado..."))
    (lein/abort)))

(defmacro assert [form & msg-args]
  `(when-not ~form
     (exit ~@msg-args)))

(defn- validate-builds
  "For each build, validates id, stylesheet and source paths. Throws an exception if some builds are not valid."
  [project builds]
  (doseq [{:keys [id stylesheet source-paths compiler]} builds
          :let [{:keys [output-to]} compiler]]
    (assert ((some-fn keyword? string?) id)
            "The build id must be a string or a keyword: " id)
    (assert (or source-paths (:source-paths project))
            (str "No source paths specified for a build: " (name id) "."
                 "Please specify global or build-specific source paths."))
    (assert stylesheet
            "No stylesheet specified for a build: " (name id))
    (assert (symbol? stylesheet)
            "Stylesheet value must be a symbol: " stylesheet " in a build: " (name id))
    (assert output-to
            "No specified output path (:output-to) for a compiled CSS build: " (name id))))

(defn- all-builds
  "Returns a sequence of Tornado builds."
  [project]
  (let [builds (-> project :tornado :builds)
        builds (cond->> builds
                        (map? builds)
                        (map (fn [[build-id build]]
                               (assoc build :id build-id))))]
    (validate-builds project builds)
    builds))

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
        (exit "Unknown build id: " build-id)))))

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
      (assert (fs/mkdirs output-to-dir)                   ;; Returns true on success, false on fail
              "Failed creating a directory: " output-to-dir))))

(defn- compile-builds
  "Automatically recompiles modified builds when needed, or once if watch? is not truthy."
  [{:keys [tornado-source-paths]} builds watch?]
  (let [stylesheets (map :stylesheet builds)
        _ (assert (every? qualified-symbol? (map :stylesheet builds))
                  (str "All stylesheets symbol must be fully qualified unquoted symbols, "
                       "e.g. my-project.css/stylesheet. Problematic stylesheets: ["
                       (str/join ", " (filter (complement qualified-symbol?) stylesheets))
                       "]"))
        all-stylesheet-namespaces (mapv #(-> % symbol namespace) stylesheets) ;;initial compilation of all stylesheets
        tornado-source-paths (vec tornado-source-paths)]
    `(let [modified-namespaces# (ns-tracker/ns-tracker ~tornado-source-paths)]
       (loop [modified-nss# ~all-stylesheet-namespaces]
         (when (seq modified-nss#)
           (try
             (println "Reloading modified namespaces...")
             (doseq [ns# modified-nss#]
               (require (symbol ns#) :reload))
             (println "Namespaces reloaded.")
             (doseq [build# ~builds]
               (let [{stylesheet# :stylesheet
                      id#         :id
                      flags#      :compiler} build#]
                 (println "   Compiling build" (name id#) "...")
                 (compiler/css flags# stylesheet#)
                 (println "   Successful.")))
             (println "All builds were successfully recompiled.")
             (catch Exception e#
               (println "Error: " (.getMessage e#)))))
         (when ~watch?
           (Thread/sleep 500)
           ;; A function to return a set of modified namespaces is called, called every 500 milliseconds.
           (recur (modified-namespaces#)))))))

(defn- run-compiler
  "Starts the compilation process."
  [{:keys [tornado]
    :as   project} args compilation-type]
  (assert tornado
          "No tornado config specified (:tornado key in project.clj)")
  (let [{:keys [output-dir output-directory source-paths]} tornado
        builds (if (seq args)
                 (find-builds project args)
                 (all-builds project))
        output-directory (or output-directory output-dir)
        builds (cond->> builds
                        (= compilation-type :release) (map #(update % :compiler assoc :pretty-print? false))
                        ;; build outputs can share the same parent folder
                        output-directory (map (fn [build] (update-in build [:compiler :output-to] #(str output-directory \/ %))))
                        ;; builds can share common source paths, but source paths for a specific build have a higher precedence
                        source-paths (map (fn [build] (if (:source-paths build) build (assoc build :source-paths source-paths))))
                        true vec)
        watch? (if (= compilation-type :auto)
                 true
                 false)
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
  (run-compiler project args :once))

(defn- auto
  "Compiles Tornado stylesheets once and recompiles them whenever they are modified. Args are optional specific builds to be compiled."
  [project args]
  (run-compiler project args :auto))

(defn- release
  "Compiles Tornado stylesheet and compresses the output CSS. Args are optional specific builds to be compiled."
  [project args]
  (run-compiler project args :release))

(def ^:private tornado-profile {:dependencies '[^:displace [ns-tracker "0.4.0"]
                                                ^:displace [org.clojars.jansuran03/tornado "0.2.5"]
                                                ^:displace [me.raynes/fs "1.4.6"]]})

; TODO: (tornado auto) compresses the build if pretty-print? is set to false only for the first time, after recompilation not anymore for some reason...

(def project-url "https://github.com/JanSuran03/lein-tornado")

(defn tornado
  "A function for evaluation in the terminal:
  \"lein tornado once\" - compiles all stylesheets once
  \"lein tornado auto\" - recompiles all stylesheets to provide hot-code reloading.
  \"lein tornado release\" - compiles and compresses all stylesheets.
  \"lein tornado help\" - prints help
  \"lein tornado <once/auto/release> & builds - compiles only specific builds."
  {:help-arglists '([once & builds?] [auto & builds?] [release & builds?] [help])
   :subtasks      [#'once #'auto #'release]}
  [project command & builds?]
  (let [project (project/merge-profiles project [tornado-profile])]
    (case command "once" (once project builds?)
                  "auto" (auto project builds?)
                  "release" (release project builds?)
                  "help" (exit :no-print-exit "For documentation, see" project-url)
                  (exit (when command (str "Unknown command: " command))
                        (help/subtask-help-for *ns* #'tornado)))))
