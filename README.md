# grease

An example of building a clojure library for iOS with graalvm native-image.


## Game of Life Example

See `examples/gol`

![game-of-life](/game-of-life.gif?raw=true)

## Public Beta

Try the public beta iOS app through test flight https://testflight.apple.com/join/GLOKMgFX

## Prerequisites

1. Download java's arm64 static libraries built for ios. They can be downloaded using `download-deps`

```sh

$ scripts/download-deps
```

2. Setup graalvm and make sure your clojure project is graalvm compatible. https://github.com/BrunoBonacci/graalvm-clojure

Make sure `GRAALVM_HOME` is set and is on your path before starting.

```
export GRAALVM_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java11-VERSION/Contents/Home
export PATH=$GRAALVM_HOME/bin:$PATH
```

3. Install [Graalvm LLVM Backend](https://www.graalvm.org/reference-manual/native-image/LLVMBackend/)

```sh
$ gu install llvm-toolchain
```

## Usage

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

## Slack

Questions? Comments? Connect with us on clojurians slack in [#graalvm-mobile](https://clojurians.slack.com/archives/C0260KHN0Q0) (join [here](http://clojurians.net/)).


## License

Copyright © 2021 Adrian

Distributed under the GPLv2 License. See LICENSE.
