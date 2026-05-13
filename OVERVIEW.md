LLM generated overview doc, TODO: refine through Adrian brain.


I’ll inspect the repository structure, README, and scripts first so the terminology follows the project’s current shape instead of imposing a generic compiler model. After that I’ll trace the app/interpreter pieces and synthesize a documentation-ready taxonomy.

The repo already exposes the likely split: Clojure build tooling, GraalVM/iOS support, native Xcode hosts, and examples. I’m going to read the project docs and the core namespaces next, then follow the native bridge enough to name the runtime layers accurately.

There are two build lanes rather than one: a lean shared-library lane around `com.phronemophobic.grease`, and a richer Membrane/iOS lane around `com.phronemophobic.grease.ios`. I’m now checking how code is loaded/evaluated at runtime and how the host apps attach to it.

The runtime picture is clearer now: the native app owns iOS lifecycle, Metal/Skia drawing, touches, and WebView embedding; the Graal native image owns exported C entry points; SCI owns user-loaded Lisp/Clojure code inside that image. I’m grabbing line numbers for the main handoff points so the taxonomy can point back to real code.

I read the repo and would document it as a layered platform with two separate “compilation” stories:

**Core Distinction**
Grease does **AOT compilation of the host Lisp runtime**, then **runtime interpretation of user Lisp/Clojure scripts**.

That distinction will prevent confusion.
The iPhone app is not compiling every user script to native code.
It embeds a native Graal image that contains SCI, Membrane, ObjC/FFI helpers, and an nREPL-capable scripting environment.

**Recommended Taxonomy**

1. **Grease Toolchain**
   The desktop build path that prepares the iOS native runtime.
   This includes `deps.edn`, `build.clj`, GraalVM native-image, CAP cache, reflection/JNI/resource configs, and the `scripts/compile-*` commands.
   See [build.clj](/root/incubator/grease/build.clj:21), [scripts/compile-membrane](/root/incubator/grease/scripts/compile-membrane:20), and [scripts/compile-shared](/root/incubator/grease/scripts/compile-shared:20).

2. **Grease Runtime Image**
   The AOT-compiled Clojure runtime that becomes `bb.o` / `bb.a`.
   I’d use this term for the Graal native image output, not for user apps.
   There are two variants:
   `Core Runtime Image`, from `com.phronemophobic.grease`.
   `Membrane Runtime Image`, from `com.phronemophobic.grease.ios`.

3. **Native Entry Surface**
   The exported C ABI that iOS calls into.
   This includes functions like `clj_init`, `clj_draw`, touch handlers, keyboard handlers, and basic test functions.
   See [ios.clj](/root/incubator/grease/src/com/phronemophobic/grease/ios.clj:868).

4. **iOS Host Shell**
   The actual Xcode app that owns lifecycle, views, Metal, WebKit, and touch delivery.
   I’d call `TestSkia` the **Grease Workbench Host** because it is the real interactive shell.
   It creates the Graal isolate and calls `clj_init` in [GameViewController.mm](/root/incubator/grease/xcode/TestSkia/TestSkia/GameViewController.mm:44).
   It drives drawing through [Renderer.mm](/root/incubator/grease/xcode/TestSkia/TestSkia/Renderer.mm:217).
   It forwards touch and text input through [MembraneView.mm](/root/incubator/grease/xcode/TestSkia/TestSkia/MembraneView.mm:82).

5. **SCI Scripting Realm**
   The interpreted Lisp/Clojure environment inside the runtime image.
   This is where user code actually runs.
   It exposes selected namespaces, Membrane UI, ObjC interop, FFI helpers, filesystem helpers, HTTP, JDBC, core.async, etc.
   See [ios.clj](/root/incubator/grease/src/com/phronemophobic/grease/ios.clj:581).

6. **Workbench App**
   The default on-device app loaded from `app.clj`.
   It is a file browser, editor, evaluator, nREPL launcher, and widget host.
   See [ios.clj](/root/incubator/grease/src/com/phronemophobic/grease/ios.clj:762) and [app.clj](/root/incubator/grease/examples/app/app.clj:131).

7. **Script App**
   A user-facing Lisp/Clojure program loaded into SCI, usually from the phone’s `scripts` directory.
   Examples: Game of Life, Tetris, ObjC experiments.
   These are not native images.
   They are interpreted apps running inside the Grease Workbench.

8. **Widget**
   A script app that renders through `app/show!` into the Membrane surface.
   This term fits the existing API well.
   See [app.clj](/root/incubator/grease/examples/app/app.clj:457).

9. **Web Overlay**
   The WKWebView layer hosted by the native app.
   It is separate from the Membrane/SCI surface.
   See [GameViewController.mm](/root/incubator/grease/xcode/TestSkia/TestSkia/GameViewController.mm:72) and [GreaseWebOverlayController.m](/root/incubator/grease/xcode/TestSkia/TestSkia/GreaseWebOverlayController.m:17).

10. **Web App**
   Any HTML/JS app running in the Web Overlay.
   If it is ClojureScript, call it a **ClojureScript Web App**.
   It compiles to JavaScript and runs in JavaScriptCore/WebKit, not in SCI.

11. **Native Web Bridge**
   The `window.Grease` JavaScript API injected into the Web Overlay.
   It currently exposes an RPC-style bridge and device info.
   See [GreaseBridge.m](/root/incubator/grease/xcode/TestSkia/TestSkia/GreaseBridge.m:32).

**Short Documentation Hierarchy**

```text
Grease Platform
  Grease Toolchain
    Core Runtime Image
    Membrane Runtime Image
  iOS Host Shells
    MobileTest Host
    Grease Workbench Host
  Native Entry Surface
    C ABI exports
    Graal isolate lifecycle
  SCI Scripting Realm
    Workbench App
    Script Apps
    Widgets
    nREPL Session
  Web Overlay
    Web Apps
    ClojureScript Web Apps
    Native Web Bridge
```

**Terminology I’d Use Consistently**

`Runtime Image` for Graal/native-image output.

`Host Shell` for the Xcode/iOS app.

`Entry Surface` for exported C functions.

`Scripting Realm` for SCI.

`Workbench` for the default editable on-device environment.

`Script App` for interpreted Lisp/Clojure programs.

`Widget` for a script app mounted via `app/show!`.

`Web Overlay` for WKWebView.

`ClojureScript Web App` for CLJS compiled to JS and loaded into the overlay.

`Native Web Bridge` for `window.Grease`.

I did not modify files.
