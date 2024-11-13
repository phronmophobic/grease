# Grease

An example of building a clojure library for iOS with graalvm native-image.

Built with [membrane](https://github.com/phronmophobic/membrane).

# Update - Nov 2024

Grease is in active development and the docs are a bit out of date. For the latest info, check the [#graalvm-mobile](https://clojurians.slack.com/archives/C0260KHN0Q0) channel on the Clojurians Slack (join [here](http://clojurians.net/)).

## Game of Life Example

See `examples/gol`

![game-of-life](/game-of-life.gif?raw=true)

## Media

Watch the project in action on the [Apropos Clojure Podcast](https://apropos-site.vercel.app/episode/54).  
[Show Notes](https://gist.github.com/ericnormand/aefbaace9b3731b26dd4dff770565271)

## Prerequisites

1. Download java's arm64 static libraries built for ios. They can be downloaded using `download-deps`

```sh
$ scripts/download-deps
```

2. Setup graalvm and make sure your clojure project is graalvm compatible. https://github.com/BrunoBonacci/graalvm-clojure. These examples were tested using https://github.com/gluonhq/graal/releases/tag/gluon-22.1.0.1-Final. Other versions may or may not work.

Make sure `GRAALVM_HOME` is set and is on your path before starting.

```
exportJAVA_HOME=<path-to-graalvm>/Contents/Home
export GRAALVM_HOME="$JAVA_HOME"
export PATH=$JAVA_HOME/bin:$PATH
```

## Usage

0. [prerequisites](#prerequisites)
1. Compile your clojure project

```sh

$ ./scripts/compile-shared

```
This can take a while. 

2. Open the xcode project in xcode/MobileTest/MobileTest.xcodeproj  
3. Select "Any iOS Device(arm64)" or your connected device as the build target. (iOS Simulator not supported yet)
4. Build and run



## Membrane Example

An example project that uses [membrane](https://github.com/phronmophobic/membrane) for UI can be found under xcode/TestSkia/TestSkia.xcodeproj. It also starts a sci repl that can be used for interactive development. Simply connect to the repl and start hacking! To update the UI, just `reset!` the main view atom. Example scripts below.

### Usage

0. [prerequisites](#prerequisites)
1. Compile `./scripts/compile-membrane`
2. Open xcode/TestSkia/TestSkia.xcodeproj
3. Build and run
4. The console will print the IP addresses available. Connect to repl on your device using the IP address and port 23456.
5. Hack away!

### Example scripts

Hello World!

```clojure
(require '[membrane.ui :as ui])
(require '[com.phronemophobic.grease.membrane :refer
           [main-view]])

(def red [1 0 0])

(reset! main-view
        (ui/translate 50 100
                      (ui/with-color red
                        (ui/label "Hello World!"
                                  (ui/font nil 42)))))
```

Simple Counter

```clojure
(require '[membrane.ui :as ui])
(require '[com.phronemophobic.grease.membrane :refer
           [main-view]])


(def my-count (atom 0))

(defn counter-view []
  (ui/translate 50 100
                (ui/on
                 :mouse-down
                 (fn [pos]
                   (swap! my-count inc)
                   nil)
                 (ui/label (str "the count "
                                @my-count)
                           (ui/font nil 42)))))


(add-watch my-count ::update-view (fn [k ref old updated]
                                    (reset! main-view (counter-view))))

(reset! my-count 0)
```

Basic Drawing

```clojure

(require '[membrane.ui :as ui])
(require '[com.phronemophobic.grease.membrane :refer
           [main-view]])


(def pixels (atom []))

(defn view []
  (ui/on
   :mouse-down
   (fn [pos]
     (swap! pixels conj pos))
   [(ui/rectangle 600 800)
    (into []
          (map (fn [[x y]]
                 (ui/translate x y
                               (ui/with-color [0 0 1 1]
                                 (ui/rectangle 10 10)))))
          @pixels
     )])
  )

(add-watch pixels ::update-view (fn [k ref old updated]
                                  (reset! main-view (view))))

(reset! pixels [])
```

### Example projects

Found in `examples/` directory.

[examples/ants](examples/ants) - Classic ant sim  
[examples/gol](examples/gol) - Game of Life  
[examples/objc](examples/objc) - Objective-c interop  
[t3tr0s-bare](https://github.com/phronmophobic/t3tr0s-bare) - Tetris  
[snake](https://github.com/phronmophobic/programming-clojure) - Snake  




## Notes

The key ingredients for creating binaries for iOS using native-image are:
- static jdk libraries build for iOS
- static native image libraries built for ios (see ./lib/ios-arm64)
- various configs (see ./conf/).

In addition to gluon, there have also been some other forks that have tried to make building the static jdk and native libs easier. See https://graalvm.slack.com/archives/CN9KSFB40/p1714544531823089 on the graalvm slack.


## License

Copyright © 2024 Adrian

Distributed under the GPLv2 License. See LICENSE.
