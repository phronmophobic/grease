# Grease

An example of building a clojure library for iOS with graalvm native-image.

Built with [membrane](https://github.com/phronmophobic/membrane).

# Update - May 2026

Grease is back!

Check the [#graalvm-mobile](https://clojurians.slack.com/archives/C0260KHN0Q0) channel on the Clojurians Slack (join [here](http://clojurians.net/)) for the lastest news.

## Game of Life Example

See `examples/gol`

![game-of-life](/game-of-life.gif?raw=true)

## Media

Watch the project in action on the [Apropos Clojure Podcast](https://apropos-site.vercel.app/episode/54).  
[Show Notes](https://gist.github.com/ericnormand/aefbaace9b3731b26dd4dff770565271)

## Building basic test project

1. Download and update all submodules

```sh
git submodule update --init --recursive
```

2. Download dependencies

```sh
./scripts/download-deps
```

3. Install autoconf

```sh
brew install autoconf
```

4. Build static JDK

```sh
./scripts/setup
```

5. Build macOS native iame

```sh
./scripts/compile-shared
```

6. Build app

```sh
./scripts/build-shared
```

### Example projects

Found in `examples/` directory.

[examples/ants](examples/ants) - Classic ant sim  
[examples/gol](examples/gol) - Game of Life  
[examples/objc](examples/objc) - Objective-c interop  
[t3tr0s-bare](https://github.com/phronmophobic/t3tr0s-bare) - Tetris  
[snake](https://github.com/phronmophobic/programming-clojure) - Snake  

## License

Copyright © 2026 Adrian

Distributed under the GPLv2 License. See LICENSE.
