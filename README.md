# lein-tornado

A Clojure plugin for automatic compilation of [Tornado](https://github.com/JanSuran03/tornado) stylesheets.

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.jansuran03/lein-tornado.svg)](https://clojars.org/org.clojars.jansuran03/lein-tornado)


# Tutorial

### Example Leiningen config:
```clojure
(defproject ...
            ...
            :plugins [...
                      [org.clojars.jansuran03/lein-tornado "0.2.3"]]
            :tornado {:source-paths     ["src/my-app/css"]
                      :output-directory "resources/public/css"
                      :builds           [{:id         :main
                                          :stylesheet my-app.css.core
                                          :compiler   {:pretty-print? false
                                                       :output-to     "main.css"}}
                                         {:id           :foo
                                          :source-paths ["src/my-app/css/foo"]
                                          :stylesheet   my-app.css.foo.core
                                          :compiler     {:output-to "foo.css"}}
                                         {:id           :common
                                          :source-paths ["src/my-app/common"]
                                          :stylesheet   my-app.common.css
                                          :compiler     {:output-to     "common.css"
                                                         :indent-length 3}}]}
            ...)
```

The `:source-paths` (if specified) is a vector of common source paths for all builds. Specifying `:source-paths` directly in a build
configuration will override the tornado-global `:source-paths`.

The `:output-directory` (if specified) is where all compiled stylesheets will be saved. The `:compiler -> :output-to` value will be appended
to this common path. If `:output-directory` is specified, you cannot (yet) tell this Tornado if you only want to go for paths
without this tornado-global prefix for specific builds. If you'd like to add this option (or anything else), please create an issue.

Each build must contain `:id`, `:stylesheet` and `:compiler -> :output-to`
- The `:id` is a key for Lein-tornado to know which build to compile. Even if you always compile all builds, you still have to specify the id.
- The `:stylesheet` is a fully-qualified symbol of the stylesheet you would like to be compiled.
- If build-specific `:source-paths` are specified, they have higher priority over the global ones, however,
- if no source paths are specified, it is an error.
- The `:compiler` specifies the options for the Tornado compiler:
  * `:output-path` is a relative path from the project root if no `:output-directory` is specified, otherwise a relative
     path to that directory is used.
  * `:pretty-print?` is an option whether you want to keep the CSS to be nicely formatted or compressed by the compiler.
  * `:indent-length` is an option where you can specify the indent length if the CSS is pretty-printed.

### There are several compilation options:
- `lein tornado once` - compiles all stylesheets once
- `lein tornado auto` - compiles all stylesheets, then watches for changes to compile them again
- `lein tornado release` - compiles all stylesheets once and compresses them regardless on the `:compiler -> :pretty-print?` option
- `lein tornado <command> main foo` - only builds with ids `:main` and `:foo` will be affected

## License

Copyright © 2023 Jan Šuráň

Distributed under the [Eclipse Public License](#http://www.eclipse.org/legal/epl-2.0.)