# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.2.2
Removed useless printlines.

## 0.2.1
Fixed a bug where the Lein-Tornado process terminated on reader's error because of `(require ... :reload)` not being in the `try` form.

## 0.2.0
Updated the version of Tornado dependency from 0.2.4 to 0.2.5.

## 0.1.9
Do not use! Equal to 0.1.8.

## 0.1.8
Updated the version of Tornado dependency from 0.2.3 to 0.2.4.

## 0.1.7
Updated the version of Tornado library dependency to 0.2.3.

## 0.1.6
Added release mode: "lein tornado release" which compiles and compresses all (or given) stylesheets. Updated Tornado dependency from 0.2.0 to 0.2.1.

## 0.1.5
Updated the version of Tornado library dependency to 0.2.0.

## 0.1.4
Improved exception handling while preparing the compilation by printing the caught exception on one line instead of
printing 40 lines of Java stacktrace.

Updated the version of Tornado library dependency to 0.1.7.

## 0.1.3
Fixed a bug which caused that individual builds could not be selected manually instead of recompiling all of the builds.

Updated the version of Tornado library dependency to 0.1.6.

## 0.1.2
Synchronized version 0.1.5 of Tornado with lein-tornado.

## 0.1.1
Synchronized version 0.1.4 of Tornado with lein-tornado.

## 0.1.0
Initial release of lein-tornado plugin.